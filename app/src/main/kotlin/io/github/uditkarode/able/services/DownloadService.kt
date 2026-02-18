/*
    Copyright 2020 Udit Karode <udit.karode@gmail.com>

    This file is part of AbleMusicPlayer.

    AbleMusicPlayer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    AbleMusicPlayer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AbleMusicPlayer.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.github.uditkarode.able.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.PreferenceManager
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.uditkarode.able.R
import io.github.uditkarode.able.model.DownloadableSong
import io.github.uditkarode.able.model.Format
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class DownloadService : Service() {
    companion object {
        private const val NOTIF_ID = 2
        private const val DL_CHANNEL_ID = "AbleMusicDownloadProgress"
        private const val MAX_RETRIES = 3
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
        private val activeLinks = mutableSetOf<String>()
        var onDownloadComplete: (() -> Unit)? = null

        fun isAlreadyQueued(youtubeLink: String): Boolean {
            synchronized(activeLinks) {
                return activeLinks.contains(youtubeLink)
            }
        }
    }

    private lateinit var builder: Notification.Builder
    private lateinit var notificationManager: NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    // Disable connection pooling so each Range chunk gets a fresh TCP connection,
    // bypassing YouTube's per-connection throttling
    private val okClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .build()
    private val queue = LinkedBlockingQueue<DownloadableSong>()
    @Volatile private var workerRunning = false
    @Volatile private var hadError = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(NOTIF_ID, builder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val song: ArrayList<String> = intent?.getStringArrayListExtra("song") ?: run {
            if (queue.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        if (song.size < 4) {
            if (queue.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }

        val dlSong = DownloadableSong(song[0], song[2], song[1], song[3])

        synchronized(activeLinks) {
            if (!activeLinks.add(dlSong.youtubeLink)) {
                return START_NOT_STICKY
            }
        }

        queue.add(dlSong)

        val notifName =
            if (dlSong.name.length > 25) dlSong.name.substring(0, 25) + "..." else dlSong.name
        builder.setContentTitle(notifName)
        builder.setContentText(getString(R.string.init_dl))
        builder.setProgress(0, 0, true)
        builder.setOngoing(true)
        notificationManager.notify(NOTIF_ID, builder.build())

        if (!workerRunning) {
            workerRunning = true
            hadError = false
            thread {
                processQueue()
                workerRunning = false
                if (hadError) {
                    // Detach notification so it stays visible after service stops
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun processQueue() {
        while (true) {
            val song = queue.poll() ?: break
            download(song)
            synchronized(activeLinks) {
                activeLinks.remove(song.youtubeLink)
            }
        }
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIF_ID, builder.build())
    }

    private fun downloadFile(url: String, tempFile: File): Boolean {
        // YouTube throttles per TCP connection. Using small Range chunks with
        // no connection pooling means each chunk gets a fresh connection and
        // avoids the throttle.
        val chunkSize = 256L * 1024 // 256KB per chunk

        // Get content length
        val headReq = Request.Builder()
            .url(url).head()
            .addHeader("User-Agent", USER_AGENT)
            .build()
        val headResp = okClient.newCall(headReq).execute()
        val contentLength = headResp.header("Content-Length")?.toLongOrNull() ?: -1L
        headResp.close()

        if (contentLength <= 0) {
            Log.d("DL>", "Unknown content length, falling back to single request")
            return downloadFileSingle(url, tempFile)
        }

        val numChunks = (contentLength + chunkSize - 1) / chunkSize
        Log.d("DL>", "Content-Length: $contentLength (${contentLength / 1024}KB), $numChunks chunks")

        val oStream = FileOutputStream(tempFile)
        var downloaded = 0L
        var lastNotifiedProgress = -1
        val startTime = System.currentTimeMillis()

        while (downloaded < contentLength) {
            val rangeEnd = minOf(downloaded + chunkSize - 1, contentLength - 1)

            for (attempt in 1..MAX_RETRIES) {
                try {
                    if (attempt > 1) {
                        Log.d("DL>", "Chunk retry $attempt for bytes $downloaded-$rangeEnd")
                        Thread.sleep(1000L * attempt)
                    }

                    val req = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Range", "bytes=$downloaded-$rangeEnd")
                        .addHeader("Connection", "close")
                        .build()
                    val resp = okClient.newCall(req).execute()
                    val body = resp.body!!
                    val iStream = BufferedInputStream(body.byteStream())

                    val data = ByteArray(65536)
                    var read: Int
                    while (iStream.read(data).also { read = it } != -1) {
                        oStream.write(data, 0, read)
                        downloaded += read

                        val progress = ((downloaded * 100) / contentLength).toInt()
                        if (progress / 5 != lastNotifiedProgress / 5) {
                            lastNotifiedProgress = progress
                            builder.setProgress(100, progress, false)
                            builder.setContentText("$progress%")
                            updateNotification()
                        }
                    }

                    iStream.close()
                    resp.close()
                    break // chunk succeeded
                } catch (e: SocketException) {
                    Log.e("DL>", "Chunk attempt $attempt failed: ${e.message}")
                    if (attempt == MAX_RETRIES) {
                        oStream.close()
                        tempFile.delete()
                        throw e
                    }
                }
            }
        }

        oStream.flush()
        oStream.close()

        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        val speedKBs = if (elapsed > 0) (contentLength / 1024.0 / elapsed).toInt() else 0
        Log.d("DL>", "Download complete: ${contentLength / 1024}KB in ${elapsed.toInt()}s ($speedKBs KB/s)")
        return true
    }

    /** Fallback for when content length is unknown */
    private fun downloadFileSingle(url: String, tempFile: File): Boolean {
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        val resp = okClient.newCall(req).execute()
        val body = resp.body!!
        val iStream = BufferedInputStream(body.byteStream())
        val oStream = FileOutputStream(tempFile)

        val data = ByteArray(65536)
        var read: Int
        while (iStream.read(data).also { read = it } != -1) {
            oStream.write(data, 0, read)
        }

        oStream.flush()
        oStream.close()
        iStream.close()
        resp.close()
        return true
    }

    private fun download(song: DownloadableSong) {
        val id = song.youtubeLink.run {
            this.substring(this.lastIndexOf("=") + 1)
        }

        val notifName =
            if (song.name.length > 25) song.name.substring(0, 25) + "..." else song.name

        builder.setContentTitle(notifName)
        builder.setContentText(getString(R.string.init_dl))
        builder.setProgress(0, 0, true)
        updateNotification()

        // Save album art
        if (song.ytmThumbnailLink.isNotBlank()) {
            try {
                val drw = Glide
                    .with(this@DownloadService)
                    .load(song.ytmThumbnailLink)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .submit()
                    .get()

                if (!Constants.albumArtDir.exists()) Constants.albumArtDir.mkdirs()
                val img = File(Constants.albumArtDir, id)
                Shared.saveAlbumArtToDisk(drw.toBitmap(), img)
            } catch (e: Exception) {
                Log.e("ERR>", "Failed to save album art: $e")
            }
        }

        try {
            Log.d("DL>", "Resolving stream for ${song.name}")
            val streamInfo = StreamInfo.getInfo(song.youtubeLink)

            // Pick the highest bitrate audio stream
            val stream = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
                ?: streamInfo.audioStreams[0]

            val url = stream.content
            val bitrate = stream.averageBitrate
            val ext = stream.getFormat()!!.suffix
            Log.d("DL>", "Selected stream: ${bitrate}kbps $ext")

            if (!Constants.ableSongDir.exists()) {
                val mkdirs = Constants.ableSongDir.mkdirs()
                if (!mkdirs) throw IOException("Could not create output directory: ${Constants.ableSongDir}")
            }

            val tempFile = File(Constants.ableSongDir, "$id.tmp.$ext")

            builder.setContentText("0%")
            builder.setProgress(100, 0, false)
            updateNotification()

            downloadFile(url, tempFile)

            // FFmpeg transcode / metadata
            builder.setContentText(getString(R.string.saving))
            builder.setProgress(100, 100, true)
            updateNotification()

            val format =
                if (PreferenceManager.getDefaultSharedPreferences(this)
                        .getString("format_key", "webm") == "mp3"
                ) Format.MODE_MP3
                else Format.MODE_WEBM

            var command = "-i " +
                    "\"${tempFile.absolutePath}\" -c copy " +
                    "-metadata title=\"${song.name}\" " +
                    "-metadata artist=\"${song.artist}\" -y "

            val mp3Bitrate = maxOf(bitrate, 128)
            if (format == Format.MODE_MP3 || Build.VERSION.SDK_INT <= Build.VERSION_CODES.M)
                command += "-vn -ab ${mp3Bitrate}k -c:a mp3 -ar 44100 "

            command += "\"${Constants.ableSongDir.absolutePath}/$id."
            command += if (format == Format.MODE_MP3) "mp3\"" else "$ext\""

            Log.d("DL>", "FFmpeg command: $command")

            when (val rc = FFmpeg.execute(command)) {
                Config.RETURN_CODE_SUCCESS -> {
                    tempFile.delete()
                    Log.d("DL>", "FFmpeg success, notifying Home")
                    mainHandler.post { onDownloadComplete?.invoke() }
                }

                Config.RETURN_CODE_CANCEL -> {
                    Log.e("ERR>", "FFmpeg cancelled.")
                    showError("Download cancelled")
                    return
                }

                else -> {
                    Log.e("ERR>", "FFmpeg failed with rc=$rc")
                    showError("Conversion failed")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("ERR>", "Download failed: $e")
            showError("Download failed")
            return
        }

        if (queue.isEmpty()) {
            notificationManager.cancel(NOTIF_ID)
        }
    }

    private fun showError(message: String) {
        hadError = true
        builder.setContentText(message)
        builder.setOngoing(false)
        builder.setProgress(0, 0, false)
        updateNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                DL_CHANNEL_ID,
                "Download Progress",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationChannel.enableLights(false)
            notificationChannel.enableVibration(false)
            notificationChannel.setSound(null, null)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, DL_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.apply {
            setContentTitle(getString(R.string.init_dl))
            setContentText(getString(R.string.pl_wait))
            setSmallIcon(R.drawable.ic_download_icon)
            setOngoing(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
        }
    }
}

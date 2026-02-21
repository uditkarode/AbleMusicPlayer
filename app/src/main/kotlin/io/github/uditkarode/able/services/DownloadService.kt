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
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.uditkarode.able.R
import io.github.uditkarode.able.model.DownloadableSong
import io.github.uditkarode.able.utils.ChunkedDownloader
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class DownloadService : Service() {
    companion object {
        private const val NOTIF_ID = 2
        private const val DL_CHANNEL_ID = "AbleMusicDownloadProgress"
        private val activeLinks = mutableSetOf<String>()
        var onDownloadComplete: (() -> Unit)? = null
        @Volatile var downloadCompletedSinceLastCheck = false

        fun isAlreadyQueued(youtubeLink: String): Boolean {
            synchronized(activeLinks) {
                return activeLinks.contains(youtubeLink)
            }
        }
    }

    private lateinit var builder: Notification.Builder
    private lateinit var notificationManager: NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = LinkedBlockingQueue<DownloadableSong>()
    @Volatile private var workerRunning = false
    @Volatile private var hadError = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(NOTIF_ID, builder.build())
        restoreQueue()
        if (queue.isNotEmpty() && !workerRunning) {
            workerRunning = true
            hadError = false
            thread {
                processQueue()
                workerRunning = false
                if (hadError) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        persistQueue()
        super.onDestroy()
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
        persistQueue()

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
            persistQueue()
        }
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIF_ID, builder.build())
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

            ChunkedDownloader.download(url, tempFile) { progress ->
                builder.setProgress(100, progress, false)
                builder.setContentText("$progress%")
                updateNotification()
            }

            // FFmpeg transcode / metadata
            builder.setContentText(getString(R.string.saving))
            builder.setProgress(100, 100, true)
            updateNotification()

            var command = "-i " +
                    "\"${tempFile.absolutePath}\" -c copy " +
                    "-metadata title=\"${song.name}\" " +
                    "-metadata artist=\"${song.artist}\" -y "

            val mp3Bitrate = maxOf(bitrate, 128)
            command += "-vn -ab ${mp3Bitrate}k -c:a mp3 -ar 44100 "

            command += "\"${Constants.ableSongDir.absolutePath}/$id.mp3\""

            Log.d("DL>", "FFmpeg command: $command")

            val session = FFmpegKit.execute(command)
            when {
                ReturnCode.isSuccess(session.returnCode) -> {
                    tempFile.delete()
                    val mp3File = File(Constants.ableSongDir, "$id.mp3")

                    // Embed album art into MP3 metadata
                    try {
                        Shared.addThumbnails(mp3File.absolutePath, song.name, this@DownloadService)
                    } catch (e: Exception) {
                        Log.e("ERR>", "Failed to embed album art: $e")
                    }

                    // Rename file from YouTube ID to song name
                    val sanitizedName = sanitizeFileName(song.name)
                    val targetFile = File(Constants.ableSongDir, "$sanitizedName.mp3")
                    val finalFile = if (targetFile.exists()) {
                        File(Constants.ableSongDir, "$sanitizedName ($id).mp3")
                    } else targetFile
                    mp3File.renameTo(finalFile)
                    // Also rename sidecar album art to match
                    val artFile = File(Constants.albumArtDir, id)
                    if (artFile.exists()) {
                        artFile.renameTo(File(Constants.albumArtDir, finalFile.nameWithoutExtension))
                    }

                    Log.d("DL>", "FFmpeg success, notifying Home")
                    downloadCompletedSinceLastCheck = true
                    mainHandler.post { onDownloadComplete?.invoke() }
                }

                ReturnCode.isCancel(session.returnCode) -> {
                    Log.e("ERR>", "FFmpeg cancelled.")
                    showError("Download cancelled")
                    return
                }

                else -> {
                    Log.e("ERR>", "FFmpeg failed with rc=${session.returnCode}")
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
            builder.setContentTitle(getString(R.string.app_name))
            builder.setContentText("Downloads complete")
            builder.setOngoing(false)
            builder.setProgress(0, 0, false)
            updateNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    private fun persistQueue() {
        try {
            val prefs = getSharedPreferences("download_queue", Context.MODE_PRIVATE)
            val gson = Gson()
            val pending = queue.toList()
            prefs.edit()
                .putString("pending", gson.toJson(pending))
                .apply()
        } catch (e: Exception) {
            Log.e("ERR>", "Failed to persist queue: $e")
        }
    }

    private fun restoreQueue() {
        try {
            val prefs = getSharedPreferences("download_queue", Context.MODE_PRIVATE)
            val json = prefs.getString("pending", null) ?: return
            val gson = Gson()
            val type = object : TypeToken<List<DownloadableSong>>() {}.type
            val pending: List<DownloadableSong> = gson.fromJson(json, type) ?: return
            for (song in pending) {
                synchronized(activeLinks) {
                    if (activeLinks.add(song.youtubeLink)) {
                        queue.add(song)
                    }
                }
            }
            prefs.edit().remove("pending").apply()
        } catch (e: Exception) {
            Log.e("ERR>", "Failed to restore queue: $e")
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
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

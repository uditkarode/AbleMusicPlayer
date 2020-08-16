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
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.PreferenceManager
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2.Fetch.Impl.getInstance
import com.tonyodev.fetch2core.DownloadBlock
import io.github.uditkarode.able.R
import io.github.uditkarode.able.models.DownloadableSong
import io.github.uditkarode.able.models.Format
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.IOException

/**
 * The JobIntentService that downloads songs when the play mode is set to download mode
 * and a user taps on a search result.
 */
class DownloadService : JobIntentService(), CoroutineScope {
    companion object {
        private var songQueue = ArrayList<DownloadableSong>()
        private const val JOB_ID = 1000
        private var fetch: Fetch? = null

        fun enqueueDownload(context: Context, intent: Intent) {
            enqueueWork(context, DownloadService::class.java, JOB_ID, intent)
        }
    }

    private var currentIndex = 1
    private lateinit var builder: Notification.Builder
    private lateinit var notification: Notification

    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
    }

    override fun onCreate() {
        createNotificationChannel()
        super.onCreate()
        if (fetch == null) {
            fetch = getInstance(
                FetchConfiguration.Builder(this)
                    .setDownloadConcurrentLimit(1)
                    .build()
            )
        }
    }

    override fun onHandleWork(p0: Intent) {
        val song: ArrayList<String> = p0.getStringArrayListExtra("song") ?: arrayListOf()
        val mResultReceiver = p0.getParcelableExtra<ResultReceiver>("receiver")!!
        songQueue.add(DownloadableSong(song[0], song[2], song[1], song[3], mResultReceiver))
        if (songQueue.size == 1) download(songQueue[0])
        else {
            NotificationManagerCompat.from(this@DownloadService).apply {
                builder.setSubText("$currentIndex of ${songQueue.size}")
                notify(2, builder.build())
            }
        }
    }

    private fun download(song: DownloadableSong) {
        launch(Dispatchers.IO) {
            if (song.ytmThumbnailLink.isNotBlank()) {
                val drw = Glide
                    .with(this@DownloadService)
                    .load(song.ytmThumbnailLink)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .submit()
                    .get()


                val id = song.youtubeLink.run {
                    this.substring(this.lastIndexOf("=") + 1)
                }

                val img = File(Constants.ableSongDir.absolutePath + "/album_art", id)
                Shared.saveAlbumArtToDisk(drw.toBitmap(), img)
            }
        }

        launch(Dispatchers.IO) {
            val bundle = Bundle()
            val id = song.youtubeLink.run {
                this.substring(this.lastIndexOf("=") + 1)
            }
            NotificationManagerCompat.from(this@DownloadService).apply {
                builder.setSubText("$currentIndex of ${songQueue.size} ")
                builder.setContentText("${song.name} ${this@DownloadService.getString(R.string.starting)}")
                builder.setOngoing(true)
                notify(2, builder.build())
            }

            val streamInfo = StreamInfo.getInfo(song.youtubeLink)
            val stream = streamInfo.audioStreams.run { this[this.size - 1] }

            val url = stream.url
            val bitrate = stream.averageBitrate
            val ext = stream.getFormat().suffix
            val mediaFile = File(Constants.ableSongDir, id)

            if (!Constants.ableSongDir.exists()) {
                val mkdirs = Constants.ableSongDir.mkdirs()
                if (!mkdirs) throw IOException("Could not create output directory: ${Constants.ableSongDir}")
            }

            val notifName =
                if (song.name.length > 25) song.name.substring(0, 25) + "..." else song.name

            NotificationManagerCompat.from(this@DownloadService).apply {
                builder.setContentTitle(notifName)
                builder.setOngoing(true)
                notify(2, builder.build())
            }
            try {
                val request = Request(url, mediaFile.absolutePath).also {
                    it.priority = Priority.HIGH
                    it.networkType = NetworkType.ALL
                }

                fetch?.addListener(object : FetchListener {
                    override fun onAdded(download: Download) {}

                    override fun onCancelled(download: Download) {}

                    override fun onCompleted(download: Download) {
                        NotificationManagerCompat.from(this@DownloadService).apply {
                            builder.setContentText(this@DownloadService.getString(R.string.saving))
                                .setProgress(100, 100, true)
                            builder.setOngoing(true)
                            notify(2, builder.build())
                        }

                        var name = song.name
                        name = name.replace(
                            Regex("${song.artist}\\s[-,:]?\\s"),
                            ""
                        )
                        name = name.replace(
                            Regex("\\(([Oo]]fficial)?\\s([Mm]usic)?\\s([Vv]ideo)?\\s\\)"),
                            ""
                        )
                        name = name.replace(
                            Regex("\\(\\[?[Ll]yrics?\\)]?\\s?([Vv]ideo)?\\)?"),
                            ""
                        )
                        name =
                            name.replace(Regex("\\(?[aA]udio\\)?\\s"), "")
                        name = name.replace(Regex("\\[Lyrics?]"), "")
                        name = name.replace(
                            Regex("\\(Official\\sMusic\\sVideo\\)"),
                            ""
                        )
                        name = name.replace(Regex("\\[HD\\s&\\sHQ]"), "")
                        val target = mediaFile.absolutePath.toString()

                        var command = "-i " +
                                "\"${target}\" -y -c copy " +
                                "-metadata title=\"${name}\" " +
                                "-metadata artist=\"${song.artist}\" "
                        val format =
                            if (PreferenceManager.getDefaultSharedPreferences(
                                    this@DownloadService
                                )
                                    .getString("format_key", "webm") == "mp3"
                            ) Format.MODE_MP3
                            else Format.MODE_WEBM
                        if (format == Format.MODE_MP3 || Build.VERSION.SDK_INT <= Build.VERSION_CODES.M)
                            command += "-vn -ab ${bitrate}k -c:a mp3 -ar 44100 "

                        command += "\"${Constants.ableSongDir.absolutePath}/$id."
                        command += if (format == Format.MODE_MP3) "mp3\"" else "$ext\""
                        Log.e("asd", "DOING")
                        when (val rc = FFmpeg.execute(command)) {
                            Config.RETURN_CODE_SUCCESS -> {
                                File(target).delete()
                                if (currentIndex == songQueue.size) {
                                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
                                        it.cancel(2)
                                    }
                                    song.resultReceiver.send(123, bundle)
                                    if (format == Format.MODE_MP3)
                                        Shared.addThumbnails(
                                            "$target.mp3",
                                            context = this@DownloadService
                                        )
                                    songQueue.clear()
                                    stopSelf()
                                } else {
                                    (++currentIndex).also {
                                        builder.setSubText("$it of ${songQueue.size}")
                                    }
                                    song.resultReceiver.send(123, bundle)
                                    download(songQueue[currentIndex - 1])
                                }
                            }
                            Config.RETURN_CODE_CANCEL -> {
                                Log.e(
                                    "ERR>",
                                    "Command execution cancelled by user."
                                )
                            }
                            else -> {
                                Log.e(
                                    "ERR>",
                                    String.format(
                                        "Command execution failed with rc=%d and the output below.",
                                        rc
                                    )
                                )
                            }
                        }
                    }

                    override fun onDeleted(download: Download) {}

                    override fun onDownloadBlockUpdated(
                        download: Download,
                        downloadBlock: DownloadBlock,
                        totalBlocks: Int
                    ) {

                    }

                    override fun onError(
                        download: Download,
                        error: Error,
                        throwable: Throwable?
                    ) {

                    }

                    override fun onPaused(download: Download) {

                    }

                    override fun onProgress(
                        download: Download,
                        etaInMilliSeconds: Long,
                        downloadedBytesPerSecond: Long
                    ) {
                        NotificationManagerCompat.from(this@DownloadService).apply {
                            builder.setProgress(100, download.progress, false)
                            builder.setOngoing(true)
                            builder.setSubText("$currentIndex of ${songQueue.size}")
                            builder.setContentText(
                                "${etaInMilliSeconds / 1000}s ${
                                    this@DownloadService.getString(
                                        R.string.left
                                    )
                                }"
                            )
                            notify(2, builder.build())
                        }
                    }

                    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {

                    }

                    override fun onRemoved(download: Download) {

                    }

                    override fun onResumed(download: Download) {

                    }

                    override fun onStarted(
                        download: Download,
                        downloadBlocks: List<DownloadBlock>,
                        totalBlocks: Int
                    ) {

                    }

                    override fun onWaitingNetwork(download: Download) {
                        NotificationManagerCompat.from(this@DownloadService).apply {
                            builder.setContentText("Waiting for network...")
                                .setProgress(100, 100, true)
                            builder.setOngoing(true)
                            notify(2, builder.build())
                        }
                    }
                })

                fetch?.enqueue(request)
            } catch (e: Exception) {
                Log.e("ERR>", e.toString())
            }
        }
    }

    private fun createNotificationChannel() {
        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, Constants.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.apply {
            setContentTitle(getString(R.string.init_dl))
            setContentText(getString(R.string.pl_wait))
            setSmallIcon(R.drawable.ic_download_icon)
            builder.setOngoing(true)
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                Constants.CHANNEL_ID,
                "AbleMusicDownload",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.enableLights(false)
            notificationChannel.enableVibration(false)
            notificationManager.createNotificationChannel(notificationChannel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = builder.setChannelId(Constants.CHANNEL_ID).build()
        } else {
            notification = builder.build()
            notificationManager.notify(2, notification)
        }
    }
}

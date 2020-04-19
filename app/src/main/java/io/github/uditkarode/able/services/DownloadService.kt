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
import androidx.preference.PreferenceManager
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.github.kiulian.downloader.YoutubeDownloader
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2.Fetch.Impl.getInstance
import com.tonyodev.fetch2core.DownloadBlock
import io.github.uditkarode.able.R
import io.github.uditkarode.able.models.Format
import io.github.uditkarode.able.utils.Constants
import java.io.File
import java.io.IOException

class DownloadService : JobIntentService() {
    companion object {
        private var queueSize = 0
        private const val JOB_ID = 1000
        private var fetch: Fetch? = null

        fun enqueueDownload(context: Context, intent: Intent) {
            enqueueWork(context, DownloadService::class.java, JOB_ID, intent)
            queueSize += 1
        }
    }

    private var currentQueue = 0
    private lateinit var mResultReceiver: ResultReceiver
    private lateinit var builder: Notification.Builder
    private lateinit var notification: Notification

    override fun onCreate() {
        createNotificationChannel()
        super.onCreate()
        if(fetch == null){
            fetch = getInstance(
                FetchConfiguration.Builder(this)
                    .setDownloadConcurrentLimit(1)
                    .build()
            )
        }
    }

    override fun onHandleWork(p0: Intent) {
        currentQueue = +1
        val song: java.util.ArrayList<String> = p0.getStringArrayListExtra("song") ?: arrayListOf()
        mResultReceiver = p0.getParcelableExtra("receiver")!!
        val bundle = Bundle()
        val id = song[1].substring(song[1].lastIndexOf("=") + 1)
        NotificationManagerCompat.from(applicationContext).apply {
            builder.setSubText("$currentQueue of $queueSize ")
            builder.setContentText("${song[0]} starting...")
            builder.setOngoing(true)
            notify(2, builder.build())
        }
        val video = YoutubeDownloader().getVideo(id)
        val downloadFormat = video.audioFormats().run { this[this.size - 1] }
        val mediaFile = File(Constants.ableSongDir, id)

        if (!Constants.ableSongDir.exists()) {
            val mkdirs = Constants.ableSongDir.mkdirs()
            if (!mkdirs) throw IOException("Could not create output directory: ${Constants.ableSongDir}")
        }

        NotificationManagerCompat.from(applicationContext).apply {
            builder.setContentTitle(song[0])
            builder.setOngoing(true)
            notify(2, builder.build())
        }
        try {
            val request = Request(downloadFormat.url(), mediaFile.absolutePath).also {
                it.priority = Priority.HIGH
                it.networkType = NetworkType.ALL
            }

            fetch?.addListener(object : FetchListener {
                override fun onAdded(download: Download) {}

                override fun onCancelled(download: Download) {}

                override fun onCompleted(download: Download) {
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setContentText("Saving...")
                            .setProgress(100, 100, true)
                        builder.setOngoing(true)
                        notify(2, builder.build())
                    }

                    var name = song[0]
                    name = name.replace(
                        Regex("${song[2]}\\s[-,:]?\\s"),
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
                            "\"${target}\" -c copy " +
                            "-metadata title=\"${name}\" " +
                            "-metadata artist=\"${song[2]}\" "
                    val format =
                        if (PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                .getString("format_key", "webm") == "mp3"
                        ) Format.MODE_MP3
                        else Format.MODE_WEBM
                    if (format == Format.MODE_MP3)
                        command += "-vn -ab ${downloadFormat.averageBitrate() / 1024}k -c:a mp3 -ar 44100 -y "

                    command += "\"${Constants.ableSongDir.absolutePath}/$id."

                    command += if (format == Format.MODE_MP3) "mp3\"" else "webm\""
                    when (val rc = FFmpeg.execute(command)) {
                        Config.RETURN_CODE_SUCCESS -> {
                            File(target).delete()
                            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
                                it.cancel(2)
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
                    mResultReceiver.send(123, bundle)
                    fetch?.removeListener(this)
                }

                override fun onDeleted(download: Download) {}

                override fun onDownloadBlockUpdated(
                    download: Download,
                    downloadBlock: DownloadBlock,
                    totalBlocks: Int
                ) {

                }

                override fun onError(download: Download, error: Error, throwable: Throwable?) {

                }

                override fun onPaused(download: Download) {

                }

                override fun onProgress(
                    download: Download,
                    etaInMilliSeconds: Long,
                    downloadedBytesPerSecond: Long
                ) {
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setProgress(100, download.progress, false)
                        builder.setOngoing(true)
                        builder.setContentText("${etaInMilliSeconds/1000}s left")
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

                }
            })

            fetch?.enqueue(request)
        } catch (e: IOException) {
            print(e)
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
            setContentTitle("Able Music Download")
            setContentText("Download in progress")
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
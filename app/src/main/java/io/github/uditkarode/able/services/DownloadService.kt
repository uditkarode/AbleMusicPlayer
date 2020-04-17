package io.github.uditkarode.able.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.github.kiulian.downloader.OnYoutubeDownloadListener
import com.github.kiulian.downloader.YoutubeDownloader
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.models.Format
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import java.io.File


class DownloadService : JobIntentService() {
    companion object {
        const val JOB_ID = 1000
        fun enqueueDownload(context: Context, intent: Intent) {
            enqueueWork(context, DownloadService::class.java, JOB_ID, intent)
            queuesize = queuesize + 1
        }

        const val CHANNEL_ID = "AbleMusicDownload"
        private var queuesize = 0
        var currentQueue = 0
    }

    lateinit var builder: Notification.Builder
    private lateinit var notification: Notification
    var songAdapter: SongAdapter? = null

    override fun onCreate() {
        createNotificationChannel()
        super.onCreate()
    }

    override fun onHandleWork(p0: Intent) {
        Log.d("Download Service", "Service Started")
        currentQueue = +1
        val song: java.util.ArrayList<String> = p0.getStringArrayListExtra("song")
        NotificationManagerCompat.from(applicationContext).apply {
            builder.setContentText("$queuesize of $currentQueue is  ${song[0]} is Downloading")
            builder.setOngoing(true)
            notify(2, builder.build())
        }
        songAdapter?.temp(Song(song[0], "Initialising download..."))
        val id = song[1].substring(song[1].lastIndexOf("=") + 1)
        val video = YoutubeDownloader().getVideo(id)
        val downloadFormat = video.audioFormats().run { this[this.size - 1] }

        /* if the song exists, it will be deleted and re-downloaded (check the library) */
        video.downloadAsync(
            downloadFormat,
            Constants.ableSongDir,
            id,
            object : OnYoutubeDownloadListener {
                override fun onDownloading(progress: Int) {
                    print(progress)
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setProgress(100, progress, false)
                        builder.setOngoing(true)
                        notify(2, builder.build())
                    }
                    Log.d("progress", progress.toString())
                }

                override fun onFinished(downloadedFile: File?) {
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setContentText("Download complete MP3 Conversion in progress")
                            .setProgress(0, 0, false)
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
                    val target = downloadedFile!!.absolutePath.toString()

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
                            val songList = Shared.getSongList(Constants.ableSongDir)
                            Handler(Looper.getMainLooper()).post {
                                songAdapter?.update(songList)
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
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setContentText("Download complete")
                            .setProgress(0, 0, false)
                        builder.setOngoing(false)
                        notify(2, builder.build())
                    }
                }

                override fun onError(throwable: Throwable?) {
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setContentText("Failed to download  ${song[0]} is Downloading")
                        builder.setOngoing(false)
                        notify(2, builder.build())
                    }
                }
            })
    }

    override fun onStopCurrentWork(): Boolean {
        Log.d("Stoping Work", "Wdkfsdjksdkhfksdhfkdshfjdshf")
        return super.onStopCurrentWork()
    }


    private fun createNotificationChannel() {
        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
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
                CHANNEL_ID,
                "AbleMusicDownload",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.enableLights(false)
            notificationChannel.enableVibration(false)
            notificationManager.createNotificationChannel(notificationChannel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = builder.setChannelId(CHANNEL_ID).build()
        } else {
            notification = builder.build()
            notificationManager.notify(2, notification)
        }
        //startForeground(2,notification)
    }


}
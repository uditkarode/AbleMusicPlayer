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
import com.github.kiulian.downloader.YoutubeDownloader
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.models.Format
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.*

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
            builder.setContentText("$currentQueue of $queuesize is  ${song[0]} is Downloading")
            builder.setOngoing(true)
            notify(2, builder.build())
        }
        songAdapter?.temp(Song(song[0], "Initialising download..."))
        val id = song[1].substring(song[1].lastIndexOf("=") + 1)
        val video = YoutubeDownloader().getVideo(id)
        val downloadFormat = video.audioFormats().run { this[this.size - 1] }
        val url = downloadFormat.url();
        val mediaFile = File(Constants.ableSongDir, id)
        val client = OkHttpClient()
        val call: Call = client.newCall(Request.Builder().url(url).get().build())
        try {
            val response: Response = call.execute()
            if (response.code === 200 || response.code === 201) {
                var inputStream: InputStream? = null
                try {
                    inputStream = response.body?.byteStream()
                    val buff = ByteArray(1024 * 4)
                    var downloaded: Long = 0
                    val targetMax: Long = response.body?.contentLength()!!
                    val output: OutputStream = FileOutputStream(mediaFile)
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setProgress(targetMax.toInt(), 0, false)
                        builder.setOngoing(true)
                        notify(2, builder.build())
                    }
                    while (true) {
                        val readed: Int = inputStream!!.read(buff)
                        if (readed == -1) break
                        output.write(buff, 0, readed)
                        //write buff
                        downloaded += readed.toLong()
                        NotificationManagerCompat.from(applicationContext).apply {
                            builder.setProgress(targetMax.toInt(), downloaded.toInt(), false)
                            builder.setOngoing(true)
                            notify(2, builder.build())
                        }
                    }
                    output.flush()
                    output.close()
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setContentText("Download complete MP3 Conversion in progress")
                            .setProgress(targetMax.toInt(), downloaded.toInt(), true)
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
                } catch (e: IOException) {
                    print(e)
                }
            }
        } catch (e: IOException) {
            NotificationManagerCompat.from(applicationContext).apply {
                builder.setContentText("Failed to download  ${song[0]} is Downloading")
                builder.setOngoing(false)
                notify(2, builder.build())
            }
        }
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
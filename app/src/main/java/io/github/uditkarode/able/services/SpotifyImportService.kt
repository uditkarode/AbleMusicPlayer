package io.github.uditkarode.able.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.uditkarode.able.R
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.SpotifyImport

class SpotifyImportService(context: Context, workerParams: WorkerParameters) : Worker(
    context,
    workerParams
) {
    private lateinit var builder: Notification.Builder
    private lateinit var notification: Notification
    override fun doWork(): Result {
        createNotificationChannel()
        if (runAttemptCount > 2) {
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
                it.cancel(3)
            }
            return Result.failure()
        }
        return try {
            val playId = inputData.getString("inputId")
            Log.d("SpotifyService", "playid =  $playId")
            SpotifyImport.importList(playId.toString(), builder, applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.d("SpotifyService", "Rtrying to Import Again We got some problem ")
            Result.retry()
        }
    }

    override fun onStopped() {
        SpotifyImport.isImporting = false;
        super.onStopped()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
            it.cancel(3)
        }
    }

    private fun createNotificationChannel() {
        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, Constants.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }
        builder.apply {
            setContentTitle("Initialising Import")
            setContentText("Please wait...")
            setSubText("Music Import")
            setSmallIcon(R.drawable.ic_download_icon)
            builder.setOngoing(true)
        }
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                Constants.CHANNEL_ID,
                "AbleMusicImport",
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
            notificationManager.notify(3, notification)
        }
        Log.d("SpotifyService", "Notification channel Created")
    }
}
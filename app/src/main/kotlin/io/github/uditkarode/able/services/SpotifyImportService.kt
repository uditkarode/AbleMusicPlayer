/*
    Copyright 2020 Harshit Singh <harsh.008.com@gmail.com>
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
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.uditkarode.able.R
import io.github.uditkarode.able.events.ImportDoneEvent
import io.github.uditkarode.able.events.ImportStartedEvent
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.SpotifyImport
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

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
            EventBus.getDefault().postSticky(ImportStartedEvent())
            NotificationManagerCompat.from(applicationContext).apply {
                builder.setContentText("")
                builder.setContentTitle(applicationContext.getString(R.string.init_import))
                builder.setProgress(100, 100, true)
                builder.setOngoing(true)
                notify(3, builder.build())
            }
            SpotifyImport.importList(playId.toString(), builder, applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    override fun onStopped() {
        SpotifyImport.isImporting = false
        super.onStopped()
        EventBus.getDefault().unregister(this)
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
            it.cancel(3)
        }

    }

    @Subscribe
    fun importDone(@Suppress("UNUSED_PARAMETER") importDoneEvent: ImportDoneEvent){
        this.stop()
        SpotifyImport.isImporting = false
    }

    private fun createNotificationChannel() {
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, Constants.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }
        builder.apply {
            setContentTitle(applicationContext.getString(R.string.init_import))
            setContentText(applicationContext.getString(R.string.pl_wait))
            setSubText("Spotify ${applicationContext.getString(R.string.imp)}")
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
    }
}
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
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import io.github.uditkarode.able.R
import io.github.uditkarode.able.utils.SpotifyImport
import kotlin.concurrent.thread

class SpotifyImportService : Service() {
    companion object {
        private const val NOTIF_ID = 3
        private const val CHANNEL_ID = "AbleSpotifyImport"
    }

    private lateinit var builder: Notification.Builder
    private lateinit var notificationManager: NotificationManager
    @Volatile private var importThreadFinished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(NOTIF_ID, builder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val playId = intent?.getStringExtra("inputId") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        MusicService.registeredClients.forEach { it.spotifyImportChange(true) }

        thread {
            val success = SpotifyImport.importList(playId, builder, this)
            importThreadFinished = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Remove notification on success; detach on failure so error persists
                stopForeground(if (success) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
            }
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        SpotifyImport.isImporting = false
        // Only cancel notification if user explicitly cancelled (service killed
        // before the import thread finished). If the thread finished naturally,
        // error notifications should persist and success already cancelled it.
        if (!importThreadFinished) {
            notificationManager.cancel(NOTIF_ID)
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spotify Import",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.enableLights(false)
            channel.enableVibration(false)
            channel.setSound(null, null)
            notificationManager.createNotificationChannel(channel)
        }

        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.apply {
            setContentTitle(getString(R.string.init_import))
            setContentText(getString(R.string.pl_wait))
            setSubText("Spotify ${getString(R.string.imp)}")
            setSmallIcon(R.drawable.ic_download_icon)
            setOngoing(true)
            setProgress(100, 0, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
        }
    }

}

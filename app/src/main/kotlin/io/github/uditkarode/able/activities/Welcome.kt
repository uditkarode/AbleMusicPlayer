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

package io.github.uditkarode.able.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.AbleApplication
import io.github.uditkarode.able.R

/**
 * Welcome screen that requests permissions without blocking the user.
 */
class Welcome : AppCompatActivity() {

    private companion object {
        const val REQ_NOTIF = 1001
        const val REQ_AUDIO = 1002
    }

    private lateinit var btnNotif: TextView
    private lateinit var btnAudio: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        btnNotif = findViewById(R.id.btn_notif)
        btnAudio = findViewById(R.id.btn_audio)

        btnNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
        }

        btnAudio.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), REQ_AUDIO)
        }

        findViewById<TextView>(R.id.btn_continue).setOnClickListener {
            proceed()
        }

        updateButtonStates()
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
    }

    private fun updateButtonStates() {
        // Notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                btnNotif.text = getString(R.string.granted)
                btnNotif.setBackgroundResource(R.drawable.rounded_rectangle)
                btnNotif.isEnabled = false
            }
        } else {
            // Pre-Android 13: notifications always allowed
            btnNotif.text = getString(R.string.granted)
            btnNotif.setBackgroundResource(R.drawable.rounded_rectangle)
            btnNotif.isEnabled = false
        }

        // Audio permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            btnAudio.text = getString(R.string.granted)
            btnAudio.setBackgroundResource(R.drawable.rounded_rectangle)
            btnAudio.isEnabled = false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateButtonStates()
    }

    private fun proceed() {
        getSharedPreferences("able_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("welcome_shown", true)
            .apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!, AbleApplication.viewPump))
    }
}

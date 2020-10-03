package io.github.uditkarode.able.utils

import androidx.appcompat.app.AppCompatActivity
import io.github.uditkarode.able.services.MusicService

open class MusicClientActivity: AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        MusicService.registerClient(this)
    }

    override fun onPause() {
        super.onPause()
        MusicService.unregisterClient(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicService.unregisterClient(this)
    }
}
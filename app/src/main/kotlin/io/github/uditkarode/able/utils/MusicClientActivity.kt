package io.github.uditkarode.able.utils

import androidx.appcompat.app.AppCompatActivity
import io.github.uditkarode.able.services.MusicService
import kotlinx.coroutines.*

abstract class MusicClientActivity: AppCompatActivity(), CoroutineScope, MusicService.MusicClient {

    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    override fun onResume() {
        super.onResume()
        launch {
            MusicService.registerClient(this@MusicClientActivity)
        }
    }

    override fun onPause() {
        super.onPause()
        launch {
            MusicService.unregisterClient(this@MusicClientActivity)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
        MusicService.unregisterClient(this@MusicClientActivity)
    }
}
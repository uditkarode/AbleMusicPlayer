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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.AbleApplication
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.LibraryDetailAdapter
import io.github.uditkarode.able.databinding.LibraryDetailBinding
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shows the list of local songs that match a given artist or album, with a
 * play-all FAB and tap-to-play on individual rows. The songs to show are
 * handed off via [pendingSongs] right before the intent is fired — this
 * avoids the size limits of [Intent.putParcelableArrayListExtra] and keeps
 * the [Song] model unchanged.
 */
class LibraryDetail : AppCompatActivity(), CoroutineScope {
    companion object {
        /** Temporary handoff from the launcher fragment. Consumed in [onCreate]. */
        @Volatile
        var pendingSongs: ArrayList<Song>? = null
    }

    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    private val mService: MutableStateFlow<MusicService?> = MutableStateFlow(null)
    private var isBound = false
    private lateinit var serviceConn: ServiceConnection
    private lateinit var binding: LibraryDetailBinding
    private var resultArray = ArrayList<Song>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LibraryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra("title") ?: ""
        resultArray = pendingSongs ?: arrayListOf()
        pendingSongs = null // consume

        binding.libTitle.text = title.ifBlank { getString(R.string.app_name) }
        binding.libSubtitle.text = if (resultArray.size == 1)
            getString(R.string.one_song)
        else
            getString(R.string.song_count, resultArray.size)

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService.value = (service as MusicService.MusicBinder).getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
            }
        }

        binding.libRv.layoutManager = LinearLayoutManager(this)
        binding.libRv.adapter = LibraryDetailAdapter(resultArray) { position ->
            itemPressed(resultArray, position)
        }

        binding.libPlay.setOnClickListener {
            if (resultArray.isEmpty()) return@setOnClickListener
            startServiceIfNeeded { freshStart ->
                launch(Dispatchers.Default) {
                    awaitService()
                    val svc = mService.value ?: return@launch
                    svc.setQueue(ArrayList(resultArray))
                    svc.setIndex(0)
                    if (svc.getShuffle())
                        svc.setShuffleRepeat(shuffle = true, repeat = svc.getRepeat())
                    if (freshStart)
                        MusicService.registeredClients.forEach(MusicService.MusicClient::serviceStarted)
                }
            }
        }

        if (mService.value == null) bindIfRunning()
    }

    override fun onResume() {
        super.onResume()
        if (mService.value == null) bindIfRunning()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try { unbindService(serviceConn) } catch (_: Exception) {}
            isBound = false
        }
        coroutineContext.cancelChildren()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!, AbleApplication.viewPump))
    }

    /**
     * Bind to MusicService if it's already running. Used from onCreate/onResume
     * so we can immediately reflect existing playback state.
     */
    private fun bindIfRunning() {
        if (Shared.serviceRunning(MusicService::class.java, this)) {
            try {
                bindService(Intent(this, MusicService::class.java), serviceConn, 0)
            } catch (e: Exception) {
                Log.e("ERR>", e.toString())
            }
        }
    }

    /**
     * Bind with BIND_AUTO_CREATE, used right after startForegroundService.
     * Unlike [bindIfRunning] this will queue the binding even if the service
     * hasn't finished onCreate yet — startForegroundService posts onCreate to
     * the main thread, so a plain bindService gated on serviceRunning would
     * race and silently fail when called from inside a click handler.
     */
    private fun bindWithAutoCreate() {
        try {
            bindService(
                Intent(this, MusicService::class.java),
                serviceConn,
                Context.BIND_AUTO_CREATE
            )
        } catch (e: Exception) {
            Log.e("ERR>", e.toString())
        }
    }

    private fun startServiceIfNeeded(onReady: (freshStart: Boolean) -> Unit) {
        var freshStart = false
        if (!Shared.serviceRunning(MusicService::class.java, this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, MusicService::class.java))
            } else {
                startService(Intent(this, MusicService::class.java))
            }
            bindWithAutoCreate()
            freshStart = true
        } else if (mService.value == null) {
            // Service is running but we haven't bound yet (e.g. user landed
            // here with service already alive from a previous session).
            bindIfRunning()
        }
        onReady(freshStart)
    }

    private suspend fun awaitService() {
        if (mService.value != null) return
        mService.first { it != null }
    }

    /**
     * Plays the tapped song within [array]. Mirrors [LocalPlaylist.itemPressed].
     */
    fun itemPressed(array: ArrayList<Song>, index: Int) {
        startServiceIfNeeded { freshStart ->
            launch(Dispatchers.Default) {
                awaitService()
                val svc = mService.value ?: return@launch
                svc.setQueue(ArrayList(array))
                svc.setIndex(index)
                if (svc.getShuffle())
                    svc.setShuffleRepeat(shuffle = true, repeat = svc.getRepeat())
                if (freshStart)
                    MusicService.registeredClients.forEach(MusicService.MusicClient::serviceStarted)
            }
        }
    }
}

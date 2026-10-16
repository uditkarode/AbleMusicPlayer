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

package io.github.uditkarode.able.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.Settings
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.databinding.HomeBinding
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.model.song.SongState
import io.github.uditkarode.able.services.DownloadService
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import io.github.uditkarode.able.utils.SwipeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference
import java.util.*

/**
 * The first fragment. Shows a list of songs present on the user's device.
 */
class Home : Fragment(), CoroutineScope, MusicService.MusicClient {
    private lateinit var serviceConn: ServiceConnection

    private var songList = ArrayList<Song>()

    var isBound = false
    var mService: MutableStateFlow<MusicService?> = MutableStateFlow(null)

    override val coroutineContext = Dispatchers.Main + SupervisorJob()
    private var _binding: HomeBinding? = null
    private val binding get() = _binding!!

    private var isLoadingSongs = false

    companion object {
        var songAdapter: SongAdapter? = null
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
        MusicService.unregisterClient(this)
        DownloadService.onDownloadComplete = null
        if (isBound) {
            try { requireContext().unbindService(serviceConn) } catch (_: Exception) {}
            isBound = false
        }
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val songs = view.findViewById<RecyclerView>(R.id.songs)

//        RevelyGradient
//            .linear()
//            .colors(
//                intArrayOf(
//                    Color.parseColor("#7F7FD5"),
//                    Color.parseColor("#86A8E7"),
//                    Color.parseColor("#91EAE4")
//                )
//            )
//            .on(view.findViewById<TextView>(R.id.able_header))

        _binding!!.settings.setOnClickListener {
            startActivity(Intent(requireContext(), Settings::class.java))
        }

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService.value = (service as MusicService.MusicBinder).getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
            }
        }

        bindEvent()

        songAdapter = SongAdapter(songList, WeakReference(this@Home), true)
        val lam = LinearLayoutManager(requireContext())
        lam.initialPrefetchItemCount = 12
        lam.isItemPrefetchEnabled = true
        val itemTouchHelper = ItemTouchHelper(SwipeController(context, "Home", mService))
        songs.adapter = songAdapter
        songs.layoutManager = lam
        itemTouchHelper.attachToRecyclerView(songs)
        songAdapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { updateEmptyState() }
        })

        if (songList.isEmpty()) {
            isLoadingSongs = true
            updateEmptyState()
            val ctx = requireContext()
            launch(Dispatchers.IO) {
                val loadedSongs = Shared.getSongList(Constants.ableSongDir, ctx)
                if (isAdded) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            ctx, android.Manifest.permission.READ_MEDIA_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        loadedSongs.addAll(Shared.getLocalSongs(ctx))
                    }
                }
                val sorted = if (loadedSongs.isNotEmpty()) ArrayList(loadedSongs.sortedBy {
                    it.name.uppercase(Locale.getDefault())
                }) else loadedSongs
                launch(Dispatchers.Main) {
                    isLoadingSongs = false
                    songList = sorted
                    songAdapter?.update(songList)
                    updateEmptyState()
                }
            }
        } else {
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        if (_binding == null) return
        val isEmpty = songAdapter?.itemCount == 0
        if (isLoadingSongs) {
            binding.loadingView.visibility = View.VISIBLE
            binding.loadingView.playAnimation()
            binding.emptyState.visibility = View.GONE
            binding.songs.visibility = View.GONE
        } else {
            binding.loadingView.cancelAnimation()
            binding.loadingView.visibility = View.GONE
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.songs.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        MusicService.registerClient(this)
        DownloadService.onDownloadComplete = { updateSongList() }
        if (DownloadService.downloadCompletedSinceLastCheck) {
            DownloadService.downloadCompletedSinceLastCheck = false
            updateSongList()
        }
    }

    override fun onPause() {
        super.onPause()
        MusicService.unregisterClient(this)
        DownloadService.onDownloadComplete = null
    }

    fun bindEvent() {
        try {
            requireContext().bindService(
                Intent(
                    requireContext(),
                    MusicService::class.java
                ), serviceConn, 0
            )
        } catch (e: Exception) {
            Log.e("ERR>", e.toString())
        }
    }

    fun updateSongList() {
        val ctx = context ?: return
        launch(Dispatchers.IO) {
            val newList = Shared.getSongList(Constants.ableSongDir, ctx)
            newList.addAll(Shared.getLocalSongs(ctx))
            val sorted = ArrayList(newList.sortedBy {
                it.name.uppercase(Locale.getDefault())
            })
            launch(Dispatchers.Main) {
                songList = sorted
                songAdapter?.update(songList)
                updateEmptyState()
            }
        }
    }

    override fun playStateChanged(state: SongState) {}

    override fun songChanged() {}

    override fun durationChanged(duration: Int) {}

    override fun isExiting() {}

    override fun queueChanged(arrayList: ArrayList<Song>) {}

    override fun shuffleRepeatChanged(onShuffle: Boolean, onRepeat: Boolean) {}

    override fun indexChanged(index: Int) {}

    override fun isLoading(doLoad: Boolean) {}

    override fun spotifyImportChange(starting: Boolean) {}

    override fun serviceStarted() {
        bindEvent()
    }
}

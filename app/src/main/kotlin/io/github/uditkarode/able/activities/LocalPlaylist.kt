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
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.LocalPlaylistAdapter
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.albumplaylist.*
import kotlinx.android.synthetic.main.search.loading_view
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

/**
 * The activity that shows up when a user taps on a local playlist from the
 * playlist fragment.
 */
class LocalPlaylist : AppCompatActivity(), CoroutineScope {
    var mService: MusicService? = null
    var isBound = false
    private lateinit var serviceConn: ServiceConnection
    private var resultArray = ArrayList<Song>()

    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewPump.init(
            ViewPump.builder()
                .addInterceptor(
                    CalligraphyInterceptor(
                        CalligraphyConfig.Builder()
                            .setDefaultFontPath("fonts/inter.otf")
                            .setFontAttrId(R.attr.fontPath)
                            .build()
                    )
                )
                .build()
        )
        setContentView(R.layout.localplaylist)
        loading_view.progress = 0.3080229f
        loading_view.playAnimation()
        val name = intent.getStringExtra("name") ?: ""

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
            }
        }

        playbum_name.text = name.replace(".json", "")

        playbum_play.setOnClickListener {
            if (!Shared.serviceRunning(MusicService::class.java, this)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, MusicService::class.java))
                } else {
                    startService(Intent(this, MusicService::class.java))
                }

                bindEvent()
            }

            launch(Dispatchers.Default) {
                @Suppress("ControlFlowWithEmptyBody")
                while (!isBound) {
                }

                val mService = mService!!
                mService.setQueue(resultArray)
                mService.setIndex(0)
                mService.setPlayPause(SongState.playing)
            }
        }

        launch(Dispatchers.Default) {
            resultArray = Shared.getSongsFromPlaylistFile(name)

            launch(Dispatchers.Main) {
                ap_rv.adapter = LocalPlaylistAdapter(resultArray, WeakReference(this@LocalPlaylist))
                ap_rv.layoutManager = LinearLayoutManager(this@LocalPlaylist)
                loading_view.visibility = View.GONE
                loading_view.pauseAnimation()
                ap_rv.alpha = 0f
                ap_rv.visibility = View.VISIBLE
                ap_rv.animate().alpha(1f).duration = 200
                sr_pr.alpha = 0f
                sr_pr.visibility = View.VISIBLE
                sr_pr.animate().alpha(1f).duration = 200
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!))
    }

    private fun bindEvent() {
        if (Shared.serviceRunning(MusicService::class.java, this)) {
            try {
                this.also {
                    it.bindService(Intent(it, MusicService::class.java), serviceConn, 0)
                }
            } catch (e: Exception) {
                Log.e("ERR>", e.toString())
            }
        }
    }

    /**
     * invoked when an item is pressed in the recyclerview.
     */
    fun itemPressed(array: ArrayList<Song>, index: Int) {
        if (!Shared.serviceRunning(MusicService::class.java, this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, MusicService::class.java))
            } else {
                startService(Intent(this, MusicService::class.java))
            }

            bindEvent()
        }

        launch(Dispatchers.IO) {
            @Suppress("ControlFlowWithEmptyBody")
            while (!isBound) {
            }
            val mService = mService!!
            mService.setQueue(array)
            mService.setIndex(index)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!this.isDestroyed)
            Glide.with(this).clear(playbum_art)
    }
}
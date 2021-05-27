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
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.PlaybumAdapter
import io.github.uditkarode.able.databinding.AlbumplaylistBinding
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.lang.ref.WeakReference

/**
 * The activity that shows up when a user taps on an album or playlist
 * from the search results.
 */
@ExperimentalCoroutinesApi
class AlbumPlaylist : AppCompatActivity(), CoroutineScope {
    private lateinit var serviceConn: ServiceConnection
    private val resultArray = ArrayList<Song>()
    var mService: MutableStateFlow<MusicService?> = MutableStateFlow(null)
    var isBound = false

    override val coroutineContext = Dispatchers.Main + SupervisorJob()
    private lateinit var binding: AlbumplaylistBinding

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
        binding = AlbumplaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.loadingView.progress = 0.3080229f
        binding.loadingView.playAnimation()
        val name = intent.getStringExtra("name") ?: ""
        val artist = intent.getStringExtra("artist") ?: ""
        val art = intent.getStringExtra("art") ?: ""
        val link = intent.getStringExtra("link") ?: ""

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService.value = (service as MusicService.MusicBinder).getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
            }
        }

        binding.playbumName.text = name
        binding.playbumArtist.text = artist
        Glide
            .with(this@AlbumPlaylist)
            .load(art)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(binding.playbumArt)

        binding.playbumPlay.setOnClickListener {
            var freshStart = false
            if (!Shared.serviceRunning(MusicService::class.java, this)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, MusicService::class.java))
                } else {
                    startService(Intent(this, MusicService::class.java))
                }

                bindEvent()
                freshStart = true
            }

            launch(Dispatchers.Default) {
                val playSong = fun(){
                    val mService = mService.value!!
                    mService.setQueue(resultArray)
                    mService.setIndex(0)
                    if(freshStart)
                        MusicService.registeredClients.forEach(MusicService.MusicClient::serviceStarted)
                }

                if(mService.value != null) playSong()
                else {
                    mService.collect {
                        if(it != null) {
                            playSong()
                        }
                    }
                }
            }
        }

        launch(Dispatchers.IO) {
            val plExtractor = YouTube.getPlaylistExtractor(link)
            plExtractor.fetchPage()
            for (song in plExtractor.initialPage.items) {
                val ex = song as StreamInfoItem
                if (song.thumbnailUrl.contains("ytimg")) {
                    val songId = Shared.getIdFromLink(ex.url)
                    song.thumbnailUrl = "https://i.ytimg.com/vi/$songId/maxresdefault.jpg"
                }
                resultArray.add(
                    Song(
                        name = ex.name,
                        artist = ex.uploaderName,
                        youtubeLink = ex.url,
                        ytmThumbnail = song.thumbnailUrl
                    )
                )
            }

            launch(Dispatchers.Main) {
                binding.apRv.adapter =
                    PlaybumAdapter(resultArray, WeakReference(this@AlbumPlaylist), "Song")
                binding.apRv.layoutManager = LinearLayoutManager(this@AlbumPlaylist)
                binding.loadingView.visibility = View.GONE
                binding.loadingView.pauseAnimation()
                binding.apRv.alpha = 0f
                binding.apRv.visibility = View.VISIBLE
                binding.apRv.animate().alpha(1f).duration = 200
                binding.srPr.alpha = 0f
                binding.srPr.visibility = View.VISIBLE
                binding.srPr.animate().alpha(1f).duration = 200
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
        var freshStart = false
        if (!Shared.serviceRunning(MusicService::class.java, this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, MusicService::class.java))
            } else {
                startService(Intent(this, MusicService::class.java))
            }

            bindEvent()
            freshStart = true
        }

        launch(Dispatchers.Default) {
            val playSong = fun() {
                val mService = mService.value!!
                mService.setQueue(array)
                mService.setIndex(index)
                if(freshStart)
                    MusicService.registeredClients.forEach(MusicService.MusicClient::serviceStarted)
            }

            if(mService.value != null) playSong()
            else {
                mService.collect {
                    if(it != null){
                        playSong()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Handler(Looper.getMainLooper()).postDelayed({
            if (!this.isDestroyed)
                Glide.with(this).clear(binding.playbumArt)
        }, 300)
        Glide.with(this).clear(binding.playbumArt)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!this.isDestroyed)
            Glide.with(this).clear(binding.playbumArt)
    }

    override fun onResume() {
        super.onResume()
        if(mService.value == null)
            bindEvent()
    }
}
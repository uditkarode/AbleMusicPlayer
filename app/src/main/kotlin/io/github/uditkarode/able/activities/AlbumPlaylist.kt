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
import android.os.Handler
import android.os.IBinder
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
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.albumplaylist.*
import kotlinx.android.synthetic.main.search.loading_view
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

/**
 * The activity that shows up when a user taps on an album or playlist
 * from the search results.
 */
class AlbumPlaylist: AppCompatActivity() {
    var mService: MusicService? = null
    var isBound = false
    private lateinit var serviceConn: ServiceConnection
    private val resultArray = ArrayList<Song>()

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
        setContentView(R.layout.albumplaylist)
        loading_view.progress = 0.3080229f
        loading_view.playAnimation()
        val name = intent.getStringExtra("name")?:""
        val artist = intent.getStringExtra("artist")?:""
        val art = intent.getStringExtra("art")?:""
        val link = intent.getStringExtra("link")?:""

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                Shared.mService = service.getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
            }
        }

        playbum_name.text = name
        playbum_artist.text = artist
        Glide
            .with(this@AlbumPlaylist)
            .load(art)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(playbum_art)
        
        playbum_play.setOnClickListener {
            if(!Shared.serviceRunning(MusicService::class.java, this)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, MusicService::class.java))
                } else {
                    startService(Intent(this, MusicService::class.java))
                }

                bindEvent()
            }

            thread {
                if(Shared.serviceLinked()) {
                    mService = Shared.mService
                } else {
                    @Suppress("ControlFlowWithEmptyBody")
                    while(!isBound){}
                }

                val mService = mService!!
                mService.setQueue(resultArray)
                mService.setIndex(0)
                mService.setPlayPause(SongState.playing)
            }
        }

        thread {
            val plExtractor = YouTube.getPlaylistExtractor(link)
            plExtractor.fetchPage()
            for(song in plExtractor.initialPage.items) {
                val ex = song as StreamInfoItem
                if(song.thumbnailUrl.contains("ytimg")) {
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

            runOnUiThread {
                ap_rv.adapter = PlaybumAdapter(resultArray, WeakReference(this), "Song")
                ap_rv.layoutManager = LinearLayoutManager(this)
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
    fun itemPressed(song: Song){
        if(!Shared.serviceRunning(MusicService::class.java, this)){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, MusicService::class.java))
            } else {
                startService(Intent(this, MusicService::class.java))
            }

            bindEvent()
        }

        thread {
            if(Shared.serviceLinked()) {
                mService = Shared.mService
            } else {
                @Suppress("ControlFlowWithEmptyBody")
                while(!isBound){}
            }

            val mService = mService!!
            mService.setQueue(arrayListOf(song))
            mService.setIndex(0)
            mService.setPlayPause(SongState.playing)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Handler().postDelayed({
            if(!this.isDestroyed)
                Glide.with(this).clear(playbum_art)
        }, 300)
        Glide.with(this).clear(playbum_art)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(!this.isDestroyed)
            Glide.with(this).clear(playbum_art)
    }
}
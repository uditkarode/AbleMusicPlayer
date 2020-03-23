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
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.ViewPagerAdapter
import io.github.uditkarode.able.events.*
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.fragments.Search
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity(), Search.SongCallback {
    private lateinit var okClient: OkHttpClient
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var mainContent: ViewPager
    private var mService: MusicService? = null
    private lateinit var serviceConn: ServiceConnection

    private var playing = false

    private lateinit var timer: Timer
    private var scheduled = false

    private lateinit var home: Home

    override fun onCreate(savedInstanceState: Bundle?) {
        if(!Shared.serviceRunning(MusicService::class.java, this@MainActivity))
            startActivity(Intent(this@MainActivity, Splash::class.java))

        super.onCreate(savedInstanceState)
        home = Home(applicationContext)
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
        setContentView(R.layout.activity_main)
        okClient = OkHttpClient()

        mainContent = main_content
        bb_icon.setOnClickListener {
            thread {
                if(playing) Shared.mService.setPlayPause(SongState.paused)
                else Shared.mService.setPlayPause(SongState.playing)
            }
        }

        mainContent.adapter = ViewPagerAdapter(supportFragmentManager, home)
        mainContent.setPageTransformer(false) { page, _ ->
            page.alpha = 0f
            page.visibility = View.VISIBLE

            page.animate()
                .alpha(1f).duration = 200
        }

        bottomNavigation = bottom_navigation
        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home_menu -> main_content.currentItem = 0
                R.id.search_menu -> main_content.currentItem = 1
                R.id.settings_menu -> main_content.currentItem = 2
            }
            true
        }

        activity_seekbar.thumb.alpha = 0

        bb_song.isSelected = true
        bb_song.setOnClickListener {
            if(Shared.serviceRunning(MusicService::class.java, this@MainActivity))
                startActivity(Intent(this@MainActivity, Player::class.java))
        }

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                songChange(GetSongChangedEvent())
                playPauseEvent(GetPlayPauseEvent(service.getService().mediaPlayer.run {
                    if(this.isPlaying) SongState.playing
                    else SongState.paused
                }))
            }

            override fun onServiceDisconnected(name: ComponentName) { }
        }

        bindEvent(BindServiceEvent())
    }

    @Subscribe
    private fun bindEvent(bindServiceEvent: BindServiceEvent){
        bindServiceEvent.toString() /* because the IDE doesn't like it unused */
        if(Shared.serviceRunning(MusicService::class.java, this@MainActivity)) bindService(Intent(this@MainActivity, MusicService::class.java), serviceConn, Context.BIND_IMPORTANT)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun playPauseEvent(playPauseEvent: GetPlayPauseEvent){
        playing = playPauseEvent.state == SongState.playing
        if(playing) bb_icon.setImageDrawable(getDrawable(R.drawable.pause))
        else bb_icon.setImageDrawable(getDrawable(R.drawable.play))

        if(playing) startSeekbarUpdates()
        else {
            if(scheduled){
                scheduled = false
                timer.cancel()
                timer.purge()
            }
        }
    }

    private fun startSeekbarUpdates(){
        if(!scheduled){
            scheduled = true
            timer = Timer()
            timer.schedule(object: TimerTask() {
                override fun run() {
                    activity_seekbar.progress = Shared.mService.mediaPlayer.currentPosition
                }
            }, 0, 1000)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun songChange(event: GetSongChangedEvent){
        event.toString() /* because the IDE doesn't like it unused */
        activity_seekbar.progress = 0
        activity_seekbar.max = Shared.mService.mediaPlayer.duration

        startSeekbarUpdates()
        val song = Shared.mService.playQueue[Shared.mService.currentIndex]

        bb_song.text = Html.fromHtml(
            "${song.name} <font color=\"#5e92f3\">•</font> ${song.artist}",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songSet(songEvent: GetSongEvent){
        activity_seekbar.progress = 0

        bb_song.text = Html.fromHtml(
            "${songEvent.song.name} <font color=\"#5e92f3\">•</font> ${songEvent.song.artist}",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun getProgressUpdater(progressEvent: GetProgressEvent){
        activity_seekbar.progress = progressEvent.progress
    }

    @Subscribe
    fun durationUpdate(durationEvent: GetDurationEvent){
        activity_seekbar.max = durationEvent.duration
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!))
    }

    override fun onPause() {
        super.onPause()
        if(scheduled){
            scheduled = false
            timer.cancel()
            timer.purge()
        }
        EventBus.getDefault().unregister(this)
    }

    override fun onResume() {
        super.onResume()
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
    }

    override fun sendItem(song: Song) {
        home.downloadVideo(song)
        mainContent.currentItem = -1
        bottomNavigation.menu.findItem(R.id.home_menu)?.isChecked = true
    }
}

package io.github.uditkarode.able.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
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

    private var playing = false

    private lateinit var timer: Timer
    private var scheduled = false

    private val home = Home()

    override fun onCreate(savedInstanceState: Bundle?) {
        updateYoutubeDl()

        if(!Shared.serviceRunning(MusicService::class.java, this@MainActivity))
            startActivity(Intent(this@MainActivity, Splash::class.java))

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
        setContentView(R.layout.activity_main)

        thread {
            YoutubeDL.getInstance().init(application)
            okClient = OkHttpClient()
        }

        mainContent = main_content
        bb_icon.setOnClickListener {
            thread {
                if(playing) EventBus.getDefault().postSticky(PlayPauseEvent(SongState.paused))
                else EventBus.getDefault().postSticky(PlayPauseEvent(SongState.playing))
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
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun GetPlayPauseUpdate(playPauseEvent: GetPlayPauseEvent){
        playing = playPauseEvent.state == SongState.playing
        if(playing) bb_icon.setImageDrawable(getDrawable(R.drawable.pause))
        else bb_icon.setImageDrawable(getDrawable(R.drawable.play))

        if(playing){
            startSeekbarUpdates()
        }
        else {
            if(scheduled){
                scheduled = false
                timer.cancel()
                timer.purge()
            }
        }
    }

    private fun startSeekbarUpdates(){
        thread {
            EventBus.getDefault().postSticky(RequestEvent("GetProgressEvent"))
        }
        if(!scheduled){
            scheduled = true
            timer = Timer()
            timer.schedule(object: TimerTask() {
                override fun run() {
                    activity_seekbar.progress += 1000
                }
            }, 0, 1000)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songChange(songChangedEvent: SongChangedEvent){
        activity_seekbar.progress = 0
        activity_seekbar.max = songChangedEvent.duration

        startSeekbarUpdates()

        bb_song.text = Html.fromHtml(
            "${songChangedEvent.song.name} <font color=\"#5e92f3\">•</font> ${songChangedEvent.song.artist}",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songSet(songEvent: GetSongEvent){
        activity_seekbar.progress = 0
        EventBus.getDefault().postSticky(RequestEvent("GetDurationEvent"))

        bb_song.text = Html.fromHtml(
            "${songEvent.song.name} <font color=\"#5e92f3\">•</font> ${songEvent.song.artist}",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun requestData(){
        EventBus.getDefault().postSticky(RequestEvent("GetDurationEvent"))
        EventBus.getDefault().postSticky(RequestEvent("GetSongEvent"))
        EventBus.getDefault().postSticky(RequestEvent("GetPlayPauseEvent"))
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
        requestData()
    }

    override fun onStop() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        super.onStop()
    }

    private fun updateYoutubeDl(){
            thread {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(application)
                } catch (err: YoutubeDLException){
                    Log.e("ERR: ", err.toString())
                }
            }
    }

    override fun sendItem(song: Song) {
        home.downloadVideo(song)
        mainContent.currentItem = -1
        bottomNavigation.menu.findItem(R.id.home_menu)?.isChecked = true
    }
}

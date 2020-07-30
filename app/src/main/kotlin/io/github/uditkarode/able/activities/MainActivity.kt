/*
    Copyright 2020 Udit Karode <udit.karode@gmail.com>

    This file is part of AbleMusicPlayer.

    AbleMusicPlayer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    AbleMusicPlayer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AbleMusicPlayer.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.github.uditkarode.able.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.text.Html
import android.view.TouchDelegate
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.flurry.android.FlurryAgent
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
import io.github.uditkarode.able.models.MusicMode
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.DownloadService
import io.github.uditkarode.able.services.DownloadService.Companion.enqueueDownload
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.services.ServiceResultReceiver
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.CustomDownloader
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.schabi.newpipe.extractor.NewPipe
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

/**
 * First activity that shows up when the user opens the application
 */
class MainActivity : AppCompatActivity(), Search.SongCallback, ServiceResultReceiver.Receiver {
    private lateinit var okClient: OkHttpClient
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var mainContent: ViewPager
    private var mService: MusicService? = null
    private lateinit var serviceConn: ServiceConnection
    private lateinit var mServiceResultReceiver: ServiceResultReceiver

    private var playing = false

    private lateinit var timer: Timer
    private var scheduled = false

    private lateinit var home: Home

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!Shared.serviceRunning(MusicService::class.java, applicationContext)
            && Shared.isFirstOpen
        ) {
            if (checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                startActivity(Intent(applicationContext, Welcome::class.java))
            } else startActivity(Intent(applicationContext, Splash::class.java))
        }
        thread {  //gets and stores the default Bitmap in a small size
            Shared.defBitmap = (ResourcesCompat.getDrawable(this.resources,
                R.drawable.def_albart, null) as BitmapDrawable).bitmap
            val outputStream = ByteArrayOutputStream()
            Shared.defBitmap.compress(Bitmap.CompressFormat.JPEG, 20, outputStream)
            val byte = outputStream.toByteArray()
            Shared.defBitmap  = BitmapFactory.decodeByteArray(byte,
                0, byte.size)
        }
        thread {
            NewPipe.init(CustomDownloader.instance)
            Shared.setupFetch(this@MainActivity)
        }

        mServiceResultReceiver = ServiceResultReceiver(Handler())
        mServiceResultReceiver.setReceiver(this)
        super.onCreate(savedInstanceState)
        FlurryAgent.Builder()
            .withLogEnabled(false)
            .build(this, Constants.FLURRY_KEY)
        home = Home()
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
            if (Shared.serviceRunning(MusicService::class.java, applicationContext)) {
                thread {
                    if (playing) Shared.mService.setPlayPause(SongState.paused)
                    else Shared.mService.setPlayPause(SongState.playing)
                }
            }
        }
        // extend the touchable area for the play button, since it's so small.
        (bb_icon.parent as View).post {
            val rect = Rect().also {
                bb_icon.getHitRect(it)
                it.top -= 200
                it.left -= 200
                it.bottom += 200
                it.right += 200
            }

            (bb_icon.parent as View).touchDelegate = TouchDelegate(rect, bb_icon)
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
            if (Shared.serviceRunning(MusicService::class.java, applicationContext))
                startActivity(Intent(applicationContext, Player::class.java))
        }

        bb_expand.setOnClickListener {
            if (Shared.serviceRunning(MusicService::class.java, applicationContext))
                startActivity(Intent(applicationContext, Player::class.java))
        }

        // bind to the service.
        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                songChange(GetSongChangedEvent())
                playPauseEvent(GetPlayPauseEvent(service.getService().getMediaPlayer().run {
                    if (this.isPlaying) SongState.playing
                    else SongState.paused
                }))
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun loadingEvent(homeLoadingEvent: HomeLoadingEvent){
        bb_ProgressBar?.visibility = if(homeLoadingEvent.loading) View.VISIBLE else View.GONE
        if(!homeLoadingEvent.loading){
            activity_seekbar.visibility = View.VISIBLE
            bn_parent.invalidate()
        }
        else {
            activity_seekbar.visibility = View.GONE
            bn_parent.invalidate()
        }
    }

    private fun bindService() {
        if (!Shared.serviceLinked()) {
            if (Shared.serviceRunning(MusicService::class.java, applicationContext))
                bindService(
                    Intent(applicationContext, MusicService::class.java),
                    serviceConn, Context.BIND_IMPORTANT
                )
        } else {
            mService = Shared.mService
            songChange(GetSongChangedEvent())
            playPauseEvent(GetPlayPauseEvent(mService!!.getMediaPlayer().run {
                if (this.isPlaying) SongState.playing
                else SongState.paused
            }))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun playPauseEvent(playPauseEvent: GetPlayPauseEvent) {
        playing = playPauseEvent.state == SongState.playing
        if (playing) Glide.with(this).load(R.drawable.pause).into(bb_icon)
        else Glide.with(this).load(R.drawable.play).into(bb_icon)

        if (playing) startSeekbarUpdates()
        else {
            if (scheduled) {
                scheduled = false
                timer.cancel()
                timer.purge()
            }
        }
    }

    private fun startSeekbarUpdates() {
        if (!scheduled) {
            scheduled = true
            timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    activity_seekbar.progress = Shared.mService.getMediaPlayer().currentPosition
                }
            }, 0, 1000)
        }
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun songChange(@Suppress("UNUSED_PARAMETER") event: GetSongChangedEvent) {
        activity_seekbar.progress = 0
        activity_seekbar.max = Shared.mService.getMediaPlayer().duration

        startSeekbarUpdates()
        val song = Shared.mService.getPlayQueue()[Shared.mService.getCurrentIndex()]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            bb_song.text = Html.fromHtml(
                "${song.name} <font color=\"#5e92f3\">•</font> ${song.artist}",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } else {
            bb_song.text = "${song.name} • ${song.artist}"
        }
    }

    @Subscribe
    fun durationUpdate(durationEvent: GetDurationEvent) {
        activity_seekbar.max = durationEvent.duration
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!))
    }

    override fun onPause() {
        super.onPause()
        if (scheduled) {
            scheduled = false
            timer.cancel()
            timer.purge()
        }
        EventBus.getDefault().unregister(this)
    }

    override fun onResume() {
        super.onResume()
        bindService()
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun exitEvent(@Suppress("UNUSED_PARAMETER") exitEvent: ExitEvent) { finish() }

    override fun onStop() {
        super.onStop()
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
    }

    override fun sendItem(song: Song, mode: String) {
        var currentMode= PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString("mode_key", MusicMode.download)
        if(mode.isNotEmpty())
            currentMode=mode
        when (currentMode) {
            MusicMode.download -> {
                val songL = ArrayList<String>()
                songL.add(song.name)
                songL.add(song.youtubeLink)
                songL.add(song.artist)
                songL.add(song.ytmThumbnail)
                val serviceIntentService = Intent(applicationContext, DownloadService::class.java)
                    .putStringArrayListExtra("song", songL)
                    .putExtra("receiver", mServiceResultReceiver)
                enqueueDownload(this, serviceIntentService)
                Toast.makeText(applicationContext, "${song.name} ${getString(R.string.dl_added)}", Toast.LENGTH_SHORT).show()
                /*
                    * takes user back to the home screen when download starts *
                    mainContent.currentItem = -1
                    bottomNavigation.menu.findItem(R.id.home_menu)?.isChecked = true
                 */
            }

            MusicMode.stream -> {
                home.streamAudio(song, false)
                loadingEvent(HomeLoadingEvent(true))
            }

            MusicMode.both -> {
                home.streamAudio(song, true)
                loadingEvent(HomeLoadingEvent(true))
            }
        }
    }

    override fun onReceiveResult(resultCode: Int) { home.updateSongList() }
}

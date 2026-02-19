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
import android.os.IBinder
import android.text.Html
import android.view.TouchDelegate
import android.view.View
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.AbleApplication
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.ViewPagerAdapter
import io.github.uditkarode.able.databinding.ActivityMainBinding
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.fragments.Search
import io.github.uditkarode.able.model.MusicMode
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.model.song.SongState
import io.github.uditkarode.able.services.DownloadService
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.CustomDownloader
import io.github.uditkarode.able.utils.MusicClientActivity
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import java.io.ByteArrayOutputStream

/**
 * First activity that shows up when the user opens the application
 */
class MainActivity : MusicClientActivity(), Search.SongCallback {
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var serviceConn: ServiceConnection
    private lateinit var mainContent: ViewPager2
    private lateinit var home: Home
    private var seekbarJob: Job? = null

    private var mService: MusicService? = null
    private var playing = false
    private var isCurrentlyLoading = false
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        NewPipe.init(CustomDownloader.getInstance())
        Shared.cleanupTempFiles()

        if (!getSharedPreferences("able_prefs", MODE_PRIVATE)
                .getBoolean("welcome_shown", false)
        ) {
            super.onCreate(savedInstanceState)
            startActivity(Intent(this@MainActivity, Welcome::class.java))
            finish()
            return
        }

        // Request notification permission for existing users (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        launch(Dispatchers.Main) {
            Shared.defBitmap = (ResourcesCompat.getDrawable(
                resources,
                R.drawable.def_albart, null
            ) as BitmapDrawable).bitmap
            val outputStream = ByteArrayOutputStream()
            Shared.defBitmap.compress(Bitmap.CompressFormat.JPEG, 20, outputStream)
            val byte = outputStream.toByteArray()
            Shared.defBitmap = BitmapFactory.decodeByteArray(
                byte,
                0, byte.size
            )
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mainContent = binding.mainContent
        binding.bbIcon.setOnClickListener {
            if (Shared.serviceRunning(MusicService::class.java, this@MainActivity)) {
                if (playing) mService?.setPlayPause(SongState.paused)
                else mService?.setPlayPause(SongState.playing)
            }
        }
        // extend the touchable area for the play button, since it's so small.
        (binding.bbIcon.parent as View).post {
            val rect = Rect().also {
                binding.bbIcon.getHitRect(it)
                it.top -= 200
                it.left -= 200
                it.bottom += 200
                it.right += 200
            }

            (binding.bbIcon.parent as View).touchDelegate = TouchDelegate(rect, binding.bbIcon)
        }

        home = Home()
        mainContent.isUserInputEnabled = false
        mainContent.adapter = ViewPagerAdapter(this, home)
        mainContent.setPageTransformer { page, _ ->
            page.alpha = 0f
            page.visibility = View.VISIBLE

            page.animate()
                .alpha(1f).duration = 200
        }

        bottomNavigation = binding.bottomNavigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home_menu -> binding.mainContent.currentItem = 0
                R.id.search_menu -> binding.mainContent.currentItem = 1
                R.id.settings_menu -> binding.mainContent.currentItem = 2
            }
            true
        }

        binding.activitySeekbar.thumb.alpha = 0

        binding.bbSong.isSelected = true

        binding.bbSong.setOnClickListener {
            if (Shared.serviceRunning(MusicService::class.java, this@MainActivity))
                startActivity(Intent(this@MainActivity, Player::class.java))
        }

        binding.bbExpand.setOnClickListener {
            if (Shared.serviceRunning(MusicService::class.java, this@MainActivity))
                startActivity(Intent(this@MainActivity, Player::class.java))
        }

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                songChange()
                playPauseEvent(service.getService().getMediaPlayer().run {
                    if (this.isPlaying) SongState.playing
                    else SongState.paused
                })
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
    }

    private fun loadingEvent(loading: Boolean) {
        isCurrentlyLoading = loading
        binding.bbProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.activitySeekbar.visibility = View.GONE
            binding.bbIcon.visibility = View.INVISIBLE
        } else {
            binding.activitySeekbar.visibility = View.VISIBLE
            binding.bbIcon.visibility = View.VISIBLE
        }
        binding.bnParent.invalidate()
    }

    private fun bindService() {
        if (Shared.serviceRunning(MusicService::class.java, this@MainActivity))
            bindService(
                Intent(this@MainActivity, MusicService::class.java),
                serviceConn,
                0
            )
    }

    fun playPauseEvent(state: SongState) {
        launch(Dispatchers.Main) {
            if (state == SongState.playing) {
                if (isCurrentlyLoading) {
                    loadingEvent(false)
                }
                Glide.with(this@MainActivity).load(R.drawable.pause)
                    .into(binding.bbIcon)
                playing = true
            } else {
                playing = false
                Glide.with(this@MainActivity).load(R.drawable.play).into(binding.bbIcon)
            }

            if (isCurrentlyLoading) {
                binding.bbIcon.visibility = View.INVISIBLE
            }

            if (state == SongState.playing) startSeekbarUpdates()
            else seekbarJob?.cancel()
        }
    }

    private fun startSeekbarUpdates() {
        if (seekbarJob?.isActive == true) return
        seekbarJob = launch {
            while (isActive) {
                binding.activitySeekbar.progress =
                    mService?.getMediaPlayer()?.currentPosition ?: 0
                delay(1000)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun songChange() {
        if (mService != null) {
            launch(Dispatchers.Main) {
                val song = mService!!.getPlayQueue()[mService!!.getCurrentIndex()]

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    binding.bbSong.text = Html.fromHtml(
                        "${song.name} <font color=\"#5e92f3\">•</font> ${song.artist}",
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                } else {
                    binding.bbSong.text = "${song.name} • ${song.artist}"
                }

                binding.activitySeekbar.progress = 0

                try {
                    val duration = mService!!.getMediaPlayer().duration
                    if (duration > 0) binding.activitySeekbar.max = duration
                } catch (_: Exception) {}
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!, AbleApplication.viewPump))
    }

    override fun onPause() {
        super.onPause()
        seekbarJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (mService == null)
            bindService()
        else {
            songChange()
            if (MusicService.isLoading) {
                loadingEvent(true)
            } else {
                playPauseEvent(
                    if ((mService as MusicService)
                            .getMediaPlayer().isPlaying
                    ) SongState.playing else SongState.paused
                )
            }
        }
    }

    override fun sendItem(song: Song, mode: String) {
        var currentMode = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
            .getString("mode_key", MusicMode.download)
        if (mode.isNotEmpty())
            currentMode = mode

        song.ytmThumbnail = Shared.upscaleThumbnailUrl(song.ytmThumbnail)
        when (currentMode) {
            MusicMode.download -> {
                if (DownloadService.isAlreadyQueued(song.youtubeLink)) {
                    Toast.makeText(
                        this@MainActivity,
                        "${song.name} is already downloading",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                val songL = ArrayList<String>()
                songL.add(song.name)
                songL.add(song.youtubeLink)
                songL.add(song.artist)
                songL.add(song.ytmThumbnail)
                val dlIntent = Intent(this@MainActivity, DownloadService::class.java)
                    .putStringArrayListExtra("song", songL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(dlIntent)
                } else {
                    startService(dlIntent)
                }
                Toast.makeText(
                    this@MainActivity,
                    "${song.name} ${getString(R.string.dl_added)}",
                    Toast.LENGTH_SHORT
                ).show()
                /*
                    * takes user back to the home screen when download starts *
                    mainContent.currentItem = -1
                    bottomNavigation.menu.findItem(R.id.home_menu)?.isChecked = true
                 */
            }

            MusicMode.stream -> {
                home.streamAudio(song)
                launch(Dispatchers.Main) {
                    loadingEvent(true)
                }
            }
        }
    }

    override fun playStateChanged(state: SongState) {
        playPauseEvent(state)
    }

    override fun songChanged() {
        songChange()
    }

    override fun durationChanged(duration: Int) {
        launch(Dispatchers.Main) {
            binding.activitySeekbar.max = duration
        }
    }

    override fun isExiting() {
        finish()
    }

    override fun queueChanged(arrayList: ArrayList<Song>) {}

    override fun shuffleRepeatChanged(onShuffle: Boolean, onRepeat: Boolean) {}

    override fun indexChanged(index: Int) {}

    override fun isLoading(doLoad: Boolean) {
        if (doLoad) {
            launch(Dispatchers.Main) {
                loadingEvent(true)
            }
        }
        // Don't call loadingEvent(false) here — prepareAsync completes
        // before audio actually starts. Loading ends in playPauseEvent
        // when SongState.playing is received.
    }

    override fun spotifyImportChange(starting: Boolean) {}

    override fun serviceStarted() {
        bindService()
    }
}

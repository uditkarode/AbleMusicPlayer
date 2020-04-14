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
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.TouchDelegate
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import co.revely.gradient.RevelyGradient
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.customListAdapter
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.bumptech.glide.Glide
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.events.*
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.player.artist_name
import kotlinx.android.synthetic.main.player.complete_position
import kotlinx.android.synthetic.main.player.next_song
import kotlinx.android.synthetic.main.player.player_bg
import kotlinx.android.synthetic.main.player.player_center_icon
import kotlinx.android.synthetic.main.player.player_current_position
import kotlinx.android.synthetic.main.player.player_seekbar
import kotlinx.android.synthetic.main.player.previous_song
import kotlinx.android.synthetic.main.player.repeat_button
import kotlinx.android.synthetic.main.player.shuffle_button
import kotlinx.android.synthetic.main.player.song_name
import kotlinx.android.synthetic.main.player410.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class Player : AppCompatActivity() {
    private var onShuffle = false
    private var onRepeat = false
    private var playing = SongState.paused
    private var scheduled = false

    private lateinit var timer: Timer
    private lateinit var mService: MusicService
    private lateinit var serviceConn: ServiceConnection

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

        val ydpi = DisplayMetrics().run {
            windowManager.defaultDisplay.getMetrics(this)
            this.ydpi
        }

        if(ydpi > 400) setContentView(R.layout.player410)
        else if(ydpi >= 395) setContentView(R.layout.player400)
        else if(ydpi < 395 && ydpi > 230) setContentView(R.layout.player320)
        else setContentView(R.layout.player220)

        player_down_arrow?.setOnClickListener {
            finish()
        }

        shuffle_button.setOnClickListener {
            if (onShuffle) {
                mService.setShuffleRepeat(shuffle = false, repeat = onRepeat)
            } else {
                mService.setShuffleRepeat(shuffle = true, repeat = onRepeat)
            }
        }

        player_center_icon.setOnClickListener {
            thread {
                if (playing == SongState.playing) mService.setPlayPause(SongState.paused)
                else mService.setPlayPause(SongState.playing)
            }
        }

        repeat_button.setOnClickListener {
            if (onRepeat) {
                mService.setShuffleRepeat(shuffle = onShuffle, repeat = false)
                DrawableCompat.setTint(repeat_button.drawable, Color.parseColor("#80fbfbfb"))
            } else {
                mService.setShuffleRepeat(shuffle = onShuffle, repeat = true)
                DrawableCompat.setTint(repeat_button.drawable, Color.parseColor("#805e92f3"))
            }
        }

        next_song.setOnClickListener {
            mService.setNextPrevious(next = true)
        }

        previous_song.setOnClickListener {
            mService.setNextPrevious(next = false)
        }

        RevelyGradient
            .linear()
            .colors(
                intArrayOf(
                    Color.parseColor("#002171"),
                    Color.parseColor("#212121")
                )
            )
            .angle(90f)
            .alpha(0.2f)
            .onBackgroundOf(player_bg)

        player_seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mService.seekTo(seekBar.progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        img_albart.setOnClickListener {
            MaterialDialog(this@Player).show {
                title(text = "Enter the song name")
                input("e.g. Wake Up Eden"){ _, charSequence ->
                    updateAlbumArt(charSequence.toString())
                }
                getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
            }
        }

        song_name.setOnClickListener {
            val current = mService.playQueue[mService.currentIndex]
            MaterialDialog(this@Player).show {
                title(text = "Enter the new song name")
                input("e.g. Wake Up"){ _, charSequence ->
                    val ext = current.filePath.run {
                        this.substring(this.lastIndexOf(".") + 1)
                    }
                    when (val rc = FFmpeg.execute(
                        "-i " +
                                "\"${current.filePath}\" -c copy " +
                                "-metadata title=\"$charSequence\" " +
                                "-metadata artist=\"${current.artist}\"" +
                                " \"${current.filePath}.new.$ext\""
                    )) {
                        Config.RETURN_CODE_SUCCESS -> {
                            File(current.filePath).delete()
                            File(current.filePath + ".new.$ext").renameTo(File(current.filePath))
                            EventBus.getDefault().post(GetMetaDataEvent(name = charSequence.toString()))
                        }
                        Config.RETURN_CODE_CANCEL -> {
                            Log.e(
                                "ERR>",
                                "Command execution cancelled by user."
                            )
                        }
                        else -> {
                            Log.e(
                                "ERR>",
                                String.format(
                                    "Command execution failed with rc=%d and the output below.",
                                    rc
                                )
                            )
                        }
                    }
                }
                getInputField().setText(current.name)
                getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
            }
        }

        artist_name.setOnClickListener {
            val current = mService.playQueue[mService.currentIndex]
            MaterialDialog(this@Player).show {
                title(text = "Enter the new song artist")
                input("e.g. Eden"){ _, charSequence ->
                    val ext = current.filePath.run {
                        this.substring(this.lastIndexOf(".") + 1)
                    }
                    when (val rc = FFmpeg.execute(
                        "-i " +
                                "\"${current.filePath}\" -c copy " +
                                "-metadata title=\"${current.name}\" " +
                                "-metadata artist=\"$charSequence\"" +
                                " \"${current.filePath}.new.$ext\""
                    )) {
                        Config.RETURN_CODE_SUCCESS -> {
                            File(current.filePath).delete()
                            File(current.filePath + ".new.$ext").renameTo(File(current.filePath))
                            EventBus.getDefault().post(GetMetaDataEvent(artist = charSequence.toString()))
                        }
                        Config.RETURN_CODE_CANCEL -> {
                            Log.e(
                                "ERR>",
                                "Command execution cancelled by user."
                            )
                        }
                        else -> {
                            Log.e(
                                "ERR>",
                                String.format(
                                    "Command execution failed with rc=%d and the output below.",
                                    rc
                                )
                            )
                        }
                    }
                }
                getInputField().setText(current.artist)
                getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
            }
        }

        (shuffle_button.parent as View).post {
            val rect = Rect().also {
                shuffle_button.getHitRect(it)
                it.top -= 200
                it.left -= 200
                it.bottom += 200
                it.right += 100
            }

            (shuffle_button.parent as View).touchDelegate = TouchDelegate(rect, shuffle_button)
        }

        (repeat_button.parent as View).post {
            val rect = Rect().also {
                repeat_button.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 200
            }

            (repeat_button.parent as View).touchDelegate = TouchDelegate(rect, repeat_button)
        }

        (previous_song.parent as View).post {
            val rect = Rect().also {
                previous_song.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 100
            }

            (previous_song.parent as View).touchDelegate = TouchDelegate(rect, previous_song)
        }

        (next_song.parent as View).post {
            val rect = Rect().also {
                next_song.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 100
            }

            (next_song.parent as View).touchDelegate = TouchDelegate(rect, next_song)
        }

        (player_center_icon.parent as View).post {
            val rect = Rect().also {
                player_center_icon.getHitRect(it)
                it.top -= 200
                it.left -= 150
                it.bottom += 200
                it.right += 150
            }

            (player_center_icon.parent as View).touchDelegate = TouchDelegate(rect, player_center_icon)
        }

        player_queue?.setOnClickListener {
            /* TODO add to settings val additive = if(!mService.onRepeat){
                val ret = arrayListOf<Song>()
                ret.add(Song(name = "(repeat)", placeholder = true))
                if(mService.playQueue.size > 3){
                    ret += mService.playQueue.subList(0, 3)
                    ret.add(Song(name = "...", placeholder = true))
                    ret
                }
                else ret + mService.playQueue
            } else {
                arrayListOf()
            } */

            MaterialDialog(this@Player, BottomSheet()).show {
                customListAdapter(
                    SongAdapter(
                        ArrayList(
                            mService.playQueue.subList(
                                mService.currentIndex,
                                mService.playQueue.size
                            ) //+ additive
                        )
                    )
                )
            }
        }


        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                onBindDone()
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
    }

    private fun onBindDone(){
        mService = Shared.mService
        if (mService.mediaPlayer.isPlaying) Glide.with(this).load(R.drawable.pause).into(player_center_icon)
        else Glide.with(this).load(R.drawable.play).into(player_center_icon)
        songChangeEvent(GetSongChangedEvent())
    }

    private fun bindEvent() {
        if(!Shared.serviceLinked()){
            if (Shared.serviceRunning(MusicService::class.java, this@Player))
                bindService(
                    Intent(this@Player, MusicService::class.java),
                    serviceConn,
                    Context.BIND_IMPORTANT
                )
        } else onBindDone()
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

    private fun startSeekbarUpdates() {
        if (!scheduled) {
            scheduled = true
            timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        player_seekbar.progress = mService.mediaPlayer.currentPosition
                        player_current_position.text = getDurationFromMs(player_seekbar.progress)
                    }
                }
            }, 0, 1000)
        }
    }

    @Subscribe
    fun getPlayPauseEvent(pp: GetPlayPauseEvent) {
        playPauseEvent(pp.state)
    }

    private fun playPauseEvent(ss: SongState) {
        playing = ss
        runOnUiThread {
            if (playing == SongState.playing) player_center_icon.setImageDrawable(getDrawable(R.drawable.pause))
            else player_center_icon.setImageDrawable(getDrawable(R.drawable.play))
        }

        if (playing == SongState.playing) {
            startSeekbarUpdates()
        } else {
            if (scheduled) {
                scheduled = false
                timer.cancel()
                timer.purge()
            }
        }
    }

    private fun updateAlbumArt(customSongName: String? = null){
        thread {
            try {
                val current = mService.playQueue[mService.currentIndex]
                val imageName = current.filePath.run {
                    this.substring(this.lastIndexOf("/") + 1).substring(0, 11)
                }

                val img = File(Constants.ableSongDir.absolutePath + "/album_art", imageName)
                if(img.exists() && customSongName == null){
                    runOnUiThread {
                        Glide.with(this@Player).load(img).into(img_albart)
                        note_ph.visibility = View.GONE
                    }
                } else {
                    val albumArtRequest = if(customSongName == null){
                        Request.Builder()
                            .url(Constants.DEEZER_API + current.name)
                            .get()
                            .addHeader("x-rapidapi-host", "deezerdevs-deezer.p.rapidapi.com")
                            .addHeader("x-rapidapi-key", Constants.RAPID_API_KEY)
                            .build()
                    } else {
                        Request.Builder()
                            .url(Constants.DEEZER_API + customSongName)
                            .get()
                            .addHeader("x-rapidapi-host", "deezerdevs-deezer.p.rapidapi.com")
                            .addHeader("x-rapidapi-key", Constants.RAPID_API_KEY)
                            .build()
                    }

                    val response = OkHttpClient().newCall(albumArtRequest).execute().body
                    try {
                        if(response != null){
                            val imgLink = JSONObject(response.string()).getJSONArray("data")
                                .getJSONObject(0).getJSONObject("album").getString("cover_big")

                            try {
                                val drw = Glide
                                    .with(this@Player)
                                    .load(imgLink)
                                    .skipMemoryCache(true)
                                    .submit()
                                    .get()

                                val bmp = drw.toBitmap()

                                if(img.exists()) img.delete()
                                Shared.saveAlbumArtToDisk(bmp, imageName)

                                runOnUiThread {
                                    img_albart.setImageDrawable(drw)
                                    if(mService.mediaPlayer.isPlaying){
                                        mService.showNotification(mService.generateAction(
                                            R.drawable.pause,
                                            "Pause",
                                            "ACTION_PAUSE"
                                        ), true, bmp
                                        )
                                    } else {
                                        mService.showNotification(
                                            mService.generateAction(
                                                R.drawable.play,
                                                "Play",
                                                "ACTION_PLAY"
                                            ), false, bmp
                                        )
                                    }
                                    note_ph.visibility = View.GONE
                                }
                            } catch (e: Exception){
                                Log.e("ERR>", e.toString())
                            }
                        }
                    } catch (e: Exception){
                        Log.e("ERR>", e.toString())
                    }
                }
            } catch (e: Exception){
                Log.e("ERR>", e.toString())
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun metadataChangeEvent(metaDataEvent: GetMetaDataEvent){
        mService.playQueue = Shared.getSongList(Constants.ableSongDir)

        if(metaDataEvent.name != null){
            song_name.text = metaDataEvent.name
            mService.currentIndex = mService.playQueue.run {
                this.indexOf(this.find { it.name == metaDataEvent.name } )
            }
        }

        if(metaDataEvent.artist != null) artist_name.text = metaDataEvent.artist

        if(mService.mediaPlayer.isPlaying){
            mService.showNotification(mService.generateAction(
                R.drawable.pause,
                "Pause",
                "ACTION_PAUSE"
            ), true
            )
        } else {
            mService.showNotification(
                mService.generateAction(
                    R.drawable.play,
                    "Play",
                    "ACTION_PLAY"
                ), false
            )
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songChangeEvent(songChangedEvent: GetSongChangedEvent) {
        songChangedEvent.toString() /* because the IDE doesn't like it unused */
        updateAlbumArt()

        val duration = mService.mediaPlayer.duration
        player_seekbar.max = duration
        complete_position.text = getDurationFromMs(duration)

        val song = mService.playQueue[mService.currentIndex]
        song_name.text = song.name
        artist_name.text = song.artist
        player_seekbar.progress = mService.mediaPlayer.currentPosition
        playPauseEvent(SongState.playing)
    }

    @Subscribe
    fun setupShuffleRepeat(songEvent: GetShuffleRepeatEvent) {
        onShuffle = songEvent.onShuffle
        onRepeat = songEvent.onRepeat

        if (onShuffle)
            DrawableCompat.setTint(shuffle_button.drawable, Color.parseColor("#805e92f3"))
        else
            DrawableCompat.setTint(shuffle_button.drawable, Color.parseColor("#fbfbfb"))
    }

    @Subscribe
    fun durationUpdate(durationEvent: GetDurationEvent) {
        player_seekbar.max = durationEvent.duration
        complete_position.text = getDurationFromMs(durationEvent.duration)
    }

    private fun getDurationFromMs(durtn: Int): String {
        val duration = durtn.toLong()
        val seconds = (TimeUnit.MILLISECONDS.toSeconds(duration) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)))

        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)

        var ret = "${minutes}:"
        if (seconds < 10) ret += "0"
        return ret + seconds
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!))
    }

    override fun onResume() {
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
        bindEvent()
        super.onResume()
    }

    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        super.onStop()
    }
}
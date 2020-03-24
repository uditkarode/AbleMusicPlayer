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
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import co.revely.gradient.RevelyGradient
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.list.customListAdapter
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.events.*
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.player.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class Player: AppCompatActivity() {
    private var onShuffle = false
    private var onRepeat = false
    private var playing = false
    private var scheduled = false

    private lateinit var timer: Timer
    private lateinit var mService: MusicService
    private lateinit var serviceConn: ServiceConnection

    override fun onCreate(savedInstanceState: Bundle?){
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
        setContentView(R.layout.player)

        player_down_arrow.setOnClickListener {
            finish()
        }

        shuffle_button.setOnClickListener {
            if(onShuffle){
                mService.setShuffleRepeat(shuffle = false, repeat = onRepeat)
            }
            else {
                mService.setShuffleRepeat(shuffle = true, repeat = onRepeat)
            }
        }

        player_center_icon.setOnClickListener {
            thread {
                if(playing) mService.setPlayPause(SongState.paused)
                else mService.setPlayPause(SongState.playing)
            }
        }

        repeat_button.setOnClickListener {
            if(onRepeat) {
                mService.setShuffleRepeat(shuffle = onShuffle, repeat = false)
                DrawableCompat.setTint(repeat_button.drawable, Color.parseColor("#80fbfbfb"))
            }
            else {
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
            .colors(intArrayOf(
                Color.parseColor("#002171"),
                Color.parseColor("#212121")
            ))
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

         player_queue.setOnClickListener {
            MaterialDialog(this@Player, BottomSheet()).show {
                customListAdapter(SongAdapter(
                    ArrayList(mService.playQueue.subList(
                        mService.currentIndex,
                        mService.playQueue.size-1
                    ))
                ))
            }
        }

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                songChangeEvent(GetSongChangedEvent())
            }

            override fun onServiceDisconnected(name: ComponentName) { }
        }

        bindEvent(BindServiceEvent())
    }

    @Subscribe
    private fun bindEvent(bindServiceEvent: BindServiceEvent){
        bindServiceEvent.toString() /* because the IDE doesn't like it unused */
        if(Shared.serviceRunning(MusicService::class.java, this@Player))
            bindService(Intent(this@Player, MusicService::class.java), serviceConn, Context.BIND_IMPORTANT)
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

    private fun startSeekbarUpdates(){
        if(!scheduled){
            scheduled = true
            timer = Timer()
            timer.schedule(object: TimerTask() {
                override fun run() {
                    player_seekbar.progress = mService.mediaPlayer.currentPosition
                    runOnUiThread {
                        player_current_position.text = getDurationFromMs(player_seekbar.progress)
                    }
                }
            }, 0, 1000)
        }
    }

    @Subscribe
    fun getPlayPauseEvent(pp: GetPlayPauseEvent){
        playPauseEvent(pp.state)
    }

    private fun playPauseEvent(ss: SongState){
        playing = ss == SongState.playing
        if(playing) player_center_icon.setImageDrawable(getDrawable(R.drawable.pause))
        else player_center_icon.setImageDrawable(getDrawable(R.drawable.play))

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


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songChangeEvent(songChangedEvent: GetSongChangedEvent){
        songChangedEvent.toString() /* because the IDE doesn't like it unused */
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
    fun setupShuffleRepeat(songEvent: GetShuffleRepeatEvent){
        onShuffle = songEvent.onShuffle
        onRepeat = songEvent.onRepeat

        if(onShuffle)
            DrawableCompat.setTint(shuffle_button.drawable, Color.parseColor("#805e92f3"))
        else
            DrawableCompat.setTint(shuffle_button.drawable, Color.parseColor("#fbfbfb"))
    }

    @Subscribe
    fun durationUpdate(durationEvent: GetDurationEvent){
        player_seekbar.max = durationEvent.duration
        complete_position.text = getDurationFromMs(durationEvent.duration)
    }

    @Subscribe
    fun getProgressUpdate(progressEvent: GetProgressEvent){
        player_seekbar.progress = progressEvent.progress
        player_current_position.text = getDurationFromMs(progressEvent.progress)
    }

    private fun getDurationFromMs(durtn: Int): String {
        val duration = durtn.toLong()
        val seconds = (TimeUnit.MILLISECONDS.toSeconds(duration) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)))

        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)

        var ret = "${minutes}:"
        if(seconds < 10) ret += "0${seconds}"
        else ret += seconds

        return ret
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!))
    }

    override fun onResume() {
        super.onResume()
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
        bindEvent(BindServiceEvent())
    }

    override fun onStop() {
        super.onStop()
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        unbindService(serviceConn)
    }
}
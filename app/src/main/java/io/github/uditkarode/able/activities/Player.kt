package io.github.uditkarode.able.activities

import android.content.Context
import android.graphics.Color
import android.os.Bundle
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
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
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
    private var currentIndex = 0
    private var playing = false
    private var scheduled = false

    private lateinit var timer: Timer
    private lateinit var playQueue: ArrayList<Song>

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
                EventBus.getDefault().postSticky(ShuffleRepeatEvent(false, onRepeat))
            }
            else {
                EventBus.getDefault().postSticky(ShuffleRepeatEvent(true, onRepeat))
            }
        }

        player_center_icon.setOnClickListener {
            thread {
                if(playing) EventBus.getDefault().postSticky(PlayPauseEvent(SongState.paused))
                else EventBus.getDefault().postSticky(PlayPauseEvent(SongState.playing))
            }
        }

        repeat_button.setOnClickListener {
            if(onRepeat) {
                EventBus.getDefault().postSticky(ShuffleRepeatEvent(onShuffle, false))
                DrawableCompat.setTint(repeat_button.drawable, Color.parseColor("#80fbfbfb"))
            }
            else {
                EventBus.getDefault().postSticky(ShuffleRepeatEvent(onShuffle, true))
                DrawableCompat.setTint(repeat_button.drawable, Color.parseColor("#805e92f3"))
            }
        }

        next_song.setOnClickListener {
            EventBus.getDefault().postSticky(NextPreviousEvent(next = true))
        }

        previous_song.setOnClickListener {
            EventBus.getDefault().postSticky(NextPreviousEvent(next = false))
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
                EventBus.getDefault().postSticky(ProgressEvent(seekBar.progress))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        player_queue.setOnClickListener {
            MaterialDialog(this@Player, BottomSheet()).show {
                customListAdapter(SongAdapter(
                    ArrayList(playQueue.subList(
                        currentIndex,
                        playQueue.size-1
                    ))
                ))
            }
        }
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
                    player_seekbar.progress += 1000
                    runOnUiThread {
                        player_current_position.text = getDurationFromMs(player_seekbar.progress)
                    }
                }
            }, 0, 1000)
        }
    }

    private fun playPauseEvent(ss: SongState){
        playing = ss == SongState.playing
        if(playing) player_center_icon.setImageDrawable(getDrawable(R.drawable.pause))
        else player_center_icon.setImageDrawable(getDrawable(R.drawable.play))

        if(playing){
            EventBus.getDefault().postSticky(RequestEvent("GetProgressEvent"))
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
    fun GetPlayPauseUpdate(playPauseEvent: GetPlayPauseEvent){
        playPauseEvent(playPauseEvent.state)
    }

    private fun setUi(song: Song){
        song_name.text = song.name
        artist_name.text = song.artist
        player_seekbar.progress = 0
    }

    @Subscribe
    fun getIndexEvent(getIndexObj: GetIndexEvent){
        currentIndex = getIndexObj.index
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songEvent(songEvent: GetSongEvent){
        setUi(songEvent.song)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songChangeEvent(songChangedEvent: SongChangedEvent){
        player_seekbar.max = songChangedEvent.duration
        complete_position.text = getDurationFromMs(songChangedEvent.duration)
        setUi(songChangedEvent.song)
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

    @Subscribe
    fun progressUpdate(queueEvent: GetQueueEvent){
        playQueue = queueEvent.queue
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
        EventBus.getDefault().register(this)
        EventBus.getDefault().postSticky(RequestEvent("GetShuffleRepeatEvent"))
        EventBus.getDefault().postSticky(RequestEvent("GetSongEvent"))
        EventBus.getDefault().postSticky(RequestEvent("GetPlayPauseEvent"))
        EventBus.getDefault().postSticky(RequestEvent("GetDurationEvent"))
        EventBus.getDefault().postSticky(RequestEvent("GetQueueEvent"))
        EventBus.getDefault().postSticky(RequestEvent("GetIndexEvent"))
        EventBus.getDefault().postSticky(RequestEvent("GetProgressEvent"))
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }
}
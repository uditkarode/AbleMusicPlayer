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

import android.content.*
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.TouchDelegate
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.glidebitmappool.GlideBitmapFactory
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
import kotlinx.android.synthetic.main.player410.img_albart
import kotlinx.android.synthetic.main.player410.note_ph
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

/**
 * The Player UI activity.
 */

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

        when (PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString("player_layout_key", "Default")) {
            "Tiny" -> setContentView(R.layout.player220)

            "Small" -> setContentView(R.layout.player320)

            "Normal" -> setContentView(R.layout.player400)

            "Large" -> setContentView(R.layout.player410)

            "Massive" -> setContentView(R.layout.playermassive)

            else -> {
                if (ydpi > 400) setContentView(R.layout.player410)
                else if (ydpi >= 395) setContentView(R.layout.player400)
                else if (ydpi < 395 && ydpi > 230) setContentView(R.layout.player320)
                else setContentView(R.layout.player220)
            }
        }

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

        setBgColor(0x002171)

        player_seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mService.seekTo(seekBar.progress)
                player_seekbar.progress = mService.getMediaPlayer().currentPosition
                player_current_position.text = getDurationFromMs(player_seekbar.progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        album_art.setOnClickListener {
            MaterialDialog(this@Player).show {
                cornerRadius(20f)
                title(text = this@Player.getString(R.string.enter_song))
                input(this@Player.getString(R.string.song_ex)) { _, charSequence ->
                    updateAlbumArt(charSequence.toString())
                }
                getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
            }
        }
        album_art.setOnLongClickListener {
            MaterialDialog(this@Player).show {
                cornerRadius(20f)
                title(text = this@Player.getString(R.string.enter_song))
                input(prefill = mService.getPlayQueue()[mService.getCurrentIndex()].name) { _, charSequence ->
                    updateAlbumArt(charSequence.toString(), true)
                }
                getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
            }
            true
        }

        song_name.setOnClickListener {
            val current = mService.getPlayQueue()[mService.getCurrentIndex()]
            MaterialDialog(this@Player).show {
                title(text = this@Player.getString(R.string.enter_new_song))
                input(this@Player.getString(R.string.song_ex2)) { _, charSequence ->
                    val ext = current.filePath.run {
                        this.substring(this.lastIndexOf(".") + 1)
                    }
                    when (val rc = FFmpeg.execute(
                        "-i " +
                                "\"${current.filePath}\" -y -c copy " +
                                "-metadata title=\"$charSequence\" " +
                                "-metadata artist=\"${current.artist}\"" +
                                " \"${current.filePath}.new.$ext\""
                    )) {
                        Config.RETURN_CODE_SUCCESS -> {
                            File(current.filePath).delete()
                            File(current.filePath + ".new.$ext").renameTo(File(current.filePath))
                            if (current.isLocal) {
                                /* update media store for the song in question */
                                MediaScannerConnection.scanFile(this@Player,
                                    arrayOf(current.filePath), null,
                                    object : MediaScannerConnection.MediaScannerConnectionClient {
                                        override fun onMediaScannerConnected() {}

                                        override fun onScanCompleted(path: String?, uri: Uri?) {
                                            changeMetadata(
                                                name = charSequence.toString(),
                                                artist = current.artist
                                            )
                                        }
                                    })
                            }
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
            val current = mService.getPlayQueue()[mService.getCurrentIndex()]
            MaterialDialog(this@Player).show {
                title(text = this@Player.getString(R.string.enter_new_art))
                input(this@Player.getString(R.string.art_ex)) { _, charSequence ->
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
                            if (current.isLocal) {
                                /* update media store for the song in question */
                                MediaScannerConnection.scanFile(this@Player,
                                    arrayOf(current.filePath), null,
                                    object : MediaScannerConnection.MediaScannerConnectionClient {
                                        override fun onMediaScannerConnected() {}

                                        override fun onScanCompleted(path: String?, uri: Uri?) {
                                            changeMetadata(
                                                name = current.name,
                                                artist = charSequence.toString()
                                            )

                                        }
                                    })
                            }
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

            (player_center_icon.parent as View).touchDelegate =
                TouchDelegate(rect, player_center_icon)
        }

        player_queue?.setOnClickListener {
            /* TODO add to settings val additive = if(!mService.onRepeat){
                val ret = arrayListOf<Song>()
                ret.add(Song(name = "(repeat)", placeholder = true))
                if(mService.getPlayQueue.size > 3){
                    ret += mService.getPlayQueue.subList(0, 3)
                    ret.add(Song(name = "...", placeholder = true))
                    ret
                }
                else ret + mService.getPlayQueue
            } else {
                arrayListOf()
            } */

            MaterialDialog(this@Player, BottomSheet()).show {
                customListAdapter(
                    SongAdapter(
                        ArrayList(
                            mService.getPlayQueue().subList(
                                mService.getCurrentIndex(),
                                mService.getPlayQueue().size
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
        youtubeProgressbar?.visibility = View.GONE
    }

    private fun onBindDone() {
        mService = Shared.mService
        if (mService.getMediaPlayer().isPlaying) player_center_icon.setImageDrawable(getDrawable(R.drawable.pause))
        else player_center_icon.setImageDrawable(getDrawable(R.drawable.play))
        songChangeEvent(GetSongChangedEvent())
    }

    private fun bindEvent() {
        if (!Shared.serviceLinked()) {
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
                        val songPosition = mService.getMediaPlayer().currentPosition
                        player_seekbar.progress = songPosition
                        player_current_position.text = getDurationFromMs(songPosition)
                    }
                }
            }, 0, 1000)
        }
    }

    /**
     * @param rawColor a color in Integer form (hex).
     * Tints the control buttons to rawColor.
     */
    private fun tintControls(rawColor: Int) {
        val color = if (Shared.isColorDark(rawColor))
            ColorUtils.blendARGB(rawColor, Color.WHITE, 0.9F)
        else
            ColorUtils.blendARGB(rawColor, Color.WHITE, 0.3F)

        previous_song.run {
            this.setImageDrawable(this.drawable.run { this.setTint(color); this })
        }

        player_center_icon.run {
            this.setImageDrawable(this.drawable.run { this.setTint(color); this })
        }

        next_song.run {
            this.setImageDrawable(this.drawable.run { this.setTint(color); this })
        }
    }

    /**
     * @param color the color to set on the background as a gradient.
     * @param lightVibrantColor the color to set on the seekbar, usually
     * derived from the album art.
     */
    private fun setBgColor(color: Int, lightVibrantColor: Int? = null, titleColor: Int? = null) {
        RevelyGradient
            .linear()
            .colors(
                intArrayOf(
                    color,
                    Color.parseColor("#212121")
                )
            )
            .angle(90f)
            .alpha(0.76f)
            .onBackgroundOf(player_bg)

        if (Shared.isColorDark(color)) {
            player_down_arrow.setImageDrawable(getDrawable(R.drawable.down_arrow))
            player_queue.setImageDrawable(getDrawable(R.drawable.pl_playlist))
            if (lightVibrantColor != null) {
                if ((lightVibrantColor and 0xff000000.toInt()) shr 24 == 0) {
                    player_seekbar.progressDrawable.setTint(titleColor!!)
                    player_seekbar.thumb.setTint(titleColor)
                    window.statusBarColor = titleColor
                    tintControls(0x002171)
                } else {
                    player_seekbar.progressDrawable.setTint(lightVibrantColor)
                    player_seekbar.thumb.setTint(lightVibrantColor)
                    window.statusBarColor = lightVibrantColor
                    tintControls(lightVibrantColor)
                }
            }
        } else {
            player_down_arrow.setImageDrawable(getDrawable(R.drawable.down_arrow_black))
            player_queue.setImageDrawable(getDrawable(R.drawable.playlist_black))
            player_seekbar.progressDrawable.setTint(color)
            player_seekbar.thumb.setTint(color)
            tintControls(color)
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

    private fun updateAlbumArt(customSongName: String? = null, forceDeezer: Boolean = false) {
        /* set helper variables */
        img_albart.visibility = View.GONE
        note_ph.visibility = View.VISIBLE
        var didGetArt = false
        val current = mService.getPlayQueue()[mService.getCurrentIndex()]
        val img = File(
            Constants.ableSongDir.absolutePath + "/album_art",
            File(current.filePath).nameWithoutExtension
        )
        val cacheImg = File(
            Constants.ableSongDir.absolutePath + "/cache",
            "sCache" + Shared.getIdFromLink(MusicService.playQueue[MusicService.currentIndex].youtubeLink)
        )

        thread {
            /*
            Check priority:
            1) Album art from metadata (if the song is a local song)
            2) Album art from disk (if the song is not a local song)
            3) Album art from Deezer (regardless of local or not local)

            in state (3), if the song is local, the album art should be added
            to the song metadata, and if not, it should be stored in the Able
            album art folder.
            */


            /* (1) Check albumart in song metadata (if the song is a local song) */
            if (current.isLocal && !forceDeezer) {
                Log.e("INFO>", "Fetching from metadata")
                try {
                    note_ph.visibility = View.GONE
                    val sArtworkUri =
                        Uri.parse("content://media/external/audio/albumart")
                    Shared.bmp = Glide
                        .with(this@Player)
                        .load(ContentUris.withAppendedId(sArtworkUri, current.albumId))
                        .signature(ObjectKey("player"))
                        .submit()
                        .get().toBitmap()
                    runOnUiThread {
                        img_albart.setImageBitmap(Shared.bmp)
                        img_albart.visibility = View.VISIBLE
                        note_ph.visibility = View.GONE
                        Palette.from(Shared.getSharedBitmap()).generate {
                            setBgColor(
                                it?.getDominantColor(0x002171) ?: 0x002171,
                                it?.getLightMutedColor(0x002171) ?: 0x002171,
                                it?.dominantSwatch?.bodyTextColor ?: 0x002171
                            )
                        }
                    }
                    didGetArt = true
                } catch (e: java.lang.Exception) {
                    didGetArt = false
                }
            }

            /* (2) Album art from disk (if the song is not a local song) */
            if (!didGetArt && !forceDeezer) {
                if (!current.isLocal) {
                    Log.e("INFO>", "Fetching from Able folder")
                    val imgToLoad = if (img.exists()) img else cacheImg
                    if (imgToLoad.exists()) {
                        runOnUiThread {
                            img_albart.visibility = View.VISIBLE
                            note_ph.visibility = View.GONE
                            Glide.with(this@Player)
                                .load(imgToLoad)
                                .centerCrop()
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .into(img_albart)

                            Shared.bmp = GlideBitmapFactory.decodeFile(imgToLoad.absolutePath)
                            Palette.from(Shared.getSharedBitmap()).generate {
                                setBgColor(
                                    it?.getDominantColor(0x002171) ?: 0x002171,
                                    it?.getLightMutedColor(0x002171) ?: 0x002171,
                                    it?.lightMutedSwatch?.titleTextColor ?: 0xfbfbfb // causes transparent bar
                                )
                                Shared.clearBitmap()
                            }
                        }
                        didGetArt = true
                    }
                }
            }

            /* (3) Album art from Deezer (regardless of local or not local) */
            if (!didGetArt) {
                Log.e("INFO>", "Fetching from Deezer")
                val albumArtRequest = if (customSongName == null) {
                    Request.Builder()
                        .url(Constants.DEEZER_API + current.name)
                        .get()
                        .addHeader("x-rapidapi-host", "deezerdevs-deezer.p.rapidapi.com")
                        .addHeader("x-rapidapi-key", Constants.RAPID_API_KEY)
                        .cacheControl(CacheControl.FORCE_NETWORK)
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
                    if (response != null) {
                        val json = JSONObject(response.string()).getJSONArray("data")
                            .getJSONObject(0).getJSONObject("album")
                        val imgLink = json.getString("cover_big")
                        val albumName = json.getString("title")

                        try {
                            Shared.bmp = Glide
                                .with(this@Player)
                                .load(imgLink)
                                .centerCrop()
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .submit()
                                .get().toBitmap()

                            Palette.from(Shared.getSharedBitmap()).generate {
                                setBgColor(
                                    it?.getDominantColor(0x002171) ?: 0x002171,
                                    it?.getLightVibrantColor(0x002171) ?: 0x002171,
                                    it?.lightMutedSwatch?.titleTextColor
                                )
                            }

                            if (img.exists()) img.delete()
                            Shared.saveAlbumArtToDisk(Shared.getSharedBitmap(), img)

                            runOnUiThread {
                                img_albart.setImageBitmap(Shared.getSharedBitmap())
                                img_albart.visibility = View.VISIBLE
                                note_ph.visibility = View.GONE
                                if (mService.getMediaPlayer().isPlaying) {
                                    mService.showNotification(
                                        mService.generateAction(
                                            R.drawable.notif_pause,
                                            "Pause",
                                            "ACTION_PAUSE"
                                        ), Shared.getSharedBitmap()
                                    )
                                } else {
                                    mService.showNotification(
                                        mService.generateAction(
                                            R.drawable.notif_play,
                                            "Play",
                                            "ACTION_PLAY"
                                        ), Shared.getSharedBitmap()
                                    )
                                }
                            }
                            Shared.addThumbnails(
                                current.filePath,
                                albumName,
                                this@Player
                            )
                            didGetArt = true
                        } catch (e: Exception) {
                            didGetArt = false
                            Log.e("ERR>", e.toString())
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ERR>", e.toString())
                }
            }

            if (!didGetArt) {
                runOnUiThread {
                    img_albart.visibility = View.GONE
                    note_ph.visibility = View.VISIBLE
                    setBgColor(0x002171)
                    player_seekbar.progressDrawable.setTint(
                        ContextCompat.getColor(
                            this,
                            R.color.thatAccent
                        )
                    )
                    player_seekbar.thumb.setTint(
                        ContextCompat.getColor(
                            this,
                            R.color.colorPrimary
                        )
                    )
                    tintControls(0x002171)
                }
            }
        }
    }

    private fun changeMetadata(name: String, artist: String) {
        runOnUiThread {
            song_name.text = name
            artist_name.text = artist
        }

        if (mService.getMediaPlayer().isPlaying) {
            mService.showNotification(
                mService.generateAction(
                    R.drawable.pause,
                    getString(R.string.pause),
                    "ACTION_PAUSE"
                ), nameOverride = name, artistOverride = artist
            )
        } else {
            mService.showNotification(
                mService.generateAction(
                    R.drawable.play,
                    getString(R.string.play),
                    "ACTION_PLAY"
                ), nameOverride = name, artistOverride = artist
            )
        }

        EventBus.getDefault().postSticky(UpdateQueueEvent())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songChangeEvent(@Suppress("UNUSED_PARAMETER") songChangedEvent: GetSongChangedEvent) {
        updateAlbumArt()

        val duration = mService.getMediaPlayer().duration
        player_seekbar.max = duration
        complete_position.text = getDurationFromMs(duration)

        val song = mService.getPlayQueue()[mService.getCurrentIndex()]
        song_name.text = song.name
        artist_name.text = song.artist
        player_seekbar.progress = mService.getMediaPlayer().currentPosition
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

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun exitEvent(@Suppress("UNUSED_PARAMETER") exitEvent: ExitEvent) {
        finish()
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
        if (!Shared.serviceRunning(MusicService::class.java, this@Player))
            finish()
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

    override fun onBackPressed() {
        super.onBackPressed()
        Handler().postDelayed({
            if (!this.isDestroyed) Glide.with(this@Player).clear(img_albart)
        }, 300)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!this.isDestroyed)
            Glide.with(this@Player).clear(img_albart)
    }
}
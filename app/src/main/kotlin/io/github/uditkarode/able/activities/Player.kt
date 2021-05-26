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
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
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
import com.makeramen.roundedimageview.RoundedImageView
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.databinding.*
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.MusicClientActivity
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.*
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

/**
 * The Player UI activity.
 */

@ExperimentalCoroutinesApi
class Player : MusicClientActivity() {
    private lateinit var serviceConn: ServiceConnection
    private var mService: MusicService? = null
    private lateinit var timer: Timer

    private var playing = SongState.paused
    private var scheduled = false
    private var onShuffle = false
    private var onRepeat = false

    private var lastSelectedColor = 0x00fbfbfb

    private var binding220: Player220Binding?= null
    private var binding320: Player320Binding?= null
    private var binding400: Player400Binding?= null
    private var binding410: Player410Binding? = null
    private var bindingMassive: PlayermassiveBinding? = null

    private lateinit var playerCenterIcon : ImageView
    private lateinit var topControls : RelativeLayout
    private lateinit var playerDownArrow: ImageView
    private lateinit var shuffleButton : ImageView
    private lateinit var repeatButton : ImageView
    private lateinit var nextSong : ImageView
    private lateinit var previousSong : ImageView
    private lateinit var playerCurrentPosition : TextView
    private lateinit var albumArt : RelativeLayout
    private lateinit var playerSeekbar : SeekBar
    private lateinit var songName : TextView
    private lateinit var artistName : TextView
    private lateinit var playerQueue: ImageView
    private lateinit var youtubeProgressbar : ProgressBar
    private lateinit var completePosition : TextView
    private lateinit var imgAlbart: RoundedImageView


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
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(this)
            this.ydpi
        }

        when (PreferenceManager.getDefaultSharedPreferences(this@Player)
            .getString("player_layout_key", "Default")) {
            "Tiny" -> {
                binding220 = Player220Binding.inflate(layoutInflater)
                setContentView(binding220!!.root)
            }

            "Small" -> {
                binding320 = Player320Binding.inflate(layoutInflater)
                setContentView(binding320!!.root)
            }

            "Normal" -> {
                binding400 = Player400Binding.inflate(layoutInflater)
                setContentView(binding400!!.root)
            }

            "Large" -> {
                binding410 = Player410Binding.inflate(layoutInflater)
                setContentView(binding410!!.root)
            }

            "Massive" -> {
                bindingMassive = PlayermassiveBinding.inflate(layoutInflater)
                setContentView(bindingMassive!!.root)
            }

            else -> {
                if (ydpi > 400) {
                    binding410 = Player410Binding.inflate(layoutInflater)
                    setContentView(binding410!!.root)
                }
                else if (ydpi >= 395) {
                    binding400 = Player400Binding.inflate(layoutInflater)
                    setContentView(binding400!!.root)
                }
                else if (ydpi < 395 && ydpi > 230) {
                    binding320 = Player320Binding.inflate(layoutInflater)
                    setContentView(binding320!!.root)
                }
                else {
                    binding220 = Player220Binding.inflate(layoutInflater)
                    setContentView(binding220!!.root)
                }
            }
        }

        /**
         * Since we don't use fitSystemWindows, we need to manually
         * apply window insets as margin.
         */
        if (binding410?.bottomCast != null) {
            binding410?.bottomCast!!.setOnApplyWindowInsetsListener { _, insets ->
                val kek = binding410?.bottomCast!!.layoutParams as ViewGroup.MarginLayoutParams
                @Suppress("DEPRECATION")
                kek.setMargins(0, 0, 0, insets.systemWindowInsetBottom)
                insets
            }
        }
        topControls =
            (binding220?.topControls
                ?:binding320?.topControls
                ?:binding400?.topControls
                ?:binding410?.topControls
                ?:bindingMassive?.topControls) as RelativeLayout

        completePosition =
            (binding220?.completePosition
                ?:binding320?.completePosition
                ?:binding400?.completePosition
                ?:binding410?.completePosition
                ?:bindingMassive?.completePosition) as TextView

        topControls.setOnApplyWindowInsetsListener { _, insets ->
            val kek = topControls.layoutParams as ViewGroup.MarginLayoutParams
            @Suppress("DEPRECATION")
            kek.setMargins(0, insets.systemWindowInsetTop, 0, 0)
            insets
        }

        playerDownArrow =
            (binding220?.playerDownArrow
                ?:binding320?.playerDownArrow
                ?:binding400?.playerDownArrow
                ?:binding410?.playerDownArrow
                ?:bindingMassive?.playerDownArrow) as ImageView


        playerQueue =
            (binding220?.playerQueue
                ?:binding320?.playerQueue
                ?:binding400?.playerQueue
                ?:binding410?.playerQueue
                ?:bindingMassive?.playerQueue) as ImageView

        playerDownArrow.setOnClickListener {
            finish()
        }

        shuffleButton =
            (binding220?.shuffleButton
                ?:binding320?.shuffleButton
                ?:binding400?.shuffleButton
                ?:binding410?.shuffleButton
                ?:bindingMassive?.shuffleButton) as ImageView

        shuffleButton.setOnClickListener {
            if (onShuffle) {
                mService?.setShuffleRepeat(shuffle = false, repeat = onRepeat)
            } else {
                mService?.setShuffleRepeat(shuffle = true, repeat = onRepeat)
            }
        }

        playerCenterIcon =
            (binding220?.playerCenterIcon
                ?:binding320?.playerCenterIcon
                ?:binding400?.playerCenterIcon
                ?:binding410?.playerCenterIcon
                ?:bindingMassive?.playerCenterIcon) as ImageView

        playerCenterIcon.setOnClickListener {
            launch(Dispatchers.Default) {
                if (playing == SongState.playing) mService?.setPlayPause(SongState.paused)
                else mService?.setPlayPause(SongState.playing)
            }
        }

        repeatButton =
            (binding220?.repeatButton
                ?:binding320?.repeatButton
                ?:binding400?.repeatButton
                ?:binding410?.repeatButton
                ?:bindingMassive?.repeatButton) as ImageView

        repeatButton.setOnClickListener {
            if (onRepeat) {
                mService?.setShuffleRepeat(shuffle = onShuffle, repeat = false)
                DrawableCompat.setTint(repeatButton.drawable, Color.parseColor("#80fbfbfb"))
            } else {
                mService?.setShuffleRepeat(shuffle = onShuffle, repeat = true)
                DrawableCompat.setTint(repeatButton.drawable, Color.parseColor("#805e92f3"))
            }
        }

        nextSong =
            (binding220?.nextSong
                ?:binding320?.nextSong
                ?:binding400?.nextSong
                ?:binding410?.nextSong
                ?:bindingMassive?.nextSong) as ImageView

        nextSong.setOnClickListener {
            mService?.setNextPrevious(next = true)
        }

        previousSong =
            (binding220?.previousSong
                ?:binding320?.previousSong
                ?:binding400?.previousSong
                ?:binding410?.previousSong
                ?:bindingMassive?.previousSong) as ImageView

        previousSong.setOnClickListener {
            mService?.setNextPrevious(next = false)
        }

        setBgColor(0x002171)
        playerSeekbar =
            (binding220?.playerSeekbar
                ?:binding320?.playerSeekbar
                ?:binding400?.playerSeekbar
                ?:binding410?.playerSeekbar
                ?:bindingMassive?.playerSeekbar) as SeekBar

        playerCurrentPosition =
            (binding220?.playerCurrentPosition
                ?:binding320?.playerCurrentPosition
                ?:binding400?.playerCurrentPosition
                ?:binding410?.playerCurrentPosition
                ?:bindingMassive?.playerCurrentPosition) as TextView

        playerSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if(mService != null) {
                    val ms = mService as MusicService
                    ms.seekTo(seekBar.progress)
                    playerSeekbar.progress = ms.getMediaPlayer().currentPosition
                    playerCurrentPosition.text = getDurationFromMs(playerSeekbar.progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        albumArt =
            (binding220?.albumArt
                ?:binding320?.albumArt
                ?:binding400?.albumArt
                ?:binding410?.albumArt
                ?:bindingMassive?.albumArt) as RelativeLayout

        albumArt.setOnClickListener {
            if(mService != null) {
                val ms = mService as MusicService
                if (ms.getPlayQueue()[ms.getCurrentIndex()].filePath.contains("emulated/0/"))
                    MaterialDialog(this@Player).show {
                        cornerRadius(20f)
                        title(text = this@Player.getString(R.string.enter_song))
                        input(this@Player.getString(R.string.song_ex)) { _, charSequence ->
                            updateAlbumArt(charSequence.toString(), true)
                        }
                        getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
                    }
            }
        }

        albumArt.setOnLongClickListener {
            if(mService != null) {
                val ms = mService as MusicService
                if(ms.getPlayQueue()[ms.getCurrentIndex()].filePath.contains("emulated/0/"))
                MaterialDialog(this@Player).show {
                    cornerRadius(20f)
                    title(text = this@Player.getString(R.string.enter_song))
                    input(prefill = ms.getPlayQueue()[ms.getCurrentIndex()].name) { _, charSequence ->
                        updateAlbumArt(charSequence.toString(), true)
                    }
                    getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
                }
            }
            true
        }

        songName =
            (binding220?.songName
                ?:binding320?.songName
                ?:binding400?.songName
                ?:binding410?.songName
                ?:bindingMassive?.songName) as TextView

        songName.setOnClickListener {
            if(mService != null) {
                val mService = this.mService as MusicService
                val current = mService.getPlayQueue()[mService.getCurrentIndex()]
                if (current.filePath.contains("emulated/0/")) {
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
                                            object :
                                                MediaScannerConnection.MediaScannerConnectionClient {
                                                override fun onMediaScannerConnected() {}

                                                override fun onScanCompleted(
                                                    path: String?,
                                                    uri: Uri?
                                                ) {
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
            }
        }

        artistName =
            (binding220?.artistName
                ?:binding320?.artistName
                ?:binding400?.artistName
                ?:binding410?.artistName
                ?:bindingMassive?.artistName) as TextView

        artistName.setOnClickListener {
            if(mService != null) {
                val mService = this.mService as MusicService
                val current = mService.getPlayQueue()[mService.getCurrentIndex()]
                if (current.filePath.contains("emulated/0/")) {
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
                                            object :
                                                MediaScannerConnection.MediaScannerConnectionClient {
                                                override fun onMediaScannerConnected() {}

                                                override fun onScanCompleted(
                                                    path: String?,
                                                    uri: Uri?
                                                ) {
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
            }
        }

        (shuffleButton.parent as View).post {
            val rect = Rect().also {
                shuffleButton.getHitRect(it)
                it.top -= 200
                it.left -= 200
                it.bottom += 200
                it.right += 100
            }

            (shuffleButton.parent as View).touchDelegate = TouchDelegate(rect, shuffleButton)
        }

        (repeatButton.parent as View).post {
            val rect = Rect().also {
                repeatButton.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 200
            }

            (repeatButton.parent as View).touchDelegate = TouchDelegate(rect, repeatButton)
        }

        (previousSong.parent as View).post {
            val rect = Rect().also {
                previousSong.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 100
            }

            (previousSong.parent as View).touchDelegate = TouchDelegate(rect, previousSong)
        }

        (nextSong.parent as View).post {
            val rect = Rect().also {
                nextSong.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 100
            }

            (nextSong.parent as View).touchDelegate = TouchDelegate(rect, nextSong)
        }

        (playerCenterIcon.parent as View).post {
            val rect = Rect().also {
                playerCenterIcon.getHitRect(it)
                it.top -= 200
                it.left -= 50
                it.bottom += 200
                it.right += 50
            }

            (playerCenterIcon.parent as View).touchDelegate =
                TouchDelegate(rect, playerCenterIcon)
        }


        playerQueue.setOnClickListener {
            if(mService != null) {
                val mService = this.mService as MusicService
                MaterialDialog(this@Player, BottomSheet()).show {
                    customListAdapter(
                        SongAdapter(
                            ArrayList(
                                mService.getPlayQueue().subList(
                                    mService.getCurrentIndex(),
                                    mService.getPlayQueue().size
                                )
                            ),
                            mServiceFromPlayer = mService
                        )
                    )
                }
            }
        }


        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                onBindDone()
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
        youtubeProgressbar =
            (binding220?.youtubeProgressbar
                ?:binding320?.youtubeProgressbar
                ?:binding400?.youtubeProgressbar
                ?:binding410?.youtubeProgressbar
                ?:bindingMassive?.youtubeProgressbar) as ProgressBar

        youtubeProgressbar.visibility = View.GONE
    }

    private fun onBindDone() {
        if (mService!!.getMediaPlayer().isPlaying) playerCenterIcon.setImageDrawable(
            ContextCompat.getDrawable(
                this@Player,
                R.drawable.nobg_pause
            )
        )
        else playerCenterIcon.setImageDrawable(
            ContextCompat.getDrawable(
                this@Player,
                R.drawable.nobg_play
            )
        )

        playing = if(mService!!.getMediaPlayer().isPlaying) SongState.playing else SongState.paused
        playPauseEvent(playing)

        songChangeEvent()
    }

    private fun bindEvent() {
        if (Shared.serviceRunning(MusicService::class.java, this@Player))
            bindService(
                Intent(this@Player, MusicService::class.java),
                serviceConn,
                0
            )
    }

    override fun onPause() {
        super.onPause()
        if (scheduled) {
            scheduled = false
            timer.cancel()
            timer.purge()
        }
    }

    private fun startSeekbarUpdates() {
        if (!scheduled) {
            scheduled = true
            timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    if(mService != null) {
                        val mService = mService as MusicService
                        launch(Dispatchers.Main) {
                            val songPosition = mService.getMediaPlayer().currentPosition
                            playerSeekbar.progress = songPosition
                            playerCurrentPosition.text = getDurationFromMs(songPosition)
                        }
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

        lastSelectedColor = color

        previousSong.run {
            this.setImageDrawable(this.drawable.run { this.setTint(color); this })
        }

        playerCenterIcon.run {
            this.setImageDrawable(this.drawable.run { this.setTint(color); this })
        }

        nextSong.run {
            this.setImageDrawable(this.drawable.run { this.setTint(color); this })
        }
    }

    /**
     * @param color the color to set on the background as a gradient.
     * @param lightVibrantColor the color to set on the seekbar, usually
     * derived from the album art.
     */
    private fun setBgColor(
        color: Int,
        lightVibrantColor: Int? = null,
        palette: Palette? = null
    ) {
        val playerBg =
            binding220?.playerBg
                ?:binding320?.playerBg
                ?:binding400?.playerBg
                ?:binding410?.playerBg
                ?:bindingMassive?.playerBg

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
            .onBackgroundOf(playerBg!!)


        if (Shared.isColorDark(color)) {
            playerDownArrow.setImageDrawable(
                ContextCompat.getDrawable(
                    this@Player,
                    R.drawable.down_arrow
                )
            )
            playerQueue.setImageDrawable(
                ContextCompat.getDrawable(
                    this@Player,
                    R.drawable.pl_playlist
                )
            )
            if (lightVibrantColor != null) {
                if ((lightVibrantColor and 0xff000000.toInt()) shr 24 == 0) {
                    val newTitleColor = palette?.darkVibrantSwatch?.titleTextColor
                        ?: palette?.dominantSwatch?.titleTextColor
                    playerSeekbar.progressDrawable.setTint(newTitleColor!!)
                    playerSeekbar.thumb.setTint(newTitleColor)
                    tintControls(0x002171)
                } else {
                    playerSeekbar.progressDrawable.setTint(lightVibrantColor)
                    playerSeekbar.thumb.setTint(lightVibrantColor)
                    tintControls(lightVibrantColor)
                }
            }
        } else {
            playerDownArrow.setImageDrawable(
                ContextCompat.getDrawable(
                    this@Player,
                    R.drawable.down_arrow_black
                )
            )
            playerQueue.setImageDrawable(
                ContextCompat.getDrawable(
                    this@Player,
                    R.drawable.playlist_black
                )
            )
            playerSeekbar.progressDrawable.setTint(color)
            playerSeekbar.thumb.setTint(color)
            tintControls(color)
        }
    }

    private fun playPauseEvent(ss: SongState) {
        playing = ss
        launch(Dispatchers.Main) {
            if (playing == SongState.playing) playerCenterIcon.setImageDrawable(
                ContextCompat.getDrawable(
                    this@Player,
                    R.drawable.nobg_pause
                )
            )
            else playerCenterIcon.setImageDrawable(
                ContextCompat.getDrawable(
                    this@Player,
                    R.drawable.nobg_play
                )
            )

            tintControls(lastSelectedColor)
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
        if(mService != null) {
            val mService = mService as MusicService
            /* set helper variables */
            imgAlbart =
                (binding220?.imgAlbart
                    ?:binding320?.imgAlbart
                    ?:binding400?.imgAlbart
                    ?:binding410?.imgAlbart
                    ?:bindingMassive?.imgAlbart) as RoundedImageView

            val notePh =
                (binding220?.notePh
                    ?:binding320?.notePh
                    ?:binding400?.notePh
                    ?:binding410?.notePh
                    ?:bindingMassive?.imgAlbart) as ImageView

            imgAlbart.visibility = View.GONE
            notePh.visibility = View.VISIBLE
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

            launch(Dispatchers.IO) {
                /*
                Check priority:
                1) Album art from metadata (if the song is a local song)
                2) Album art from disk (if the song is not a local song)
                3) Album art from Deezer (regardless of song being local or not)

                in case (3), if the song is local, the album art should be added
                to the song metadata, and if not, it should be stored in the Able
                album art folder.
                */


                /* (1) Check albumart in song metadata (if the song is a local song) */
                if (current.isLocal && !forceDeezer) {
                    Log.i("INFO>", "Fetching from metadata")
                    try {
                        notePh.visibility = View.GONE
                        val sArtworkUri =
                            Uri.parse("content://media/external/audio/albumart")

                        Shared.bmp = Glide
                            .with(this@Player)
                            .load(ContentUris.withAppendedId(sArtworkUri, current.albumId))
                            .signature(ObjectKey("player"))
                            .submit()
                            .get().toBitmap()

                        launch(Dispatchers.Main) {
                            imgAlbart.setImageBitmap(Shared.bmp)
                            imgAlbart.visibility = View.VISIBLE
                            notePh.visibility = View.GONE
                            Palette.from(Shared.getSharedBitmap()).generate {
                                setBgColor(
                                    it?.getDominantColor(0x002171) ?: 0x002171,
                                    it?.getLightMutedColor(0x002171) ?: 0x002171,
                                    it
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
                        Log.i("INFO>", "Fetching from Able folder")
                        val imgToLoad = if (img.exists()) img else cacheImg
                        if (imgToLoad.exists()) {
                            launch(Dispatchers.Main) {
                                imgAlbart.visibility = View.VISIBLE
                                notePh.visibility = View.GONE
                                Glide.with(this@Player)
                                    .load(imgToLoad)
                                    .centerCrop()
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .skipMemoryCache(true)
                                    .into(imgAlbart)
                                try {
                                    Shared.bmp?.recycle()
                                    Shared.bmp = BitmapFactory.decodeFile(imgToLoad.absolutePath)
                                    Palette.from(Shared.getSharedBitmap()).generate {
                                        setBgColor(
                                            it?.getDominantColor(0x002171) ?: 0x002171,
                                            it?.getLightMutedColor(0x002171) ?: 0x002171,
                                            it // causes transparent bar
                                        )

                                        Shared.clearBitmap()
                                    }
                                } catch (e: java.lang.Exception) {
                                    e.printStackTrace()
                                }
                            }
                            didGetArt = true
                        }
                    }
                }

                /* (3) Album art from Deezer (regardless of song being local or not) */
                if (!didGetArt && Shared.isInternetConnected(this@Player)) {
                    Log.i("INFO>", "Fetching from Deezer")
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
                                        it
                                    )
                                }

                                if (img.exists()) img.delete()
                                Shared.saveAlbumArtToDisk(Shared.getSharedBitmap(), img)

                                launch(Dispatchers.Main) {
                                    imgAlbart.setImageBitmap(Shared.getSharedBitmap())
                                    imgAlbart.visibility = View.VISIBLE
                                    notePh.visibility = View.GONE
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
                                launch(Dispatchers.Main) {
                                    Home.songAdapter?.notifyItemChanged(mService.getCurrentIndex())
                                }
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
                    launch(Dispatchers.Main) {
                        imgAlbart.visibility = View.GONE
                        notePh.visibility = View.VISIBLE
                        setBgColor(0x002171)
                        playerSeekbar.progressDrawable.setTint(
                            ContextCompat.getColor(
                                this@Player,
                                R.color.thatAccent
                            )
                        )
                        playerSeekbar.thumb.setTint(
                            ContextCompat.getColor(
                                this@Player,
                                R.color.colorPrimary
                            )
                        )
                        tintControls(0x002171)
                    }
                }
            }
        }
    }

    private fun changeMetadata(name: String, artist: String) {
        if(mService != null) {
            val mService = mService as MusicService
            launch(Dispatchers.Main) {
                songName.text = name
                artistName.text = artist
            }

            if (mService.getMediaPlayer().isPlaying) {
                mService.showNotification(
                    mService.generateAction(
                        R.drawable.notif_pause,
                        getString(R.string.pause),
                        "ACTION_PAUSE"
                    ), nameOverride = name, artistOverride = artist
                )
            } else {
                mService.showNotification(
                    mService.generateAction(
                        R.drawable.notif_play,
                        getString(R.string.play),
                        "ACTION_PLAY"
                    ), nameOverride = name, artistOverride = artist
                )
            }

            // TODO MusicService.registeredClients.forEach { it.queueChanged() }
        }
    }

    private fun songChangeEvent() {
        if(mService != null) {
            val mService = mService as MusicService
            updateAlbumArt()

            val duration = mService.getMediaPlayer().duration
            playerSeekbar.max = duration


            completePosition.text = getDurationFromMs(duration)

            val song = mService.getPlayQueue()[mService.getCurrentIndex()]
            songName.text = song.name
            artistName.text = song.artist
            playerSeekbar.progress = mService.getMediaPlayer().currentPosition
        }
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
        super.onResume()
        if(mService == null)
            bindEvent()
        else
            playPauseEvent(if((mService as MusicService)
                    .getMediaPlayer().isPlaying) SongState.playing else SongState.paused)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Handler(Looper.getMainLooper()).postDelayed({
            if (!this.isDestroyed) Glide.with(this@Player).clear(imgAlbart)
        }, 300)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!this.isDestroyed)
            Glide.with(this@Player).clear(imgAlbart)
    }

    override fun playStateChanged(state: SongState) {
        playPauseEvent(state)
    }

    override fun songChanged() = runOnUiThread {
        songChangeEvent()
    }

    override fun durationChanged(duration: Int) {
        launch(Dispatchers.Main) {
            playerSeekbar.max = duration
            completePosition.text = getDurationFromMs(duration)
        }
    }

    override fun isExiting() {
        finish()
    }

    override fun queueChanged(arrayList: ArrayList<Song>) {}

    override fun shuffleRepeatChanged(onShuffle: Boolean, onRepeat: Boolean) {
        launch(Dispatchers.Main) {
            if (onShuffle)
                DrawableCompat.setTint(shuffleButton.drawable, Color.parseColor("#805e92f3"))
            else
                DrawableCompat.setTint(shuffleButton.drawable, Color.parseColor("#fbfbfb"))

            if (onRepeat)
                DrawableCompat.setTint(repeatButton.drawable, Color.parseColor("#805e92f3"))
            else
                DrawableCompat.setTint(repeatButton.drawable, Color.parseColor("#fbfbfb"))

            (this@Player).onShuffle = onShuffle
            (this@Player).onRepeat = onRepeat
        }
    }

    override fun indexChanged(index: Int) {}

    override fun isLoading(doLoad: Boolean) {}

    override fun spotifyImportChange(starting: Boolean) {}

    override fun serviceStarted() {
        bindEvent()
    }
}
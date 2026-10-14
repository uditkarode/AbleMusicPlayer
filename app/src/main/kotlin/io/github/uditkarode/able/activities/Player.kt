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
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.TouchDelegate
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.customListAdapter
import com.afollestad.materialdialogs.list.listItems
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.makeramen.roundedimageview.RoundedImageView
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.AbleApplication
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.databinding.PlayerBinding
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.adapters.DiscoverResultAdapter
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.model.song.SongState
import io.github.uditkarode.able.services.DownloadService
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.ApiKeyProvider
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.DeezerClient
import io.github.uditkarode.able.utils.LastFmClient
import io.github.uditkarode.able.utils.LastFmKeyPrompt
import io.github.uditkarode.able.utils.MusicClientActivity
import io.github.uditkarode.able.utils.Shared
import io.github.uditkarode.able.utils.TrackResolver
import io.github.uditkarode.able.utils.ViewMode
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The Player UI activity.
 */

class Player : MusicClientActivity() {
    private lateinit var serviceConn: ServiceConnection
    private var mService: MusicService? = null
    private var seekbarJob: Job? = null

    private var playing = SongState.paused
    private var onShuffle = false
    private var onRepeat = false

    private var lastSelectedColor = 0x00fbfbfb

    private lateinit var binding: PlayerBinding
    private lateinit var centerLoadingSpinner: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = PlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**
         * Since we don't use fitSystemWindows, we need to manually
         * apply window insets as margin.
         */
        binding.bottomCast.setOnApplyWindowInsetsListener { view, insets ->
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            @Suppress("DEPRECATION")
            params.setMargins(0, 0, 0, insets.systemWindowInsetBottom)
            insets
        }

        binding.topControls.setOnApplyWindowInsetsListener { view, insets ->
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            @Suppress("DEPRECATION")
            params.setMargins(0, insets.systemWindowInsetTop, 0, 0)
            insets
        }

        binding.playerDownArrow.setOnClickListener {
            finish()
        }

        binding.playerDiscover.setOnClickListener { showSimilarSheet() }

        binding.shuffleButton.setOnClickListener {
            if (onShuffle) {
                mService?.setShuffleRepeat(shuffle = false, repeat = onRepeat)
            } else {
                mService?.setShuffleRepeat(shuffle = true, repeat = onRepeat)
            }
        }

        centerLoadingSpinner = ProgressBar(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                binding.playerCenterIcon.layoutParams.width,
                binding.playerCenterIcon.layoutParams.height
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            visibility = View.GONE
        }
        (binding.playerCenterIcon.parent as ViewGroup).addView(centerLoadingSpinner)

        binding.playerCenterIcon.setOnClickListener {
            launch(Dispatchers.Default) {
                if (playing == SongState.playing) mService?.setPlayPause(SongState.paused)
                else mService?.setPlayPause(SongState.playing)
            }
        }

        binding.repeatButton.setOnClickListener {
            if (onRepeat) {
                mService?.setShuffleRepeat(shuffle = onShuffle, repeat = false)
                DrawableCompat.setTint(binding.repeatButton.drawable, Color.parseColor("#80fbfbfb"))
            } else {
                mService?.setShuffleRepeat(shuffle = onShuffle, repeat = true)
                DrawableCompat.setTint(binding.repeatButton.drawable, Color.parseColor("#805e92f3"))
            }
        }

        binding.nextSong.setOnClickListener {
            mService?.setNextPrevious(next = true)
        }

        binding.previousSong.setOnClickListener {
            mService?.setNextPrevious(next = false)
        }

        setBgColor(0x002171)

        binding.playerSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (mService != null) {
                    val ms = mService as MusicService
                    ms.seekTo(seekBar.progress)
                    binding.playerSeekbar.progress = ms.getMediaPlayer().currentPosition
                    binding.playerCurrentPosition.text = getDurationFromMs(binding.playerSeekbar.progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        binding.albumArt.setOnClickListener {
            if (mService != null) {
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

        binding.albumArt.setOnLongClickListener {
            if (mService != null) {
                val ms = mService as MusicService
                if (ms.getPlayQueue()[ms.getCurrentIndex()].filePath.contains("emulated/0/"))
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

        binding.songName.setOnClickListener {
            if (mService != null) {
                val mService = this.mService as MusicService
                val current = mService.getPlayQueue()[mService.getCurrentIndex()]
                if (current.filePath.contains("emulated/0/")) {
                    MaterialDialog(this@Player).show {
                        title(text = this@Player.getString(R.string.enter_new_song))
                        input(this@Player.getString(R.string.song_ex2)) { _, charSequence ->
                            val ext = current.filePath.run {
                                this.substring(this.lastIndexOf(".") + 1)
                            }
                            val session = FFmpegKit.execute(
                                "-i " +
                                        "\"${current.filePath}\" -y -c copy " +
                                        "-metadata title=\"$charSequence\" " +
                                        "-metadata artist=\"${current.artist}\"" +
                                        " \"${current.filePath}.new.$ext\""
                            )
                            when {
                                ReturnCode.isSuccess(session.returnCode) -> {
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

                                ReturnCode.isCancel(session.returnCode) -> {
                                    Log.e(
                                        "ERR>",
                                        "Command execution cancelled by user."
                                    )
                                }

                                else -> {
                                    Log.e(
                                        "ERR>",
                                        "Command execution failed with rc=${session.returnCode}"
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

        binding.artistName.setOnClickListener {
            if (mService != null) {
                val mService = this.mService as MusicService
                val current = mService.getPlayQueue()[mService.getCurrentIndex()]
                if (current.filePath.contains("emulated/0/")) {
                    MaterialDialog(this@Player).show {
                        title(text = this@Player.getString(R.string.enter_new_art))
                        input(this@Player.getString(R.string.art_ex)) { _, charSequence ->
                            val ext = current.filePath.run {
                                this.substring(this.lastIndexOf(".") + 1)
                            }
                            val session = FFmpegKit.execute(
                                "-i " +
                                        "\"${current.filePath}\" -c copy " +
                                        "-metadata title=\"${current.name}\" " +
                                        "-metadata artist=\"$charSequence\"" +
                                        " \"${current.filePath}.new.$ext\""
                            )
                            when {
                                ReturnCode.isSuccess(session.returnCode) -> {
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

                                ReturnCode.isCancel(session.returnCode) -> {
                                    Log.e(
                                        "ERR>",
                                        "Command execution cancelled by user."
                                    )
                                }

                                else -> {
                                    Log.e(
                                        "ERR>",
                                        "Command execution failed with rc=${session.returnCode}"
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

        (binding.shuffleButton.parent as View).post {
            val rect = Rect().also {
                binding.shuffleButton.getHitRect(it)
                it.top -= 200
                it.left -= 200
                it.bottom += 200
                it.right += 100
            }

            (binding.shuffleButton.parent as View).touchDelegate = TouchDelegate(rect, binding.shuffleButton)
        }

        (binding.repeatButton.parent as View).post {
            val rect = Rect().also {
                binding.repeatButton.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 200
            }

            (binding.repeatButton.parent as View).touchDelegate = TouchDelegate(rect, binding.repeatButton)
        }

        (binding.previousSong.parent as View).post {
            val rect = Rect().also {
                binding.previousSong.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 100
            }

            (binding.previousSong.parent as View).touchDelegate = TouchDelegate(rect, binding.previousSong)
        }

        (binding.nextSong.parent as View).post {
            val rect = Rect().also {
                binding.nextSong.getHitRect(it)
                it.top -= 200
                it.left -= 100
                it.bottom += 200
                it.right += 100
            }

            (binding.nextSong.parent as View).touchDelegate = TouchDelegate(rect, binding.nextSong)
        }

        (binding.playerCenterIcon.parent as View).post {
            val rect = Rect().also {
                binding.playerCenterIcon.getHitRect(it)
                it.top -= 200
                it.left -= 50
                it.bottom += 200
                it.right += 50
            }

            (binding.playerCenterIcon.parent as View).touchDelegate =
                TouchDelegate(rect, binding.playerCenterIcon)
        }


        binding.playerQueue.setOnClickListener {
            if (mService != null) {
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

        binding.youtubeProgressbar.visibility = View.GONE

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                launch {
                    delay(300)
                    if (!this@Player.isDestroyed) Glide.with(this@Player).clear(binding.imgAlbart)
                }
                finish()
            }
        })
    }

    private fun onBindDone() {
        if (MusicService.isLoading) {
            isLoading(true)
        }

        playing = if (mService!!.getMediaPlayer().isPlaying) SongState.playing else SongState.paused
        playPauseEvent(playing)

        // Sync shuffle/repeat button state with the service (which may have
        // restored these flags from SharedPreferences on a fresh start).
        mService?.let { shuffleRepeatChanged(it.getShuffle(), it.getRepeat()) }

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
        seekbarJob?.cancel()
    }

    private fun startSeekbarUpdates() {
        if (seekbarJob?.isActive == true) return
        seekbarJob = launch {
            while (isActive) {
                if (mService != null) {
                    val ms = mService as MusicService
                    val songPosition = ms.getMediaPlayer().currentPosition
                    binding.playerSeekbar.progress = songPosition
                    binding.playerCurrentPosition.text = getDurationFromMs(songPosition)
                }
                delay(1000)
            }
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

        binding.previousSong.run {
            this.setImageDrawable(this.drawable.run { this.setTint(color); this })
        }

        binding.playerCenterIcon.run {
            this.setImageDrawable(this.drawable.run { this.setTint(color); this })
        }

        binding.nextSong.run {
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
//        RevelyGradient
//            .linear()
//            .colors(
//                intArrayOf(
//                    color,
//                    Color.parseColor("#212121")
//                )
//            )
//            .angle(90f)
//            .alpha(0.76f)
//            .onBackgroundOf(binding.playerBg)


        // Always use white icons since background is always #212121 (dark)
        binding.playerDownArrow.setImageDrawable(
            ContextCompat.getDrawable(this@Player, R.drawable.down_arrow)
        )
        binding.playerQueue.setImageDrawable(
            ContextCompat.getDrawable(this@Player, R.drawable.pl_playlist)
        )

        val accentColor = lightVibrantColor ?: color
        if (accentColor != 0 && lightVibrantColor != null) {
            if ((lightVibrantColor and 0xff000000.toInt()) shr 24 == 0) {
                val newTitleColor = palette?.darkVibrantSwatch?.titleTextColor
                    ?: palette?.dominantSwatch?.titleTextColor
                binding.playerSeekbar.progressDrawable.setTint(newTitleColor!!)
                binding.playerSeekbar.thumb.setTint(newTitleColor)
                tintControls(0x002171)
            } else {
                binding.playerSeekbar.progressDrawable.setTint(lightVibrantColor)
                binding.playerSeekbar.thumb.setTint(lightVibrantColor)
                tintControls(lightVibrantColor)
            }
        }
    }

    private fun playPauseEvent(ss: SongState) {
        playing = ss
        launch(Dispatchers.Main) {
            if (playing == SongState.playing) binding.playerCenterIcon.setImageDrawable(
                ContextCompat.getDrawable(
                    this@Player,
                    R.drawable.nobg_pause
                )
            )
            else binding.playerCenterIcon.setImageDrawable(
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
            seekbarJob?.cancel()
        }
    }

    private fun updateAlbumArt(customSongName: String? = null, forceWeb: Boolean = false) {
        if (mService == null) return
        val mService = mService as MusicService

        binding.imgAlbart.visibility = View.GONE
        binding.notePh.visibility = View.VISIBLE
        var didGetArt = false
        val current = mService.getPlayQueue()[mService.getCurrentIndex()]

        // Cover stored beside downloaded files. Only meaningful for real
        // downloaded files: streamed/unknown paths ("", "<id>.stream.tmp")
        // resolve to bogus targets — including the album_art directory
        // itself, which exists() and made the lookup believe it had found
        // art, leaving the previous song's cover on screen.
        val isDownloaded = !current.isLocal && current.filePath.isNotBlank() &&
                !current.filePath.endsWith(".tmp")
        val img = if (isDownloaded)
            File(Constants.albumArtDir, File(current.filePath).nameWithoutExtension)
        else null

        // Streaming cover cache, keyed by the YouTube video id.
        val streamId = Shared.getIdFromLink(current.youtubeLink)
        val cacheImg = if (streamId.isNotBlank())
            File(Constants.cacheDir, "sCache$streamId")
        else null

        launch(Dispatchers.IO) {
            /*
            Check priority:
            1) Album art from metadata (local songs only)
            2) Album art saved by the app (downloaded → album_art/<name>,
               streamed → cache/sCache<videoId>)
            3) Last.fm track.getInfo — exact artist+track match (~300px),
               also returning the album the track belongs to
            4) Deezer — high-res cover (1400px) of that identified album

            The previous song's cover is never left on screen: every level
            repaints through showArtBitmap and failed loads simply fall
            through to the next level instead of keeping stale pixels.
            */

            /* (1) Check albumart in song metadata (if the song is a local song) */
            if (current.isLocal && !forceWeb) {
                Log.i("INFO>", "Fetching from metadata")
                try {
                    val sArtworkUri =
                        Uri.parse("content://media/external/audio/albumart")

                    val bmp = Glide
                        .with(this@Player)
                        .load(ContentUris.withAppendedId(sArtworkUri, current.albumId))
                        .signature(ObjectKey("player"))
                        .submit()
                        .get().toBitmap()
                    Shared.bmp = bmp

                    showArtBitmap(bmp)
                    didGetArt = true
                } catch (e: java.lang.Exception) {
                    didGetArt = false
                }
            }

            /* (2) Cover files saved by the app */
            if (!didGetArt && !forceWeb) {
                val imgToLoad = img?.takeIf { it.exists() }
                    ?: cacheImg?.takeIf { it.exists() }
                if (imgToLoad != null) {
                    Log.i("INFO>", "Fetching app-stored cover: ${imgToLoad.name}")
                    try {
                        val bmp = Glide.with(this@Player)
                            .asBitmap()
                            .load(imgToLoad)
                            .signature(ObjectKey("player"))
                            .submit()
                            .get()
                        showArtBitmap(bmp)
                        didGetArt = true
                    } catch (e: Exception) {
                        Log.e("ERR>", "Stored cover load failed: $e")
                    }
                }
            }

            /* (3) Last.fm — exact artist+track identity, cached */
            var albumName: String? = null
            val webTitle = customSongName ?: current.name
            if (!didGetArt &&
                Shared.isInternetConnected(this@Player) &&
                current.artist.isNotBlank() && webTitle.isNotBlank()
            ) {
                try {
                    val identity =
                        LastFmClient.resolveTrackIdentity(current.artist, webTitle)
                    if (identity != null) {
                        Log.i("INFO>", "Fetching from Last.fm")
                        albumName = identity.album.takeIf { it.isNotBlank() }
                        if (identity.image.isNotBlank()) {
                            val bmp = Glide.with(this@Player)
                                .asBitmap()
                                .load(identity.image)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .submit()
                                .get()
                            showArtBitmap(bmp)
                            didGetArt = true
                            // Persist so the next play loads straight from disk
                            (cacheImg ?: img)?.let { Shared.saveAlbumArtToDisk(bmp, it) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ERR>", "Last.fm cover failed: $e")
                }
            }

            /* (4) Deezer — hi-res cover of the album Last.fm identified */
            if (albumName != null && Shared.isInternetConnected(this@Player)) {
                try {
                    val hiRes = DeezerClient.albumCover(current.artist, albumName)
                    if (hiRes.isNotBlank()) {
                        Log.i("INFO>", "Upgrading cover from Deezer")
                        val bmp = Glide.with(this@Player)
                            .asBitmap()
                            .load(hiRes)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .submit()
                            .get()
                        showArtBitmap(bmp)
                        didGetArt = true
                        // Keep the best art we have for the next play
                        (cacheImg ?: img)?.let { Shared.saveAlbumArtToDisk(bmp, it) }
                    }
                } catch (e: Exception) {
                    Log.e("ERR>", "Deezer cover upgrade failed: $e")
                }
            }

            /* Metadata-edit flow: persist the fetched cover into the song */
            if (didGetArt && forceWeb && isDownloaded) {
                img?.let { file ->
                    Shared.getSharedBitmap()?.let { Shared.saveAlbumArtToDisk(it, file) }
                    Shared.addThumbnails(current.filePath, albumName ?: "", this@Player)
                }
                launch(Dispatchers.Main) {
                    Home.songAdapter?.notifyItemChanged(mService.getCurrentIndex())
                }
            }

            if (!didGetArt) {
                launch(Dispatchers.Main) {
                    binding.imgAlbart.visibility = View.GONE
                    binding.notePh.visibility = View.VISIBLE
                    setBgColor(0x002171)
                    binding.playerSeekbar.progressDrawable.setTint(
                        ContextCompat.getColor(
                            this@Player,
                            R.color.thatAccent
                        )
                    )
                    binding.playerSeekbar.thumb.setTint(
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

    /** Shows a freshly fetched cover and re-derives the palette colors. */
    private fun showArtBitmap(bitmap: Bitmap) {
        Shared.bmp = bitmap
        launch(Dispatchers.Main) {
            binding.imgAlbart.setImageBitmap(bitmap)
            binding.imgAlbart.visibility = View.VISIBLE
            binding.notePh.visibility = View.GONE
            Palette.from(bitmap).generate {
                setBgColor(
                    it?.getDominantColor(0x002171) ?: 0x002171,
                    it?.getLightMutedColor(0x002171) ?: 0x002171,
                    it
                )
            }
        }
    }

    private fun changeMetadata(name: String, artist: String) {
        if (mService != null) {
            val mService = mService as MusicService
            launch(Dispatchers.Main) {
                binding.songName.text = name
                binding.artistName.text = artist
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
        if (mService != null) {
            val mService = mService as MusicService
            updateAlbumArt()

            val song = mService.getPlayQueue()[mService.getCurrentIndex()]
            binding.songName.text = song.name
            binding.artistName.text = song.artist

            val duration = mService.getMediaPlayer().duration
            if (duration > 0) {
                binding.playerSeekbar.max = duration
                binding.completePosition.text = getDurationFromMs(duration)
                binding.playerSeekbar.progress = mService.getMediaPlayer().currentPosition
                binding.playerCurrentPosition.text = getDurationFromMs(mService.getMediaPlayer().currentPosition)
            } else {
                binding.playerSeekbar.max = 100
                binding.playerSeekbar.progress = 0
                binding.completePosition.text = "0:00"
                binding.playerCurrentPosition.text = "0:00"
            }
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
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!, AbleApplication.viewPump))
    }

    override fun onResume() {
        super.onResume()
        if (mService == null)
            bindEvent()
        else
            playPauseEvent(
                if ((mService as MusicService)
                        .getMediaPlayer().isPlaying
                ) SongState.playing else SongState.paused
            )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!this.isDestroyed)
            Glide.with(this@Player).clear(binding.imgAlbart)
    }

    override fun playStateChanged(state: SongState) {
        playPauseEvent(state)
    }

    override fun songChanged() {
        launch(Dispatchers.Main) {
            songChangeEvent()
        }
    }

    override fun durationChanged(duration: Int) {
        launch(Dispatchers.Main) {
            binding.playerSeekbar.max = duration
            binding.completePosition.text = getDurationFromMs(duration)
        }
    }

    override fun isExiting() {
        finish()
    }

    override fun queueChanged(arrayList: ArrayList<Song>) {}

    override fun shuffleRepeatChanged(onShuffle: Boolean, onRepeat: Boolean) {
        launch(Dispatchers.Main) {
            if (onShuffle)
                DrawableCompat.setTint(binding.shuffleButton.drawable, Color.parseColor("#805e92f3"))
            else
                DrawableCompat.setTint(binding.shuffleButton.drawable, Color.parseColor("#fbfbfb"))

            if (onRepeat)
                DrawableCompat.setTint(binding.repeatButton.drawable, Color.parseColor("#805e92f3"))
            else
                DrawableCompat.setTint(binding.repeatButton.drawable, Color.parseColor("#fbfbfb"))

            (this@Player).onShuffle = onShuffle
            (this@Player).onRepeat = onRepeat
        }
    }

    override fun indexChanged(index: Int) {}

    override fun isLoading(doLoad: Boolean) {
        launch(Dispatchers.Main) {
            if (doLoad) {
                binding.playerCenterIcon.visibility = View.INVISIBLE
                centerLoadingSpinner.visibility = View.VISIBLE
                binding.playerSeekbar.progress = 0
                binding.playerSeekbar.max = 100
                binding.completePosition.text = "0:00"
                binding.playerCurrentPosition.text = "0:00"
            } else {
                centerLoadingSpinner.visibility = View.GONE
                binding.playerCenterIcon.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Discovery on the playing track: bottom sheet with Last.fm suggestions
     * similar to the song currently in `playQueue[currentIndex]`.
     */
    private fun showSimilarSheet() {
        val current = MusicService.playQueue.getOrNull(MusicService.currentIndex)
        if (current == null) {
            Toast.makeText(this, R.string.disc_play_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (current.artist.isBlank()) {
            Toast.makeText(this, R.string.disc_unknown_artist, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ApiKeyProvider.hasLastFmKey) {
            LastFmKeyPrompt.showIfMissing(this)
            return
        }
        launch(Dispatchers.IO) {
            val tracks = try {
                LastFmClient.similarTracks(current.artist, current.name)
            } catch (e: Exception) {
                Log.e("Player", "Similar tracks failed", e); emptyList()
            }
            launch(Dispatchers.Main) {
                if (tracks.isEmpty()) {
                    Toast.makeText(this@Player, R.string.disc_no_suggestions, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                showSimilarSheet(tracks)
            }
        }
    }

    /**
     * Compact bottom sheet for the suggestions: RecyclerView with the global
     * list/grid preference and an inline toggle, instead of the default
     * full-screen MaterialDialog list. Opens collapsed at ~45% of the screen
     * and can be dragged to full height.
     */
    private fun showSimilarSheet(tracks: List<TrackSuggestion>) {
        val sheet = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.sheet_similar, null)
        sheet.setContentView(content)
        sheet.behavior.peekHeight =
            (resources.displayMetrics.heightPixels * 0.45f).toInt()
        val rv = content.findViewById<RecyclerView>(R.id.sheet_rv)
        val toggle = content.findViewById<ImageView>(R.id.sheet_view_mode)

        fun render() {
            toggle.setImageResource(ViewMode.toggleIcon(this))
            rv.layoutManager = ViewMode.layoutManagerFor(this)
            rv.adapter = DiscoverResultAdapter(
                ArrayList<Any>(tracks),
                onTrackPressed = { suggestion ->
                    showTrackActionMenu(suggestion)
                },
                onArtistPressed = { }
            )
        }
        render()
        toggle.setOnClickListener {
            ViewMode.toggle(this)
            render()
        }
        sheet.show()
    }

    /** Choice menu for a tapped suggestion: play now / add to queue / download. */
    private fun showTrackActionMenu(suggestion: TrackSuggestion) {
        MaterialDialog(this).show {
            title(text = "${suggestion.name} - ${suggestion.artist}")
            listItems(items = listOf(
                getString(R.string.disc_play_now),
                getString(R.string.disc_add_to_queue),
                getString(R.string.disc_add_to_download)
            )) { _, index, _ ->
                when (index) {
                    0 -> resolveSuggestion(suggestion) { song ->
                        val svc = mService ?: return@resolveSuggestion
                        svc.addToPlayQueue(song)
                        svc.setIndex(MusicService.currentIndex + 1) // starts playing
                    }
                    1 -> resolveSuggestion(suggestion) { song ->
                        mService?.addToPlayQueue(song) // now notifies queueChanged
                        Toast.makeText(
                            this@Player,
                            getString(R.string.disc_added_to_queue, song.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    2 -> resolveSuggestion(suggestion) { song -> enqueueDownload(song) }
                }
            }
        }
    }

    /** Resolves a suggestion on YouTube Music, then runs the action with the Song. */
    private fun resolveSuggestion(suggestion: TrackSuggestion, action: (Song) -> Unit) {
        launch(Dispatchers.IO) {
            val song = try { TrackResolver.resolve(suggestion) } catch (e: Exception) {
                Log.e("Player", "Resolution failed for ${suggestion.name}", e); null
            }
            launch(Dispatchers.Main) {
                if (song == null)
                    Toast.makeText(this@Player, R.string.disc_yt_fail, Toast.LENGTH_SHORT).show()
                else
                    action(song)
            }
        }
    }

    /** Adds a resolved song to the DownloadService queue (same flow as MainActivity). */
    private fun enqueueDownload(song: Song) {
        if (DownloadService.isAlreadyQueued(song.youtubeLink)) {
            Toast.makeText(this, R.string.disc_dl_already, Toast.LENGTH_SHORT).show()
            return
        }
        val list = ArrayList<String>().apply {
            add(song.name)
            add(song.youtubeLink)
            add(song.artist)
            add(Shared.upscaleThumbnailUrl(song.ytmThumbnail))
        }
        val intent = Intent(this, DownloadService::class.java)
            .putStringArrayListExtra("song", list)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)
        Toast.makeText(this, R.string.disc_dl_added, Toast.LENGTH_SHORT).show()
    }

    override fun spotifyImportChange(starting: Boolean) {}

    override fun serviceStarted() {
        bindEvent()
    }
}

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

package io.github.uditkarode.able.services

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.Player
import io.github.uditkarode.able.models.CacheStatus
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.*
import kotlin.collections.ArrayList

/**
 * The service that plays music.
 */

@ExperimentalCoroutinesApi
class MusicService : Service(), AudioManager.OnAudioFocusChangeListener, CoroutineScope {

    interface MusicClient {
        fun playStateChanged(state: SongState)
        fun songChanged()
        fun durationChanged(duration: Int)
        fun isExiting()
        fun queueChanged(arrayList: ArrayList<Song>)
        fun shuffleRepeatChanged(onShuffle: Boolean, onRepeat: Boolean)
        fun indexChanged(index: Int)
        fun isLoading(doLoad: Boolean)
        fun spotifyImportChange(starting: Boolean)
        fun serviceStarted()
    }

    companion object {
        var songCoverArt: WeakReference<Bitmap>? = null
        var playQueue = ArrayList<Song>()
        val mediaPlayer = MediaPlayer()
        var previousIndex = -1
        var currentIndex = -1

        val registeredClients = mutableSetOf<MusicClient>()

        fun registerClient(client: Any){
            try {
                registeredClients.add(client as MusicClient)
            } catch(e: ClassCastException){
                Log.e("ERR>", "Could not register client!")
            }
        }

        fun unregisterClient(client: Any){
            try {
                registeredClients.remove(client as MusicClient)
            } catch(e: ClassCastException){}
        }
        
        private lateinit var notificationManager: NotificationManager
        private var focusRequest: AudioFocusRequest? = null
        private var wakeLock: PowerManager.WakeLock? = null
        private var builder: Notification.Builder? = null
        private lateinit var mediaSession: MediaSession
        private var ps = PlaybackState.Builder()
        private var coverArtHeight: Int? = null
        private var isInstantiated = false
        private var onShuffle = false
        private var onRepeat = false
    }

    override val coroutineContext = Dispatchers.Main + SupervisorJob()
    private val binder = MusicBinder(this@MusicService)
    fun getMediaPlayer() = mediaPlayer
    fun getPlayQueue() = playQueue
    fun getCurrentIndex() = currentIndex
    fun getPreviousIndex() = previousIndex
    fun setCurrentIndex(ind: Int) {
        currentIndex = ind
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            setPlayPause(SongState.paused)
        }
    }

    private val actions: Long = (PlaybackState.ACTION_PLAY
            or PlaybackState.ACTION_PAUSE
            or PlaybackState.ACTION_PLAY_PAUSE
            or PlaybackState.ACTION_SKIP_TO_NEXT
            or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            or PlaybackState.ACTION_STOP
            or PlaybackState.ACTION_SEEK_TO)

    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    build()
                })
                setAcceptsDelayedFocusGain(false)
                setOnAudioFocusChangeListener {
                    if (it == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || it == AudioManager.AUDIOFOCUS_LOSS) pauseAudio()
                }
                build()
            }
        }

        if (coverArtHeight == null)
            coverArtHeight = resources.getDimension(R.dimen.top_art_height).toInt()

        if (!isInstantiated) {
            notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }

        mediaSession = MediaSession(this, "AbleSession")

        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                seekTo(pos.toInt())
            }

            override fun onPause() {
                super.onPause()
                setPlayPause(SongState.paused)
            }

            override fun onPlay() {
                super.onPlay()
                mediaSessionPlay()
                setPlayPause(SongState.playing)
            }
        })

        mediaPlayer.setOnErrorListener { _, _, _ ->
            true
        }

        mediaPlayer.setOnCompletionListener {
            previousIndex = currentIndex
            nextSong()
        }

        if (builder == null) {
            builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, "10002")
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

            val intent = Intent(this, MusicService::class.java)
            intent.action = "ACTION_STOP"
            val style = Notification.MediaStyle().setMediaSession(mediaSession.sessionToken)
            style.setShowActionsInCompactView(1, 2, 3)

            (builder as Notification.Builder)
                .setSmallIcon(R.drawable.ic_notification)
                .setSubText(getString(R.string.music))
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, Player::class.java),
                        0
                    )
                )
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDeleteIntent(PendingIntent.getService(this, 1, intent, 0))
                .setOngoing(true)
                .style = style
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
        unregisterReceiver(receiver)
        //exitProcess(0)
    }

    class MusicBinder(private val service: MusicService) : Binder() {
        fun getService(): MusicService {
            return service
        }
    }

    override fun onBind(intent: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(intent)
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action == null) return
        val action = intent.action
        when {
            action.equals("ACTION_PLAY", ignoreCase = true) -> {
                mediaSessionPlay()
                setPlayPause(SongState.playing)
            }
            action.equals("ACTION_PAUSE", ignoreCase = true) -> {
                setPlayPause(SongState.paused)
            }
            action.equals("ACTION_PREVIOUS", ignoreCase = true) -> {
                setNextPrevious(next = false)
            }
            action.equals("ACTION_NEXT", ignoreCase = true) -> {
                setNextPrevious(next = true)
            }
            action.equals("ACTION_REPEAT", ignoreCase = true) -> {
                if(onRepeat)
                    setShuffleRepeat(shuffle = false, repeat = false)
                else
                    setShuffleRepeat(shuffle = false, repeat = true)
            }
            action.equals("ACTION_KILL", ignoreCase = true) -> {
                cleanUp()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    stopForeground(true)
                stopSelf()
            }
        }
    }

    /**
     * @param song the song to be added to the play queue.
     */
    fun addToQueue(song: Song) {
        addToPlayQueue(song)
        launch(Dispatchers.Default) {
            registeredClients.forEach { it.queueChanged(playQueue) }
        }
    }

    /**
     * @param queue an ArrayList of Song objects to be set as the new queue.
     */
    fun setQueue(queue: ArrayList<Song>) {
        playQueue = queue
        launch(Dispatchers.Default) {
            registeredClients.forEach { it.queueChanged(playQueue) }
        }
    }

    /**
     * @param shuffle boolean value for turning shuffle on/off.
     * @param repeat value for turning repeat on/off.
     */
    fun setShuffleRepeat(shuffle: Boolean, repeat: Boolean) {
        setShuffle(shuffle)
        onRepeat = repeat
        launch(Dispatchers.Default)  {
            registeredClients.forEach { it.shuffleRepeatChanged(onShuffle, onRepeat) }
        }
    }

    /**
     * @param index sets the new index to play from the queue.
     */
    fun setIndex(index: Int) {
        previousIndex = currentIndex
        currentIndex = index
        songChanged()
        launch(Dispatchers.Default) {
                registeredClients.forEach { it.indexChanged(currentIndex) }
        }
    }

    /**
     * @param state a SongState object used to play or pause audio.
     */
    fun setPlayPause(state: SongState) {
        if (state == SongState.playing) playAudio()
        else pauseAudio()

        launch(Dispatchers.Default) {
                registeredClients.forEach { it.playStateChanged(state) }
        }
    }

    /**
     * @param next changes to the next song if true, previous song if false.
     */
    fun setNextPrevious(next: Boolean) {
        previousIndex = currentIndex
        if (next) nextSong()
        else previousSong()
    }

    /* Music Related Helper Functions Here */

    /**
     * @param enabled enables or disables shuffle.
     */
    private fun setShuffle(enabled: Boolean) {
        if (enabled) {
            val detachedSong = playQueue[currentIndex]
            playQueue.removeAt(currentIndex)
            playQueue.shuffle()
            playQueue.add(0, detachedSong)
            currentIndex = 0
            onShuffle = true
        } else {
            onShuffle = false
            val currSong = playQueue[currentIndex]
            playQueue = ArrayList(playQueue.sortedBy { it.name.toUpperCase(Locale.getDefault()) })
            currentIndex = playQueue.indexOf(currSong)
        }

        launch(Dispatchers.Default) {
            registeredClients.forEach { it.queueChanged(playQueue) }
        }

        launch(Dispatchers.Default)  {
            registeredClients.forEach { it.indexChanged(currentIndex) }
        }
    }

    /**
     * changes to the previous song
     */
    private fun previousSong() {
        if (mediaPlayer.currentPosition > 2000) {
            seekTo(0)
        } else {
            if (currentIndex == 0) currentIndex = playQueue.size - 1
            else currentIndex--
            songChanged()
        }
    }

    /**
     * changes to the next song
     */
    private fun nextSong() {
        if (onRepeat) seekTo(0)
        if (currentIndex + 1 < playQueue.size) {
            if (!onRepeat) currentIndex++
            songChanged()
        } else {
            currentIndex = 0
            songChanged()
        }
    }

    /**
     * @param song the song to be added to the play queue.
     */
    fun addToPlayQueue(song: Song) {
        if (currentIndex != playQueue.size - 1) playQueue.add(currentIndex + 1, song)
        else playQueue.add(song)
    }

    /**
     * @param position the position to seek the current song to.
     */
    fun seekTo(position: Int) {
        mediaPlayer.seekTo(position)
        mediaSessionPlay()
    }

    private fun mediaSessionPlay() {
        mediaSession.setPlaybackState(
            ps
                .setActions(actions)
                .setState(
                    PlaybackState.STATE_PLAYING,
                    mediaPlayer.currentPosition.toLong(), 1f
                )
                .build()
        )
    }

    /**
     * changes the current song to playQueue[currentIndex] and starts playing it.
     */
    private fun songChanged() {
        if (!isInstantiated) isInstantiated = true
        else {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
            mediaPlayer.reset()
        }
        if (playQueue[currentIndex].filePath == "") {
            launch(Dispatchers.Default) {
                getStreamArt()
            }
        }

        mediaPlayer.setOnPreparedListener {
            mediaSessionPlay()
            setPlayPause(SongState.playing)

            val dur = mediaPlayer.duration
            launch(Dispatchers.Default) {
                registeredClients.forEach { it.durationChanged(dur) }
            }
        }

        if (playQueue[currentIndex].cacheStatus != CacheStatus.NULL) { // Cache while streaming
            var fplay = false
            var prevDur = 0
            var prevOff = 0
            var prevProg = 0
            var songDur = 0
            val tmpf = File("${Constants.ableSongDir.absolutePath}/tmp_stream-$currentIndex.tmp")

            launch(Dispatchers.Default) {
                registeredClients.forEach(MusicClient::songChanged)
            }
            launch(Dispatchers.Default) {
                registeredClients.forEach { it.indexChanged(currentIndex) }
            }

            launch(Dispatchers.IO) {
                while (playQueue[currentIndex].cacheStatus != CacheStatus.NULL) {
                    while (
                        (playQueue[currentIndex].streamProg < 10) or
                        (playQueue[currentIndex].streamProg - prevProg < 10) or
                        (playQueue[currentIndex].streamMutexes[0].isLocked and
                                playQueue[currentIndex].streamMutexes[1].isLocked) or
                        mediaPlayer.isPlaying
                    ) {
                        delay(50)
                    }
                    val prog = playQueue[currentIndex].streamProg
                    prevProg = prog

                    val streamNum =
                        if (playQueue[currentIndex].streamMutexes[0].isLocked) 1 else 0

                    playQueue[currentIndex].streamMutexes[streamNum].lock()

                    tmpf.outputStream().use {
                        it.write(playQueue[currentIndex].streams[streamNum])
                    }
                    try {
                        tmpf.inputStream().use {
                                mediaPlayer.setDataSource(it.fd)
                        }
                    } catch (e: Exception) {
                        mediaPlayer.stop()
                        mediaPlayer.reset()
                        continue
                    } finally {
                        playQueue[currentIndex].streamMutexes[streamNum].unlock()
                    }

                    var sleepT: Long

                    try {
                        mediaPlayer.prepare()

                        if (fplay) {
                            seekTo(prevOff)
                        }

                        if ((songDur == 0) and (mediaPlayer.duration > 0)) songDur = mediaPlayer.duration; fplay = true
                    } catch (e: Exception) {
                        mediaPlayer.stop()
                        mediaPlayer.reset()
                        continue
                    } finally {
                        sleepT = ((prog * songDur) / 100).toLong()
                        if ((sleepT > 0) and (sleepT < songDur)) {
                            prevOff = sleepT.toInt()
                        }
                    }


                    while (mediaPlayer.currentPosition < sleepT) {
                        prevDur = if (mediaPlayer.currentPosition < prevOff) mediaPlayer.currentPosition else prevOff
                        delay(100)
                    }

                    mediaPlayer.stop()
                    mediaPlayer.reset()
                }

                tmpf.delete()

                if (prevDur < songDur) {
                    val dur = prevDur

                    mediaPlayer.setDataSource(playQueue[currentIndex].filePath)
                    mediaPlayer.prepare()

                    seekTo(dur)
                }
            }
        } else {
            try {
                mediaPlayer.setDataSource(playQueue[currentIndex].filePath)
                mediaPlayer.prepareAsync()
                launch(Dispatchers.Default) {
                registeredClients.forEach(MusicClient::songChanged)
                }
                launch(Dispatchers.Default) {
                registeredClients.forEach { it.indexChanged(currentIndex) }
                }
            } catch (e: java.lang.Exception) {
                Log.e("ERR>", "$e")
            }
        }
    }

    /* user might sleep with songs still playing, let it jam */
    @SuppressLint("WakelockTimeout")

    /**
     * Starts playing audio (after pause).
     * Acquires a wakelock in the progress.
     */
    private fun playAudio() {
        val audioManager =
            getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (wakeLock?.isHeld != true) {
                wakeLock =
                    (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AbleMusic::lock").apply {
                            acquire()
                        }
                    }
            }

            showNotification(
                generateAction(
                    R.drawable.notif_pause,
                    getString(R.string.pause),
                    "ACTION_PAUSE"
                )
            )

            if (!mediaPlayer.isPlaying) {
                try {
                    mediaPlayer.start()
                } catch (e: Exception) {
                    Log.e("ERR>", "-$e-")
                }
            }

            mediaSession.setPlaybackState(
                ps
                    .setActions(actions)
                    .setState(
                        PlaybackState.STATE_PLAYING,
                        mediaPlayer.currentPosition.toLong(), 1f
                    )
                    .build()
            )

        } else {
            Log.e("ERR>", "Unable to get focus - $result")
        }
    }

    fun showNotif() {
        showNotification(
            generateAction(
                R.drawable.notif_play,
                getString(R.string.pause),
                "ACTION_PLAY"
            )
        )
    }

    /**
     * used to pause audio play. Abandons audio focus and releases held wakelock.
     */
    private fun pauseAudio() {
        val audioManager =
            getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus {}
        }

        if (wakeLock?.isHeld == true)
            wakeLock?.release()

        showNotification(
            generateAction(
                R.drawable.notif_play,
                getString(R.string.pause),
                "ACTION_PLAY"
            )
        )

        mediaSession.setPlaybackState(
            ps
                .setActions(actions)
                .setState(
                    PlaybackState.STATE_PAUSED,
                    mediaPlayer.currentPosition.toLong(), 1f
                )
                .build()
        )

        mediaPlayer.pause()
        launch(Dispatchers.Default) {
                registeredClients.forEach { it.playStateChanged(run {
                if (mediaPlayer.isPlaying) SongState.playing
                else SongState.paused
            }) }
        }
    }

    /**
     * releases the media session and wakelock and gets ready to die.
     */
    private fun cleanUp() {
        launch(Dispatchers.Default) {
                registeredClients.forEach(MusicClient::isExiting)
        }
        mediaPlayer.stop()
        mediaSession.release()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
            it.cancel(1)
        }
        if (wakeLock?.isHeld == true)
            wakeLock?.release()
    }

    /**
     * @param file the File object pointing to the album art image.
     *
     */
    private fun getAlbumArt(file: File) {
        BitmapFactory.decodeFile(file.absolutePath).also {
            songCoverArt = WeakReference(
                if (it.height > coverArtHeight!! * 2) {
                    val ratio = it.width / it.height.toFloat()
                    Bitmap.createScaledBitmap(
                        it,
                        (coverArtHeight!! * ratio).toInt(),
                        coverArtHeight!!,
                        false
                    )
                } else {
                    it
                }
            )
        }
    }

    fun generateAction(
        icon: Int,
        title: String,
        intentAction: String
    ): Notification.Action {
        val intent = Intent(this, MusicService::class.java)
        intent.action = intentAction
        val pendingIntent =
            PendingIntent.getService(this, 1, intent, 0)
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(icon, title, pendingIntent).build()
    }

    fun showNotification(
        action: Notification.Action,
        image: Bitmap? = null,
        nameOverride: String? = null,
        artistOverride: String? = null
    ) {
        songCoverArt = null
        try {
            if (image == null) {
                File(
                    Constants.ableSongDir.absolutePath + "/album_art",
                    File(playQueue[currentIndex].filePath).nameWithoutExtension
                ).also {
                    if (it.exists() && it.isFile) {
                        getAlbumArt(it)
                    }
                }

                File(
                    Constants.ableSongDir.absolutePath + "/cache",
                    "sCache" + Shared.getIdFromLink(playQueue[currentIndex].youtubeLink)
                ).also {
                    if (it.exists() && it.isFile) {
                        getAlbumArt(it)
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        if (songCoverArt == null || songCoverArt?.get()?.isRecycled == true) {
            val sArtworkUri =
                Uri.parse("content://media/external/audio/albumart")
            val albumArtUri =
                ContentUris.withAppendedId(sArtworkUri, playQueue[currentIndex].albumId)
            try {
                songCoverArt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    WeakReference(
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(this.contentResolver, albumArtUri)
                        )
                    )
                } else {
                    WeakReference(
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(
                            this.contentResolver,
                            albumArtUri
                        )
                    )
                }
            } catch (e: java.lang.Exception) {
                songCoverArt = WeakReference(Shared.defBitmap)
            }
        }
        builder?.setLargeIcon(songCoverArt?.get())
        builder?.setContentTitle(nameOverride ?: playQueue[currentIndex].name)
        builder?.setContentText(artistOverride ?: playQueue[currentIndex].artist)

        /* clear actions */
        try {
            val f: Field = builder!!.javaClass.getDeclaredField("mActions")
            f.isAccessible = true
            f.set(builder, ArrayList<Notification.Action>())
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        builder?.addAction(
            generateAction(
                R.drawable.repeat,
                getString(R.string.repeat),
                "ACTION_REPEAT"
            )
        )

        builder?.addAction(
            generateAction(
                R.drawable.notif_previous,
                getString(R.string.prev),
                "ACTION_PREVIOUS"
            )
        )

        builder?.addAction(action)

        builder?.addAction(
            generateAction(
                R.drawable.notif_next,
                getString(R.string.next),
                "ACTION_NEXT"
            )
        )

        builder?.addAction(
            generateAction(
                R.drawable.kill,
                getString(R.string.kill),
                "ACTION_KILL"
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "10002",
                getString(R.string.music),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.enableLights(false)
            notificationChannel.enableVibration(false)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, builder?.setChannelId("10002")?.build())
        } else {
            notificationManager.notify(1, builder?.build())
        }
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ART, songCoverArt?.get())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, mediaPlayer.duration.toLong())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artistOverride ?: playQueue[currentIndex].artist)
                .putString(MediaMetadata.METADATA_KEY_TITLE, nameOverride ?: playQueue[currentIndex].name)
                .build()
        )
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pauseAudio()
        else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) cleanUp()
    }

    /**
     * if the filePath of the current song is empty,
     * it is assumed that it contains a youtubeLink.
     * fetches the album art and streams the song.
     */
    private fun getStreamArt() {
        launch(Dispatchers.IO) {
            try {
                val song = playQueue[currentIndex]
                val tmp: StreamInfo?
                try {
                    tmp = StreamInfo.getInfo(song.youtubeLink)
                } catch (e: java.lang.Exception) {
                    Log.e("ERR>", e.toString())
                    nextSong()
                    return@launch
                }
                val streamInfo = tmp ?: StreamInfo.getInfo(song.youtubeLink)
                val stream = streamInfo.audioStreams.run { this[this.size - 1] }

                if (song.ytmThumbnail.isNotBlank()) {
                    Glide.with(this@MusicService)
                        .asBitmap()
                        .load(song.ytmThumbnail)
                        .signature(ObjectKey("save"))
                        .listener(object : RequestListener<Bitmap> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Bitmap>?,
                                isFirstResource: Boolean
                            ): Boolean {
                                return false
                            }

                            override fun onResourceReady(
                                resource: Bitmap?,
                                model: Any?,
                                target: Target<Bitmap>?,
                                dataSource: DataSource?,
                                isFirstResource: Boolean
                            ): Boolean {
                                if (resource != null) {
                                    Shared.saveStreamingAlbumArt(
                                        resource,
                                        Shared.getIdFromLink(song.youtubeLink)
                                    )
                                }
                                val url = stream.url
                                playQueue[currentIndex].filePath = url
                                return false
                            }
                        }).submit()
                }
            } catch (e: java.lang.Exception) {
                Log.e("ERR>", e.toString())
                nextSong()
            }
        }
    }
}

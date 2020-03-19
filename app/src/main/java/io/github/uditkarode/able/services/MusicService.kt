package io.github.uditkarode.able.services

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.Player
import io.github.uditkarode.able.events.*
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import kotlin.concurrent.thread

class MusicService : Service() {

    companion object {
        private val mediaPlayer = MediaPlayer()
        var currentIndex = -1
        var onShuffle = false
        var onRepeat = false
        var playQueue = ArrayList<Song>()

        private var isInstantiated = false
        private var ps = PlaybackState.Builder()

        private lateinit var notification: Notification
        private lateinit var builder: Notification.Builder
        private var wakeLock: PowerManager.WakeLock? = null
        private lateinit var mediaSession: MediaSession
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

        registerReceiver(object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                EventBus.getDefault().postSticky(PlayPauseEvent(SongState.paused))
            }
        }, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        EventBus.getDefault().register(this)

        mediaSession = MediaSession(this, "AbleSession")

        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                seekTo(pos.toInt())
            }
        })

        mediaPlayer.setOnErrorListener { _, _, _ ->
            true
        }

        mediaPlayer.setOnCompletionListener {
            nextSong()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(intent)
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action == null) return
        val action = intent.action
        when {
            action.equals("ACTION_PLAY", ignoreCase = true) -> {
                showNotification(
                    generateAction(
                        R.drawable.pause,
                        "Pause",
                        "ACTION_PAUSE"
                    ), true
                )
                EventBus.getDefault().postSticky(PlayPauseEvent(SongState.playing))
            }
            action.equals("ACTION_PAUSE", ignoreCase = true) -> {
                showNotification(
                    generateAction(
                        R.drawable.play,
                        "Play",
                        "ACTION_PLAY"
                    ), false
                )
                EventBus.getDefault().postSticky(PlayPauseEvent(SongState.paused))
            }
            action.equals("ACTION_PREVIOUS", ignoreCase = true) -> {
                EventBus.getDefault().postSticky(NextPreviousEvent(next = false))
            }
            action.equals("ACTION_NEXT", ignoreCase = true) -> {
                EventBus.getDefault().postSticky(NextPreviousEvent(next = true))
            }
            action.equals("ACTION_KILL", ignoreCase = true) -> {
                cleanUp()
                stopSelf()
            }
        }
    }

    /* Event Provider Related Functions Here */

    @Subscribe(sticky = true)
    fun RequestFulfiller(request: RequestEvent){
        when(request.event){
            "GetDurationEvent" -> {
                EventBus.getDefault().post(GetDurationEvent(mediaPlayer.duration))
            }

            "GetIndexEvent" -> {
                EventBus.getDefault().post(GetIndexEvent(currentIndex))
            }

            "GetPlayPauseEvent" -> {
                EventBus.getDefault().post(GetPlayPauseEvent(
                    run {
                        val songState =
                            if(mediaPlayer.isPlaying)
                                SongState.playing
                            else SongState.paused

                        songState
                    }
                ))
            }

            "GetProgressEvent" -> {
                EventBus.getDefault().post(GetProgressEvent(mediaPlayer.currentPosition))
            }

            "GetQueueEvent" -> {
                EventBus.getDefault().post(GetQueueEvent(playQueue))
            }

            "GetShuffleRepeatEvent" -> {
                EventBus.getDefault().post(GetShuffleRepeatEvent(onShuffle, onRepeat))
            }

            "GetSongEvent" -> {
                EventBus.getDefault().post(GetSongEvent(playQueue[currentIndex]))
            }
        }
    }

    @Subscribe(sticky = true)
    fun QueueAdder(addToQueueEvent: AddToQueueEvent){
        addToPlayQueue(addToQueueEvent.song)
        EventBus.getDefault().post(GetQueueEvent(playQueue))
    }

    @Subscribe
    fun QueueSetter(queueEvent: QueueEvent){
        playQueue = queueEvent.queue
        EventBus.getDefault().post(GetQueueEvent(playQueue))
    }

    @Subscribe(sticky = true)
    fun ProgressSetter(progressEvent: ProgressEvent){
        seekTo(progressEvent.progress)
        EventBus.getDefault().post(GetProgressEvent(progressEvent.progress))
    }

    @Subscribe(sticky = true)
    fun ShuffleRepeatSetter(shuffleRepeatEvent: ShuffleRepeatEvent){
        setShuffle(shuffleRepeatEvent.onShuffle)
        onRepeat = shuffleRepeatEvent.onRepeat
        EventBus.getDefault().post(GetShuffleRepeatEvent(onShuffle, onRepeat))
    }

    @Subscribe(sticky = true)
    fun IndexSetter(indexEvent: IndexEvent){
        currentIndex = indexEvent.index
        songChanged()
        EventBus.getDefault().post(GetIndexEvent(currentIndex))
    }

    @Subscribe(sticky = true)
    fun PlayPauseSetter(playPauseEvent: PlayPauseEvent){
        if(playPauseEvent.state == SongState.playing) playAudio()
        else pauseAudio()

        EventBus.getDefault().post(GetPlayPauseEvent(playPauseEvent.state))
    }

    @Subscribe
    fun NextPreviousSetter(nextPreviousEvent: NextPreviousEvent){
        if(nextPreviousEvent.next) nextSong()
        else previousSong()
    }

    /* Music Related Helper Functions Here */

    private fun setShuffle(enabled: Boolean){
        if(enabled){
            val detachedSong = playQueue[currentIndex]
            playQueue.removeAt(currentIndex)
            playQueue.shuffle()
            playQueue.add(0, detachedSong)
            currentIndex = 0
            onShuffle = true
        } else {
            onShuffle = false
            val currSong = playQueue[currentIndex]
            playQueue = ArrayList(playQueue.sortedBy { it.name })
            currentIndex = playQueue.indexOf(currSong)
        }

        EventBus.getDefault().post(GetQueueEvent(playQueue))
        EventBus.getDefault().post(GetIndexEvent(currentIndex))
    }

    private fun previousSong(){
        if(mediaPlayer.currentPosition > 2000){
            EventBus.getDefault().post(ProgressEvent(0))
        } else {
            if(currentIndex == 0) currentIndex = playQueue.size - 1
            else currentIndex--
            songChanged()
            playAudio()
        }
    }

    private fun nextSong(){
        if(onRepeat) EventBus.getDefault().postSticky(ProgressEvent(0))
        if(currentIndex+1 < playQueue.size){
            if(!onRepeat) currentIndex++
            songChanged()
            playAudio()
        } else {
            currentIndex = 0
            songChanged()
            playAudio()
        }
    }

    private fun addToPlayQueue(song: Song){
        if(currentIndex != playQueue.size-1) playQueue.add(currentIndex+1, song)
        else playQueue.add(song)
    }

    fun seekTo(position: Int){
        mediaPlayer.seekTo(position)
        mediaSession.setPlaybackState(
            ps
                .setActions(actions)
                .setState(
                    PlaybackState.STATE_PLAYING,
                    mediaPlayer.currentPosition.toLong(), 1f
                )
                .build()
        )

        EventBus.getDefault().post(GetProgressEvent(position))
    }

    private fun songChanged(){
        if(!isInstantiated) isInstantiated = true
        else {
            if(mediaPlayer.isPlaying) mediaPlayer.stop()
            mediaPlayer.reset()
        }
        mediaPlayer.setDataSource(playQueue[currentIndex].filePath)
        mediaPlayer.prepare()

        EventBus.getDefault().post(SongChangedEvent(playQueue[currentIndex], mediaPlayer.duration))
        EventBus.getDefault().post(GetSongEvent(playQueue[currentIndex]))
        EventBus.getDefault().postSticky(GetIndexEvent(currentIndex))
    }

    @SuppressLint("WakelockTimeout")
    private fun playAudio() {
        if(wakeLock?.isHeld != true) {
            wakeLock =
                (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                    newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AbleMusic::lock").apply {
                        acquire()
                    }
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

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_DURATION, mediaPlayer.duration.toLong())
                .build()
        )

        showNotification(
            generateAction(
                R.drawable.pause,
                "Pause",
                "ACTION_PAUSE"
            ), true
        )
        if (!mediaPlayer.isPlaying) {
            try {
                thread {
                    mediaPlayer.start()
                }
            } catch (e: Exception) {
                Log.e("ERR>", "-$e-")
            }
        }
    }

    private fun pauseAudio() {
        if(wakeLock?.isHeld == true)
            wakeLock?.release()

        showNotification(
            generateAction(
                R.drawable.play,
                "Play",
                "ACTION_PLAY"
            ), false
        )

        mediaSession.setPlaybackState(
            ps
                .setActions(actions)
                .setState(
                    PlaybackState.STATE_PAUSED,
                    mediaPlayer.currentPosition.toLong(), 1f
                )
                .build())

        mediaPlayer.pause()
        EventBus.getDefault().post(GetPlayPauseEvent(run {
            if(mediaPlayer.isPlaying) SongState.playing
            else SongState.paused
        }))
    }

    private fun cleanUp(){
        EventBus.getDefault().unregister(this)
        mediaPlayer.stop()
        mediaSession.release()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
            it.cancelAll()
        }
        if(wakeLock?.isHeld == true)
            wakeLock?.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUp()
    }

    private fun generateAction(
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

    private fun showNotification(action: Notification.Action, playing: Boolean) {
        val style = Notification.MediaStyle().setMediaSession(mediaSession.sessionToken)
        val intent = Intent(this, MusicService::class.java)
        intent.action = "ACTION_STOP"
        val pendingIntent =
            PendingIntent.getService(this, 1, intent, 0)
        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "10002")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setSubText("Music")
            .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.drawable.def_albart))
            .setContentTitle(playQueue[currentIndex].name)
            .setContentText(playQueue[currentIndex].artist)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, Player::class.java), 0))
            .setDeleteIntent(pendingIntent).style = style

        builder.addAction(
            generateAction(
                R.drawable.skip_previous,
                "Previous",
                "ACTION_PREVIOUS"
            )
        )

        builder.addAction(action)

        builder.addAction(
            generateAction(
                R.drawable.skip_next,
                "Next",
                "ACTION_NEXT"
            )
        )

        builder.addAction(
            generateAction(
                R.drawable.kill,
                "Kill",
                "ACTION_KILL"
            )
        )

        style.setShowActionsInCompactView(0, 1, 2, 3)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "10002",
                "MUSIC",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.enableLights(false)
            notificationChannel.enableVibration(false)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        builder.setOngoing(playing)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = builder.setChannelId("10002").build()
        } else {
            notification = builder.build()
            notificationManager.notify(1, notification)
        }

        startForeground(1, notification)
    }
}
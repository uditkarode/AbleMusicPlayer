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

package io.github.uditkarode.able.fragments

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.revely.gradient.RevelyGradient
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.vincan.medialoader.MediaLoader
import com.vincan.medialoader.MediaLoaderConfig
import com.vincan.medialoader.download.DownloadListener
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.Settings
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.events.HomeLoadingEvent
import io.github.uditkarode.able.models.Format
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.home.*
import org.greenrobot.eventbus.EventBus
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

class Home: Fragment() {
    private var songList = ArrayList<Song>()
    private var songAdapter: SongAdapter? = null
    var mService: MusicService? = null
    var isBound = false
    private lateinit var serviceConn: ServiceConnection
    private var songId: String = "temp"
    private lateinit var mediaLoaderConfig: MediaLoaderConfig
    private lateinit var mediaLoader: MediaLoader

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
        inflater.inflate(
            R.layout.home,
            container, false
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val songs = view.findViewById<RecyclerView>(R.id.songs)

        mediaLoaderConfig = MediaLoaderConfig.Builder(activity)
            .cacheRootDir(
                Constants.ableSongDir
            )
            .cacheFileNameGenerator {
                "$songId.tmp.webm"
            }
            .downloadThreadPriority(Thread.NORM_PRIORITY)
            .build()

        mediaLoader = MediaLoader.getInstance(activity)
        mediaLoader.init(mediaLoaderConfig)

        RevelyGradient
            .linear()
            .colors(intArrayOf(
                Color.parseColor("#7F7FD5"),
                Color.parseColor("#86A8E7"),
                Color.parseColor("#91EAE4")
            ))
            .on(view.findViewById<TextView>(R.id.able_header))

        settings.setOnClickListener {
            startActivity(Intent(requireContext(), Settings::class.java))
        }

        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                Shared.mService = service.getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
            }
        }

        bindEvent()

        thread {
            songList = Shared.getSongList(Constants.ableSongDir)
            songAdapter = SongAdapter(songList, WeakReference(this@Home), true)
            activity?.runOnUiThread {
                songs.adapter = songAdapter
                songs.layoutManager = LinearLayoutManager(requireContext())
            }
        }
    }

    fun bindEvent(){
        if(Shared.serviceRunning(MusicService::class.java, requireContext())) {
            try {
                activity?.applicationContext?.bindService(Intent(activity?.applicationContext, MusicService::class.java), serviceConn, 0)
            } catch(e: Exception){
                Log.e("ERR>", e.toString())
            }
        }
    }

    fun streamAudio(song: Song, toCache: Boolean){
        if(!Shared.serviceRunning(MusicService::class.java, requireContext())){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireActivity().startForegroundService(Intent(activity, MusicService::class.java))
            } else {
                requireActivity().startService(Intent(activity, MusicService::class.java))
            }

            bindEvent()
        }

        thread {
            @Suppress("ControlFlowWithEmptyBody")
            while(!isBound){}
            mService?.setPlayQueue(arrayListOf(Song(name = getString(R.string.loading), artist = "")))
            mService?.setCurrentIndex(0)
            mService?.showNotif()

            val streamInfo = StreamInfo.getInfo(song.youtubeLink)
            val stream = streamInfo.audioStreams.run { this[size - 1] }

            val url = stream.url
            val bitrate = stream.averageBitrate
            val ext = stream.getFormat().suffix

            if(song.ytmThumbnail.isNotBlank()){
                Glide.with(requireContext())
                    .asBitmap()
                    .load(song.ytmThumbnail)
                    .signature(ObjectKey("save"))
                    .listener(object: RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>?,
                            isFirstResource: Boolean
                        ): Boolean { return false }

                        override fun onResourceReady(
                            resource: Bitmap?,
                            model: Any?,
                            target: Target<Bitmap>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            if(resource != null)
                                Shared.saveStreamingAlbumArt(resource, Shared.getIdFromLink(song.youtubeLink))
                            return false
                        }
                    }).submit()
            }

            if(toCache){
                mediaLoader.addDownloadListener(url, object: DownloadListener {
                    override fun onProgress(url: String?, file: File?, progress: Int) {
                        if(progress == 100){
                            val current = mService!!.getPlayQueue()[mService!!.getCurrentIndex()]
                            val tempFile = File(Constants.ableSongDir.absolutePath
                                    + "/" + songId + ".tmp.$ext")
                            val format =
                                if (PreferenceManager.getDefaultSharedPreferences(
                                        context
                                    )
                                        .getString("format_key", "webm") == "mp3"
                                ) Format.MODE_MP3
                                else Format.MODE_WEBM

                            var command = "-i " +
                                    "\"${tempFile.absolutePath}\" -c copy " +
                                    "-metadata title=\"${current.name}\" " +
                                    "-metadata artist=\"${current.artist}\" -y "

                            if (format == Format.MODE_MP3)
                                command += "-vn -ab ${bitrate}k -c:a mp3 -ar 44100 "

                            command += "\"${tempFile.absolutePath.replace(".tmp", "")}/$id."

                            command += if (format == Format.MODE_MP3) "mp3\"" else "$ext\""

                            when (val rc = FFmpeg.execute(command)) {
                                Config.RETURN_CODE_SUCCESS -> {
                                    tempFile.delete()
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
                    }

                    override fun onError(e: Throwable?) {
                        Log.e("ERR>", e.toString())
                    }
                })

                song.filePath = mediaLoader.getProxyUrl(url)
            }
            else song.filePath = url
            mService?.setPlayQueue(arrayListOf(song))
            mService?.setIndex(0)
            EventBus.getDefault().post(HomeLoadingEvent(false))
            mService?.setPlayPause(SongState.playing)
        }
    }

    /* download to videoId.webm.tmp, add metadata and save to videoId.webm */
    fun downloadVideo(){
        songList = Shared.getSongList(Constants.ableSongDir)
        activity?.runOnUiThread {
            songAdapter?.update(songList)
        }
    }
}
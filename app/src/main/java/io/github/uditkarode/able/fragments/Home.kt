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
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.revely.gradient.RevelyGradient
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.FFprobe
import com.github.kiulian.downloader.OnYoutubeDownloadListener
import com.github.kiulian.downloader.YoutubeDownloader
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.Settings
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.home.*
import java.io.File
import java.lang.Exception
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

class Home: Fragment() {
    private var songList = ArrayList<Song>()
    var songAdapter: SongAdapter? = null
    var mService: MusicService? = null
    var isBound = false
    private lateinit var serviceConn: ServiceConnection

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
        inflater.inflate(
            R.layout.home,
            container, false
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val songs = view.findViewById<RecyclerView>(R.id.songs)

        RevelyGradient
            .linear()
            .colors(intArrayOf(
                Color.parseColor("#7F7FD5"),
                Color.parseColor("#86A8E7"),
                Color.parseColor("#91EAE4")
            ))
            .on(view.findViewById<TextView>(R.id.able_header))

        settings.setOnClickListener {
            startActivity(Intent((activity as Context), Settings::class.java))
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
            songList = getSongList(Constants.ableSongDir)
            songAdapter = SongAdapter(songList, WeakReference(this@Home))
            activity?.runOnUiThread {
                songs.adapter = songAdapter
                songs.layoutManager = LinearLayoutManager(activity as Context)
            }
        }
    }

    fun bindEvent(){
        if(Shared.serviceRunning(MusicService::class.java, activity as Context)) {
            try {
                activity?.applicationContext?.bindService(Intent(activity?.applicationContext, MusicService::class.java), serviceConn, 0)
            } catch(e: Exception){
                Log.e("ERR>", e.toString())
            }
        }
    }

    /* download to videoId.webm.tmp, add metadata and save to videoId.webm */
    fun downloadVideo(song: Song){
        songAdapter?.temp(Song(song.name, "Initialising download..."))
        val id = song.youtubeLink.substring(song.youtubeLink.lastIndexOf("=") + 1)

        thread {
            val video = YoutubeDownloader().getVideo(id)
            val downloadFormat = video.audioFormats().run { this[this.size - 1] }

            video.downloadAsync(downloadFormat, Constants.ableSongDir, object: OnYoutubeDownloadListener {
                override fun onDownloading(progress: Int) {
                    activity?.runOnUiThread {
                        songAdapter?.temp(Song(song.name,
                            "${progress}% ~ bitrate ${downloadFormat.averageBitrate() / 1024}k")
                        )
                    }
                }

                override fun onFinished(downloadedFile: File?) {
                    thread {
                        var name = song.name
                        name = name.replace(
                            Regex("${song.artist}\\s*[-,:]*\\s*"),
                            ""
                        )
                        name = name.replace(
                            Regex("\\(([Oo]]fficial)?\\s*([Mm]usic)?\\s*([Vv]ideo)?\\s*\\)"),
                            ""
                        )
                        name = name.replace(
                            Regex("\\s?\\(\\[?[Ll]yrics?\\)]?\\s*([Vv]ideo)?\\)?"),
                            ""
                        )
                        name =
                            name.replace(Regex("\\s?\\(?[aA]udio\\)?\\s*"), "")
                        name = name.replace(Regex("\\[Lyrics]"), "")
                        name = name.replace(
                            Regex("\\(Official\\sMusic\\sVideo\\)"),
                            ""
                        )
                        name = name.replace(Regex("\\[HD\\s&\\sHQ]"), "")
                        val target = downloadedFile!!.absolutePath.toString()

                        when (val rc = FFmpeg.execute(
                            "-i " +
                                    "\"${target}\" -c copy " +
                                    "-metadata title=\"${name}\" " +
                                    "-metadata artist=\"${song.artist}\"" +
                                    " ${Constants.ableSongDir.absolutePath}/$id.webm"
                        )) {
                            Config.RETURN_CODE_SUCCESS -> {
                                File(target).delete()
                                songList = getSongList(Constants.ableSongDir)
                                activity?.runOnUiThread {
                                    songAdapter?.update(songList)
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
                }

                override fun onError(throwable: Throwable?) {
                    Toast.makeText(activity?.applicationContext, "failed: ${throwable.toString()}", Toast.LENGTH_SHORT).show()
                    songList = getSongList(Constants.ableSongDir)
                    activity?.runOnUiThread {
                        songAdapter?.update(songList)
                    }
                }
            })
        }
    }

    private fun getSongList(musicFolder: File): ArrayList<Song> {
        var songs: ArrayList<Song> = ArrayList()
        var name = "???"
        var artist = "???"
        for (f in musicFolder.listFiles()?:arrayOf()) {
            if(!f.isDirectory){
                if(f.name.contains(".tmp") || f.nameWithoutExtension.length != 11){
                    //f.delete()
                    continue
                }

                val metadata = FFprobe.getMediaInformation(f.absolutePath).metadataEntries
                for(map in metadata){
                    if(map.key == "title")
                        name = map.value
                    else if(map.key == "ARTIST")
                        artist = map.value
                }
                if(name != "???"){
                    songs.add(
                        Song(
                        name,
                        artist,
                        filePath = f.path
                        )
                    )
                }
            }
        }

        if(songs.isNotEmpty()) songs = ArrayList(songs.sortedBy { it.name })

        return songs
    }
}
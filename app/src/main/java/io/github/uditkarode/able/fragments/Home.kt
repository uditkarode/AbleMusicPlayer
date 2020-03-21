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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.revely.gradient.RevelyGradient
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.FFprobe
import com.yausername.youtubedl_android.DownloadProgressCallback
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.Settings
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.home.*
import java.io.File
import java.lang.Exception
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

class Home(private val applContext: Context): Fragment() {
    private var songList = ArrayList<Song>()
    var songAdapter: SongAdapter? = null
    var mService: MusicService? = null
    var isBound = false
    private lateinit var serviceConn: ServiceConnection

    @Suppress("DEPRECATION")
    val ableSongDir = File(
        Environment.getExternalStorageDirectory(),
        "AbleMusic")

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
            songList = getSongList(ableSongDir)
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
                applContext.bindService(Intent(applContext, MusicService::class.java), serviceConn, 0)
            } catch(e: Exception){
                Log.e("ERR>", e.toString())
            }
        }
    }

    fun downloadVideo(song: Song){
        songAdapter?.temp(Song(song.name, "Initialising download..."))

        val link = song.youtubeLink
        val request = YoutubeDLRequest(link)
        val fileName = link.substring(link.lastIndexOf("=") + 1)
        var hasCompleted = false
        request.setOption("-f", "251")
        request.setOption("--cache-dir", ableSongDir.absolutePath + "/cache/")
        request.setOption("--audio-format", "aac")
        request.setOption("-o", ableSongDir.absolutePath + "/$fileName.webm.tmp")
        AsyncTask.execute {
            YoutubeDL.instance.execute(request,
                object : DownloadProgressCallback {
                    override fun onProgressUpdate(progress: Float, etaInSeconds: Long) {
                        activity?.runOnUiThread {
                            songAdapter?.temp(Song(song.name, "${progress}% - ${etaInSeconds}s remaining"))
                        }

                        if (progress == 100.toFloat()) {
                            if (!hasCompleted) {
                                hasCompleted = true
                                activity?.runOnUiThread {
                                    Handler().postDelayed({
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
                                                Regex("\\(Official Music Video\\)"),
                                                ""
                                            )
                                            name = name.replace(Regex("\\[HD & HQ]"), "")

                                            when (val rc = FFmpeg.execute(
                                                "-i ${ableSongDir.absolutePath}/${fileName}.webm.tmp -c copy " +
                                                        "-metadata title=\"${name}\" " +
                                                        "-metadata artist=\"${song.artist}\"" +
                                                        " ${ableSongDir.absolutePath}/${fileName}.webm"
                                            )) {
                                                Config.RETURN_CODE_SUCCESS -> {
                                                    File("${ableSongDir.absolutePath}/${fileName}.webm.tmp").delete()
                                                    songList = getSongList(ableSongDir)
                                                    activity?.runOnUiThread {
                                                        songAdapter?.update(songList)
                                                    }
                                                }
                                                Config.RETURN_CODE_CANCEL -> {
                                                    Log.e(
                                                        "K",
                                                        "Command execution cancelled by user."
                                                    )
                                                }
                                                else -> {
                                                    Log.e(
                                                        "K",
                                                        String.format(
                                                            "Command execution failed with rc=%d and the output below.",
                                                            rc
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }, 300)
                                }
                            }
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
                if(f.name.contains(".tmp")){
                    f.delete()
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
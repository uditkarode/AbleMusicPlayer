package io.github.uditkarode.able.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.os.Handler
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
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.Settings
import io.github.uditkarode.able.adapters.SongAdapter
import io.github.uditkarode.able.models.Song
import kotlinx.android.synthetic.main.home.*
import java.io.File
import kotlin.concurrent.thread

class Home: Fragment() {
    private var songList = ArrayList<Song>()
    var songAdapter: SongAdapter? = null

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

        thread {
            songList = getSongList(ableSongDir)
            songAdapter = SongAdapter(songList)
            activity?.runOnUiThread {
                songs.adapter = songAdapter
                songs.layoutManager = LinearLayoutManager(activity as Context)
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
            YoutubeDL.getInstance().execute(request) { progress: Float, eta: Long ->
                activity?.runOnUiThread {
                    songAdapter?.temp(Song(song.name, "${progress}% - ${eta}s remaining"))
                }

                if(progress == 100.toFloat()){
                    if(!hasCompleted) {
                        hasCompleted = true
                        activity?.runOnUiThread{
                            Handler().postDelayed({
                                thread {
                                    var name = song.name
                                    name = name.replace(Regex("${song.artist}\\s*[-,:]*\\s*"), "")
                                    name = name.replace(Regex("\\(([Oo]]fficial)?\\s*([Mm]usic)?\\s*([Vv]ideo)?\\s*\\)"), "")
                                    name = name.replace(Regex("\\s?\\(\\[?[Ll]yrics?\\)]?\\s*([Vv]ideo)?\\)?"), "")
                                    name = name.replace(Regex("\\s?\\(?[aA]udio\\)?\\s*"), "")
                                    name = name.replace(Regex("\\[Lyrics]"), "")
                                    name = name.replace(Regex("\\(Official Music Video\\)"), "")
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
                                            Log.e("K", "Command execution cancelled by user.")
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
package io.github.uditkarode.able.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.uditkarode.able.events.GetQueueEvent
import io.github.uditkarode.able.events.PlaylistEvent
import io.github.uditkarode.able.models.Playlist
import io.github.uditkarode.able.models.Song
import org.greenrobot.eventbus.EventBus
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class Shared {
    companion object {
        @Suppress("DEPRECATION")
        private val playlistFolder = File(
            Environment.getExternalStorageDirectory(),
            "AbleMusic/playlists")

        fun getPlaylists(): ArrayList<Playlist> {
            val ret: ArrayList<Playlist> = ArrayList()
            for (f in playlistFolder.listFiles()?:arrayOf()) {
                if(!f.isDirectory){
                    if(!f.name.contains(".json")) continue

                    val jsonReader = BufferedReader(
                        InputStreamReader(
                            f.inputStream()
                        )
                    )
                    val jsonBuilder = StringBuilder()
                    try {
                        var line: String?
                        while (jsonReader.readLine().also { line = it } != null) {
                            jsonBuilder.append(line).append("\n")
                        }

                        ret.add(Playlist(f.name, JSONArray(jsonBuilder.toString())))
                    } catch (e: Exception) {
                        Log.e("ERR>", e.toString())
                    }
                }
            }
            return ret
        }

        fun getSongsFromPlaylist(playlist: Playlist): ArrayList<Song>{
            val songs = ArrayList<Song>()
            val gson = Gson()

            try {
                for(i in 0 until playlist.songs.length())
                    songs.add(gson.fromJson(playlist.songs[i].toString(), Song::class.java))
            } catch(e: java.lang.Exception){
                Log.e("ERR>", e.toString())
            }

            return songs
        }

        fun removeFromPlaylist(playlist: Playlist, delTarget: Song){
            val songs = getSongsFromPlaylist(playlist)
            var targetIndex = -1
            for(i in 0 until songs.size){
                val song = songs[i]
                if(song.filePath == delTarget.filePath)
                    targetIndex = i
            }

            if(targetIndex != -1)
                songs.removeAt(targetIndex)

            modifyPlaylist(playlist.name, songs)
            EventBus.getDefault().post(PlaylistEvent(getPlaylists()))
        }

        private fun modifyPlaylist(name: String, songs: ArrayList<Song>? = null){
            try {
                if(name.isNotBlank()){
                    val playlistFile = File(playlistFolder.absolutePath + "/" + name)
                    playlistFile.writeText("[]")
                    if(!songs.isNullOrEmpty()){
                        playlistFile.writeText(
                            Gson().toJson(songs)
                        )
                    }
                }
            } catch (e: java.lang.Exception){
                Log.e("ERR>", e.toString())
            }
        }

        fun serviceRunning(serviceClass: Class<*>, context: Context): Boolean {
            val manager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
            return false
        }

        fun createPlaylist(name: String, context: Context){
            try {
                if(name.isNotBlank()){
                    val playlistFile = File(playlistFolder.absolutePath + "/" + name + ".json")
                    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    playlistFile.parentFile.mkdirs()
                    playlistFile.writeText("[]")

                    Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Playlist name cannot be empty!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: java.lang.Exception){
                Log.e("ERR>", e.toString())
                Toast.makeText(context, "Couldn't create playlist :(", Toast.LENGTH_SHORT).show()
            }
        }

        fun addToPlaylist(playlist: Playlist, song: Song, context: Context){
            val songs = getSongsFromPlaylist(playlist)
            var targetIndex = -1
            for(i in 0 until songs.size){
                val sng = songs[i]
                if(sng.filePath == song.filePath)
                    targetIndex = i
            }

            if(targetIndex == -1)
                songs.add(song)
            else Toast.makeText(context, "Playlist already contains this song", Toast.LENGTH_SHORT).show()
            modifyPlaylist(playlist.name, ArrayList(songs.sortedBy { it.name }))
        }
    }
}
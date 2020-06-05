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

package io.github.uditkarode.able.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.widget.Toast
import com.arthenica.mobileffmpeg.FFprobe
import com.google.gson.Gson
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.Fetch.Impl.getInstance
import com.tonyodev.fetch2.FetchConfiguration
import io.github.uditkarode.able.R
import io.github.uditkarode.able.events.PlaylistEvent
import io.github.uditkarode.able.models.Playlist
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.services.MusicService
import org.greenrobot.eventbus.EventBus
import org.json.JSONArray
import java.io.*

class Shared {
    companion object {
        var isFirstRun = true
        lateinit var fetch: Fetch

        lateinit var mService: MusicService

        fun serviceLinked(): Boolean{
            return this::mService.isInitialized
        }

        fun isColorDark(color: Int): Boolean {
            val darkness: Double =
                1-(0.299*Color.red(color) + 0.587*Color.green(color) + 0.114* Color.blue(color))/255
            return darkness >= 0.5
        }

        fun setupFetch(context: Context){
            fetch = getInstance(
                FetchConfiguration.Builder(context)
                    .setDownloadConcurrentLimit(1)
                    .build()
            )
        }

        fun saveAlbumArtToDisk(image: Bitmap, imageFile: File) {
            File(Constants.ableSongDir.absolutePath + "/album_art").run {
                if (!this.exists()) this.mkdirs()
            }
            try {
                val fOut: OutputStream = FileOutputStream(imageFile)
                image.compress(Bitmap.CompressFormat.JPEG, 90, fOut)
                fOut.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        fun getPlaylists(): ArrayList<Playlist> {
            val ret: ArrayList<Playlist> = ArrayList()
            for (f in Constants.playlistFolder.listFiles()?:arrayOf()) {
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

        fun getSongsFromPlaylist(playlist: Playlist): ArrayList<Song> {
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

        fun modifyPlaylist(name: String, songs: ArrayList<Song>? = null){
            try {
                if(name.isNotBlank()){
                    val playlistFile = File(Constants.playlistFolder.absolutePath + "/" + name)
                    playlistFile.parentFile!!.also { if(!it.exists()) it.mkdirs() }
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

        fun getSongList(musicFolder: File): ArrayList<Song> {
            var songs: ArrayList<Song> = ArrayList()
            var name = "???"
            var artist = "???"
            for (f in musicFolder.listFiles()?:arrayOf()) {
                if(!f.isDirectory){
                    if(f.extension == "tmp" ||
                        (f.nameWithoutExtension.length != 11 && f.nameWithoutExtension.length != 17)
                        || (f.extension != "webm" && f.extension != "mp3")){
                        continue
                    }

                    val mediaInfo = FFprobe.getMediaInformation(f.absolutePath)
                    if(mediaInfo != null){
                        val metadata = mediaInfo.metadataEntries
                        for(map in metadata){
                            if(map.key == "title")
                                name = map.value
                            else if(map.key == "ARTIST" || map.key == "artist")
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
            }

            if(songs.isNotEmpty()) songs = ArrayList(songs.sortedBy { it.name })

            return songs
        }

        fun createPlaylist(name: String, context: Context) {
            try {
                if(name.isNotBlank()){
                    val playlistFile = File(Constants.playlistFolder.absolutePath + "/" + name + ".json")
                    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    if(!playlistFile.parentFile.exists()) playlistFile.parentFile.mkdirs()
                    playlistFile.writeText("[]")

                    Toast.makeText(context, context.getString(R.string.playlist_created), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.playlist_empty), Toast.LENGTH_SHORT).show()
                }
            } catch (e: java.lang.Exception){
                Log.e("ERR>", e.toString())
                Toast.makeText(context, context.getString(R.string.playlist_fail), Toast.LENGTH_SHORT).show()
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
            else Toast.makeText(context, context.getString(R.string.playlist_dup), Toast.LENGTH_SHORT).show()
            modifyPlaylist(playlist.name, ArrayList(songs.sortedBy { it.name }))
        }
    }
}
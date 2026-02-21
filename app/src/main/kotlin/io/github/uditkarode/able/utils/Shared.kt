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

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import io.github.uditkarode.able.R
import io.github.uditkarode.able.model.Playlist
import io.github.uditkarode.able.model.song.Song
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.mp4.Mp4FieldKey
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.json.JSONArray
import org.schabi.newpipe.extractor.Image
import java.io.*
import java.util.*

object Shared {
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }

    fun uniqueFile(dir: File, baseName: String, ext: String): File {
        val target = File(dir, "$baseName.$ext")
        if (!target.exists()) return target
        var n = 2
        while (true) {
            val candidate = File(dir, "$baseName ($n).$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    private val youtubeIdPattern = Regex("^[A-Za-z0-9_-]{11,}$")

    fun migrateFileNames(context: Context) {
        val prefs = context.getSharedPreferences("able_migrations", Context.MODE_PRIVATE)
        if (prefs.getBoolean("filenames_migrated", false)) return

        val songDir = Constants.ableSongDir
        if (!songDir.exists()) {
            prefs.edit().putBoolean("filenames_migrated", true).apply()
            return
        }

        val pathMap = mutableMapOf<String, String>() // old path -> new path

        for (f in songDir.listFiles() ?: arrayOf()) {
            if (f.isDirectory || f.extension != "mp3" || f.name.contains(".tmp")) continue
            val baseName = f.nameWithoutExtension
            if (!youtubeIdPattern.matches(baseName)) continue

            val mmr = android.media.MediaMetadataRetriever()
            var title: String? = null
            try {
                mmr.setDataSource(f.absolutePath)
                title = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            } catch (_: Exception) {
            } finally {
                mmr.release()
            }

            if (title.isNullOrBlank() || title == baseName) continue

            val newFile = uniqueFile(songDir, sanitizeFileName(title), "mp3")
            val oldPath = f.absolutePath
            if (f.renameTo(newFile)) {
                pathMap[oldPath] = newFile.absolutePath
                // Rename sidecar album art
                val oldArt = File(Constants.albumArtDir, baseName)
                if (oldArt.exists()) {
                    oldArt.renameTo(File(Constants.albumArtDir, newFile.nameWithoutExtension))
                }
            }
        }

        // Update playlist file paths
        if (pathMap.isNotEmpty()) {
            for (playlist in getPlaylists()) {
                val songs = getSongsFromPlaylist(playlist)
                var changed = false
                for (song in songs) {
                    val newPath = pathMap[song.filePath]
                    if (newPath != null) {
                        song.filePath = newPath
                        changed = true
                    }
                }
                if (changed) modifyPlaylist(playlist.name, songs)
            }

            MediaScannerConnection.scanFile(
                context, pathMap.values.toTypedArray(), null, null
            )
        }

        prefs.edit().putBoolean("filenames_migrated", true).apply()
        Log.i("Shared", "Migration complete: renamed ${pathMap.size} files")
    }

    /** To only show the splash screen on the first launch of
     *  the MainActivity in a session.
     * */
    var isFirstOpen = true

    //    lateinit var fetch: Fetch
    var bmp: Bitmap? = null
    lateinit var defBitmap: Bitmap

    /**
     * @return the variable bmp.
     * This is used to prevent memory leaks caused by bitmaps
     * by using a single shared bitmap instance.
     */
    fun getSharedBitmap(): Bitmap {
        return bmp as Bitmap
    }

    /**
     * Recycles and assigns null to the variable bmp.
     * This is to get the GC to more quickly free memory
     * associated with it.
     */
    fun clearBitmap() {
        bmp?.recycle()
        bmp = null
    }

    /**
     * @param color a color in form of Integer.
     * @return if the color is 'dark' (light colors to be used on it to make them visible),
     * or 'light' (dark colors to be used on it to make them visible).
     */
    fun isColorDark(color: Int): Boolean {
        val darkness: Double =
            1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    /**
     * @param context a valid Context object
     * initialises the shared Fetch instance (fetch).
     */
//    fun setupFetch(context: Context) {
//        fetch = getInstance(
//            FetchConfiguration.Builder(context)
//                .setDownloadConcurrentLimit(1)
//                .build()
//        )
//    }

    /**
     * @param image the bitmap to save to disk.
     * @param imageFile the File object (used for path) to save the image to.
     */
    fun saveAlbumArtToDisk(image: Bitmap, imageFile: File) {
        Constants.albumArtDir.run { if (!exists()) mkdirs() }
        try {
            val fOut: OutputStream = FileOutputStream(imageFile)
            image.compress(Bitmap.CompressFormat.JPEG, 90, fOut)
            fOut.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * @param imageFile the File object that points to the thumbnail.
     * The ID will be extracted from the image path and the mp3 or m4a with the same filename
     * will have the image added to it as artwork in the metadata.
     */
    fun addThumbnails(imageFile: String, albumName: String = "", context: Context) {
        try {
            var id = imageFile.substringAfterLast("/")
            id = id.substringBeforeLast(".")
            val albumArt = File(Constants.albumArtDir, id)
            val audioFile: AudioFile = AudioFileIO.read(File(imageFile))
            if (albumName.isNotEmpty())
                id = albumName
            when {
                imageFile.contains(".mp3") -> {
                    audioFile.tag.deleteField(FieldKey.ALBUM)
                    audioFile.tag.deleteArtworkField()
                    audioFile.tag.setField(
                        FieldKey.ALBUM,
                        id
                    )
                    audioFile.tag.setField(AndroidArtwork.createArtworkFromFile(albumArt))
                }

                imageFile.contains(".m4a") -> {
                    val mp4tag = audioFile.tag as Mp4Tag
                    mp4tag.deleteField(Mp4FieldKey.ARTWORK)
                    mp4tag.deleteField(Mp4FieldKey.ALBUM)
                    mp4tag.setField(FieldKey.ALBUM, id)
                    val bitmap = Glide
                        .with(context)
                        .asBitmap()
                        .load(albumArt)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .submit()
                        .get()
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    val bitmapData = stream.toByteArray()
                    mp4tag.addField(mp4tag.createArtworkField(bitmapData))
                }
            }
            audioFile.commit()
            MediaScannerConnection.scanFile(context, arrayOf(imageFile), null, null)

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
//            GlobalScope.launch(Dispatchers.Main) {
//                Home.songAdapter?.notifyItemChanged(index)
//            }
    }


    /**
     * @param link a URL.
     * @return YouTube ID of the video from the link param.
     */
    fun getIdFromLink(link: String): String {
        return link.run {
            substring(lastIndexOf("=") + 1)
        }
    }

    /**
     * Picks the best available thumbnail URL from a list of NewPipe Image objects.
     * For ytimg URLs, constructs a maxresdefault URL.
     * For googleusercontent URLs, requests high-resolution via URL params.
     * Falls back to the largest available thumbnail.
     */
    fun getBestThumbnail(thumbnails: List<Image>, videoUrl: String): String {
        if (thumbnails.isEmpty()) return ""

        // Try to construct a high-res ytimg URL
        for (thumbnail in thumbnails) {
            if (thumbnail.url.contains("ytimg")) {
                val songId = getIdFromLink(videoUrl)
                return "https://i.ytimg.com/vi/$songId/maxresdefault.jpg"
            }
        }

        // For googleusercontent URLs, upscale the dimensions
        for (thumbnail in thumbnails) {
            if (thumbnail.url.contains("googleusercontent")) {
                return thumbnail.url.replace(Regex("=w\\d+-h\\d+"), "=w1500-h1500")
            }
        }

        // Fallback: pick the largest thumbnail by resolution
        return thumbnails.maxByOrNull { it.height * it.width }?.url
            ?: thumbnails[0].url
    }

    /**
     * Upscales a thumbnail URL to high resolution if possible.
     */
    fun upscaleThumbnailUrl(url: String): String {
        if (url.contains("googleusercontent")) {
            return url.replace(Regex("=w\\d+-h\\d+"), "=w1500-h1500")
        }
        return url
    }

    /**
     * Returns a small thumbnail URL suitable for list items.
     */
    fun getSmallThumbnailUrl(url: String): String {
        if (url.contains("maxresdefault.jpg")) {
            return url.replace("maxresdefault.jpg", "mqdefault.jpg")
        }
        if (url.contains("googleusercontent")) {
            return url.replace(Regex("=w\\d+-h\\d+"), "=w180-h180")
        }
        return url
    }

    /**
     * @param image a Bitmap object (album art).
     * @param id the YouTube ID of the song that's being streamed.
     * This is used to temporarily store the album art of the song being streamed.
     * The album art is stored to cache in case the user streams the same song
     * again. Once the cache reaches a maximum of 10 images, all images in the cache
     * are deleted.
     */
    fun saveStreamingAlbumArt(image: Bitmap, id: String) {
        val outputDir = Constants.cacheDir
        if ((outputDir.listFiles() ?: arrayOf()).size > 10) {
            for (child in outputDir.listFiles() ?: arrayOf()) {
                child.delete()
            }
        }

        outputDir.run { if (!exists()) mkdirs() }

        try {
            val fOut: OutputStream = FileOutputStream(File(outputDir, "sCache$id"))
            image.compress(Bitmap.CompressFormat.JPEG, 90, fOut)
            fOut.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * @param name the filename of the playlist json file.
     * @return an ArrayList containing the songs that the playlist
     * json file contains.
     */
    fun getSongsFromPlaylistFile(name: String): ArrayList<Song> {
        for (f in Constants.playlistFolder.listFiles() ?: arrayOf()) {
            if (!f.isDirectory) {
                if (f.name != name) continue
                else {
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

                        return getSongsFromPlaylist(
                            Playlist(
                                f.name,
                                JSONArray(jsonBuilder.toString())
                            )
                        )
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return arrayListOf()
    }

    /**
     * @return an ArrayList of Playlist objects containing
     * all playlists stored on the device.
     */
    fun getPlaylists(): ArrayList<Playlist> {
        val ret: ArrayList<Playlist> = ArrayList()
        for (f in Constants.playlistFolder.listFiles() ?: arrayOf()) {
            if (!f.isDirectory) {
                if (!f.name.contains(".json")) continue

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

    /**
     * @param playlist a Playlist object.
     * @return an ArrayList containing all songs that the playlist contains.
     */
    fun getSongsFromPlaylist(playlist: Playlist): ArrayList<Song> {
        val songs = ArrayList<Song>()
        val gson = Gson()

        try {
            for (i in 0 until playlist.songs.length())
                songs.add(gson.fromJson(playlist.songs[i].toString(), Song::class.java))
        } catch (e: java.lang.Exception) {
            Log.e("ERR>", e.toString())
        }

        return songs
    }

    /**
     * @param playlist the Playlist object to remove from.
     * @param delTarget the Song object to remove from the Playlist.
     * Removes songs from a playlist.
     */
    fun removeFromPlaylist(playlist: Playlist, delTarget: Song) {
        val songs = getSongsFromPlaylist(playlist)
        var targetIndex = -1
        for (i in 0 until songs.size) {
            val song = songs[i]
            if (song.filePath == delTarget.filePath)
                targetIndex = i
        }

        if (targetIndex != -1)
            songs.removeAt(targetIndex)

        modifyPlaylist(playlist.name, songs)
    }

    /**
     * @param name the filename of a potentially existing JSON file.
     * @return if a playlist with the same filename exists on the device.
     */
    fun playlistExists(name: String): Boolean {
        return File(Constants.playlistFolder.absolutePath + "/" + name).exists()
    }

    /**
     * @param name the filename of the playlist to modify.
     * @param songs optional ArrayList containing the songs that the playlist should have.
     * When the songs param is not specified, the playlist is cleared.
     */
    fun modifyPlaylist(name: String, songs: ArrayList<Song>? = null) {
        try {
            if (name.isNotBlank()) {
                val playlistFile = File(Constants.playlistFolder.absolutePath + "/" + name)
                playlistFile.parentFile!!.also { if (!it.exists()) it.mkdirs() }
                playlistFile.writeText("[]")
                if (!songs.isNullOrEmpty()) {
                    playlistFile.writeText(
                        Gson().toJson(songs)
                    )
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e("ERR>", e.toString())
        }
    }

    /**
     * @param serviceClass the service class to check.
     * @param context a valid Context object.
     * Checks if the service provided is running.
     */
    fun serviceRunning(serviceClass: Class<*>, context: Context): Boolean {
        if (serviceClass == io.github.uditkarode.able.services.MusicService::class.java) {
            return io.github.uditkarode.able.services.MusicService.isServiceRunning
        }
        val manager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE))
            if (serviceClass.name == service.service.className) return true
        return false
    }

    /**
     * @param musicFolder the File object pointing to a folder to check for songs in.
     * @return an ArrayList of Song objects containing all the Songs that the musicFolder
     * contains.
     */
    @SuppressLint("InlinedApi")
    fun getSongList(musicFolder: File, context: Context): ArrayList<Song> {
        val songs = ArrayList<Song>()
        val folderPath = musicFolder.absolutePath
        val indexedPaths = mutableSetOf<String>()

        // Query MediaStore for songs already scanned in this folder (instant)
        @Suppress("DEPRECATION") val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val path = it.getString(2)
                if (path.endsWith(".mp3") && !path.contains(".tmp")) {
                    songs.add(Song(
                        name = it.getString(0) ?: File(path).nameWithoutExtension,
                        artist = it.getString(1) ?: "",
                        filePath = path
                    ))
                    indexedPaths.add(path)
                }
            }
        }

        // Pick up any files not yet in MediaStore (title from filename, artist from metadata)
        val unindexedPaths = mutableListOf<String>()
        for (f in musicFolder.listFiles() ?: arrayOf()) {
            if (!f.isDirectory && f.extension == "mp3" && !f.name.contains(".tmp")
                && f.absolutePath !in indexedPaths) {
                var artist = ""
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    mmr.setDataSource(f.absolutePath)
                    mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.takeIf { it.isNotBlank() }?.let { artist = it }
                } catch (_: Exception) {
                } finally {
                    mmr.release()
                }
                songs.add(Song(name = f.nameWithoutExtension, artist = artist, filePath = f.absolutePath))
                unindexedPaths.add(f.absolutePath)
            }
        }
        // Trigger MediaStore scan so they're instant next time
        if (unindexedPaths.isNotEmpty()) {
            MediaScannerConnection.scanFile(
                context, unindexedPaths.toTypedArray(), null, null
            )
        }

        return songs
    }

    @SuppressLint("InlinedApi")
    fun getLocalSongs(context: Context): ArrayList<Song> {
        val songs: ArrayList<Song> = ArrayList()
        val ablePath = Constants.ableSongDir.absolutePath

        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
        val contentResolver: ContentResolver = context.contentResolver
        val songUri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION") val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val songCursor: Cursor? = contentResolver.query(
            songUri,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.DEFAULT_SORT_ORDER + " ASC"
        )
        try {
            if (songCursor != null && songCursor.moveToFirst()) {
                do {
                    val path: String = songCursor.getString(2)
                    if (path.startsWith(ablePath)) continue
                    if (!path.contains("webm") && !path.contains("WhatsApp")) {
                        if (path.contains("mp3") || path.contains("m4a")) {
                            songs.add(
                                Song(
                                    name = songCursor.getString(0),
                                    artist = songCursor.getString(1),
                                    filePath = path,
                                    albumId = songCursor.getLong(3),
                                    isLocal = true
                                )
                            )
                        }
                    }
                } while (songCursor.moveToNext())
            }
        } finally {
            songCursor?.close()
        }
        return songs
    }

    /**
     * @param name the name of the new playlist.
     * @param context a valid Context object.
     */
    fun createPlaylist(name: String, context: Context) {
        try {
            if (name.isNotBlank()) {
                val playlistFile =
                    File(Constants.playlistFolder.absolutePath + "/" + name + ".json")
                @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                if (!playlistFile.parentFile.exists()) playlistFile.parentFile.mkdirs()
                playlistFile.writeText("[]")

                Toast.makeText(
                    context,
                    context.getString(R.string.playlist_created),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.playlist_empty),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: java.lang.Exception) {
            Log.e("ERR>", e.toString())
            Toast.makeText(context, context.getString(R.string.playlist_fail), Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * @param song the song to add to a playlist.
     * @param playlist the Playlist object to add the song to.
     * @param context a valid Context object.
     */
    fun addToPlaylist(playlist: Playlist, song: Song, context: Context) {
        val songs = getSongsFromPlaylist(playlist)
        var targetIndex = -1
        for (i in 0 until songs.size) {
            val sng = songs[i]
            if (sng.filePath == song.filePath)
                targetIndex = i
        }

        if (targetIndex == -1)
            songs.add(song)
        else Toast.makeText(context, context.getString(R.string.playlist_dup), Toast.LENGTH_SHORT)
            .show()
        modifyPlaylist(
            playlist.name,
            ArrayList(songs.sortedBy { it.name.uppercase(Locale.getDefault()) })
        )
    }

    /**
     * @param context Context of the activity
     * Return True if Internet is Connected otherwise False
     */
    @Suppress("DEPRECATION")
    fun isInternetConnected(context: Context): Boolean {
        var result = false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw =
                connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            result = when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            connectivityManager.run {
                connectivityManager.activeNetworkInfo?.run {
                    result = when (type) {
                        ConnectivityManager.TYPE_WIFI -> true
                        ConnectivityManager.TYPE_MOBILE -> true
                        ConnectivityManager.TYPE_ETHERNET -> true
                        else -> false
                    }
                }
            }
        }
        return result
    }

    fun cleanupTempFiles() {
        Constants.cacheDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp") || file.name.contains(".tmp.")) {
                file.delete()
            }
        }
    }
}

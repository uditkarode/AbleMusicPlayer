/*
    Copyright 2020 Rupansh Sekar <rupanshsekar@hotmail.com>
    Copyright 2020 Udit Karode <udit.karode@gmail.com>
    Copyright 2020 Harshit Singh <harsh.008.com@gmail.com>

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

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.uditkarode.able.R
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.services.MusicService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.util.Collections
import java.util.regex.Pattern

object SpotifyImport {
    private const val TAG = "SpotifyImport"
    private const val EMBED_URL = "https://open.spotify.com/embed/playlist/"

    private val okClient = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nextDataPattern = Pattern.compile(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>"""
    )

    @Volatile
    var isImporting = false

    // UI-observable state for Downloads screen
    @Volatile var totalTracks: Int = 0
    @Volatile var currentTrackIndex: Int = 0
    @Volatile var currentTrackName: String = ""
    @Volatile var currentTrackStatus: String = ""

    data class SpotifyTrack(val title: String, val artist: String)

    sealed class EmbedResult {
        data class Success(val name: String, val tracks: List<SpotifyTrack>) : EmbedResult()
        data class Error(val message: String) : EmbedResult()
    }

    /**
     * @return true if at least one song was imported successfully.
     */
    fun importList(playId: String, builder: Notification.Builder, context: Context): Boolean {
        isImporting = true
        try {
            val result = fetchPlaylistFromEmbed(playId)
            if (result is EmbedResult.Error) {
                showFailureNotification(builder, context, result.message)
                return false
            }

            val success = result as EmbedResult.Success
            val playlistName = success.name
            val tracks = success.tracks
            val safeName = playlistName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val fileName = "Spotify: $safeName.json"

            val songArr = ArrayList<Song>()
            val totalTracks = tracks.size
            this.totalTracks = totalTracks

            if (!Constants.ableSongDir.exists()) Constants.ableSongDir.mkdirs()
            if (!Constants.albumArtDir.exists()) Constants.albumArtDir.mkdirs()

            for (i in tracks.indices) {
                if (!isImporting) return false

                val track = tracks[i]
                val displayName = "${track.title} - ${track.artist}".run {
                    if (length > 30) substring(0, 30) + "..." else this
                }
                currentTrackIndex = i + 1
                currentTrackName = "${track.title} - ${track.artist}"
                currentTrackStatus = "Searching…"

                updateNotification(builder, context, displayName, "${i + 1} of $totalTracks", totalTracks, i, true)

                try {
                    val song = downloadFromYouTubeMusic(
                        "${track.title} - ${track.artist}",
                        builder, context, i, totalTracks
                    )
                    if (song != null) {
                        songArr.add(song)
                    } else {
                        Log.w(TAG, "No YouTube Music result for: ${track.title} - ${track.artist}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download: ${track.title} - ${track.artist}", e)
                }
            }

            if (songArr.size > 0) {
                Shared.modifyPlaylist(fileName, songArr)
                return true
            } else {
                showFailureNotification(builder, context, context.getString(R.string.spot_ytfail))
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            showFailureNotification(builder, context, context.getString(R.string.spot_fail))
            return false
        } finally {
            isImporting = false
            totalTracks = 0
            currentTrackIndex = 0
            currentTrackName = ""
            currentTrackStatus = ""
            mainHandler.post {
                MusicService.registeredClients.forEach { it.spotifyImportChange(false) }
            }
        }
    }

    private fun downloadFromYouTubeMusic(
        query: String,
        builder: Notification.Builder,
        context: Context,
        trackIndex: Int,
        totalTracks: Int
    ): Song? {
        // Search YouTube Music
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query,
            Collections.singletonList(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        extractor.fetchPage()

        val items = extractor.initialPage.items
        if (items.isEmpty()) return null

        val searchResult = items[0] as StreamInfoItem
        val thumbnailUrl = Shared.getBestThumbnail(searchResult.thumbnails, searchResult.url)
        val songId = Shared.getIdFromLink(searchResult.url)

        val sanitizedName = Shared.sanitizeFileName(searchResult.name)

        // Skip if already downloaded (check both old ID-based and new title-based naming)
        val oldFile = File(Constants.ableSongDir, "$songId.mp3")
        val titleFile = File(Constants.ableSongDir, "$sanitizedName.mp3")
        val existingFile = when {
            oldFile.exists() -> oldFile
            titleFile.exists() -> titleFile
            else -> null
        }
        if (existingFile != null) {
            Log.i(TAG, "Skipping already downloaded: ${existingFile.name}")
            currentTrackStatus = "Already exists"
            return Song(
                name = searchResult.name,
                artist = searchResult.uploaderName,
                youtubeLink = searchResult.url,
                filePath = existingFile.absolutePath,
                ytmThumbnail = thumbnailUrl
            )
        }

        val displayName = searchResult.name.run {
            if (length > 30) substring(0, 30) + "..." else this
        }

        // Resolve stream
        val streamInfo = StreamInfo.getInfo(searchResult.url)
        val stream = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
            ?: streamInfo.audioStreams[0]
        val url = stream.content
        val bitrate = stream.averageBitrate
        val ext = stream.getFormat()!!.suffix

        // Download to temp file
        val tempFile = File(Constants.ableSongDir, "$songId.tmp.$ext")

        currentTrackStatus = "0%"
        updateNotification(builder, context, displayName, "${trackIndex + 1} of $totalTracks — 0%", totalTracks, trackIndex, false)

        ChunkedDownloader.download(url, tempFile) { progress ->
            currentTrackStatus = "$progress%"
            updateNotification(
                builder, context, displayName,
                "${trackIndex + 1} of $totalTracks — $progress%",
                100, progress, false
            )
        }

        // FFmpeg transcode + metadata (store YouTube ID in comment tag)
        currentTrackStatus = "Saving…"
        updateNotification(builder, context, displayName, "${trackIndex + 1} of $totalTracks — saving", totalTracks, trackIndex + 1, true)

        val mp3Bitrate = maxOf(bitrate, 128)
        val tmpMp3 = File(Constants.ableSongDir, "$songId.mp3")
        val command = "-i \"${tempFile.absolutePath}\" -c copy " +
                "-metadata title=\"${searchResult.name}\" " +
                "-metadata artist=\"${searchResult.uploaderName}\" " +
                "-metadata comment=\"$songId\" -y " +
                "-vn -ab ${mp3Bitrate}k -c:a mp3 -ar 44100 " +
                "\"${tmpMp3.absolutePath}\""

        val session = FFmpegKit.execute(command)
        tempFile.delete()

        if (!ReturnCode.isSuccess(session.returnCode)) {
            Log.e(TAG, "FFmpeg failed for $songId with rc=${session.returnCode}")
            tmpMp3.delete()
            return null
        }

        // Rename to song title
        val finalFile = Shared.uniqueFile(Constants.ableSongDir, sanitizedName, "mp3")
        tmpMp3.renameTo(finalFile)

        // Save album art (keyed by final filename)
        if (thumbnailUrl.isNotBlank()) {
            try {
                val drw = Glide.with(context)
                    .load(thumbnailUrl)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .submit().get()
                if (!Constants.albumArtDir.exists()) Constants.albumArtDir.mkdirs()
                Shared.saveAlbumArtToDisk(drw.toBitmap(), File(Constants.albumArtDir, finalFile.nameWithoutExtension))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save album art for ${finalFile.name}", e)
            }
        }

        return Song(
            name = searchResult.name,
            artist = searchResult.uploaderName,
            youtubeLink = searchResult.url,
            filePath = finalFile.absolutePath,
            ytmThumbnail = thumbnailUrl
        )
    }

    private fun updateNotification(
        builder: Notification.Builder,
        context: Context,
        title: String,
        text: String,
        max: Int,
        progress: Int,
        indeterminate: Boolean
    ) {
        NotificationManagerCompat.from(context).apply {
            builder.setContentTitle(title)
            builder.setContentText(text)
            builder.setProgress(max, progress, indeterminate)
            builder.setOngoing(true)
            notify(3, builder.build())
        }
    }

    private fun fetchPlaylistFromEmbed(playId: String): EmbedResult {
        return try {
            val request = Request.Builder()
                .url("$EMBED_URL$playId")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
                )
                .build()
            val response = okClient.newCall(request).execute()
            val html = response.body.string()

            val matcher = nextDataPattern.matcher(html)
            if (!matcher.find()) {
                Log.e(TAG, "Could not find __NEXT_DATA__ in embed page")
                return EmbedResult.Error("Failed to load playlist")
            }

            val json = JSONObject(matcher.group(1)!!)
            val pageProps = json
                .getJSONObject("props")
                .getJSONObject("pageProps")

            if (!pageProps.has("state")) {
                val status = pageProps.optInt("status", -1)
                Log.e(TAG, "Embed page error: status=$status")
                return when (status) {
                    404 -> EmbedResult.Error("Playlist not found — it may be private")
                    else -> EmbedResult.Error("Failed to load playlist")
                }
            }

            val state = pageProps.getJSONObject("state")
            val entity = state
                .getJSONObject("data")
                .getJSONObject("entity")

            val playlistName = entity.getString("name")

            // Try to get all tracks via Spotify Web API using the embed's access token
            val accessToken = state.optJSONObject("session")?.optString("accessToken", "")
            if (!accessToken.isNullOrBlank()) {
                val apiTracks = fetchAllTracksFromApi(playId, accessToken)
                if (apiTracks != null && apiTracks.isNotEmpty()) {
                    return EmbedResult.Success(playlistName, apiTracks)
                }
            }

            // Fallback: use the embedded trackList (limited to ~100)
            val trackList = entity.getJSONArray("trackList")
            val tracks = mutableListOf<SpotifyTrack>()

            for (i in 0 until trackList.length()) {
                val track = trackList.getJSONObject(i)
                val title = track.getString("title")
                val artist = track.getString("subtitle")
                tracks.add(SpotifyTrack(title, artist))
            }

            if (tracks.isEmpty()) {
                return EmbedResult.Error("Playlist is empty")
            }

            EmbedResult.Success(playlistName, tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch playlist from embed page", e)
            EmbedResult.Error("Failed to load playlist")
        }
    }

    private fun fetchAllTracksFromApi(playId: String, accessToken: String): List<SpotifyTrack>? {
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        val limit = 100

        try {
            while (true) {
                val request = Request.Builder()
                    .url("https://api.spotify.com/v1/playlists/$playId/tracks?limit=$limit&offset=$offset&fields=items(track(name,artists(name))),total")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = okClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Spotify API returned ${response.code}")
                    return null
                }

                val body = JSONObject(response.body.string())
                val items = body.getJSONArray("items")
                val total = body.getInt("total")

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val track = item.optJSONObject("track") ?: continue
                    val title = track.optString("name", "")
                    if (title.isBlank()) continue
                    val artists = track.optJSONArray("artists")
                    val artist = if (artists != null && artists.length() > 0) {
                        (0 until artists.length()).joinToString(", ") {
                            artists.getJSONObject(it).optString("name", "")
                        }
                    } else ""
                    tracks.add(SpotifyTrack(title, artist))
                }

                offset += limit
                if (offset >= total) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tracks from Spotify API", e)
            return null
        }

        return tracks
    }

    private fun showFailureNotification(
        builder: Notification.Builder,
        context: Context,
        message: String
    ) {
        NotificationManagerCompat.from(context).apply {
            builder.setContentTitle(message)
            builder.setContentText("")
            builder.setProgress(0, 0, false)
            builder.setOngoing(false)
            notify(3, builder.build())
        }
        isImporting = false
    }
}

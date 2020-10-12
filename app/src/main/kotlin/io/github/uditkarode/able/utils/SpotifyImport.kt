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
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.PreferenceManager
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import io.github.uditkarode.able.models.Format
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.spotifyplaylist.SpotifyPlaylist
import io.github.uditkarode.able.R
import io.github.uditkarode.able.services.MusicService
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/**
 * object that takes care of inner workings of Spotify songs import.
 */
@ExperimentalCoroutinesApi
object SpotifyImport: CoroutineScope {
    private const val auth =
        "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"

    private val okClient = OkHttpClient()
    private val gson = Gson()
    var isImporting = true

    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    fun importList(playId: String, builder: Notification.Builder, context: Context) {
        val authR = Request.Builder().url(auth).removeHeader("User-Agent")
            .addHeader("Accept", "application/json").addHeader("Accept-Language", "en").build()
        val resp = okClient.newCall(authR).execute()
        val respDataType = object : TypeToken<Map<String?, String?>?>() {}.type
        val respMap: Map<String, String> = gson.fromJson(resp.body?.string(), respDataType)
        val authT = respMap["accessToken"]
        if (authT != null) {
            val playR = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/${playId}?type=track%2Cepisode")
                .removeHeader("User-Agent")
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "en")
                .addHeader("authorization", "Bearer $authT").build()

            val resp2 = okClient.newCall(playR).execute()
            val respPlayList = gson.fromJson(resp2.body?.string(), SpotifyPlaylist::class.java)
            if (!Shared.playlistExists("Spotify: ${respPlayList.name}.json")) {
                val songArr: ArrayList<Song> = ArrayList()
                Constants.playlistSongDir.run {
                    if (!this.exists()) this.mkdirs()
                }
                for (i in respPlayList.tracks.items.indices) {
                    val item = respPlayList.tracks.items[i]
                    if (isImporting) {
                        val songName = "${item.track.name} - ${item.track.artists[0].name}".run {
                            if (this.length > 25) this.substring(0, 25) + "..." else this
                        }
                        NotificationManagerCompat.from(context).apply {
                            builder.setContentText("$i of ${respPlayList.tracks.items.size}")
                            builder.setContentTitle(songName)
                            builder.setProgress(100, 100, true)
                            builder.setOngoing(true)
                            notify(3, builder.build())
                        }

                        var downloadDone = false

                        val extractor = ServiceList.YouTube.getSearchExtractor(
                            "${item.track.name} - ${item.track.artists[0].name}",
                            Collections.singletonList(
                                YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
                            ), ""
                        )
                        extractor.fetchPage()

                        if (extractor.initialPage.items.size > 0) {
                            val toAdd = extractor.initialPage.items[0] as StreamInfoItem
                            val fileName = toAdd.url.run {
                                this.substring(this.lastIndexOf("=") + 1)
                            }

                            val streamInfo = StreamInfo.getInfo(toAdd.url)
                            val stream = streamInfo.audioStreams.run { this[this.size - 1] }

                            launch(Dispatchers.IO) {
                                if (toAdd.thumbnailUrl.isNotBlank()) {
                                    val drw = Glide
                                        .with(context)
                                        .load(toAdd.thumbnailUrl)
                                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                                        .skipMemoryCache(true)
                                        .submit()
                                        .get()

                                    val img = File(
                                        Constants.ableSongDir.absolutePath + "/album_art",
                                        fileName
                                    )
                                    Shared.saveAlbumArtToDisk(drw.toBitmap(), img)
                                }
                            }

                            val url = stream.url
                            val bitrate = stream.averageBitrate
                            val ext = stream.getFormat().suffix
                            val mediaFile = File(Constants.playlistSongDir, fileName)
                            var finalExt = "."

                            if(File(mediaFile.absolutePath + ".tmp.$ext").exists())
                                continue

                            try {
                                val request =
                                    Request(url, mediaFile.absolutePath + ".tmp.$ext").also {
                                        it.priority = Priority.HIGH
                                        it.networkType = NetworkType.ALL
                                    }

                                Shared.fetch.addListener(object : FetchListener {
                                    override fun onAdded(download: Download) {}

                                    override fun onCancelled(download: Download) {}

                                    override fun onCompleted(download: Download) {
                                        NotificationManagerCompat.from(context).apply {
                                            builder.setContentText((i + 1).toString() + " of ${respPlayList.tracks.items.size}")
                                            builder.setContentTitle("Saving...")
                                                .setProgress(100, 100, true)
                                            builder.setOngoing(true)
                                            notify(3, builder.build())
                                        }
                                        val target = mediaFile.absolutePath.toString() + ".tmp.$ext"

                                        var command = "-i " +
                                                "\"${target}\" -c copy " +
                                                "-metadata title=\"${item.track.name}\" " +
                                                "-metadata artist=\"${item.track.artists[0].name}\" -y "
                                        val format =
                                            if (PreferenceManager.getDefaultSharedPreferences(
                                                    context
                                                )
                                                    .getString("format_key", "webm") == "mp3"
                                            ) Format.MODE_MP3
                                            else Format.MODE_WEBM
                                        if (format == Format.MODE_MP3)
                                            command += "-vn -ab ${bitrate}k -c:a mp3 -ar 44100 "

                                        command += "\"${Constants.playlistSongDir.absolutePath}/$fileName."

                                        command += if (format == Format.MODE_MP3) "mp3\"" else "$ext\""
                                        finalExt += if (format == Format.MODE_MP3) "mp3" else ext

                                        when (val rc = FFmpeg.execute(command)) {
                                            Config.RETURN_CODE_SUCCESS -> {
                                                File(target).delete()
                                                Shared.fetch.removeListener(this)
                                                downloadDone = true
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

                                    override fun onDeleted(download: Download) {}

                                    override fun onDownloadBlockUpdated(
                                        download: Download,
                                        downloadBlock: DownloadBlock,
                                        totalBlocks: Int
                                    ) {
                                    }

                                    override fun onError(
                                        download: Download,
                                        error: Error,
                                        throwable: Throwable?
                                    ) {
                                    }

                                    override fun onPaused(download: Download) {}

                                    override fun onProgress(
                                        download: Download,
                                        etaInMilliSeconds: Long,
                                        downloadedBytesPerSecond: Long
                                    ) {
                                        NotificationManagerCompat.from(context).apply {
                                            builder.setContentText((i + 1).toString() + " of ${respPlayList.tracks.items.size}")
                                            builder.setContentTitle(songName)
                                            builder.setProgress(100, download.progress, false)
                                            builder.setOngoing(true)
                                            notify(3, builder.build())
                                        }
                                    }

                                    override fun onQueued(
                                        download: Download,
                                        waitingOnNetwork: Boolean
                                    ) {

                                    }

                                    override fun onRemoved(download: Download) {

                                    }

                                    override fun onResumed(download: Download) {

                                    }

                                    override fun onStarted(
                                        download: Download,
                                        downloadBlocks: List<DownloadBlock>,
                                        totalBlocks: Int
                                    ) {

                                    }

                                    override fun onWaitingNetwork(download: Download) {

                                    }
                                })

                                Shared.fetch.enqueue(request)
                            } catch (e: Exception) {
                                Log.e("ERR>", e.toString())
                            }

                            while (!downloadDone) Thread.sleep(1000)
                            if(toAdd.thumbnailUrl.contains("ytimg")) {
                                val songId = Shared.getIdFromLink(toAdd.url)
                                toAdd.thumbnailUrl = "https://i.ytimg.com/vi/$songId/maxresdefault.jpg"
                            }
                            songArr.add(
                                Song(
                                    name = toAdd.name,
                                    artist = toAdd.uploaderName,
                                    filePath = mediaFile.absolutePath + finalExt,
                                    youtubeLink = toAdd.url,
                                    ytmThumbnail = toAdd.thumbnailUrl
                                )
                            )
                        }
                    } else {
                        return
                    }
                }

                if (songArr.size > 0) {
                    MusicService.registeredClients.forEach { it.spotifyImportChange(false) }
                    Shared.modifyPlaylist("Spotify: ${respPlayList.name}.json", songArr)
                    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
                        it.cancel(3)
                    }

                    Toast.makeText(
                        context,
                        context.getString(R.string.spot_suc),
                        Toast.LENGTH_LONG
                    ).show()

                    isImporting = false
                } else {
                    NotificationManagerCompat.from(context).apply {
                        builder.setContentText(context.getString(R.string.spot_fail))
                        builder.setOngoing(false)
                        notify(3, builder.build())
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.spot_ytfail),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            NotificationManagerCompat.from(context).apply {
                builder.setContentText(context.getString(R.string.spot_fail))
                builder.setOngoing(false)
                notify(3, builder.build())
            }
            Toast.makeText(
                context,
                context.getString(R.string.unexpected_err),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
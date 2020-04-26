/*
    Copyright 2020 Rupansh Sekar <rupanshsekar@hotmail.com>
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
import android.app.Notification
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.spotifyplaylist.SpotifyPlaylist
import io.github.uditkarode.able.utils.Shared.Companion.modifyPlaylist
import io.github.uditkarode.able.utils.Shared.Companion.ytSearchRequestBuilder
import io.github.uditkarode.able.utils.Shared.Companion.ytSearcher
import okhttp3.OkHttpClient
import okhttp3.Request

object SpotifyImport {
    private const val auth =
        "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"

    private val okClient = OkHttpClient()
    private val gson = Gson()
    var isImporting = true
    @SuppressLint("StaticFieldLeak")
    fun importList(playId: String, builder: Notification.Builder, applicationContext: Context) {
        Log.d("Spotify Import List ", "playid =  $playId")

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
            val songArr: ArrayList<Song> = ArrayList()
            for (i in respPlayList.tracks.items.indices) {
                val item = respPlayList.tracks.items[i]
                if (isImporting) {
                    NotificationManagerCompat.from(applicationContext).apply {
                        builder.setContentText("($i/${respPlayList.tracks.items.size})")
                        builder.setContentTitle("${item.track.name}  ${item.track.artists[0].name})")
                        builder.setProgress(100, 100, true)
                        builder.setOngoing(true)
                        notify(3, builder.build())
                    }
                    val (videos, channels) = ytSearcher(
                        okClient,
                        ytSearchRequestBuilder("${item.track.name} - ${item.track.artists[0].name}")
                    )
                    if (videos.size > 0) {
                        Log.d(
                            "Importify",
                            "name is ${videos[0].text()}url is https://www.youtube.com" + videos[0].attr(
                                "href"
                            )
                        )
                        try {
                            val song = Song(
                                name = videos[0].text(),
                                youtubeLink = "https://www.youtube.com" + videos[0].attr("href")
                            )
                            song.artist = channels[0].text()
                            songArr.add(song)
                            /**
                             *           object : YouTubeExtractor(applicationContext) {
                            override fun onExtractionComplete(
                            ytFiles: SparseArray<YtFile?>?,
                            vMeta: VideoMeta?
                            ) {
                            val audioFormats = ArrayList<YtFile>()

                            ytFiles!!.forEach { _, value ->
                            if (value!!.format.audioBitrate != -1) audioFormats.add(
                            value
                            )
                            }
                            song.filePath = audioFormats[audioFormats.size - 1].url

                            }
                            }.extract(song.youtubeLink, true, true)
                             * */
                        } catch (e: Exception) {
                            Log.d("Exception Occured", "$e   ")
                            continue
                        }
                    }
                } else {
                    return
                }
            }
            if (songArr.size > 0) {
                modifyPlaylist("Spotify: ${respPlayList.name}.json", songArr)
                NotificationManagerCompat.from(applicationContext).apply {
                    builder.setContentText("Done! Enjoy your spotify songs!")
                    builder.setOngoing(false)
                    notify(3, builder.build())
                }
            } else {
                NotificationManagerCompat.from(applicationContext).apply {
                    builder.setContentText("Couldn't find any songs on YouTube :( sorry!")
                    builder.setOngoing(false)
                    notify(3, builder.build())
                }
                Toast.makeText(
                    applicationContext,
                    "Couldn't find any songs on YouTube :( sorry!",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            NotificationManagerCompat.from(applicationContext).apply {
                builder.setContentText("Something went wrong. Please report this to us!")
                builder.setOngoing(false)
                notify(3, builder.build())
            }
            Toast.makeText(
                applicationContext,
                "Something went wrong. Please report this to us!",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
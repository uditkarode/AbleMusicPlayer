/*
    Copyright 2020 Rupansh Sekar <rupanshsekar@hotmail.com>

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

import android.app.Activity
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.github.kiulian.downloader.YoutubeDownloader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.PlaylistAdapter
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.utils.Shared.Companion.getPlaylists
import io.github.uditkarode.able.utils.Shared.Companion.modifyPlaylist
import io.github.uditkarode.able.utils.Shared.Companion.ytSearchRequestBuilder
import io.github.uditkarode.able.utils.Shared.Companion.ytSearcher
import io.github.uditkarode.able.models.spotifyplaylist.SpotifyPlaylist
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

object SpotifyImport {
    private const val auth = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"

    private val okClient = OkHttpClient()
    private val gson = Gson()

    fun importList(playId: String, activity: Activity, dialog: MaterialDialog) {
        val authR = Request.Builder().url(auth).removeHeader("User-Agent").addHeader("Accept", "application/json").addHeader("Accept-Language", "en").build()
        thread {
            val resp = okClient.newCall(authR).execute()
            val respDataType = object : TypeToken<Map<String?, String?>?>() {}.type
            val respMap: Map<String, String> = gson.fromJson(resp.body?.string(), respDataType)
            val authT = respMap["accessToken"]
            if (authT != null) {
                val playR = Request.Builder().url("https://api.spotify.com/v1/playlists/${playId}?type=track%2Cepisode").removeHeader("User-Agent")
                    .addHeader("Accept", "application/json")
                    .addHeader("Accept-Language", "en")
                    .addHeader("authorization", "Bearer $authT").build()

                val resp2 = okClient.newCall(playR).execute()
                val respPlayList = gson.fromJson(resp2.body?.string(), SpotifyPlaylist::class.java)
                val songArr: ArrayList<Song> = ArrayList()
                for (item in respPlayList.tracks.items) {
                    val (videos, channels) = ytSearcher(okClient, ytSearchRequestBuilder("${item.track.name} - ${item.track.artists[0].name}"))
                    if (videos.size > 0) {
                        try {
                            val song = Song(
                                name = videos[0].text(),
                                youtubeLink = "https://www.youtube.com" + videos[0].attr("href")
                            )
                            song.artist = channels[0].text()
                            val video = YoutubeDownloader().getVideo(
                                song.youtubeLink.substring(
                                    song.youtubeLink.lastIndexOf("=") + 1
                                )
                            )
                            song.filePath = (video.audioFormats().run { this[this.size - 1] }).url()
                            songArr.add(song)
                        } catch (e: Exception) {
                            continue
                        }
                    }
                }

                if (songArr.size > 0) {
                    modifyPlaylist("Spotify: ${respPlayList.name}.json", songArr)
                    activity.runOnUiThread {
                        dialog.dismiss()
                        val playlistAdapter: PlaylistAdapter =
                            activity.findViewById<RecyclerView>(R.id.playlists_rv).adapter as PlaylistAdapter
                        playlistAdapter.update(getPlaylists())
                        Toast.makeText(
                            activity,
                            "Done! Enjoy your spotify songs!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    activity.runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(activity, "Couldn't find any songs on YouTube :( sorry!", Toast.LENGTH_LONG).show()
                    }
                }

            } else {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Something went wrong. Please report this to us!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
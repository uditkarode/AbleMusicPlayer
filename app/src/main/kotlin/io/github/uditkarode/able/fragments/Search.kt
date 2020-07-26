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

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.YtResultAdapter
import io.github.uditkarode.able.adapters.YtmResultAdapter
import io.github.uditkarode.able.models.Song
import kotlinx.android.synthetic.main.search.*
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.lang.ref.WeakReference
import java.util.Collections.singletonList
import kotlin.concurrent.thread

/**
 * The second fragment. Used to search for songs.
 */
class Search : Fragment() {
    private lateinit var itemPressed: SongCallback
    private lateinit var sp: SharedPreferences

    interface SongCallback {
        fun sendItem(song: Song)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.search, container, false)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            itemPressed = context as Activity as SongCallback
        } catch (e: ClassCastException) {
            throw ClassCastException(
                activity.toString()
                        + " must implement SongCallback"
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sp = requireContext().getSharedPreferences("search", 0)

        when(sp.getString("mode", "Music")){
            "Album" -> {
                search_mode.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.mode_album))
            }

            "Playlists" -> {
                search_mode.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.mode_playlist))
            }
        }

            View.OnClickListener {
                when (sp.getString("mode", "Music")) {
                    "Music" -> {
                        sp.edit().putString("mode", "Album").apply()
                        search_mode.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.mode_album
                            )
                        )
                    }

                    "Album" -> {
                        sp.edit().putString("mode", "Playlists").apply()
                        search_mode.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.mode_playlist
                            )
                        )
                    }

                    "Playlists" -> {
                        sp.edit().putString("mode", "Music").apply()
                        search_mode.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.mode_music
                            )
                        )
                    }
                }
            }.also {
                search_mode.setOnClickListener(it)
                search_mode_pr.setOnClickListener(it)
            }
            loading_view.enableMergePathsForKitKatAndAbove(true)
            getItems(view.findViewById(R.id.search_bar),view.findViewById(R.id.search_rv))
    }

    private fun getItems(searchBar:EditText, searchRv:RecyclerView){
            searchBar.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == 6) {
                    if(isInternetConnected()){
                        loading_view.progress = 0.3080229f
                        loading_view.playAnimation()

                        if (searchRv.visibility == View.VISIBLE) {
                            searchRv.animate().alpha(0f).duration = 200
                            searchRv.visibility = View.GONE
                        }
                        val text = searchBar.text
                        if (loading_view.visibility == View.GONE) {
                            loading_view.alpha = 0f
                            loading_view.visibility = View.VISIBLE
                            loading_view.animate().alpha(1f).duration = 200
                        }

                        hideKeyboard(activity as Activity)
                        val resultArray = ArrayList<Song>()

                        try {
                            var query = text.toString()
                            if (query.isEmpty())
                                query = "songs"
                            val useYtMusic: Boolean = when {
                                text.startsWith("!") -> {
                                    query = text.toString().replaceFirst(Regex("^!\\s*"), "")
                                    true
                                }

                                text.startsWith("?") -> {
                                    query = text.toString().replaceFirst(Regex("^?\\s*"), "")
                                    false
                                }

                                else -> (PreferenceManager.getDefaultSharedPreferences(requireContext())
                                    .getString("source_key", "Youtube Music") == "Youtube Music")
                            }

                            thread {
                                if (useYtMusic) {
                                    when (sp.getString("mode", "Music")) {
                                        "Music" -> {
                                            val extractor = YouTube.getSearchExtractor(
                                                query, singletonList(
                                                    YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
                                                ), ""
                                            )

                                            extractor.fetchPage()

                                            for (song in extractor.initialPage.items) {
                                                val ex = song as StreamInfoItem
                                                resultArray.add(
                                                    Song(
                                                        name = ex.name,
                                                        artist = ex.uploaderName,
                                                        youtubeLink = ex.url,
                                                        ytmThumbnail = song.thumbnailUrl
                                                    )
                                                )
                                            }
                                        }

                                        "Album" -> {
                                            val extractor = YouTube.getSearchExtractor(
                                                query, singletonList(
                                                    YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
                                                ), ""
                                            )

                                            extractor.fetchPage()

                                            for (song in extractor.initialPage.items) {
                                                val ex = song as PlaylistInfoItem
                                                resultArray.add(
                                                    Song(
                                                        name = ex.name,
                                                        artist = ex.uploaderName,
                                                        youtubeLink = ex.url,
                                                        ytmThumbnail = song.thumbnailUrl
                                                    )
                                                )
                                            }
                                        }

                                        "Playlists" -> {
                                            val extractor = if (query.startsWith("https://"))
                                                YouTube.getPlaylistExtractor(query)
                                            else
                                                YouTube.getSearchExtractor(
                                                    query, singletonList(
                                                        YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
                                                    ), ""
                                                )

                                            extractor.fetchPage()

                                            for (song in extractor.initialPage.items) {
                                                val ex = song as PlaylistInfoItem
                                                resultArray.add(
                                                    Song(
                                                        name = ex.name,
                                                        artist = ex.uploaderName,
                                                        youtubeLink = ex.url,
                                                        ytmThumbnail = song.thumbnailUrl
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val extractor = YouTube.getSearchExtractor(
                                        query, singletonList(
                                            YoutubeSearchQueryHandlerFactory.VIDEOS
                                        ), ""
                                    )

                                    extractor.fetchPage()

                                    for (song in extractor.initialPage.items) {
                                        val ex = song as StreamInfoItem
                                        resultArray.add(
                                            Song(
                                                name = ex.name,
                                                artist = ex.uploaderName,
                                                youtubeLink = ex.url,
                                                ytmThumbnail = song.thumbnailUrl
                                            )
                                        )
                                    }
                                }

                                activity?.runOnUiThread {
                                    if (useYtMusic)
                                        searchRv.adapter =
                                            YtmResultAdapter(
                                                resultArray,
                                                WeakReference(this@Search),
                                                sp.getString("mode", "Music") ?: "Music"
                                            )
                                    else
                                        searchRv.adapter =
                                            YtResultAdapter(resultArray, WeakReference(this@Search))
                                    searchRv.layoutManager = LinearLayoutManager(requireContext())
                                    loading_view.visibility = View.GONE
                                    loading_view.pauseAnimation()
                                    searchRv.alpha = 0f
                                    searchRv.visibility = View.VISIBLE
                                    searchRv.animate().alpha(1f).duration = 200
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Something failed!", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    else
                        Toast.makeText(requireContext(),"No Internet Connection", Toast.LENGTH_LONG).show()
                }
                false
            }
        }

    @Suppress("DEPRECATION")
    private fun isInternetConnected(): Boolean {
        var result = false
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
                false
            }
        }

    fun itemPressed(song: Song) {
        if(isInternetConnected())
            itemPressed.sendItem(song)
        else
            Toast.makeText(requireContext(),"No Internet Connection", Toast.LENGTH_LONG).show()
    }

    private fun hideKeyboard(activity: Activity) {
        val imm: InputMethodManager =
            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = activity.currentFocus ?: View(activity)

        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

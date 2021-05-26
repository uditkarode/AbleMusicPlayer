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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.YtResultAdapter
import io.github.uditkarode.able.adapters.YtmResultAdapter
import io.github.uditkarode.able.databinding.SearchBinding
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.utils.Shared
import io.github.uditkarode.able.utils.SwipeController
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.lang.ref.WeakReference
import java.util.Collections.singletonList

/**
 * The second fragment. Used to search for songs.
 */
@ExperimentalCoroutinesApi
class Search : Fragment(), CoroutineScope {
    private lateinit var itemPressed: SongCallback
    private lateinit var sp: SharedPreferences
    companion object {
        val resultArray = ArrayList<Song>()
    }
    interface SongCallback {
        fun sendItem(song: Song , mode:String = "")
    }
    
    override val coroutineContext = Dispatchers.Main + SupervisorJob()
    private var _binding: SearchBinding? = null

    private val binding get() = _binding!!

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SearchBinding.inflate(inflater, container, false)
        return binding.root
    }

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
                _binding!!.searchMode.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.mode_album))
            }

            "Playlists" -> {
                _binding!!.searchMode.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.mode_playlist))
            }
        }

        View.OnClickListener {
            when (sp.getString("mode", "Music")) {
                "Music" -> {
                    sp.edit().putString("mode", "Album").apply()
                    _binding!!.searchMode.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.mode_album
                        )
                    )
                }

                "Album" -> {
                    sp.edit().putString("mode", "Playlists").apply()
                    _binding!!.searchMode.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.mode_playlist
                        )
                    )
                }

                "Playlists" -> {
                    sp.edit().putString("mode", "Music").apply()
                    _binding!!.searchMode.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.mode_music
                        )
                    )
                }
            }
        }.also {
            _binding!!.searchMode.setOnClickListener(it)
            _binding!!.searchModePr.setOnClickListener(it)
        }
        _binding!!.loadingView.enableMergePathsForKitKatAndAbove(true)
        getItems(view.findViewById(R.id.search_bar),view.findViewById(R.id.search_rv))
    }

    private fun getItems(searchBar:EditText, searchRv:RecyclerView){
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == 6) {
                if(Shared.isInternetConnected(requireContext())){
                    _binding!!.loadingView.progress = 0.3080229f
                    _binding!!.loadingView.playAnimation()

                    if (searchRv.visibility == View.VISIBLE) {
                        searchRv.animate().alpha(0f).duration = 200
                        searchRv.visibility = View.GONE
                    }
                    val text = searchBar.text
                    if (_binding!!.loadingView.visibility == View.GONE) {
                        _binding!!.loadingView.alpha = 0f
                        _binding!!.loadingView.visibility = View.VISIBLE
                        _binding!!.loadingView.animate().alpha(1f).duration = 200
                    }

                    hideKeyboard(activity as Activity)
                    resultArray.clear()
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

                        launch(Dispatchers.IO) {
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
                                            if(song.thumbnailUrl.contains("ytimg")) {
                                                val songId = Shared.getIdFromLink(ex.url)
                                                song.thumbnailUrl = "https://i.ytimg.com/vi/$songId/maxresdefault.jpg"
                                            }
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
                                            if(song.thumbnailUrl.contains("ytimg")) {
                                                val songId = Shared.getIdFromLink(ex.url)
                                                song.thumbnailUrl = "https://i.ytimg.com/vi/$songId/maxresdefault.jpg"
                                            }
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
                                    if(song.thumbnailUrl.contains("ytimg")) {
                                        val songId = Shared.getIdFromLink(ex.url)
                                        song.thumbnailUrl = "https://i.ytimg.com/vi/$songId/maxresdefault.jpg"
                                    }
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

                            launch(Dispatchers.Main) {
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
                                _binding!!.loadingView.visibility = View.GONE
                                _binding!!.loadingView.pauseAnimation()
                                searchRv.alpha = 0f
                                searchRv.visibility = View.VISIBLE
                                searchRv.animate().alpha(1f).duration = 200
                                val itemTouchHelper= ItemTouchHelper(SwipeController(
                                    context,
                                    "Search",
                                    null
                                ))
                                itemTouchHelper.attachToRecyclerView(searchRv)
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

    fun itemPressed(song: Song) {
        if(Shared.isInternetConnected(requireContext()))
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
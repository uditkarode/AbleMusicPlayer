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
import android.graphics.Typeface
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.list.listItems
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.DiscographyAdapter
import io.github.uditkarode.able.adapters.DiscoverResultAdapter
import io.github.uditkarode.able.adapters.YtResultAdapter
import io.github.uditkarode.able.adapters.YtmResultAdapter
import io.github.uditkarode.able.databinding.SearchBinding
import io.github.uditkarode.able.model.discovery.AlbumSuggestion
import io.github.uditkarode.able.model.discovery.ArtistSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.utils.ApiKeyProvider
import io.github.uditkarode.able.utils.LastFmClient
import io.github.uditkarode.able.utils.LastFmKeyPrompt
import io.github.uditkarode.able.utils.Shared
import io.github.uditkarode.able.utils.SwipeController
import io.github.uditkarode.able.utils.TrackResolver
import io.github.uditkarode.able.utils.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.lang.ref.WeakReference
import java.util.Collections.singletonList

/**
 * The second fragment. Used to search for songs.
 */
class Search : Fragment(), CoroutineScope {
    private lateinit var itemPressed: SongCallback
    private lateinit var sp: SharedPreferences

    companion object {
        const val TAG = "Search"

        val resultArray = ArrayList<Song>()

        /** Last.fm suggestions (TrackSuggestion / ArtistSuggestion) shown in Discover mode. */
        val suggestArray = ArrayList<Any>()
    }

    interface SongCallback {
        fun sendItem(song: Song, mode: String = "")
    }

    override val coroutineContext = Dispatchers.Main + SupervisorJob()
    private var _binding: SearchBinding? = null

    /** Active swipe controller for YT/YTM results; detached for discovery lists. */
    private var searchItemTouchHelper: ItemTouchHelper? = null

    /** True while the artist discography drill-down is on screen. */
    private var inDrillDown = false

    /** Back handler: enabled only during the discography drill-down. */
    private var backCallback: OnBackPressedCallback? = null

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

        when (sp.getString("mode", "Music")) {
            "Album" -> {
                _binding!!.searchMode.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.mode_album
                    )
                )
            }

            "Playlists" -> {
                _binding!!.searchMode.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.mode_playlist
                    )
                )
            }

            "Discover" -> {
                _binding!!.searchMode.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.mode_discover
                    )
                )
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
                    sp.edit().putString("mode", "Discover").apply()
                    _binding!!.searchMode.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.mode_discover
                        )
                    )
                }

                "Discover" -> {
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
        _binding!!.loadingView.progress = 0.3080229f
        getItems(view.findViewById(R.id.search_bar), view.findViewById(R.id.search_rv))
        setupDiscoveryChips()

        // System back: during the artist drill-down it returns to the previous
        // list instead of leaving the app.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { exitDrill() }
        }.also { cb ->
            requireActivity().onBackPressedDispatcher
                .addCallback(viewLifecycleOwner, cb)
        }
        _binding!!.searchBack.setOnClickListener { exitDrill() }

        _binding!!.searchViewMode.apply {
            setImageResource(ViewMode.toggleIcon(requireContext()))
            setOnClickListener {
                ViewMode.toggle(requireContext())
                setImageResource(ViewMode.toggleIcon(requireContext()))
                renderResultsForViewMode()
            }
        }
    }

    /** Re-renders whichever list is on screen after a list/grid switch. */
    private fun renderResultsForViewMode() {
        val rv = _binding?.searchRv ?: return
        if (rv.visibility != View.VISIBLE) return
        val ctx = requireContext()
        rv.layoutManager = ViewMode.layoutManagerFor(ctx)
        when {
            suggestArray.isNotEmpty() ->
                rv.adapter = DiscoverResultAdapter(
                    suggestArray,
                    onTrackPressed = { onDiscoverTrackPressed(it) },
                    onArtistPressed = { onDiscoverArtistPressed(it) }
                )
            resultArray.isNotEmpty() && PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString("source_key", "Youtube Music") == "Youtube Music" ->
                rv.adapter = YtmResultAdapter(
                    resultArray, WeakReference(this@Search),
                    sp.getString("mode", "Music") ?: "Music"
                )
            resultArray.isNotEmpty() ->
                rv.adapter = YtResultAdapter(resultArray, WeakReference(this@Search))
        }
    }

    // region discovery chips (always visible, below the search bar)

    /** Global Last.fm sources shown as chips: only user-independent ones. */
    private fun setupDiscoveryChips() {
        val container = _binding!!.searchChips
        addChip(container, R.string.disc_top_tracks) { loadDiscoverTracks { LastFmClient.topTracks() } }
        addChip(container, R.string.disc_top_artists) { loadDiscoverArtists { LastFmClient.topArtists() } }
        addChip(container, R.string.disc_genres) { pickDiscoverGenre() }
        // Preload the first source so the tab isn't empty (cached for 30 min),
        // but only when discovery is usable — the auto-click would otherwise
        // pop the API-key prompt on every app start.
        if (suggestArray.isEmpty() && ApiKeyProvider.hasLastFmKey)
            container.getChildAt(0).performClick()
    }

    private fun addChip(container: LinearLayout, labelRes: Int, onClick: () -> Unit) {
        val chip = TextView(requireContext()).apply {
            text = getString(labelRes)
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            typeface = Typeface.createFromAsset(requireContext().assets, "fonts/intersemibold.otf")
            background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_rectangle)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                highlightChip(container, this)
                onClick()
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = dp(8)
        container.addView(chip, params)
    }

    private fun highlightChip(container: LinearLayout, selected: TextView) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as TextView
            child.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (child === selected) R.color.colorAccent else android.R.color.white
                )
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun loadDiscoverTracks(fetch: () -> List<TrackSuggestion>) {
        if (!ApiKeyProvider.hasLastFmKey) { LastFmKeyPrompt.showIfMissing(requireContext()); return }
        setDiscoverLoading(true)
        launch(Dispatchers.IO) {
            val suggestions = try { fetch() } catch (e: Exception) { emptyList<TrackSuggestion>() }
            launch(Dispatchers.Main) { showDiscoverSuggestions(ArrayList<Any>(suggestions)) }
        }
    }

    private fun loadDiscoverArtists(fetch: () -> List<ArtistSuggestion>) {
        if (!ApiKeyProvider.hasLastFmKey) { LastFmKeyPrompt.showIfMissing(requireContext()); return }
        setDiscoverLoading(true)
        launch(Dispatchers.IO) {
            val suggestions = try { fetch() } catch (e: Exception) { emptyList<ArtistSuggestion>() }
            launch(Dispatchers.Main) { showDiscoverSuggestions(ArrayList<Any>(suggestions)) }
        }
    }

    private fun pickDiscoverGenre() {
        if (!ApiKeyProvider.hasLastFmKey) { LastFmKeyPrompt.showIfMissing(requireContext()); return }
        setDiscoverLoading(true)
        launch(Dispatchers.IO) {
            val tags = try { LastFmClient.topTags() } catch (e: Exception) { emptyList<String>() }
            launch(Dispatchers.Main) {
                setDiscoverLoading(false)
                if (tags.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.disc_no_genres, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                MaterialDialog(requireContext(), BottomSheet()).show {
                    title(R.string.disc_genres)
                    listItems(items = tags) { _, index, _ ->
                        loadDiscoverTracks { LastFmClient.tagTopTracks(tags[index]) }
                    }
                }
            }
        }
    }

    private fun setDiscoverLoading(loading: Boolean) {
        _binding?.loadingView?.apply {
            if (loading) {
                progress = 0.3080229f
                visibility = View.VISIBLE
                playAnimation()
            } else {
                pauseAnimation()
                visibility = View.GONE
            }
        }
    }

    /** Renders discovery results on `search_rv`, disabling the search swipe controller. */
    private fun showDiscoverSuggestions(items: List<Any>) {
        setDiscoverLoading(false)
        val rv = _binding?.searchRv ?: return
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), R.string.disc_no_suggestions, Toast.LENGTH_SHORT).show()
            return
        }
        suggestArray.clear()
        suggestArray.addAll(items)
        searchItemTouchHelper?.attachToRecyclerView(null)
        rv.adapter = DiscoverResultAdapter(
            suggestArray,
            onTrackPressed = { onDiscoverTrackPressed(it) },
            onArtistPressed = { onDiscoverArtistPressed(it) }
        )
        rv.layoutManager = ViewMode.layoutManagerFor(requireContext())
        rv.alpha = 0f
        rv.visibility = View.VISIBLE
        rv.animate().alpha(1f).duration = 200
    }

    // endregion

    private fun getItems(searchBar: EditText, searchRv: RecyclerView) {
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == 6) {
                if (Shared.isInternetConnected(requireContext())) {
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

                        val discoverMode = sp.getString("mode", "Music") == "Discover"
                        launch(Dispatchers.IO) {
                            if (discoverMode) {
                                // No key configured yet → show the import prompt instead of searching.
                                if (!ApiKeyProvider.hasLastFmKey) {
                                    launch(Dispatchers.Main) {
                                        _binding?.loadingView?.apply {
                                            pauseAnimation()
                                            visibility = View.GONE
                                        }
                                        LastFmKeyPrompt.showIfMissing(requireContext())
                                    }
                                    return@launch
                                }
                                val suggestions = LastFmClient.searchTracks(query)
                                suggestArray.clear()
                                suggestArray.addAll(suggestions)
                            } else if (useYtMusic) {
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
                                            val thumbnailUrl = Shared.getBestThumbnail(song.thumbnails, ex.url)
                                            resultArray.add(
                                                Song(
                                                    name = ex.name,
                                                    artist = ex.uploaderName,
                                                    youtubeLink = ex.url,
                                                    ytmThumbnail = thumbnailUrl
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
                                            val thumbnailUrl = Shared.getBestThumbnail(song.thumbnails, ex.url)
                                            resultArray.add(
                                                Song(
                                                    name = ex.name,
                                                    artist = ex.uploaderName,
                                                    youtubeLink = ex.url,
                                                    ytmThumbnail = thumbnailUrl
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
                                            val thumbnailUrl = Shared.getBestThumbnail(song.thumbnails, ex.url)
                                            resultArray.add(
                                                Song(
                                                    name = ex.name,
                                                    artist = ex.uploaderName,
                                                    youtubeLink = ex.url,
                                                    ytmThumbnail = thumbnailUrl
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
                                    val ex = song as PlaylistInfoItem
                                    val thumbnailUrl = Shared.getBestThumbnail(song.thumbnails, ex.url)
                                    resultArray.add(
                                        Song(
                                            name = ex.name,
                                            artist = ex.uploaderName,
                                            youtubeLink = ex.url,
                                            ytmThumbnail = thumbnailUrl
                                        )
                                    )
                                }
                            }

                            launch(Dispatchers.Main) {
                                if (discoverMode) {
                                    if (suggestArray.isEmpty())
                                        Toast.makeText(
                                            requireContext(),
                                            R.string.disc_no_suggestions,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    searchRv.adapter = DiscoverResultAdapter(
                                        suggestArray,
                                        onTrackPressed = { onDiscoverTrackPressed(it) },
                                        onArtistPressed = { onDiscoverArtistPressed(it) }
                                    )
                                } else if (useYtMusic)
                                    searchRv.adapter =
                                        YtmResultAdapter(
                                            resultArray,
                                            WeakReference(this@Search),
                                            sp.getString("mode", "Music") ?: "Music"
                                        )
                                else
                                    searchRv.adapter =
                                        YtResultAdapter(resultArray, WeakReference(this@Search))
                                searchRv.layoutManager =
                                    ViewMode.layoutManagerFor(requireContext())
                                _binding!!.loadingView.visibility = View.GONE
                                _binding!!.loadingView.pauseAnimation()
                                searchRv.alpha = 0f
                                searchRv.visibility = View.VISIBLE
                                searchRv.animate().alpha(1f).duration = 200
                                if (!discoverMode) {
                                    val itemTouchHelper = ItemTouchHelper(
                                        SwipeController(
                                            context,
                                            "Search",
                                            null
                                        )
                                    )
                                    itemTouchHelper.attachToRecyclerView(searchRv)
                                    searchItemTouchHelper = itemTouchHelper
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Something failed!", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else
                    Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_LONG)
                        .show()
            }
            false
        }
    }

    fun itemPressed(song: Song) {
        if (Shared.isInternetConnected(requireContext()))
            itemPressed.sendItem(song)
        else
            Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_LONG).show()
    }

    /** Resolves a Last.fm suggestion on YouTube Music, then hands the Song to MainActivity. */
    private fun onDiscoverTrackPressed(track: TrackSuggestion) {
        if (!Shared.isInternetConnected(requireContext())) {
            Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_LONG).show()
            return
        }
        _binding?.let {
            it.loadingView.progress = 0.3080229f
            it.loadingView.visibility = View.VISIBLE
            it.loadingView.playAnimation()
        }
        launch(Dispatchers.IO) {
            val song = try { TrackResolver.resolve(track) } catch (e: Exception) { null }
            launch(Dispatchers.Main) {
                _binding?.let {
                    it.loadingView.pauseAnimation()
                    it.loadingView.visibility = View.GONE
                }
                if (song == null)
                    Toast.makeText(requireContext(), R.string.disc_yt_fail, Toast.LENGTH_SHORT).show()
                else
                    itemPressed(song)
            }
        }
    }

    /**
     * Drill-down: the artist's discography from Last.fm — albums newest first
     * (with release year), then a "Singles" section with leftover top tracks.
     */
    private fun onDiscoverArtistPressed(artist: ArtistSuggestion) = showDiscography(artist)

    private fun showDiscography(artist: ArtistSuggestion) {
        if (!ApiKeyProvider.hasLastFmKey) { LastFmKeyPrompt.showIfMissing(requireContext()); return }
        setDiscoverLoading(true)
        inDrillDown = true
        backCallback?.isEnabled = true
        _binding?.searchBack?.apply {
            text = getString(R.string.disc_back_to_artists)
            visibility = View.VISIBLE
        }
        launch(Dispatchers.IO) {
            val albums = try { LastFmClient.artistTopAlbums(artist.name) }
                catch (e: Exception) { Log.e(TAG, "Top albums failed", e); emptyList<AlbumSuggestion>() }
            val loaded = albums.mapNotNull { album ->
                try { LastFmClient.albumTracks(artist.name, album.name, album.image) }
                catch (e: Exception) { Log.e(TAG, "Album tracks failed for ${album.name}", e); null }
            }
            val albumTrackNames = loaded.flatMap { it.tracks }
                .map { it.name.lowercase() }
                .toSet()
            val singles = try { LastFmClient.artistTopTracks(artist.name) }
                catch (e: Exception) { emptyList<TrackSuggestion>() }
                .filter { it.name.lowercase() !in albumTrackNames }
            // Albums first, newest first; undated albums last (popularity order).
            val ordered = loaded.sortedWith(
                compareByDescending<AlbumSuggestion> { it.year != 0 }
                    .thenByDescending { it.year }
            )

            launch(Dispatchers.Main) {
                if (!inDrillDown) return@launch // user went back in the meantime
                setDiscoverLoading(false)
                val rv = _binding?.searchRv ?: return@launch
                if (ordered.isEmpty() && singles.isEmpty()) {
                    Toast.makeText(
                        requireContext(), R.string.disc_no_discography, Toast.LENGTH_SHORT
                    ).show()
                    exitDrill()
                    return@launch
                }
                searchItemTouchHelper?.attachToRecyclerView(null)
                rv.adapter = DiscographyAdapter(ordered, singles) {
                    onDiscoverTrackPressed(it)
                }
                rv.layoutManager = LinearLayoutManager(requireContext())
                rv.alpha = 0f
                rv.visibility = View.VISIBLE
                rv.animate().alpha(1f).duration = 200
            }
        }
    }

    /** Leaves the drill-down and restores the previous suggestions list. */
    private fun exitDrill() {
        inDrillDown = false
        backCallback?.isEnabled = false
        _binding?.searchBack?.visibility = View.GONE
        if (suggestArray.isEmpty()) return
        // Render from a copy: showDiscoverSuggestions clears its input first.
        val restored = ArrayList<Any>(suggestArray)
        val rv = _binding?.searchRv ?: return
        searchItemTouchHelper?.attachToRecyclerView(null)
        rv.adapter = DiscoverResultAdapter(
            restored,
            onTrackPressed = { onDiscoverTrackPressed(it) },
            onArtistPressed = { onDiscoverArtistPressed(it) }
        )
        rv.layoutManager = ViewMode.layoutManagerFor(requireContext())
        rv.alpha = 0f
        rv.visibility = View.VISIBLE
        rv.animate().alpha(1f).duration = 200
    }

    override fun onPause() {
        super.onPause()
        // The back callback is registered per-view-lifecycle; drop the drill
        // state so returning to the tab starts clean.
        inDrillDown = false
        backCallback?.isEnabled = false
        _binding?.searchBack?.visibility = View.GONE
    }

    private fun hideKeyboard(activity: Activity) {
        val imm: InputMethodManager =
            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = activity.currentFocus ?: View(activity)

        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
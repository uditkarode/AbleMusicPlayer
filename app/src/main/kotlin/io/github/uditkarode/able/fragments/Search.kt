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
import androidx.recyclerview.widget.ConcatAdapter
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
import io.github.uditkarode.able.model.MusicMode
import io.github.uditkarode.able.model.discovery.AlbumSuggestion
import io.github.uditkarode.able.model.discovery.ArtistSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.ApiKeyProvider
import io.github.uditkarode.able.utils.CatalogSearch
import io.github.uditkarode.able.utils.LastFmClient
import io.github.uditkarode.able.utils.LastFmKeyPrompt
import io.github.uditkarode.able.utils.Shared
import io.github.uditkarode.able.utils.normKey
import io.github.uditkarode.able.utils.SwipeController
import io.github.uditkarode.able.utils.TrackResolver
import io.github.uditkarode.able.utils.ViewMode
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
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

    /** Source (YT Music vs YouTube) resolved for the current Discover search;
     *  the hybrid list and its view-mode re-renders must stay consistent even
     *  if the preference changes while the results are on screen. */
    private var hybridYtMusic = true

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

        // The old raw-YouTube-Music "Music" mode IS the Discover cascade now:
        // migrate the stored preference and treat Discover as the default.
        if (sp.getString("mode", "") == "Music")
            sp.edit().putString("mode", "Discover").apply()

        when (sp.getString("mode", "Discover")) {
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
            // Cycle: Discover → Album → Playlists → Discover. The raw song
            // search IS Discover (catalog cascade + YouTube rows); raw
            // YouTube stays reachable via the "!" and "?" query prefixes.
            when (sp.getString("mode", "Discover")) {
                "Discover" -> {
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
        val useYtMusic = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getString("source_key", "Youtube Music") == "Youtube Music"
        when {
            suggestArray.isNotEmpty() && resultArray.isNotEmpty() ->
                // Hybrid Discover results: canonical catalog matches lead,
                // raw YouTube rows follow.
                rv.adapter = ConcatAdapter(discoverAdapter(), ytAdapter())
            suggestArray.isNotEmpty() ->
                rv.adapter = discoverAdapter()
            resultArray.isNotEmpty() && useYtMusic ->
                rv.adapter = YtmResultAdapter(
                    resultArray, WeakReference(this@Search),
                    sp.getString("mode", "Discover") ?: "Discover"
                )
            resultArray.isNotEmpty() ->
                rv.adapter = YtResultAdapter(resultArray, WeakReference(this@Search))
        }
    }

    /** Suggestions adapter with the standard Discover tap handlers. */
    private fun discoverAdapter(): DiscoverResultAdapter =
        DiscoverResultAdapter(
            suggestArray,
            onTrackPressed = { onDiscoverTrackPressed(it) },
            onArtistPressed = { onDiscoverArtistPressed(it) }
        )

    /** Adapter for the YouTube section of the Discover results; the source
     *  (YT Music vs YouTube) is the one resolved for the query, not the
     *  current preference. */
    private fun ytAdapter(): RecyclerView.Adapter<*> =
        if (hybridYtMusic)
            YtmResultAdapter(resultArray, WeakReference(this@Search), "Discover")
        else
            YtResultAdapter(resultArray, WeakReference(this@Search))

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
        // Chip suggestions and search results are exclusive views; dropping
        // the stale result list keeps the view-mode re-render unambiguous.
        resultArray.clear()
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

    // region raw YouTube/YT Music fetch helpers

    /**
     * One raw search page for [filter], mapped to playable rows. Accepts both
     * stream rows (songs/videos) and playlist rows (albums/playlists) — every
     * search filter yields a mix of item types. Replaces the old per-mode
     * branches, one of which cast its VIDEOS results to PlaylistInfoItem.
     */
    private fun ytRows(query: String, filter: String): List<Song> = try {
        val extractor = YouTube.getSearchExtractor(query, singletonList(filter), "")
        extractor.fetchPage()
        extractor.initialPage.items.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> Song(
                    name = item.name,
                    artist = item.uploaderName ?: "",
                    youtubeLink = item.url,
                    ytmThumbnail = Shared.getBestThumbnail(item.thumbnails, item.url)
                )
                is PlaylistInfoItem -> Song(
                    name = item.name,
                    artist = item.uploaderName ?: "",
                    youtubeLink = item.url,
                    ytmThumbnail = Shared.getBestThumbnail(item.thumbnails, item.url)
                )
                else -> null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "YT search '$query' [$filter] failed", e)
        emptyList()
    }

    /** Tracks of a pasted playlist URL (playlist contents are videos). */
    private fun playlistRows(url: String): List<Song> = try {
        val extractor = YouTube.getPlaylistExtractor(url)
        extractor.fetchPage()
        extractor.initialPage.items.mapNotNull { it as? StreamInfoItem }
            .map { ex ->
                Song(
                    name = ex.name,
                    artist = ex.uploaderName ?: "",
                    youtubeLink = ex.url,
                    ytmThumbnail = Shared.getBestThumbnail(ex.thumbnails, ex.url)
                )
            }
    } catch (e: Exception) {
        Log.e(TAG, "Playlist fetch failed", e)
        emptyList()
    }

    /**
     * YouTube Music artist cards (MUSIC_ARTISTS filter): the tier-4 source
     * for artists the catalogs don't know. Cards arrive as ChannelInfoItem;
     * auto-generated topic-channel suffixes ("greek - Topic") are stripped so
     * the card reads (and dedups) like a catalog one.
     */
    private fun ytArtistCards(query: String): List<ArtistSuggestion> = try {
        val extractor = YouTube.getSearchExtractor(
            query, singletonList(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS), ""
        )
        extractor.fetchPage()
        extractor.initialPage.items.mapNotNull { it as? ChannelInfoItem }
            .map { card ->
                ArtistSuggestion(
                    name = card.name.replace(
                        Regex("\\s+-\\s+topic\\s*$", RegexOption.IGNORE_CASE), ""
                    ).trim(),
                    image = Shared.getBestThumbnail(card.thumbnails, card.url)
                )
            }
    } catch (e: Exception) {
        Log.e(TAG, "YT artist cards failed", e)
        emptyList()
    }

    /**
     * The artist's YouTube Music albums and singles as a discography
     * (drill-down fallback when Last.fm doesn't know the artist or no key is
     * configured): a MUSIC_ALBUMS search, then parallel track enumeration of
     * the first 8 playlists so the cards expand inline like the Last.fm ones.
     * Blocking — invoke from a background dispatcher.
     */
    private suspend fun ytDiscography(artist: String): List<AlbumSuggestion> = try {
        val extractor = YouTube.getSearchExtractor(
            artist, singletonList(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), ""
        )
        extractor.fetchPage()
        val albums = extractor.initialPage.items.mapNotNull { it as? PlaylistInfoItem }.take(8)
        coroutineScope {
            albums.map { item ->
                async {
                    val tracks = try { playlistTracks(item.url) }
                        catch (e: Exception) {
                            Log.e(TAG, "Playlist tracks failed for ${item.name}", e)
                            emptyList<TrackSuggestion>()
                        }
                    AlbumSuggestion(
                        name = item.name,
                        artist = artist,
                        image = Shared.getBestThumbnail(item.thumbnails, item.url),
                        tracks = tracks
                    )
                }
            }.awaitAll()
        }
    } catch (e: Exception) {
        Log.e(TAG, "YT discography failed", e)
        emptyList()
    }

    /** Track list of one YouTube playlist (playlist contents are videos). */
    private fun playlistTracks(url: String): List<TrackSuggestion> {
        val extractor = YouTube.getPlaylistExtractor(url)
        extractor.fetchPage()
        return extractor.initialPage.items.mapNotNull { it as? StreamInfoItem }
            .map { ex ->
                TrackSuggestion(
                    name = ex.name,
                    artist = ex.uploaderName ?: "",
                    image = Shared.getBestThumbnail(ex.thumbnails, ex.url)
                )
            }
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

                        val discoverMode = sp.getString("mode", "Discover") == "Discover"
                        // NewPipe items are platform-typed Java objects: any
                        // unexpected null or extractor hiccup must surface as
                        // a toast, never as a process kill from an IO coroutine
                        // (the try/catch below can't catch launch-internal
                        // failures).
                        launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                            Log.e(TAG, "Search failed", e)
                            launch(Dispatchers.Main) {
                                _binding?.loadingView?.apply {
                                    pauseAnimation()
                                    visibility = View.GONE
                                }
                                Toast.makeText(
                                    requireContext(), "Something failed!", Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            if (!discoverMode)
                                // Stale suggestion lists (chips, a previous
                                // Discover search) must not leak into YT renders.
                                suggestArray.clear()
                            if (discoverMode) {
                                suggestArray.clear()
                                // Tier 1+2: catalog tracks + artist cards;
                                // tier 3: raw YouTube rows; tier 4: YT Music
                                // artist cards. All run in parallel; the
                                // relevance gate decides who leads the list.
                                val catalogDeferred = async { CatalogSearch.search(query) }
                                val ytDeferred = async {
                                    ytRows(
                                        query,
                                        if (useYtMusic) YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
                                        else YoutubeSearchQueryHandlerFactory.VIDEOS
                                    )
                                }
                                val ytArtistsDeferred = async { ytArtistCards(query) }
                                val catalog = catalogDeferred.await()
                                // Artist cards lead the canonical section,
                                // track matches follow in catalog rank order.
                                suggestArray.addAll(catalog.artists)
                                suggestArray.addAll(catalog.matches)
                                resultArray.clear()
                                resultArray.addAll(ytDeferred.await())
                                // Tier 4: YouTube Music artist cards for artists
                                // the catalogs don't know, deduped by normalized
                                // name (the topic-channel suffix is stripped in
                                // the card mapping).
                                val knownArtists = suggestArray
                                    .filterIsInstance<ArtistSuggestion>()
                                    .mapTo(HashSet()) { normKey(it.name) }
                                for (card in ytArtistsDeferred.await()) {
                                    if (normKey(card.name) in knownArtists) continue
                                    knownArtists.add(normKey(card.name))
                                    suggestArray.add(card)
                                }
                                hybridYtMusic = useYtMusic
                                Log.i(
                                    "ABLE>",
                                    "discover '$query': ${catalog.artists.size} artists," +
                                        " ${catalog.matches.size} track matches (first=" +
                                        "${catalog.matches.firstOrNull()?.let { it.artist + " - " + it.name }})" +
                                        ", yt=${resultArray.size} rows"
                                )
                            } else {
                                // Unified raw YouTube/YT Music fetch: one helper
                                // for every mode (the old per-mode branches
                                // duplicated the extractor dance, and the plain-
                                // YouTube branch cast its VIDEOS StreamInfoItems
                                // to PlaylistInfoItem — a guaranteed
                                // ClassCastException → "Something failed!").
                                val mode = sp.getString("mode", "Discover") ?: "Discover"
                                if (useYtMusic && mode == "Playlists" &&
                                    query.startsWith("https://"))
                                    resultArray.addAll(playlistRows(query))
                                else {
                                    val filter = when {
                                        !useYtMusic -> YoutubeSearchQueryHandlerFactory.VIDEOS
                                        mode == "Album" ->
                                            YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
                                        mode == "Playlists" ->
                                            YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
                                        else -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
                                    }
                                    resultArray.addAll(ytRows(query, filter))
                                }
                            }

                            launch(Dispatchers.Main) {
                                if (!discoverMode)
                                    Log.i(
                                        "ABLE>",
                                        "search '$query' → ${resultArray.size} rows " +
                                            "(${if (useYtMusic) "ytmusic" else "youtube"})"
                                    )
                                if (discoverMode) {
                                    when {
                                        // Hybrid: canonical catalog matches
                                        // lead, the raw YouTube rows follow.
                                        suggestArray.isNotEmpty() && resultArray.isNotEmpty() ->
                                            searchRv.adapter = ConcatAdapter(
                                                discoverAdapter(), ytAdapter()
                                            )
                                        suggestArray.isNotEmpty() ->
                                            searchRv.adapter = discoverAdapter()
                                        resultArray.isNotEmpty() ->
                                            searchRv.adapter = ytAdapter()
                                        else -> Toast.makeText(
                                            requireContext(),
                                            R.string.disc_no_suggestions,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else if (useYtMusic)
                                    searchRv.adapter =
                                        YtmResultAdapter(
                                            resultArray,
                                            WeakReference(this@Search),
                                            sp.getString("mode", "Discover") ?: "Discover"
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

    /** Resolves a suggestion on YouTube Music, then runs the action with the
     *  resolved Song (loading spinner while the resolution runs). */
    private fun resolveFor(track: TrackSuggestion, action: (Song) -> Unit) {
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
                    action(song)
            }
        }
    }

    /** Choice menu for a tapped discography track: play now / add to queue /
     *  download — the same UX as the Player's discography sheet. */
    private fun showTrackActionMenu(track: TrackSuggestion) {
        MaterialDialog(requireContext()).show {
            title(text = "${track.name} - ${track.artist}")
            listItems(items = listOf(
                getString(R.string.disc_play_now),
                getString(R.string.disc_add_to_queue),
                getString(R.string.disc_add_to_download)
            )) { _, index, _ ->
                when (index) {
                    0 -> resolveFor(track) { song ->
                        // Stream-mode send starts playback right away.
                        itemPressed.sendItem(song, MusicMode.stream)
                    }
                    1 -> resolveFor(track) { song ->
                        val svc = MusicService.instance
                        if (svc == null)
                            Toast.makeText(requireContext(), R.string.disc_play_first, Toast.LENGTH_SHORT).show()
                        else {
                            svc.addToPlayQueue(song)
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.disc_added_to_queue, song.name),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    2 -> resolveFor(track) { song ->
                        if (Shared.isInLibrary(Shared.getIdFromLink(song.youtubeLink))) {
                            Toast.makeText(requireContext(), R.string.dl_already_library, Toast.LENGTH_SHORT).show()
                        } else {
                            itemPressed.sendItem(song, MusicMode.download)
                        }
                    }
                }
            }
        }
    }

    /**
     * Drill-down: the artist's discography from Last.fm — albums newest first
     * (with release year), then a "Singles" section with leftover top tracks.
     */
    private fun onDiscoverArtistPressed(artist: ArtistSuggestion) = showDiscography(artist)

    private fun showDiscography(artist: ArtistSuggestion) {
        setDiscoverLoading(true)
        inDrillDown = true
        backCallback?.isEnabled = true
        _binding?.searchBack?.apply {
            text = getString(R.string.disc_back_to_artists)
            visibility = View.VISIBLE
        }
        launch(Dispatchers.IO) {
            coroutineScope {
                // Artist photo: the tapped suggestion often carries a real one
                // already (YTM card thumbnail / Deezer search) — use it and
                // resolve via Deezer only when blank. Runs next to the
                // discography fetches so it doesn't add latency.
                val imageDeferred = async {
                    if (artist.image.isNotBlank()) artist.image
                    else try { LastFmClient.resolveArtistImage(artist.name) }
                         catch (e: Exception) { Log.e(TAG, "Artist image failed", e); "" }
                }
                // Tier 1: the Last.fm discography — albums newest first (with
                // release year), then a "Singles" section with leftover top
                // tracks. Skipped entirely without an API key.
                var albums = emptyList<AlbumSuggestion>()
                var singles = emptyList<TrackSuggestion>()
                if (ApiKeyProvider.hasLastFmKey) {
                    try {
                        val loaded = LastFmClient.artistTopAlbums(artist.name).mapNotNull { album ->
                            try { LastFmClient.albumTracks(artist.name, album.name, album.image) }
                            catch (e: Exception) { Log.e(TAG, "Album tracks failed for ${album.name}", e); null }
                        }
                        val albumTrackNames = loaded.flatMap { it.tracks }
                            .map { it.name.lowercase() }
                            .toSet()
                        singles = try { LastFmClient.artistTopTracks(artist.name) }
                            catch (e: Exception) { emptyList<TrackSuggestion>() }
                            .filter { it.name.lowercase() !in albumTrackNames }
                        // Albums first, newest first; undated albums last (popularity order).
                        albums = loaded.sortedWith(
                            compareByDescending<AlbumSuggestion> { it.year != 0 }
                                .thenByDescending { it.year }
                        )
                    } catch (e: Exception) { Log.e(TAG, "Last.fm discography failed", e) }
                }
                // Tier 2: the artist's YouTube Music albums+singles playlists —
                // works keyless and for artists Last.fm doesn't know.
                if (albums.isEmpty() && singles.isEmpty()) albums = ytDiscography(artist.name)
                val artistImage = imageDeferred.await()
                Log.i(
                    "ABLE>",
                    "discography '${artist.name}': ${albums.size} albums, ${singles.size} singles"
                )

                launch(Dispatchers.Main) {
                    if (!inDrillDown) return@launch // user went back in the meantime
                    setDiscoverLoading(false)
                    val rv = _binding?.searchRv ?: return@launch
                    if (albums.isEmpty() && singles.isEmpty()) {
                        Toast.makeText(
                            requireContext(), R.string.disc_no_discography, Toast.LENGTH_SHORT
                        ).show()
                        exitDrill()
                        return@launch
                    }
                    searchItemTouchHelper?.attachToRecyclerView(null)
                    rv.adapter = DiscographyAdapter(
                        artist.name, artistImage, albums, singles
                    ) {
                        showTrackActionMenu(it)
                    }
                    rv.layoutManager = LinearLayoutManager(requireContext())
                    rv.alpha = 0f
                    rv.visibility = View.VISIBLE
                    rv.animate().alpha(1f).duration = 200
                }
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
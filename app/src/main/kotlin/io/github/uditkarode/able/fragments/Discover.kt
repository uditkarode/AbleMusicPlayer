/*
    Copyright 2025 AbleMusicPlayer contributors

    This file is part of AbleMusicPlayer.

    AbleMusicPlayer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    AbleMusicPlayer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AbleMusicPlayer.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.github.uditkarode.able.fragments

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.DiscoverResultAdapter
import io.github.uditkarode.able.databinding.DiscoverBinding
import io.github.uditkarode.able.model.Playlist
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.utils.ApiKeyProvider
import io.github.uditkarode.able.utils.LastFmClient
import io.github.uditkarode.able.utils.LastFmKeyPrompt
import io.github.uditkarode.able.utils.Shared
import io.github.uditkarode.able.utils.TrackResolver
import io.github.uditkarode.able.utils.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Fifth tab. Playlist-based discovery: shows the user's playlists; picking
 * one generates track suggestions seeded by the tracks in that playlist
 * (Last.fm "similar tracks" aggregated and ranked).
 */
class Discover : Fragment(), CoroutineScope {
    private lateinit var itemPressed: Search.SongCallback
    private var _binding: DiscoverBinding? = null
    private val binding get() = _binding!!

    /** Last rendered suggestions, used to rebuild the list on view-mode toggle. */
    private var suggestions: List<TrackSuggestion> = emptyList()

    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    companion object {
        private const val TAG = "Discover"

        /** How many playlist tracks to use as seeds. */
        private const val SEEDS = 5

        /** Suggestions kept per seed track. */
        private const val PER_SEED = 5

        /** Max suggestions shown. */
        private const val MAX_RESULTS = 30
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            itemPressed = context as Activity as Search.SongCallback
        } catch (e: ClassCastException) {
            throw ClassCastException(
                activity.toString()
                        + " must implement SongCallback"
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.discoverBack.setOnClickListener { showPlaylists() }
        binding.discoverViewMode.apply {
            setImageResource(ViewMode.toggleIcon(requireContext()))
            setOnClickListener {
                ViewMode.toggle(requireContext())
                setImageResource(ViewMode.toggleIcon(requireContext()))
                applySuggestionViewMode()
            }
        }
        showPlaylists()
    }

    // region playlist list

    private fun showPlaylists() {
        val playlists = Shared.getPlaylists()
        binding.discoverBack.visibility = View.GONE
        binding.discoverViewMode.visibility = View.GONE
        if (playlists.isEmpty()) {
            showEmpty(getString(R.string.disc_no_playlists))
            return
        }
        val rv = binding.discoverRv
        rv.adapter = PlaylistPickAdapter(playlists) { pick(it) }
        rv.layoutManager = LinearLayoutManager(requireContext())
        binding.discoverEmpty.visibility = View.GONE
        rv.alpha = 0f
        rv.visibility = View.VISIBLE
        rv.animate().alpha(1f).duration = 200
    }

    /** Seed track list for a playlist: spread evenly across it. */
    private fun seedsFor(songs: List<Song>): List<Song> {
        val usable = songs.filter { it.artist.isNotBlank() && it.name.isNotBlank() }
        return if (usable.size <= SEEDS) usable
        else (0 until SEEDS).map { usable[it * usable.size / SEEDS] }
    }

    // endregion

    // region suggestions

    private fun pick(playlist: Playlist) {
        if (!Shared.isInternetConnected(requireContext())) {
            Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_LONG).show()
            return
        }
        if (!ApiKeyProvider.hasLastFmKey) {
            LastFmKeyPrompt.showIfMissing(requireContext())
            return
        }
        setLoading(true)
        binding.discoverBack.visibility = View.VISIBLE
        launch(Dispatchers.IO) {
            val songs = Shared.getSongsFromPlaylist(playlist)
            val playlistKeys = songs.map { it.name.lowercase() + "|" + it.artist.lowercase() }.toSet()
            val aggregated = LinkedHashMap<String, Pair<TrackSuggestion, Int>>()
            for (seed in seedsFor(songs)) {
                val similar = try {
                    LastFmClient.similarTracks(seed.artist, seed.name)
                } catch (e: Exception) {
                    Log.e(TAG, "Similar tracks failed for ${seed.name}", e); emptyList()
                }
                for (suggestion in similar.take(PER_SEED)) {
                    val key = suggestion.name.lowercase() + "|" + suggestion.artist.lowercase()
                    if (key in playlistKeys) continue
                    val hit = aggregated[key]
                    if (hit == null) aggregated[key] = suggestion to 1
                    else aggregated[key] = hit.first to hit.second + 1
                }
            }
            val results = aggregated.values
                .sortedWith(
                    compareByDescending<Pair<TrackSuggestion, Int>> { it.second }
                        .thenByDescending { it.first.listeners.toLongOrNull() ?: 0 }
                )
                .take(MAX_RESULTS)
                .map { it.first }

            launch(Dispatchers.Main) {
                if (songs.isEmpty()) {
                    binding.discoverBack.visibility = View.GONE
                    showEmpty(getString(R.string.disc_playlist_unusable))
                    return@launch
                }
                if (results.isEmpty()) {
                    showEmpty(getString(R.string.disc_no_suggestions))
                    return@launch
                }
                showSuggestions(results)
            }
        }
    }

    private fun showSuggestions(items: List<TrackSuggestion>) {
        setLoading(false)
        suggestions = items
        val rv = binding.discoverRv
        binding.discoverEmpty.visibility = View.GONE
        binding.discoverViewMode.visibility = View.VISIBLE
        rv.adapter = DiscoverResultAdapter(
            ArrayList<Any>(items),
            onTrackPressed = ::onTrackPressed,
            onArtistPressed = { }
        )
        rv.layoutManager = ViewMode.layoutManagerFor(requireContext())
        rv.alpha = 0f
        rv.visibility = View.VISIBLE
        rv.animate().alpha(1f).duration = 200
    }

    /** (Re)builds the suggestions adapter + layout manager for the current view mode. */
    private fun applySuggestionViewMode() {
        if (_binding == null || suggestions.isEmpty()) return
        val rv = binding.discoverRv
        if (rv.visibility != View.VISIBLE) return
        rv.layoutManager = ViewMode.layoutManagerFor(requireContext())
        rv.adapter = DiscoverResultAdapter(
            ArrayList<Any>(suggestions),
            onTrackPressed = ::onTrackPressed,
            onArtistPressed = { }
        )
    }

    private fun showEmpty(message: String) {
        setLoading(false)
        binding.discoverRv.visibility = View.GONE
        binding.discoverEmpty.text = message
        binding.discoverEmpty.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        val lv = binding.discoverLoading
        if (loading) {
            lv.progress = 0.3080229f
            lv.visibility = View.VISIBLE
            lv.playAnimation()
            binding.discoverEmpty.visibility = View.GONE
        } else {
            lv.visibility = View.GONE
            lv.pauseAnimation()
        }
    }

    /** Resolves the Last.fm suggestion on YouTube Music, then plays it via MainActivity. */
    private fun onTrackPressed(track: TrackSuggestion) {
        if (!Shared.isInternetConnected(requireContext())) {
            Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_LONG).show()
            return
        }
        setLoading(true)
        launch(Dispatchers.IO) {
            val song = try { TrackResolver.resolve(track) } catch (e: Exception) {
                Log.e(TAG, "Resolution failed for ${track.name}", e); null
            }
            launch(Dispatchers.Main) {
                setLoading(false)
                if (song == null)
                    Toast.makeText(requireContext(), R.string.disc_yt_fail, Toast.LENGTH_SHORT).show()
                else
                    itemPressed.sendItem(song)
            }
        }
    }

    // endregion

}

/**
 * Renders the user's playlists (row layout shared with the Playlists tab);
 * tapping one seeds the suggestions.
 */
private class PlaylistPickAdapter(
    private var playlists: ArrayList<Playlist>,
    private val onPicked: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistPickAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.playlist_item, parent, false)
        )

    override fun getItemCount() = playlists.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val playlist = playlists[position]
        val context = holder.itemView.context
        holder.name.text = playlist.name
        holder.count.text = context.getString(
            R.string.song_count, Shared.getSongsFromPlaylist(playlist).size
        )
        holder.name.typeface =
            Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
        holder.count.typeface =
            Typeface.createFromAsset(context.assets, "fonts/inter.otf")
        holder.itemView.setOnClickListener { onPicked(playlist) }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.playlist_name)
        val count: TextView = itemView.findViewById(R.id.number_songs)
    }
}

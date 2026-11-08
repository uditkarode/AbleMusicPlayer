/*
    Copyright 2025 AbleMusicPlayer contributors

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

package io.github.uditkarode.able.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.uditkarode.able.R
import io.github.uditkarode.able.model.discovery.AlbumSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.utils.DeezerClient
import io.github.uditkarode.able.utils.LastFmClient
import io.github.uditkarode.able.utils.LazyArtLoader

/**
 * Renders an artist's discography: a hero header (artist photo with the
 * name overlaid over a darkening gradient), then collapsed album headers
 * (art + title + year + track count, tap to expand/collapse the tracks
 * inline, newest first) followed by a "Singles" section with the leftover
 * top tracks. Always a vertical list — section headers would be confusing
 * in a grid.
 */
class DiscographyAdapter(
    private val artistName: String,
    private val artistImage: String,
    private val albums: List<AlbumSuggestion>,
    private val singles: List<TrackSuggestion>,
    private val onTrackPressed: (TrackSuggestion) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Resolves placeholder/missing art in the background — Last.fm answers
     *  many discography queries with its gray placeholder, and showing
     *  default art forever is not an answer (see [LazyArtLoader]). */
    private val artLoader = LazyArtLoader()

    /** Album names currently expanded inline. */
    private val expanded = HashSet<String>()

    /** Flat render list, rebuilt when an album toggles. */
    private val rows = ArrayList<Any>()

    init {
        rebuild()
    }

    companion object {
        private const val TYPE_ARTIST_HEADER = 0
        private const val TYPE_ALBUM = 1
        private const val TYPE_TRACK = 2
        private const val TYPE_SINGLES_HEADER = 3

        /** Sentinel rows. Companion-scoped so they are initialized before
         *  any instance `init` block uses them. */
        private val ARTIST_MARKER = Any()
        private val SINGLES_MARKER = Any()
    }

    private fun rebuild() {
        rows.clear()
        rows.add(ARTIST_MARKER)
        for (album in albums) {
            rows.add(album)
            if (album.name in expanded) rows.addAll(album.tracks)
        }
        if (singles.isNotEmpty()) {
            rows.add(SINGLES_MARKER)
            rows.addAll(singles)
        }
    }

    override fun getItemViewType(position: Int): Int = when {
        rows[position] === ARTIST_MARKER -> TYPE_ARTIST_HEADER
        rows[position] === SINGLES_MARKER -> TYPE_SINGLES_HEADER
        rows[position] is AlbumSuggestion -> TYPE_ALBUM
        else -> TYPE_TRACK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ARTIST_HEADER ->
                ArtistVH(inflater.inflate(R.layout.rv_artist_header, parent, false))
            TYPE_ALBUM -> AlbumVH(inflater.inflate(R.layout.rv_album_header, parent, false))
            TYPE_SINGLES_HEADER ->
                SinglesVH(inflater.inflate(R.layout.rv_section_header, parent, false))
            else -> TrackVH(inflater.inflate(R.layout.rv_ytm_result, parent, false))
        }
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holderRaw: RecyclerView.ViewHolder, position: Int) {
        val context = holderRaw.itemView.context
        holderRaw.itemView.tag = position
        when (val item = rows[position]) {
            ARTIST_MARKER -> {
                val holder = holderRaw as ArtistVH
                holder.name.text = artistName
                holder.name.typeface =
                    Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
                if (artistImage.isNotBlank() && !LastFmClient.isPlaceholderImage(artistImage)) {
                    holder.image.visibility = View.VISIBLE
                    holder.scrim.visibility = View.VISIBLE
                    Glide.with(context)
                        .load(artistImage)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.image)
                } else {
                    holder.image.visibility = View.GONE
                    holder.scrim.visibility = View.GONE
                }
            }

            is AlbumSuggestion -> {
                val holder = holderRaw as AlbumVH
                holder.name.text = item.name
                holder.name.typeface =
                    Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
                holder.year.text = buildString {
                    if (item.year != 0) append(item.year) else append(item.releaseDate)
                    append(" • ")
                    append(item.tracks.size)
                }
                holder.year.typeface =
                    Typeface.createFromAsset(context.assets, "fonts/inter.otf")
                holder.chevron.text =
                    if (item.name in expanded) "▾" else "▸"
                holder.itemView.setOnClickListener {
                    if (item.name in expanded) expanded.remove(item.name)
                    else expanded.add(item.name)
                    rebuild()
                    notifyDataSetChanged()
                }
                if (item.image.isNotBlank() && !LastFmClient.isPlaceholderImage(item.image)) {
                    holder.artProgress.visibility = View.GONE
                    Glide.with(context)
                        .load(item.image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.art)
                } else {
                    // Placeholder/blank: resolve the real cover from Deezer
                    // (the Player's hi-res path). The winner also feeds the
                    // album's track rows, which carry the same Last.fm image.
                    artLoader.load(
                        holder.itemView, holder.art, holder.artProgress, position,
                        "al:${item.artist}|${item.name}", item.image,
                        resolve = { DeezerClient.albumCover(item.artist, item.name) },
                        onResult = { url ->
                            item.image = url
                            item.tracks.forEach { track ->
                                if (track.image.isBlank() || LastFmClient.isPlaceholderImage(track.image))
                                    track.image = url
                            }
                        }
                    )
                }
            }

            is TrackSuggestion -> {
                val holder = holderRaw as TrackVH
                holder.name.text = item.name
                holder.name.typeface =
                    Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
                holder.uploader.typeface =
                    Typeface.createFromAsset(context.assets, "fonts/inter.otf")
                holder.uploader.text = item.artist
                holder.itemView.setOnClickListener { onTrackPressed(item) }
                if (item.image.isNotBlank() && !LastFmClient.isPlaceholderImage(item.image)) {
                    holder.artProgress.visibility = View.GONE
                    Glide.with(context)
                        .load(item.image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.songAlbumArt)
                } else {
                    // Same resolution path as the search list: persistent
                    // ArtworkCache, rate-limit aware.
                    artLoader.load(
                        holder.itemView, holder.songAlbumArt, holder.artProgress, position,
                        "t:${item.artist}|${item.name}", item.image,
                        resolve = { LastFmClient.resolveTrackImage(item.artist, item.name) },
                        onResult = { item.image = it }
                    )
                }
            }
        }
    }

    class ArtistVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.artist_header_image)
        val scrim: View = itemView.findViewById(R.id.artist_header_scrim)
        val name: TextView = itemView.findViewById(R.id.artist_header_name)
    }

    class AlbumVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.album_name)
        val year: TextView = itemView.findViewById(R.id.album_year)
        val chevron: TextView = itemView.findViewById(R.id.album_chevron)
        val art: ImageView = itemView.findViewById(R.id.album_art)
        val artProgress: android.widget.ProgressBar =
            itemView.findViewById(R.id.album_art_progress)
    }

    class SinglesVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.section_title)
    }

    class TrackVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.vid_song)
        val uploader: TextView = itemView.findViewById(R.id.vid_uploader)
        val songAlbumArt: ImageView = itemView.findViewById(R.id.vid_albart)
        val artProgress: android.widget.ProgressBar =
            itemView.findViewById(R.id.vid_art_progress)
    }
}

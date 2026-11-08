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

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.github.uditkarode.able.R
import io.github.uditkarode.able.model.discovery.ArtistSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.utils.LastFmClient
import io.github.uditkarode.able.utils.LazyArtLoader
import io.github.uditkarode.able.utils.ViewMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Renders Last.fm discovery suggestions (tracks and artists) reusing the
 * `rv_ytm_result` row layout or the grid card. Items is a heterogeneous
 * [List] of [TrackSuggestion] / [ArtistSuggestion]. Artwork is resolved
 * lazily per row via [LazyArtLoader].
 */
class DiscoverResultAdapter(
    private val items: List<Any>,
    private val onTrackPressed: (TrackSuggestion) -> Unit,
    private val onArtistPressed: (ArtistSuggestion) -> Unit
) : RecyclerView.Adapter<DiscoverResultAdapter.RVVH>() {

    private val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())
    private val artLoader = LazyArtLoader()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RVVH =
        RVVH(
            LayoutInflater.from(parent.context).inflate(
                if (ViewMode.isGrid(parent.context)) R.layout.rv_grid_result else R.layout.rv_ytm_result,
                parent, false
            )
        )

    override fun getItemCount() = items.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RVVH, position: Int) {
        val context = holder.itemView.context
        val current = items[position]
        holder.itemView.tag = position

        holder.songName.typeface =
            Typeface.createFromAsset(context.assets, "fonts/interbold.otf")
        holder.songUploader.typeface =
            Typeface.createFromAsset(context.assets, "fonts/inter.otf")

        when (current) {
            is ArtistSuggestion -> {
                holder.songName.text = current.name
                val listeners = current.listeners.toLongOrNull()
                val listenersTxt = if (listeners != null)
                    " • " + numberFormat.format(listeners)
                else ""
                holder.songUploader.text = "Artist$listenersTxt"
                holder.itemView.setOnClickListener { onArtistPressed(current) }
                artLoader.load(
                    holder.itemView, holder.songAlbumArt, holder.artProgress, position,
                    "a:${current.name}", current.image,
                    resolve = { LastFmClient.resolveArtistImage(current.name) },
                    onResult = { current.image = it }
                )
            }

            is TrackSuggestion -> {
                holder.songName.text = current.name
                holder.songUploader.text = current.artist
                holder.itemView.setOnClickListener { onTrackPressed(current) }
                artLoader.load(
                    holder.itemView, holder.songAlbumArt, holder.artProgress, position,
                    "t:${current.artist}|${current.name}", current.image,
                    resolve = { LastFmClient.resolveTrackImage(current.artist, current.name) },
                    onResult = { current.image = it }
                )
            }

            else -> {
                holder.itemView.setOnClickListener(null)
                holder.artProgress.visibility = View.GONE
                Glide.with(context).load(R.drawable.def_albart).into(holder.songAlbumArt)
            }
        }
    }

    class RVVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songName = itemView.findViewById<TextView>(R.id.vid_song)!!
        val songUploader = itemView.findViewById<TextView>(R.id.vid_uploader)!!
        val songAlbumArt = itemView.findViewById<ImageView>(R.id.vid_albart)!!
        val artProgress = itemView.findViewById<android.widget.ProgressBar>(R.id.vid_art_progress)!!
    }
}

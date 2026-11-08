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

package io.github.uditkarode.able.utils

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.uditkarode.able.R

/**
 * Lazy artwork loading for adapter rows: Last.fm often answers list methods
 * with a generic placeholder, so rows without a real cover show a spinner and
 * resolve the true artwork in the background ([LastFmClient.resolveTrackImage]
 * / `resolveArtistImage`, persistent-cache and rate-limit aware).
 *
 * One instance per adapter: it owns the small worker pool and the in-flight
 * dedup set. Anti-recycling is handled by comparing `itemView.tag` (expected
 * to hold the bound position) before applying the result.
 */
class LazyArtLoader {

    private val resolver = java.util.concurrent.Executors.newFixedThreadPool(2)

    /** Keys of items currently being resolved (avoids duplicate lookups). */
    private val inFlight = HashSet<String>()

    /**
     * Loads [artUrl]; when blank or a Last.fm placeholder, shows
     * `def_albart` + the spinner and resolves the real artwork in the
     * background, then applies it if the holder still shows the same row.
     *
     * @param key dedup identity of the item (e.g. "artist|track"), or null
     *   when the item has nothing to resolve from.
     * @param resolve the actual background lookup, returning the resolved URL
     *   ("" when none).
     * @param onResult called with the resolved URL to update the item model.
     */
    fun load(
        itemView: View,
        art: ImageView,
        spinner: ProgressBar,
        position: Int,
        key: String?,
        artUrl: String,
        resolve: () -> String,
        onResult: (String) -> Unit = {}
    ) {
        val context = itemView.context
        if (artUrl.isNotBlank() && !LastFmClient.isPlaceholderImage(artUrl)) {
            spinner.visibility = View.GONE
            Glide.with(context)
                .load(artUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(art)
            return
        }
        if (key == null) {
            spinner.visibility = View.GONE
            art.setImageResource(R.drawable.def_albart)
            return
        }
        // Needs resolution: placeholder now, spinner on, background lookup.
        art.setImageResource(R.drawable.def_albart)
        spinner.visibility = View.VISIBLE
        if (!inFlight.add(key)) return // already resolving elsewhere
        resolver.execute {
            val url = try { resolve() } catch (e: Exception) {
                android.util.Log.e("LazyArtLoader", "Artwork resolution failed", e)
                ""
            }
            if (url.isNotBlank()) onResult(url)
            inFlight.remove(key)
            itemView.post {
                if (itemView.tag == position && itemView.parent != null) {
                    spinner.visibility = View.GONE
                    if (url.isNotBlank())
                        Glide.with(context)
                            .load(url)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(art)
                }
            }
        }
    }
}

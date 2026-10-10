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

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent cache of resolved Last.fm artwork URLs, so covers already seen
 * load instantly even after an app restart. Stores `artist|track → url`
 * (tracks) and `name → url` (artists) pairs in SharedPreferences with FIFO
 * eviction once [MAX_ENTRIES] is reached.
 */
object ArtworkCache {
    private const val PREFS_FILE = "artwork_cache"
    private const val KEY_ORDER = "keys_order"
    private const val MAX_ENTRIES = 500

    /** Marker stored for items Last.fm has no real artwork for. */
    private const val MISSING = "\u0000missing"

    private lateinit var prefs: SharedPreferences

    /** Insertion order of the cache keys (oldest first), for FIFO eviction. */
    private val order = ArrayList<String>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        synchronized(order) {
            order.clear()
            order.addAll(prefs.getStringSet(KEY_ORDER, emptySet()).orEmpty())
        }
    }

    /** Artwork URL for a track seed, or null when not cached. */
    fun trackImage(artist: String, track: String): String? =
        get("t:${key(artist, track)}")

    /** Artwork URL for an artist, or null when not cached. */
    fun artistImage(name: String): String? = get("a:${key(name)}")

    fun putTrack(artist: String, track: String, url: String) =
        put("t:${key(artist, track)}", url)

    fun putArtist(name: String, url: String) = put("a:${key(name)}", url)

    private fun key(vararg parts: String) =
        parts.joinToString("|") { it.trim().lowercase() }

    /**
     * Returns the cached URL, `""` for a known "no artwork" entry (negative
     * caching — avoids re-querying items without art) or `null` when unknown.
     */
    private fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return if (stored == MISSING) "" else stored
    }

    private fun put(key: String, url: String) {
        prefs.edit().putString(key, url.ifBlank { MISSING }).apply()
        synchronized(order) {
            order.remove(key)
            order.add(key)
            while (order.size > MAX_ENTRIES) {
                val evicted = order.removeAt(0)
                prefs.edit().remove(evicted).apply()
            }
            prefs.edit().putStringSet(KEY_ORDER, order.toSet()).apply()
        }
    }
}

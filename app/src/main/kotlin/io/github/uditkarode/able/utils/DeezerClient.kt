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

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Minimal Deezer public API client (https://api.deezer.com — keyless).
 *
 * Two roles:
 *  - cover-art upgrade for the Player: the album identity comes from Last.fm
 *    (exact artist+track match via track.getInfo) and this client fetches a
 *    high-res cover of that already-identified album. For *that* purpose
 *    Deezer's own search must never identify the album — it is
 *    popularity-ranked and routinely returns compilations instead of the
 *    studio album.
 *  - keyless catalog search for the Discover search mode ([searchTracks],
 *    popularity-ranked text search) and the artist radio fallback
 *    ([artistRadio]) for artists Last.fm doesn't know.
 *
 * All calls are synchronous — invoke from a background dispatcher.
 */
object DeezerClient {
    private const val TAG = "DeezerClient"
    private const val BASE = "https://api.deezer.com"

    /** Cache TTL for search/radio payloads: 30 minutes (as in LastFmClient). */
    private const val CACHE_TTL_MS = 30L * 60 * 1000

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Simple in-memory cache: request key -> (payload, timestamp). */
    private val cache = HashMap<String, Pair<String, Long>>()

    /**
     * Catalog text search (`/search/track`): popularity-ranked canonical
     * titles with real album art. Feeds the Discover search mode together
     * with Last.fm's track.search — here the ranking is trustworthy, the
     * top hit is the studio song, not a live/remix upload.
     * Blocking — invoke from a background dispatcher.
     */
    fun searchTracks(query: String, limit: Int = 10): List<TrackSuggestion> {
        if (query.isBlank()) return emptyList()
        val body = cachedJson(
            "track:$query:$limit",
            "$BASE/search/track?q=${URLEncoder.encode(query, "UTF-8")}&limit=$limit"
        ) ?: return emptyList()
        return try {
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
                .mapNotNull { it.asObjectOrNull() }
                .mapNotNull { entry ->
                    val title = entry.stringField("title") ?: return@mapNotNull null
                    val artist = entry.objField("artist")?.stringField("name").orEmpty()
                    if (title.isBlank() || artist.isBlank()) return@mapNotNull null
                    TrackSuggestion(
                        name = title,
                        artist = artist,
                        image = entry.objField("album")?.let { album ->
                            album.stringField("cover_big") ?: album.stringField("cover_medium")
                        }.orEmpty()
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Deezer track search parse failed", e)
            emptyList()
        }
    }

    /**
     * Track mix for [name] via `search/artist` + `/artist/{id}/radio`: the
     * artist's own songs plus songs by related artists. Deezer-side fallback
     * for artists Last.fm doesn't know. Returns an empty list when Deezer
     * has no such artist. Blocking — invoke from a background dispatcher.
     */
    fun artistRadio(name: String): List<TrackSuggestion> {
        if (name.isBlank()) return emptyList()
        val artistId = artistId(name) ?: return emptyList()
        val body = cachedJson("radio:$artistId", "$BASE/artist/$artistId/radio")
            ?: return emptyList()
        return try {
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
                .mapNotNull { it.asObjectOrNull() }
                .mapNotNull { entry ->
                    val title = entry.stringField("title") ?: return@mapNotNull null
                    val artist = entry.objField("artist")?.stringField("name").orEmpty()
                    if (title.isBlank() || artist.isBlank()) return@mapNotNull null
                    TrackSuggestion(
                        name = title,
                        artist = artist,
                        image = entry.objField("album")?.stringField("cover_medium").orEmpty()
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Deezer artist radio parse failed", e)
            emptyList()
        }
    }

    /** First search hit for an artist name, or null when Deezer has none. */
    private fun artistId(name: String): Long? {
        val body = cachedJson(
            "artist:$name",
            "$BASE/search/artist?q=${URLEncoder.encode(name, "UTF-8")}&limit=1"
        ) ?: return null
        return try {
            val data = JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
            data.firstOrNull()?.asObjectOrNull()?.get("id")?.takeIf { it.isJsonPrimitive }?.asLong
        } catch (e: Exception) {
            Log.e(TAG, "Deezer artist search parse failed", e)
            null
        }
    }

    /**
     * Highest-resolution cover URL for [album] by [artist]. The API serves
     * `cover_xl` at 1000x1000; the CDN pattern accepts larger constructed
     * sizes (1400x1400 verified working, 2000x2000 rejected with 403), with
     * an automatic downgrade when a bigger size ever stops resolving.
     * Returns "" when the album can't be found. Blocking.
     */
    fun albumCover(artist: String, album: String): String {
        if (artist.isBlank() || album.isBlank()) return ""
        return try {
            val query = URLEncoder.encode("$artist \"$album\"", "UTF-8")
            val data = http.newCall(
                Request.Builder().url("$BASE/search/album?q=$query").build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                val body = resp.body?.string() ?: return ""
                JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
            }
            if (data == null || data.size() == 0) return ""
            val xl = data.get(0).asJsonObject.get("cover_xl")?.asString ?: return ""
            val hiRes = xl.replace("1000x1000", "1400x1400")
            if (headOk(hiRes)) hiRes else xl
        } catch (e: Exception) {
            Log.e(TAG, "Album cover lookup failed", e)
            ""
        }
    }

    /**
     * Real artist photo via Deezer's keyless `search/artist` — `picture_xl`
     * is a genuine 1000x1000 photo. Needed because Last.fm's artist.getinfo
     * no longer serves artist photos (every size comes back as the gray
     * placeholder).
     *
     * Returns null when the call failed transiently (the caller should NOT
     * cache that), "" when Deezer simply has no match (safe to cache as
     * "no photo"). Blocking.
     */
    fun artistPhoto(name: String): String? {
        if (name.isBlank()) return ""
        return try {
            val query = URLEncoder.encode(name, "UTF-8")
            val data = http.newCall(
                Request.Builder().url("$BASE/search/artist?q=$query").build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
            }
            val first = data?.firstOrNull()?.asJsonObject ?: return ""
            val pic = first.get("picture_xl")?.takeIf { it.isJsonPrimitive }?.asString
                ?: first.get("picture_big")?.takeIf { it.isJsonPrimitive }?.asString
                ?: ""
            if (pic.isBlank()) "" else pic
        } catch (e: Exception) {
            Log.e(TAG, "Artist photo lookup failed for $name", e)
            null
        }
    }

    private fun headOk(url: String): Boolean = try {
        http.newCall(Request.Builder().url(url).head().build())
            .execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }

    /** Fetches [url] and caches the raw JSON body under [key] (30 min TTL). */
    private fun cachedJson(key: String, url: String): String? {
        synchronized(cache) {
            val hit = cache[key]
            if (hit != null && System.currentTimeMillis() - hit.second < CACHE_TTL_MS)
                return hit.first
        }

        val body = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deezer request failed: $key", e)
            null
        } ?: return null

        synchronized(cache) {
            if (cache.size > 64) cache.clear()
            cache[key] = body to System.currentTimeMillis()
        }
        return body
    }

    private fun JsonObject.stringField(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive) value.asString else null
    }

    private fun JsonObject.objField(name: String): JsonObject? =
        (get(name) as? JsonObject)

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null
}

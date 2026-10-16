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
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.uditkarode.able.model.discovery.AlbumSuggestion
import io.github.uditkarode.able.model.discovery.ArtistSuggestion
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Minimal Last.fm API v2.0 client (https://www.last.fm/api). Read-only methods,
 * authenticated with the application API key only (no OAuth).
 *
 * All network calls are **synchronous** — invoke from a background dispatcher
 * (callers in this codebase already run on `Dispatchers.IO`). A small TTL cache
 * keeps us well under the public rate limit of 5 requests/second.
 *
 * Last.fm wraps the same payload under different keys depending on the method
 * (`tracks`, `toptracks`, `similartracks`, `results.trackmatches`, `artists`,
 * `topartists`, `tags`) and reports the artist as a string or an object, so the
 * responses are parsed generically instead of via fixed DTOs.
 */
object LastFmClient {
    private const val TAG = "LastFmClient"
    private const val BASE = "https://ws.audioscrobbler.com/2.0/"
    /** Cache TTL for volatile data (charts, search): 30 minutes. */
    private const val CACHE_TTL_MS = 30L * 60 * 1000

    /** Cache TTL for artwork lookups (track/artist getInfo): 7 days. */
    private const val ART_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** Cache TTL for album track lists + release dates: 1 day. */
    private const val ALBUM_TTL_MS = 24L * 60 * 60 * 1000

    /** Min interval between artwork lookups (rate limit is 5 req/s). */
    private const val ART_MIN_INTERVAL_MS = 220L

    /** Last.fm artwork hashes for the generic gray-star placeholder. */
    private val PLACEHOLDER_HASHES = setOf(
        "2a96cbd8b46e442fc41c2b86b821562f", // album/track placeholder
        "c6f59c1e5e7248a8c33aba0087ea49d0"  // artist placeholder
    )

    /** Timestamp of the last getInfo request, for the global rate limiter. */
    private var lastArtCallAt = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Simple in-memory cache: key -> (payload, timestamp). */
    private val cache = HashMap<String, Pair<String, Long>>()

    private val hasKey: Boolean get() = ApiKeyProvider.hasLastFmKey

    /** chart.getTopTracks — most-played tracks globally. */
    fun topTracks(): List<TrackSuggestion> = callTracks("chart.gettoptracks")

    /** geo.getTopTracks — most-played tracks in [country] (English country name, e.g. "Italy"). */
    fun geoTopTracks(country: String): List<TrackSuggestion> =
        callTracks("geo.gettoptracks", "country" to country)

    /** tag.getTopTracks — most-played tracks for a genre tag, e.g. "rock". */
    fun tagTopTracks(tag: String): List<TrackSuggestion> =
        callTracks("tag.gettoptracks", "tag" to tag)

    /** track.getSimilar — tracks similar to the given artist/title pair. */
    fun similarTracks(artist: String, track: String): List<TrackSuggestion> =
        callTracks("track.getsimilar", "artist" to artist, "track" to track)

    /**
     * track.getSimilar with graceful degradation for songs missing from (or
     * mis-tagged against) Last.fm's catalog. Order:
     *
     *  1. exact `track.getsimilar(artist, track)`
     *  2. undecorated title — trailing "(feat. …)" groups and "feat. …" tails
     *     are stripped from the tag title and the lookup retried
     *  3. canonicalization via `track.search` — a confidently matching result
     *     is retried with the canonical identity
     *  4. top tracks of artists similar to the seed artist (works when the
     *     artist is known but the specific track is not — e.g. keshi
     *     "Blue Water Road", absent from Last.fm entirely)
     *  5. top tracks by the seed artist itself
     *  6. Deezer artist radio — keyless, for artists Last.fm doesn't know
     *
     * Blocking — invoke from a background dispatcher.
     */
    fun similarTracksSmart(artist: String, track: String): SimilarDiscovery {
        if (artist.isNotBlank() && track.isNotBlank()) {
            val exact = similarTracks(artist, track)
            if (exact.isNotEmpty())
                return SimilarDiscovery(exact, SimilarSource.TRACK_SIMILAR)

            // Decorated tag titles ("FE!N (feat. Playboi Carti)", "Song feat. X")
            // rarely match the canonical entry — try the undecorated title.
            undecorated(track)?.let { stripped ->
                val strippedResult = similarTracks(artist, stripped)
                if (strippedResult.isNotEmpty())
                    return SimilarDiscovery(strippedResult, SimilarSource.TRACK_CORRECTED)
            }

            canonicalTrack(artist, track)?.let { (canonArtist, canonTitle) ->
                val corrected = similarTracks(canonArtist, canonTitle)
                if (corrected.isNotEmpty())
                    return SimilarDiscovery(corrected, SimilarSource.TRACK_CORRECTED)
            }

            val mix = similarArtistsMix(artist)
            if (mix.isNotEmpty())
                return SimilarDiscovery(mix, SimilarSource.ARTIST_MIX)

            val own = try { artistTopTracks(artist) } catch (e: Exception) { emptyList<TrackSuggestion>() }
            if (own.isNotEmpty())
                return SimilarDiscovery(own.take(15), SimilarSource.ARTIST_TOP)
        }
        if (artist.isNotBlank()) {
            val radio = try { DeezerClient.artistRadio(artist) } catch (e: Exception) { emptyList<TrackSuggestion>() }
            if (radio.isNotEmpty())
                return SimilarDiscovery(radio, SimilarSource.DEEZER_RADIO)
        }
        return SimilarDiscovery(emptyList(), SimilarSource.NONE)
    }

    /**
     * Strips trailing decorations from a tag title: featuring tails
     * ("Song feat. X", "Song ft. X", "Song featuring X") and trailing
     * parenthetical/bracketed groups ("FE!N (feat. Playboi Carti)"). Returns
     * null when nothing meaningful changes (avoids a duplicate API call).
     */
    private fun undecorated(title: String): String? {
        var t = title.trim()
        t = t.replace(Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s+.*", RegexOption.IGNORE_CASE), "")
        while (true) {
            val trailing = Regex("[\\[(][^)\\]]*[)\\]]\\s*$").find(t) ?: break
            t = t.substring(0, trailing.range.first).trim()
        }
        t = t.trimEnd('-', '–', ':', ',', ' ').trim()
        if (t.isBlank() || normKey(t).length < 3 || normKey(t) == normKey(title)) return null
        return t
    }

    /**
     * Looks [track] up in `track.search` (with the [artist] hint) and returns
     * the canonical (artist, title) pair when a result title confidently
     * matches the input title: normalized equality or prefix, both directions,
     * minimum 3 chars. The strictness matters — Last.fm's fuzzy search happily
     * returns unrelated songs that only share the artist, and those must not
     * be mistaken for a canonical identity.
     */
    private fun canonicalTrack(artist: String, track: String): Pair<String, String>? {
        val root = cachedJson("track.search", listOf("artist" to artist, "track" to track))
            ?: return null
        val nTrack = normKey(track)
        if (nTrack.length < 3) return null
        val inputKey = normKey(artist) + ":" + nTrack
        for (entry in findArray(root, "track").take(5)) {
            val obj = entry.asObjectOrNull() ?: continue
            val name = obj.stringField("name")?.takeIf { it.isNotBlank() } ?: continue
            val candArtist = obj.artistName().takeIf { it.isNotBlank() } ?: continue
            val nName = normKey(name)
            if (nName.length < 3 ||
                !(nName == nTrack || nName.startsWith(nTrack) || nTrack.startsWith(nName))
            ) continue
            // Same identity echoed back by the search = pointless retry.
            if (normKey(candArtist) + ":" + nName == inputKey) continue
            return candArtist to name
        }
        return null
    }

    /**
     * Round-robin over the top tracks of the artists similar to [artist]:
     * varied rows instead of six consecutive blocks of the same artist.
     */
    private fun similarArtistsMix(artist: String): List<TrackSuggestion> {
        val similar = try {
            callArtists("artist.getsimilar", "artist" to artist, "limit" to "8")
        } catch (e: Exception) { emptyList<ArtistSuggestion>() }
        if (similar.isEmpty()) return emptyList()

        val perArtist = similar.take(6).map { similarArtist ->
            try {
                callTracks("artist.gettoptracks", "artist" to similarArtist.name, "limit" to "3")
            } catch (e: Exception) { emptyList<TrackSuggestion>() }
        }

        val out = ArrayList<TrackSuggestion>()
        val seen = HashSet<String>()
        var index = 0
        while (out.size < 24) {
            var progressed = false
            for (tracks in perArtist) {
                if (index >= tracks.size) continue
                progressed = true
                val candidate = tracks[index]
                val key = normKey(candidate.name) + "|" + normKey(candidate.artist)
                if (seen.add(key)) out.add(candidate)
            }
            if (!progressed) break
            index++
        }
        return out
    }

    /** Last.fm `track.search` — metadata search used by the Discover search mode. */
    fun searchTracks(query: String): List<TrackSuggestion> =
        callTracks("track.search", "track" to query)

    /** Last.fm `artist.search` — artist cards for the Discover search mode. */
    fun searchArtists(query: String): List<ArtistSuggestion> =
        callArtists("artist.search", "artist" to query)

    /** artist.getTopTracks — drill-down used when pressing an artist suggestion. */
    fun artistTopTracks(artist: String): List<TrackSuggestion> =
        callTracks("artist.gettoptracks", "artist" to artist)

    /**
     * artist.getTopAlbums — the album side of the discography drill-down
     * (cover art included; release dates come from [albumTracks]).
     */
    fun artistTopAlbums(artist: String, limit: Int = 10): List<AlbumSuggestion> {
        val root = cachedJson("artist.gettopalbums", listOf("artist" to artist))
            ?: return emptyList()
        return findArray(root, "album")
            .mapNotNull { it.asObjectOrNull() }
            .mapNotNull { entry ->
                val name = entry.stringField("name") ?: return@mapNotNull null
                if (name.isBlank() || name == "(null)") return@mapNotNull null
                AlbumSuggestion(
                    name = name,
                    artist = artist,
                    image = entry.bestImage()
                )
            }
            .take(limit)
    }

    /**
     * album.getInfo — full track list + release date for one album. The
     * album image (usually real art) is attached to every track so rows
     * don't need extra lookups.
     */
    fun albumTracks(artist: String, album: String, fallbackImage: String): AlbumSuggestion? {
        val root = infoObject(
            "album.getinfo", listOf("artist" to artist, "album" to album), ALBUM_TTL_MS
        ) ?: return null
        val image = root.bestImage().ifBlank { fallbackImage }
        val rawDate = root.stringField("releasedate").orEmpty()
        val year = Regex("\\b\\d{4}\\b").find(rawDate)?.value?.toIntOrNull() ?: 0
        val tracks = findArray(root, "track")
            .mapNotNull { it.asObjectOrNull() }
            .mapNotNull { entry ->
                val name = entry.stringField("name") ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                TrackSuggestion(name = name, artist = artist, image = image)
            }
        return AlbumSuggestion(
            name = root.stringField("name") ?: album,
            artist = artist,
            image = image,
            releaseDate = rawDate,
            year = year,
            tracks = tracks
        )
    }

    /** chart.getTopArtists — most-played artists globally. */
    fun topArtists(): List<ArtistSuggestion> = callArtists("chart.gettopartists")

    /** geo.getTopArtists — most-played artists in [country] (English country name). */
    fun geoTopArtists(country: String): List<ArtistSuggestion> =
        callArtists("geo.gettopartists", "country" to country)

    /** chart.getTopTags — popular genre/mood tags. */
    fun topTags(): List<String> {
        val root = cachedJson("chart.gettoptags", emptyList()) ?: return emptyList()
        return findArray(root, "tag")
            .mapNotNull { it.asObjectOrNull()?.stringField("name") }
            .filter { it.isNotBlank() }
    }

    /**
     * Real artwork URL for a track, checked against the persistent cache
     * first; resolves via `track.getInfo` (rate-limited) when needed.
     * Blocks the calling thread — invoke from a background dispatcher.
     */
    fun resolveTrackImage(artist: String, track: String): String {
        if (artist.isBlank() || track.isBlank()) return ""
        ArtworkCache.trackImage(artist, track)?.let { return it }
        rateLimitArt()
        val url = trackInfoImage(artist, track) ?: return "" // failed: don't cache
        ArtworkCache.putTrack(artist, track, url) // real URL or "no art" marker
        return url
    }

    /**
     * Full track identity from one cached `track.getInfo` call: the album the
     * track belongs to plus its real artwork. Used by the Player as the
     * authoritative cover source (Deezer upgrades use [TrackIdentity.album]
     * to fetch the high-res version of the *right* album).
     * Blocks the calling thread — invoke from a background dispatcher.
     */
    fun resolveTrackIdentity(artist: String, track: String): TrackIdentity? {
        if (artist.isBlank() || track.isBlank()) return null
        val cachedImage = ArtworkCache.trackImage(artist, track)
        val cachedAlbum = ArtworkCache.trackAlbum(artist, track)
        if (cachedImage != null && cachedAlbum != null) {
            return TrackIdentity(cachedAlbum, cachedImage)
                .takeIf { it.image.isNotBlank() || it.album.isNotBlank() }
        }
        rateLimitArt()
        val identity = trackIdentityInfo(artist, track) ?: return null // failed: don't cache
        ArtworkCache.putTrack(artist, track, identity.image)
        ArtworkCache.putTrackAlbum(artist, track, identity.album)
        return identity.takeIf { it.image.isNotBlank() || it.album.isNotBlank() }
    }

    /**
     * Real artwork URL for an artist, persistent-cache aware and
     * rate-limited. Last.fm's artist.getinfo no longer serves artist photos
     * (every size comes back as the gray placeholder), so the photo comes
     * from Deezer's keyless artist search. Blocks the calling thread —
     * invoke from a background dispatcher.
     */
    fun resolveArtistImage(name: String): String {
        if (name.isBlank()) return ""
        ArtworkCache.artistImage(name)?.let { return it }
        rateLimitArt()
        val url = DeezerClient.artistPhoto(name) ?: return "" // failed: don't cache
        ArtworkCache.putArtist(name, url) // real URL or "no photo" marker
        return url
    }

    /** Global rate limiter: min [ART_MIN_INTERVAL_MS] between artwork lookups. */
    private fun rateLimitArt() {
        synchronized(this) {
            val wait = ART_MIN_INTERVAL_MS - (System.currentTimeMillis() - lastArtCallAt)
            if (wait > 0) try { Thread.sleep(wait) } catch (_: InterruptedException) {}
            lastArtCallAt = System.currentTimeMillis()
        }
    }

    /** track.getInfo — richer metadata incl. the real album art. Returns
     *  null when the lookup FAILED (no key / network / API error): the
     *  caller must not cache that as "no art". */
    private fun trackInfoImage(artist: String, track: String): String? {
        val root = infoObject("track.getinfo", listOf("artist" to artist, "track" to track))
            ?: return null
        val fromAlbum = (root.get("album") as? JsonObject)?.bestImage().orEmpty()
        val best = if (fromAlbum.isNotBlank() && !isPlaceholderImage(fromAlbum))
            fromAlbum else root.bestImage()
        return if (isPlaceholderImage(best)) "" else best
    }

    /**
     * track.getInfo — album title + real album art in one cached call.
     * Returns null only when the lookup FAILED (no key / network / API
     * error); a successful response with nothing to show comes back as an
     * empty [TrackIdentity], which is a legitimate cacheable "no art".
     */
    private fun trackIdentityInfo(artist: String, track: String): TrackIdentity? {
        val root = infoObject("track.getinfo", listOf("artist" to artist, "track" to track))
            ?: return null
        val albumJson = root.get("album") as? JsonObject
        val albumName = albumJson?.stringField("title").orEmpty()
        val fromAlbum = albumJson?.bestImage().orEmpty()
        val best = if (fromAlbum.isNotBlank() && !isPlaceholderImage(fromAlbum))
            fromAlbum else root.bestImage()
        val image = if (isPlaceholderImage(best)) "" else best
        return TrackIdentity(albumName, image)
    }

    /** Last.fm sometimes returns a generic gray-star placeholder instead of art. */
    fun isPlaceholderImage(url: String): Boolean =
        PLACEHOLDER_HASHES.any { url.contains(it) }

    /** Fetches a getInfo response and returns the wrapped track/artist/album object. */
    private fun infoObject(
        method: String,
        params: List<Pair<String, String>>,
        ttlMs: Long = ART_TTL_MS
    ): JsonObject? {
        val json = cachedJson(method, params, ttlMs) ?: return null
        val root = try { JsonParser.parseString(json) } catch (_: Exception) { return null }
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        return (obj.get("track") as? JsonObject)
            ?: (obj.get("artist") as? JsonObject)
            ?: (obj.get("album") as? JsonObject)
    }

    // region internals

    /** Live validation of a candidate API key (used by the in-app import prompt). */
    fun validateKey(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        val url = "$BASE?method=chart.gettoptags&format=json&api_key=$candidate"
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string() ?: return false
                !body.contains("\"error\"")
            }
        } catch (e: Exception) {
            Log.e(TAG, "API key validation failed", e)
            false
        }
    }

    private fun callTracks(method: String, vararg params: Pair<String, String>): List<TrackSuggestion> {
        val root = cachedJson(method, params.toList()) ?: return emptyList()
        return findArray(root, "track")
            .mapNotNull { it.asObjectOrNull() }
            .filter { it.stringField("name")?.isNotBlank() == true }
            .map { entry ->
                TrackSuggestion(
                    name = entry.stringField("name") ?: "",
                    artist = entry.artistName(),
                    image = entry.bestImage(),
                    listeners = entry.stringField("listeners")
                        ?: entry.stringField("listener") ?: "",
                    mbid = entry.stringField("mbid") ?: ""
                )
            }
            .filter { it.artist.isNotBlank() }
    }

    private fun callArtists(method: String, vararg params: Pair<String, String>): List<ArtistSuggestion> {
        val root = cachedJson(method, params.toList()) ?: return emptyList()
        return findArray(root, "artist")
            .mapNotNull { it.asObjectOrNull() }
            .filter { it.stringField("name")?.isNotBlank() == true }
            .map {
                ArtistSuggestion(
                    name = it.stringField("name") ?: "",
                    image = it.bestImage(),
                    listeners = it.stringField("listeners") ?: "",
                    mbid = it.stringField("mbid") ?: ""
                )
            }
    }

    /**
     * Locates the payload array for [memberName] anywhere within the response
     * object tree. Covers `tracks.track` (chart/geo), `toptracks.track`
     * (artist/tag), `similartracks.track`, `results.trackmatches.track`
     * (track.search), `artists.artist`, `topartists.artist` and `tags.tag`.
     */
    private fun findArray(json: String, memberName: String): List<JsonElement> {
        val root = try { JsonParser.parseString(json) } catch (_: Exception) { return emptyList() }
        if (!root.isJsonObject) return emptyList()
        return findArray(root.asJsonObject, memberName)
    }

    private fun findArray(obj: JsonObject, memberName: String): List<JsonElement> {
        val candidate = obj.get(memberName)
        if (candidate is JsonArray) return candidate.toList()
        for ((_, value) in obj.entrySet()) {
            val child = value.asObjectOrNull() ?: continue
            val found = findArray(child, memberName)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /** Artist may be a string (track.search) or an object (`artist.name`). */
    private fun JsonObject.artistName(): String {
        val artist = get("artist") ?: return ""
        return when {
            artist.isJsonObject -> artist.asJsonObject.stringField("name") ?: ""
            artist.isJsonPrimitive -> artist.asString
            else -> ""
        }
    }

    /** Picks the last non-blank image (Last.fm sizes go small → extralarge). */
    private fun JsonObject.bestImage(): String {
        val images = get("image") as? JsonArray ?: return ""
        return images.mapNotNull { it.asObjectOrNull()?.stringField("#text") }
            .lastOrNull { it.isNotBlank() } ?: ""
    }

    private fun JsonObject.stringField(name: String): String? {
        val value = get(name) ?: return null
        return when {
            value.isJsonNull -> null
            value.isJsonPrimitive -> value.asString
            else -> null
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    /** Builds the request URL and caches the raw JSON body per (method, params). */
    private fun cachedJson(
        method: String,
        params: List<Pair<String, String>>,
        ttlMs: Long = CACHE_TTL_MS
    ): String? {
        if (!hasKey) {
            Log.w(TAG, "LASTFM_API_KEY is not set — discovery unavailable")
            return null
        }
        val cacheKey = method + params.joinToString("|") { "${it.first}=${it.second}" }

        synchronized(cache) {
            val hit = cache[cacheKey]
            if (hit != null && System.currentTimeMillis() - hit.second < ttlMs)
                return hit.first
        }

        val url = StringBuilder(BASE)
            .append("?method=").append(method)
            .append("&format=json")
            .append("&api_key=").append(ApiKeyProvider.lastFmApiKey)
        params.forEach { (k, v) ->
            url.append("&").append(k).append("=").append(java.net.URLEncoder.encode(v, "UTF-8"))
        }

        val body = try {
            http.newCall(Request.Builder().url(url.toString()).build()).execute().use { resp ->
                resp.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Last.fm request failed: $method", e)
            null
        } ?: return null

        if (body.contains("\"error\"")) {
            val err = try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { null }
            Log.w(TAG, "Last.fm error on $method: ${err?.get("error")} ${err?.get("message")}")
            return null
        }

        synchronized(cache) {
            if (cache.size > 64) cache.clear()
            cache[cacheKey] = body to System.currentTimeMillis()
        }
        return body
    }

    // endregion
}

/** What one cached `track.getInfo` call identifies about a track. */
data class TrackIdentity(val album: String, val image: String)

/** Where the suggestions in a [SimilarDiscovery] came from. */
enum class SimilarSource {
    /** `track.getsimilar` matched the exact artist+title pair. */
    TRACK_SIMILAR,
    /** The file tags differed from Last.fm's canonical names; a corrected
     *  identity (via `track.search`) produced the suggestions. */
    TRACK_CORRECTED,
    /** Top tracks of artists similar to the seed artist. */
    ARTIST_MIX,
    /** Top tracks by the seed artist itself. */
    ARTIST_TOP,
    /** Deezer artist radio (seed artist unknown to Last.fm). */
    DEEZER_RADIO,
    /** No source could suggest anything. */
    NONE
}

/**
 * Suggestions for the Player's Discovery button plus the source they came
 * from; the source drives the sheet title and the "no suggestions" message.
 */
data class SimilarDiscovery(val tracks: List<TrackSuggestion>, val source: SimilarSource)
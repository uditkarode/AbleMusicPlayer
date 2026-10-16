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

import io.github.uditkarode.able.model.discovery.TrackSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Letters without a decomposition in NFD that must still fold to their ASCII
 * base ("Møme" must match a "mome" query). Applied before the NFD pass.
 */
private val EXTRA_FOLDS = mapOf(
    'ø' to "o", 'đ' to "d", 'ł' to "l", 'æ' to "ae", 'œ' to "oe",
    'ß' to "ss", 'þ' to "th", 'ı' to "i"
)

/**
 * Normalizes a string into lowercase alphanumeric words: diacritics folded
 * (Møme → mome), punctuation dropped inside words (FE!N → fein) while
 * whitespace splits words ("artist - title" → artist, title).
 * Shared by [CatalogSearch] and [LastFmClient].
 */
internal fun normWords(s: String): List<String> =
    s.lowercase().split(Regex("\\s+")).mapNotNull { word ->
        val sb = StringBuilder()
        for (c in word) {
            if (EXTRA_FOLDS.containsKey(c)) {
                sb.append(EXTRA_FOLDS[c])
                continue
            }
            for (n in Normalizer.normalize(c.toString(), Normalizer.Form.NFD))
                if (Character.getType(n) != Character.NON_SPACING_MARK.toInt() && n.isLetterOrDigit())
                    sb.append(n)
        }
        if (sb.isEmpty()) null else sb.toString()
    }

/** Normalized key for equality/prefix checks ("Blue Water Road" → bluewaterroad). */
internal fun normKey(s: String): String = normWords(s).joinToString("")

/** Normalized token set for order-insensitive matching. */
internal fun tokenSet(s: String): Set<String> = normWords(s).toSet()

/**
 * Catalog-first text search for the Discover search mode: instead of trusting
 * one source's raw output, two metadata catalogs are queried **in parallel**
 * and their results must pass a relevance gate against the user's query.
 *
 * Source order (decided by design): **Deezer → Last.fm**. Deezer ranks
 * commercially (top hit = studio song), Last.fm's track.search is fuzzy and
 * pads results with covers/live versions — hence the gate and the order.
 * Last.fm is skipped silently when no API key is configured; Deezer is
 * keyless, so the search flow never blocks on the key prompt.
 *
 * The caller falls back to the raw YouTube/YT Music search when
 * [CatalogResult.matches] is empty.
 */
object CatalogSearch {

    /** Canonical catalog matches for a query; empty = no confident match. */
    data class CatalogResult(val matches: List<TrackSuggestion>)

    private const val MAX_MATCHES = 6

    /**
     * Runs the cascade for [query]. Both catalogs are fetched concurrently
     * (blocking OkHttp calls offloaded to Dispatchers.IO), then filtered,
     * Deezer-first, deduped and capped. Blocking — invoke from a background
     * dispatcher.
     */
    suspend fun search(query: String): CatalogResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext CatalogResult(emptyList())

        coroutineScope {
            val deezer = async {
                try { DeezerClient.searchTracks(query) }
                catch (e: Exception) { emptyList<TrackSuggestion>() }
            }
            val lastfm = async {
                if (!ApiKeyProvider.hasLastFmKey) emptyList<TrackSuggestion>()
                else try { LastFmClient.searchTracks(query) }
                     catch (e: Exception) { emptyList<TrackSuggestion>() }
            }

            val qTokens = tokenSet(query)
            val qKey = normKey(query)
            val seen = HashSet<String>()
            val matches = ArrayList<TrackSuggestion>(MAX_MATCHES)
            for (candidate in deezer.await() + lastfm.await()) {
                if (matches.size >= MAX_MATCHES) break
                if (!matchesQuery(candidate, qTokens, qKey)) continue
                val key = normKey(candidate.name) + "|" + normKey(candidate.artist)
                if (!seen.add(key)) continue
                matches.add(candidate)
            }
            CatalogResult(matches)
        }
    }

    /**
     * Confidence gate between the query and a catalog entry. The fuzzy
     * sources return *something* for any input, so "non-empty" is not enough;
     * at least one rule must tie the entry to the query:
     *
     *  - title tokens equal the query tokens ("blue water road")
     *  - artist+title tokens equal the query tokens, any order
     *    ("fe!n travis scott" ↔ Travis Scott — FE!N)
     *  - query tokens ⊆ artist+title tokens and title tokens ⊆ query tokens
     *    ("fe!n feat playboi carti" ↔ "FE!N (feat. Playboi Carti)")
     *  - the query key is a prefix of the title key (≥ 3 chars)
     *    ("blue water" ↔ "Blue Water Road")
     */
    internal fun matchesQuery(track: TrackSuggestion, qTokens: Set<String>, qKey: String): Boolean {
        if (qTokens.isEmpty()) return false
        val titleTokens = tokenSet(track.name)
        if (titleTokens.isEmpty()) return false
        val fullTokens = tokenSet("${track.artist} ${track.name}")
        return titleTokens == qTokens ||
                fullTokens == qTokens ||
                (qTokens.containsAll(titleTokens) && fullTokens.containsAll(qTokens)) ||
                (qKey.length >= 3 && normKey(track.name).startsWith(qKey))
    }
}

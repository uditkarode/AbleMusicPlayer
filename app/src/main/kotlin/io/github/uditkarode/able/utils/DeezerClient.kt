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
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Minimal Deezer public API client (https://api.deezer.com — keyless).
 *
 * Used ONLY as a resolution upgrade for cover art: the album identity comes
 * from Last.fm (exact artist+track match via track.getInfo) and this client
 * fetches a high-res cover of that already-identified album. Deezer's own
 * search is popularity-ranked and routinely returns compilations instead of
 * the studio album, so it must never be used to *identify* a track.
 *
 * All calls are synchronous — invoke from a background dispatcher.
 */
object DeezerClient {
    private const val TAG = "DeezerClient"
    private const val BASE = "https://api.deezer.com"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

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

    private fun headOk(url: String): Boolean = try {
        http.newCall(Request.Builder().url(url).head().build())
            .execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }
}

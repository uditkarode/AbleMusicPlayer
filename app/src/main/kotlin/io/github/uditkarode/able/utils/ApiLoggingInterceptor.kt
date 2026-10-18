/*
    Copyright 2026 AbleMusicPlayer contributors

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
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor emitting one structured line per API call, tagged "HTTP>":
 * `GET https://… → 200 in 312ms (4.2KB)`, or `FAILED in …: <throwable>` on
 * network errors. Lines go to logcat, so with DevLog capture running they land
 * in the developer log file too; when the toggle is off this gates on
 * [DevLog.active] and costs a single volatile read.
 *
 * Response bodies are deliberately not logged — audio downloads
 * ([ChunkedDownloader]) stream megabytes through here, and error payloads are
 * already surfaced by the clients' own Log.e calls.
 */
object ApiLoggingInterceptor : Interceptor {
    private const val TAG = "HTTP>"

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!DevLog.active) return chain.proceed(chain.request())

        val request = chain.request()
        val startedAt = System.nanoTime()
        val url = DevLog.sanitize(request.url.toString())
        return try {
            val response = chain.proceed(request)
            val ms = (System.nanoTime() - startedAt) / 1_000_000
            Log.i(TAG, "$url → ${response.code} in ${ms}ms (${sizeOf(response.body?.contentLength() ?: -1L)})")
            response
        } catch (e: IOException) {
            val ms = (System.nanoTime() - startedAt) / 1_000_000
            Log.e(TAG, "$url FAILED in ${ms}ms: $e")
            throw e
        }
    }

    private fun sizeOf(bytes: Long): String = when {
        bytes < 0 -> "size unknown"
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / 1048576.0)}MB"
    }
}

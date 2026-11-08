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

import android.media.MediaDataSource
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Progressive playback source: hands MediaPlayer a [MediaDataSource] over the
 * *growing* cache file while [ChunkedDownloader] is still downloading it.
 * Audio starts as soon as the first chunks land on disk instead of after the
 * whole song — the fix for the "album takes forever to start" class.
 *
 * Deliberately IN-PROCESS: an earlier loopback HTTP proxy was never even
 * contacted by the platform's media HTTP stack (no connection ever reached the
 * ServerSocket, cause still unclear) — MediaDataSource removes that entire
 * layer: no network, no cleartext policy, no sockets.
 *
 * Contract:
 *  - [register] with the resolved content length (served as [getSize], so
 *    duration and seek work from the start).
 *  - readAt blocks until the producer frontier covers the requested offset;
 *    seeking backwards is instant, seeking forward waits for the producer,
 *    which is fast because chunked downloads bypass YouTube's throttle.
 *  - Producer failure → [markFailed] → readAt returns -1 → MediaPlayer errors
 *    → the existing OnErrorListener skip logic takes over.
 *  - Feed MediaPlayer an m4a stream when available: webm/opus carries its
 *    index (Cues) at the end of the file, which would stall prepare until the
 *    whole file is cached; m4a has its index (moov) at the front.
 */
object StreamProxy {
    private const val TAG = "StreamProxy"

    class Entry(val file: File, val contentLength: Long) {
        @Volatile var failed: Boolean = false
        /** Producer finished the full download (readAt may return EOS). */
        @Volatile var complete: Boolean = false
    }

    private val entries = HashMap<String, Entry>()

    @Synchronized
    fun register(songId: String, file: File, contentLength: Long) {
        synchronized(entries) { entries[songId] = Entry(file, contentLength) }
        Log.i("ABLE>", "proxy register $songId len=$contentLength")
    }

    /** Producer failure: cut the stream so MediaPlayer errors out and the
     *  existing skip logic takes over instead of hanging on a dead source. */
    fun markFailed(songId: String) {
        Log.i("ABLE>", "proxy FAILED $songId")
        synchronized(entries) { entries[songId]?.failed = true }
    }

    /** Producer finished: reads past the file length are now a clean EOS. */
    fun markComplete(songId: String) {
        synchronized(entries) { entries[songId]?.complete = true }
    }

    /** Builds the in-process source for a registered song, or null. */
    fun sourceFor(songId: String): MediaDataSource? {
        val entry = synchronized(entries) { entries[songId] } ?: return null
        // The cache file may not exist yet (register happens before the
        // producer creates it) — an uncaught ENOENT here killed the process
        // from the Main thread.
        val raf = try {
            RandomAccessFile(entry.file, "r")
        } catch (e: Exception) {
            Log.e(TAG, "sourceFor: cannot open ${entry.file}: $e")
            return null
        }
        return object : MediaDataSource() {
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (entry.failed) return -1
                if (position >= entry.contentLength) return -1
                // Wait for the producer frontier (progressive playback).
                while (!entry.failed && !entry.complete) {
                    val available = entry.file.length()
                    if (available > position) break
                    try {
                        Thread.sleep(60)
                    } catch (_: InterruptedException) {
                        return -1
                    }
                }
                if (entry.failed) return -1
                val available = entry.file.length()
                if (position >= available) return -1 // producer done, short file
                return try {
                    raf.seek(position)
                    val want = minOf(
                        size,
                        buffer.size - offset,
                        (available - position).toInt()
                    )
                    if (want <= 0) 0 else raf.read(buffer, offset, want)
                } catch (e: Exception) {
                    Log.e(TAG, "readAt($position) failed: $e")
                    -1
                }
            }

            override fun getSize(): Long = entry.contentLength

            override fun close() {
                try { raf.close() } catch (_: Exception) {}
            }
        }
    }

    /** Called from the service's cleanUp. */
    fun stop() {
        synchronized(entries) { entries.clear() }
    }
}
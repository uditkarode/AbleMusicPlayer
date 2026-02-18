/*
    Copyright 2020 Udit Karode <udit.karode@gmail.com>

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
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.SocketException
import java.util.concurrent.TimeUnit

/**
 * Downloads files using chunked HTTP Range requests to bypass
 * YouTube's per-connection throttle. Each 256KB chunk opens a
 * fresh TCP connection, achieving full-speed downloads.
 */
object ChunkedDownloader {

    private const val CHUNK_SIZE = 256L * 1024 // 256KB
    private const val MAX_RETRIES = 3
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    // No connection pooling — each chunk gets a fresh TCP connection
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .build()

    fun interface ProgressListener {
        fun onProgress(progress: Int)
    }

    /**
     * Downloads [url] to [outputFile] using chunked Range requests.
     * Falls back to a single request if content length is unknown.
     *
     * @param url        The URL to download from
     * @param outputFile The destination file
     * @param listener   Optional progress callback (called on download thread)
     * @throws Exception on network or file I/O failure after retries exhausted
     */
    fun download(url: String, outputFile: File, listener: ProgressListener? = null) {
        val contentLength = getContentLength(url)
        if (contentLength <= 0) {
            downloadSingle(url, outputFile)
            return
        }
        downloadChunked(url, outputFile, contentLength, listener)
    }

    private fun getContentLength(url: String): Long {
        val req = Request.Builder()
            .url(url).head()
            .addHeader("User-Agent", USER_AGENT)
            .build()
        val resp = client.newCall(req).execute()
        val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
        resp.close()
        return len
    }

    private fun downloadChunked(
        url: String,
        outputFile: File,
        contentLength: Long,
        listener: ProgressListener?
    ) {
        val numChunks = (contentLength + CHUNK_SIZE - 1) / CHUNK_SIZE
        Log.d("DL>", "Chunked download: ${contentLength / 1024}KB in $numChunks chunks")

        val oStream = FileOutputStream(outputFile)
        var downloaded = 0L
        var lastNotifiedProgress = -1
        val startTime = System.currentTimeMillis()

        while (downloaded < contentLength) {
            val rangeEnd = minOf(downloaded + CHUNK_SIZE - 1, contentLength - 1)

            for (attempt in 1..MAX_RETRIES) {
                try {
                    if (attempt > 1) {
                        Log.d("DL>", "Chunk retry $attempt for bytes $downloaded-$rangeEnd")
                        Thread.sleep(1000L * attempt)
                    }

                    val req = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Range", "bytes=$downloaded-$rangeEnd")
                        .addHeader("Connection", "close")
                        .build()
                    val resp = client.newCall(req).execute()
                    val iStream = BufferedInputStream(resp.body!!.byteStream())

                    val data = ByteArray(65536)
                    var read: Int
                    while (iStream.read(data).also { read = it } != -1) {
                        oStream.write(data, 0, read)
                        downloaded += read

                        val progress = ((downloaded * 100) / contentLength).toInt()
                        if (progress / 5 != lastNotifiedProgress / 5) {
                            lastNotifiedProgress = progress
                            listener?.onProgress(progress)
                        }
                    }

                    iStream.close()
                    resp.close()
                    break // chunk succeeded
                } catch (e: SocketException) {
                    Log.e("DL>", "Chunk attempt $attempt failed: ${e.message}")
                    if (attempt == MAX_RETRIES) {
                        oStream.close()
                        outputFile.delete()
                        throw e
                    }
                }
            }
        }

        oStream.flush()
        oStream.close()

        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        val speedKBs = if (elapsed > 0) (contentLength / 1024.0 / elapsed).toInt() else 0
        Log.d("DL>", "Download complete: ${contentLength / 1024}KB in ${elapsed.toInt()}s ($speedKBs KB/s)")
    }

    private fun downloadSingle(url: String, outputFile: File) {
        Log.d("DL>", "Single-request download (unknown content length)")
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        val resp = client.newCall(req).execute()
        val iStream = BufferedInputStream(resp.body!!.byteStream())
        val oStream = FileOutputStream(outputFile)

        val data = ByteArray(65536)
        var read: Int
        while (iStream.read(data).also { read = it } != -1) {
            oStream.write(data, 0, read)
        }

        oStream.flush()
        oStream.close()
        iStream.close()
        resp.close()
    }
}

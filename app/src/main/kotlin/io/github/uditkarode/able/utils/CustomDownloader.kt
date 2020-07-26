/*
    Copyright 2020 NewPipeExtractor Team
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

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.URL
import java.net.URLConnection
import javax.net.ssl.HttpsURLConnection

/**
 * Downloader used to search using NewPipeExtractor.
 */
class CustomDownloader: Downloader() {
    private fun setDefaultHeaders(connection: URLConnection) {
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty(
            "Accept-Language",
            DEFAULT_HTTP_ACCEPT_LANGUAGE
        )
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers =
            request.headers()
        val dataToSend = request.dataToSend()
        val connection =
            URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 30 * 1000 // 30s
        connection.readTimeout = 30 * 1000 // 30s
        connection.requestMethod = httpMethod
        setDefaultHeaders(connection)
        for ((headerName, headerValueList) in headers) {
            if (headerValueList.size > 1) {
                connection.setRequestProperty(headerName, null)
                for (headerValue in headerValueList) {
                    connection.addRequestProperty(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                connection.setRequestProperty(headerName, headerValueList[0])
            }
        }
        var outputStream: OutputStream? = null
        var input: InputStreamReader? = null
        return try {
            if (dataToSend != null && dataToSend.isNotEmpty()) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Length", dataToSend.size.toString() + "")
                outputStream = connection.outputStream
                outputStream.write(dataToSend)
            }
            val inputStream = connection.inputStream
            val response = StringBuilder()

            // Not passing any charset for decoding here... something to keep in mind.
            input = InputStreamReader(inputStream)
            var readCount: Int
            val buffer = CharArray(32 * 1024)
            while (input.read(buffer).also { readCount = it } != -1) {
                response.append(buffer, 0, readCount)
            }
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            val responseHeaders =
                connection.headerFields
            val latestUrl = connection.url.toString()
            Response(
                responseCode,
                responseMessage,
                responseHeaders,
                response.toString(),
                latestUrl
            )
        } catch (e: Exception) {
            val responseCode = connection.responseCode

            /*
             * HTTP 429 == Too Many Request
             * Receive from Youtube.com = ReCaptcha challenge request
             * See : https://github.com/rg3/youtube-dl/issues/5138
             */if (responseCode == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            } else if (responseCode != -1) {
                val latestUrl = connection.url.toString()
                return Response(
                    responseCode,
                    connection.responseMessage,
                    connection.headerFields,
                    null,
                    latestUrl
                )
            }
            throw IOException("Error occurred while fetching the content", e)
        } finally {
            outputStream?.close()
            input?.close()
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:68.0) Gecko/20100101 Firefox/68.0"
        private const val DEFAULT_HTTP_ACCEPT_LANGUAGE = "en"
        var instance: CustomDownloader? = null
            get() {
                if (field == null) {
                    synchronized(CustomDownloader::class.java) {
                        if (field == null) {
                            field = CustomDownloader()
                        }
                    }
                }
                return field
            }
            private set
    }
}
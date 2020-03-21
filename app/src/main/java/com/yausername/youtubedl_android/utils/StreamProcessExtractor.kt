/*
    Copyright 2020 yausername  <yauser@protonmail.com>
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

package com.yausername.youtubedl_android.utils

import com.orhanobut.logger.Logger
import com.yausername.youtubedl_android.DownloadProgressCallback
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.util.regex.Pattern

class StreamProcessExtractor(
    private val buffer: StringBuffer,
    private val stream: InputStream,
    private val callback: DownloadProgressCallback?
) : Thread() {
    private val p =
        Pattern.compile("\\[download]\\s+(\\d+\\.\\d)% .* ETA (\\d+):(\\d+)")

    override fun run() {
        try {
            val `in`: Reader = InputStreamReader(stream, "UTF-8")
            val currentLine = StringBuilder()
            var nextChar: Int
            while (`in`.read().also { nextChar = it } != -1) {
                buffer.append(nextChar.toChar())
                if (nextChar == '\r'.toInt() && callback != null) {
                    processOutputLine(currentLine.toString())
                    currentLine.setLength(0)
                    continue
                }
                currentLine.append(nextChar.toChar())
            }
        } catch (e: IOException) {
            Logger.e(e, "failed to read stream")
        }
    }

    private fun processOutputLine(line: String) {
        val m = p.matcher(line)
        if (m.matches()) {
            val progress =
                m.group(GROUP_PERCENT)?.toFloat()
            val eta = convertToSeconds(
                m.group(GROUP_MINUTES)?:"?",
                m.group(GROUP_SECONDS)?:"?"
            ).toLong()
            callback!!.onProgressUpdate(progress?:0f, eta)
        }
    }

    private fun convertToSeconds(minutes: String, seconds: String): Int {
        return minutes.toInt() * 60 + seconds.toInt()
    }

    companion object {
        private const val GROUP_PERCENT = 1
        private const val GROUP_MINUTES = 2
        private const val GROUP_SECONDS = 3
    }

    init {
        start()
    }
}
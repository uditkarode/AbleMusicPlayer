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
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

class StreamGobbler(private val buffer: StringBuffer, private val stream: InputStream) :
    Thread() {
    override fun run() {
        try {
            val `in`: Reader = InputStreamReader(stream, "UTF-8")
            var nextChar: Int
            while (`in`.read().also { nextChar = it } != -1) {
                buffer.append(nextChar.toChar())
            }
        } catch (e: IOException) {
            Logger.e(e, "failed to read stream")
        }
    }

    init {
        start()
    }
}
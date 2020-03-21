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

package com.yausername.youtubedl_android

import java.util.*

class YoutubeDLOptions {
    private val options: MutableMap<String, String?> =
        HashMap()

    fun setOption(key: String, value: String): YoutubeDLOptions {
        options[key] = value
        return this
    }

    fun setOption(key: String, value: Number): YoutubeDLOptions {
        options[key] = value.toString()
        return this
    }

    fun setOption(key: String): YoutubeDLOptions {
        options[key] = null
        return this
    }

    fun buildOptions(): List<String> {
        val optionsList: MutableList<String> =
            ArrayList()
        for ((name, value) in options) {
            optionsList.add(name)
            if (null != value) optionsList.add(value)
        }
        return optionsList
    }
}
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

class YoutubeDLRequest {
    private var urls: List<String>
    private val options = YoutubeDLOptions()

    constructor(url: String) {
        urls = listOf(url)
    }

    constructor(urls: List<String>) {
        this.urls = urls
    }

    fun setOption(key: String, value: String): YoutubeDLRequest {
        options.setOption(key, value)
        return this
    }

    fun setOption(key: String, value: Number): YoutubeDLRequest {
        options.setOption(key, value)
        return this
    }

    fun setOption(key: String?): YoutubeDLRequest {
        options.setOption(key!!)
        return this
    }

    fun buildCommand(): List<String> {
        val command: MutableList<String> =
            ArrayList()
        command.addAll(options.buildOptions())
        command.addAll(urls)
        return command
    }
}
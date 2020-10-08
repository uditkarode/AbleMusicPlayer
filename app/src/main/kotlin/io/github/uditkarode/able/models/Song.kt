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

package io.github.uditkarode.able.models

import kotlinx.coroutines.sync.Mutex

class Song(
    val name: String,
    var artist: String="",
    var youtubeLink: String = "",
    var filePath: String = "",
    var placeholder: Boolean = false,
    var ytmThumbnail: String = "",
    val albumId: Long = -1,
    var isLocal: Boolean = false,
    var cacheStatus: CacheStatus = CacheStatus.NULL,
    var streamProg: Int = 0,
) {
    lateinit var streamMutexes: Array<Mutex>
    lateinit var internalStream: ByteArray // SHOULDN'T BE USED FOR PLAYING
    lateinit var streams: Array<ByteArray>
}
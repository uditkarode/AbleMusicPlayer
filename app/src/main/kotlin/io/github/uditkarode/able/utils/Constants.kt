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

import android.content.Context
import java.io.File

class Constants {
    companion object {
        private lateinit var baseDir: File

        fun init(context: Context) {
            baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        }

        /** a File object pointing to the folder where playlist JSONs will be stored */
        val playlistFolder: File get() = File(baseDir, "playlists")

        /** a File object pointing to the folder where all AbleMusic related files will be stored. */
        val ableSongDir: File get() = baseDir

        /** a File object pointing to the folder where all songs imported from Spotify will be stored. */
        val playlistSongDir: File get() = File(baseDir, "playlist_songs")

        /** a File object pointing to the folder where album art JPGs will be stored */
        val albumArtDir: File get() = File(baseDir, "album_art")

        /** a File object pointing to the folder where temporary items will be stored */
        val cacheDir: File get() = File(baseDir, "cache")

        /** a File object pointing to the folder where developer logs are stored */
        val logsDir: File get() = File(baseDir, "logs")

        /**
         * API keys which *should* be replaced during compilation.
         * The old RapidAPI/Deezer key placeholder is gone: cover art
         * resolution uses Last.fm (user key, see ApiKeyProvider) with an
         * optional keyless Deezer upgrade (see DeezerClient).
         */
        const val CHANNEL_ID = "AbleMusicDownload"
    }
}

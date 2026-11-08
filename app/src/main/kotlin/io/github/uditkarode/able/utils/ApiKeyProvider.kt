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

import android.content.Context
import android.content.SharedPreferences

/**
 * Runtime storage for the Last.fm API key. The key is imported from inside
 * the app (prompt in [LastFmKeyPrompt]) instead of being baked in at build
 * time. `BuildConfig.LASTFM_API_KEY` is kept only as a legacy fallback for
 * existing local builds; a key entered in the app always takes precedence.
 */
object ApiKeyProvider {
    private const val PREFS_FILE = "api_keys"
    private const val PREF_LASTFM_KEY = "lastfm_api_key"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    var lastFmApiKey: String
        get() = prefs.getString(PREF_LASTFM_KEY, null)?.takeIf { it.isNotBlank() }
            ?: io.github.uditkarode.able.BuildConfig.LASTFM_API_KEY
        set(value) = prefs.edit().putString(PREF_LASTFM_KEY, value.trim()).apply()

    val hasLastFmKey: Boolean get() = lastFmApiKey.isNotBlank()

    fun clearLastFmKey() = prefs.edit().remove(PREF_LASTFM_KEY).apply()
}

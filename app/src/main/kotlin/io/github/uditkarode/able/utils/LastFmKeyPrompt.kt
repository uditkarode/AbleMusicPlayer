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
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import io.github.uditkarode.able.R
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import kotlin.concurrent.thread

/**
 * Dedicated prompt for importing the Last.fm API key from inside the app.
 * Shown the first time discovery is used without a key, or whenever a
 * discovery request fails because the key is missing. The key is validated
 * with a live Last.fm call before being saved.
 */
object LastFmKeyPrompt {

    /** Several flows can hit a missing key at once; show one dialog, not N. */
    @Volatile
    private var showing = false

    /**
     * Shows the import dialog only if no key is configured.
     * @return true if the dialog was shown (i.e. the key was missing).
     */
    fun showIfMissing(context: Context, onSaved: (() -> Unit)? = null): Boolean {
        if (ApiKeyProvider.hasLastFmKey) return false
        show(context, onSaved)
        return true
    }

    /** Always shows the import dialog (used by Settings and retry flows). */
    fun show(context: Context, onSaved: (() -> Unit)? = null) {
        if (showing) return
        showing = true
        MaterialDialog(context).show {
            title(R.string.lastfm_key_title)
            message(R.string.lastfm_key_message)
            // NOTE: this callback fires on EVERY text change — all logic
            // belongs on the positive action, not here.
            input(hintRes = R.string.lastfm_key_hint) { _, _ -> }
            positiveButton(R.string.lastfm_key_save) { dialog ->
                val candidate = dialog.getInputField().text.toString().trim()
                if (candidate.isEmpty()) return@positiveButton
                thread {
                    val valid = LastFmClient.validateKey(candidate)
                    // The dialog may already be dismissed by the time the
                    // network round-trip finishes; posting to its decorView
                    // would silently drop the save. Use the main looper.
                    Handler(Looper.getMainLooper()).post {
                        showing = false
                        if (valid) {
                            ApiKeyProvider.lastFmApiKey = candidate
                            Toast.makeText(
                                context, R.string.lastfm_key_saved, Toast.LENGTH_SHORT
                            ).show()
                            onSaved?.invoke()
                        } else
                            Toast.makeText(
                                context, R.string.lastfm_key_invalid, Toast.LENGTH_LONG
                            ).show()
                    }
                }
            }
            negativeButton(android.R.string.cancel)
            onDismiss { showing = false }
        }
    }
}

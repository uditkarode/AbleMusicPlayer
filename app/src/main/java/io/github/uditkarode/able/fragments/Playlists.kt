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

package io.github.uditkarode.able.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.callbacks.onCancel
import com.afollestad.materialdialogs.callbacks.onShow
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.PlaylistAdapter
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import io.github.uditkarode.able.utils.SpotifyImport
import kotlinx.android.synthetic.main.playlists.*
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference

class Playlists: Fragment() {
    var mService: MusicService? = null
    var isBound = false
    private lateinit var serviceConn: ServiceConnection

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        serviceConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as MusicService.MusicBinder).getService()
                Shared.mService = service.getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
            }
        }
        return inflater.inflate(R.layout.playlists, container, false)
    }

    private fun progDialog(playId: String) {
        val dialog = MaterialDialog(activity as Context).noAutoDismiss().show {
            message(R.string.spot_adding)
            customView(R.layout.spotify_progress)

            onShow {
                Toast.makeText(activity as Context, "Sit back and relax! We're importing the" +
                        "songs right now.", Toast.LENGTH_LONG).show()
            }
            positiveButton(text = "")
            negativeButton(text = "Cancel") {
                this.cancel()
            }
            cancelable(false)
            onCancel {
                Toast.makeText(activity as Context, "Cancelled import!", Toast.LENGTH_SHORT).show()
            }
        }

        SpotifyImport.importList(playId, activity as FragmentActivity, dialog)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playlists_rv.adapter = PlaylistAdapter(Shared.getPlaylists(), WeakReference(this@Playlists))
        playlists_rv.layoutManager = LinearLayoutManager(activity as Context)
        var inputId = ""
        spotbut.setOnClickListener {
            spotbut.playSoundEffect(SoundEffectConstants.CLICK)
            MaterialDialog(activity as Context).show {
                title(R.string.spot_title)
                input(waitForPositiveButton = false) {dialog, textInp ->
                    val inputField = dialog.getInputField()
                    val validUrl = textInp.toString().replace("https://", "").split("/").toMutableList()
                    var isValid = true
                    if (validUrl.size <= 2 || validUrl[0] != "open.spotify.com" || validUrl[1] != "playlist") {
                        isValid = false
                    } else if (validUrl[2].contains("?")) {
                        inputId = validUrl[2].split("?")[0]
                    } else {
                        inputId = validUrl[2]
                    }

                    inputField.error = if (isValid) null else "Invalid Playlist URL!"
                    dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
                }
                getInputLayout().boxBackgroundColor = Color.parseColor("#212121")
                positiveButton(R.string.pos) {
                    progDialog(inputId)
                }
            }
        }
    }

    fun bindEvent(){
        if(Shared.serviceRunning(MusicService::class.java, activity as Context)) {
            try {
                (requireActivity().applicationContext).also {
                    it.bindService(Intent(it, MusicService::class.java), serviceConn, 0)
                }
            } catch(e: Exception){
                Log.e("ERR>", e.toString())
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
    }
}
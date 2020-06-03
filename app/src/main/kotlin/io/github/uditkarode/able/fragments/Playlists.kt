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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.PlaylistAdapter
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.services.SpotifyImportService
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.playlists.*
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class Playlists : Fragment() {
    var mService: MusicService? = null
    var isBound = false
    private lateinit var serviceConn: ServiceConnection
    private var isImporting = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WorkManager.getInstance(view.context).getWorkInfosForUniqueWorkLiveData("SpotifyImport")
            .observeForever { itt ->
                if (itt.isNotEmpty()) {
                    when (itt.first().state) {
                        WorkInfo.State.RUNNING -> {
                            isImporting = true
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            isImporting = false
                            (activity?.findViewById<RecyclerView>(R.id.playlists_rv)?.adapter as PlaylistAdapter).also { playlistAdapter ->
                                playlistAdapter.update(Shared.getPlaylists())
                                spotbut.setImageResource(R.drawable.ic_spot)
                            }
                        }
                        WorkInfo.State.ENQUEUED -> {
                            isImporting = true
                            spotbut.setImageResource(R.drawable.ic_cancle_action)
                        }
                        WorkInfo.State.CANCELLED -> {
                            isImporting = false
                            spotbut.setImageResource(R.drawable.ic_spot)
                        }
                        else -> {}
                    }
                }
            }
        playlists_rv.adapter = PlaylistAdapter(Shared.getPlaylists(), WeakReference(this@Playlists))
        playlists_rv.layoutManager = LinearLayoutManager(activity as Context)
        var inputId = ""
        spotbut.setOnClickListener {
            spotbut.playSoundEffect(SoundEffectConstants.CLICK)
            if (!isImporting) {
                MaterialDialog(activity as Context).show {
                    title(R.string.spot_title)
                    input(waitForPositiveButton = false) { dialog, textInp ->
                        val inputField = dialog.getInputField()
                        val validUrl =
                            textInp.toString().replace("https://", "")
                                .split("/").toMutableList()
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
                        val builder = Data.Builder()
                        builder.put("inputId", inputId)
                        WorkManager.getInstance(view.context)
                            .beginUniqueWork(
                                "SpotifyImport",
                                ExistingWorkPolicy.KEEP,
                                OneTimeWorkRequest.Builder(SpotifyImportService::class.java)
                                    .setInputData(builder.build())
                                    .setInitialDelay(0, TimeUnit.SECONDS)
                                    .build()
                            ).enqueue()
                        Toast.makeText(
                            activity as Context, "Sit back and relax! We're importing the" +
                                    "songs right now.", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                WorkManager.getInstance(view.context).cancelUniqueWork("SpotifyImport")
            }
        }
    }

    fun bindEvent() {
        if (Shared.serviceRunning(MusicService::class.java, activity as Context)) {
            try {
                (requireActivity().applicationContext).also {
                    it.bindService(Intent(it, MusicService::class.java), serviceConn, 0)
                }
            } catch (e: Exception) {
                Log.e("ERR>", e.toString())
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
    }
}
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
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
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
import io.github.uditkarode.able.events.ImportDoneEvent
import io.github.uditkarode.able.events.ImportStartedEvent
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.services.SpotifyImportService
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.playlists.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference

class Playlists : Fragment() {
    var mService: MusicService? = null
    var isBound = false
    private lateinit var serviceConn: ServiceConnection
    private var isImporting = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        EventBus.getDefault().register(this)
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun importStarted(@Suppress("UNUSED_PARAMETER") importStartedEvent: ImportStartedEvent){
        isImporting = true
        spotbut.setImageResource(R.drawable.ic_cancle_action)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun importDone(@Suppress("UNUSED_PARAMETER") importDoneEvent: ImportDoneEvent){
        isImporting = false
        WorkManager.getInstance(requireContext()).cancelUniqueWork("SpotifyImport")
        (activity?.findViewById<RecyclerView>(R.id.playlists_rv)?.adapter as PlaylistAdapter).also { playlistAdapter ->
            playlistAdapter.update(Shared.getPlaylists())
            spotbut.setImageResource(R.drawable.ic_spot)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playlists_rv.adapter = PlaylistAdapter(Shared.getPlaylists(), WeakReference(this@Playlists))
        playlists_rv.layoutManager = LinearLayoutManager(requireContext())
        var inputId = ""
        spotbut.setOnClickListener {
            if (!isImporting) {
                MaterialDialog(requireContext()).show {
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

                        inputField.error = if (isValid) null else getString(R.string.spot_invalid)
                        dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
                    }
                    getInputLayout().boxBackgroundColor = Color.parseColor("#212121")
                    positiveButton(R.string.pos) {
                        val builder = Data.Builder()
                        builder.put("inputId", inputId)
                        WorkManager.getInstance(view.context)
                            .beginUniqueWork(
                                "SpotifyImport",
                                ExistingWorkPolicy.REPLACE,
                                OneTimeWorkRequest.Builder(SpotifyImportService::class.java)
                                    .setInputData(builder.build())
                                    .build()
                            ).enqueue()
                        Toast.makeText(
                            requireContext(), getString(R.string.spot_importing), Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                EventBus.getDefault().post(ImportDoneEvent())
                WorkManager.getInstance(view.context).cancelUniqueWork("SpotifyImport")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
    }
}
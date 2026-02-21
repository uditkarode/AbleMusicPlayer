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

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.PlaylistAdapter
import io.github.uditkarode.able.databinding.PlaylistsBinding
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.model.song.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.services.SpotifyImportService
import io.github.uditkarode.able.utils.Shared
/**
 * The third fragment. Used to view/edit/play locally stored playlists.
 * Playlists are stored in the JSON format.
 */
class Playlists : Fragment(), MusicService.MusicClient {
    private var isImporting = false
    private var _binding: PlaylistsBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        MusicService.registerClient(this)
        _binding = PlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun updateEmptyState() {
        if (_binding == null) return
        val isEmpty = _binding!!.playlistsRv.adapter?.itemCount == 0
        _binding!!.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        _binding!!.playlistsRv.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding!!.playlistsRv.adapter = PlaylistAdapter(Shared.getPlaylists())
        _binding!!.playlistsRv.layoutManager = LinearLayoutManager(requireContext())
        _binding!!.playlistsRv.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { updateEmptyState() }
        })
        updateEmptyState()
        var inputId = ""
        _binding!!.spotbut.setOnClickListener {
            if (!isImporting) {
                MaterialDialog(requireContext()).show {
                    title(R.string.spot_title)
                    input(waitForPositiveButton = false) { dialog, textInp ->
                        val inputField = dialog.getInputField()
                        val validUrl =
                            textInp.toString().replace("https://", "")
                                .split("/").toMutableList()
                        var isValid = true
                        val playlistIndex = validUrl.indexOf("playlist")
                        if (validUrl.isEmpty() || validUrl[0] != "open.spotify.com"
                            || playlistIndex == -1 || playlistIndex + 1 >= validUrl.size) {
                            isValid = false
                        } else {
                            val rawId = validUrl[playlistIndex + 1]
                            inputId = if (rawId.contains("?")) rawId.split("?")[0] else rawId
                        }

                        inputField.error = if (isValid) null else getString(R.string.spot_invalid)
                        dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
                    }
                    getInputLayout().boxBackgroundColor = Color.parseColor("#212121")
                    positiveButton(R.string.pos) {
                        if (!Shared.isInternetConnected(requireContext())) {
                            Toast.makeText(
                                requireContext(), "No Internet Connection", Toast.LENGTH_LONG
                            ).show()
                            return@positiveButton
                        }
                        val intent = Intent(requireContext(), SpotifyImportService::class.java)
                        intent.putExtra("inputId", inputId)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            requireContext().startForegroundService(intent)
                        } else {
                            requireContext().startService(intent)
                        }
                        Toast.makeText(
                            requireContext(), getString(R.string.spot_importing), Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                MusicService.registeredClients.forEach { it.spotifyImportChange(false) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicService.unregisterClient(this)
        _binding = null
    }

    override fun playStateChanged(state: SongState) {}

    override fun songChanged() {}

    override fun durationChanged(duration: Int) {}

    override fun isExiting() {}

    override fun queueChanged(arrayList: ArrayList<Song>) {}

    override fun shuffleRepeatChanged(onShuffle: Boolean, onRepeat: Boolean) {}

    override fun indexChanged(index: Int) {}

    override fun isLoading(doLoad: Boolean) {}

    override fun spotifyImportChange(starting: Boolean) {
        if (starting) {
            isImporting = true
            _binding!!.spotbut.setImageResource(R.drawable.ic_cancle_action)
        } else {
            isImporting = false
            requireContext().stopService(Intent(requireContext(), SpotifyImportService::class.java))
            (activity?.findViewById<RecyclerView>(R.id.playlists_rv)?.adapter as PlaylistAdapter).also { playlistAdapter ->
                playlistAdapter.update(Shared.getPlaylists())
                _binding!!.spotbut.setImageResource(R.drawable.ic_spot)
                updateEmptyState()
            }
        }
    }

    override fun serviceStarted() {}
}
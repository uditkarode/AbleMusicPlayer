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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.LibraryDetail
import io.github.uditkarode.able.adapters.LibraryGroupAdapter
import io.github.uditkarode.able.databinding.LibraryBinding
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The Library fragment. Groups the user's local songs by artist or album and
 * launches [LibraryDetail] when a group is tapped.
 */
class Library : Fragment(), CoroutineScope {
    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    private var _binding: LibraryBinding? = null
    private val binding get() = _binding!!

    private var mode: Mode = Mode.ARTISTS
    private var allSongs: List<Song> = emptyList()
    private lateinit var adapter: LibraryGroupAdapter

    private enum class Mode { ARTISTS, ALBUMS }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = LibraryGroupAdapter(emptyList()) { group ->
            openDetail(group.label)
        }
        binding.libraryRv.adapter = adapter
        binding.libraryRv.layoutManager = LinearLayoutManager(requireContext())

        binding.libraryToggle.check(R.id.library_btn_artists)
        binding.libraryToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mode = if (checkedId == R.id.library_btn_albums) Mode.ALBUMS else Mode.ARTISTS
            refresh()
        }

        loadSongs()
    }

    override fun onResume() {
        super.onResume()
        // Songs may have been added/removed via downloads or playlist imports.
        loadSongs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
    }

    private fun loadSongs() {
        val ctx = context ?: return
        launch(Dispatchers.IO) {
            val loaded = Shared.getSongList(Constants.ableSongDir, ctx)
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.READ_MEDIA_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                loaded.addAll(Shared.getLocalSongs(ctx))
            }
            val sorted = loaded.sortedBy { it.name.uppercase(Locale.getDefault()) }
            launch(Dispatchers.Main) {
                allSongs = sorted
                refresh()
            }
        }
    }

    private fun refresh() {
        if (_binding == null) return
        val groups: List<LibraryGroupAdapter.Group> = when (mode) {
            Mode.ARTISTS -> allSongs
                .groupBy { it.artist.ifBlank { "Unknown" } }
                .map { (k, v) -> LibraryGroupAdapter.Group(k, v.size) }
                .sortedBy { it.label.uppercase(Locale.getDefault()) }
            Mode.ALBUMS -> allSongs
                .groupBy { it.album.ifBlank { "Unknown" } }
                .map { (k, v) -> LibraryGroupAdapter.Group(k, v.size) }
                .sortedBy { it.label.uppercase(Locale.getDefault()) }
        }
        adapter.update(groups)

        val emptyMsgRes = if (mode == Mode.ALBUMS) R.string.no_albums else R.string.no_artists
        binding.libraryEmptyState.text = getString(emptyMsgRes)
        val isEmpty = groups.isEmpty()
        binding.libraryEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.libraryRv.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun openDetail(label: String) {
        val filter = when (mode) {
            Mode.ARTISTS -> { s: Song -> s.artist.ifBlank { "Unknown" } == label }
            Mode.ALBUMS -> { s: Song -> s.album.ifBlank { "Unknown" } == label }
        }
        val filtered = ArrayList(allSongs.filter(filter))
        LibraryDetail.pendingSongs = filtered
        val intent = Intent(requireContext(), LibraryDetail::class.java)
            .putExtra("title", label)
        startActivity(intent)
    }
}

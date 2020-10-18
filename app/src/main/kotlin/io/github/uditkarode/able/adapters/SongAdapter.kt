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

package io.github.uditkarode.able.adapters

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.button.MaterialButton
import io.github.uditkarode.able.R
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import java.io.File
import java.lang.ref.WeakReference

/**
 * Shows songs on the Home fragment.
 */
@ExperimentalCoroutinesApi
class SongAdapter (
    private var songList: ArrayList<Song>,
    private val wr: WeakReference<Home>? = null,
    private val showArt: Boolean = false,
    private val mServiceFromPlayer :MusicService? = null
) : RecyclerView.Adapter<SongAdapter.RVVH>(), CoroutineScope, MusicService.MusicClient {
    private var registered = false

    override val coroutineContext = Dispatchers.Main + SupervisorJob()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RVVH {
        if (!registered) {
            registered = true
            MusicService.registerClient(this)
        }

        val layoutId = if (showArt) R.layout.song_img else R.layout.rv_item
        return RVVH(LayoutInflater.from(parent.context).inflate(layoutId, parent, false), showArt)
    }

    private var originalLength = songList.size
    override fun getItemCount() = songList.size

    private var currentIndex = -1
    private var onShuffle = false
    private lateinit var playingSong: Song

    override fun onBindViewHolder(holder: RVVH, position: Int) {
        val current = songList[position]
        holder.songName.text = current.name
        holder.artistName.text = current.artist
        if (showArt) {
            holder.albumArt.run {
                if (this != null) {
                    File(
                        Constants.ableSongDir.absolutePath + "/album_art",
                        File(current.filePath).nameWithoutExtension
                    ).also {
                        when {
                            it.exists() -> Glide.with(holder.getContext())
                                .load(it)
                                .placeholder(Shared.defBitmap.toDrawable(resources))
                                .signature(ObjectKey("home"))
                                .into(this)

                            else -> {
                                try {
                                    val sArtworkUri =
                                        Uri.parse("content://media/external/audio/albumart")
                                    val albumArtUri =
                                        ContentUris.withAppendedId(sArtworkUri, current.albumId)
                                    Glide
                                        .with(holder.getContext())
                                        .load(albumArtUri)
                                        .placeholder(Shared.defBitmap.toDrawable(resources))
                                        .signature(ObjectKey("home"))
                                        .into(this)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }

        }
        if (currentIndex >= 0 && currentIndex < songList.size && songList.size != 0) {
            if (current.placeholder) holder.songName.setTextColor(Color.parseColor("#66bb6a"))
            else {
                val service = wr?.get()?.mService?.value
                if(service != null) {
                    if (current.filePath == service.getPlayQueue()[service.getCurrentIndex()].filePath) {
                        holder.songName.setTextColor(Color.parseColor("#5e92f3"))
                    } else holder.songName.setTextColor(Color.parseColor("#fbfbfb"))
                }
            }
        }

        holder.itemView.setOnClickListener {
            var freshStart = false
            if (!current.placeholder) {
                if (!Shared.serviceRunning(MusicService::class.java, holder.itemView.context)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        holder.itemView.context.startForegroundService(
                            Intent(
                                holder.itemView.context,
                                MusicService::class.java
                            )
                        )
                    } else {
                        holder.itemView.context.startService(
                            Intent(
                                holder.itemView.context,
                                MusicService::class.java
                            )
                        )
                    }
                    wr?.get()!!.bindEvent()
                    freshStart = true
                }

                launch(Dispatchers.Default) {
                    var mService: MutableStateFlow<MusicService?> = MutableStateFlow(mServiceFromPlayer)
                    if(wr?.get()!=null)
                        mService = wr.get()!!.mService

                    val playSong = fun(){
                        if (currentIndex != position) {
                            currentIndex = position
                            launch(Dispatchers.Default) {
                                if (onShuffle) {
                                    mService.value?.addToQueue(current)
                                    mService.value?.setNextPrevious(next = true)
                                } else {
                                    currentIndex = position
                                    mService.value?.setQueue(songList)
                                    mService.value?.setIndex(position)
                                }

                                if(freshStart)
                                    MusicService.registeredClients.forEach(MusicService.MusicClient::serviceStarted)
                            }
                        }
                    }

                    if(mService.value != null) playSong()
                    else {
                        mService.collect {
                            if(it != null) {
                                playSong()
                            }
                        }
                    }
                }
            }
        }

        holder.addToPlaylist.setOnClickListener {
            val playlists = Shared.getPlaylists()
            val names = playlists.run {
                ArrayList<String>().also {
                    for (playlist in this) it.add(
                        playlist.name.replace(
                            ".json",
                            ""
                        )
                    )
                }
            }

            names.add(0, holder.itemView.context.getString(R.string.pq))
            names.add(1, holder.itemView.context.getString(R.string.crp))

            MaterialDialog(holder.itemView.context).show {
                listItems(items = names) { _, index, _ ->
                    when (index) {
                        0 -> {
                            if(mServiceFromPlayer==null)
                                wr?.get()?.mService!!.value!!.addToQueue(current)
                            else {
                                mServiceFromPlayer.addToPlayQueue(current)
                                songList.add(1,songList[position])
                                songList.removeAt(position)
                                notifyItemMoved(position, 1)
                            }
                        }
                        1 -> {
                            MaterialDialog(holder.itemView.context).show {
                                title(text = holder.itemView.context.getString(R.string.playlist_namei))
                                input(holder.itemView.context.getString(R.string.name_s)) { _, charSequence ->
                                    Shared.createPlaylist(
                                        charSequence.toString(),
                                        holder.itemView.context
                                    )
                                    Shared.addToPlaylist(Shared.getPlaylists().filter {
                                        it.name == "$charSequence.json"
                                    }[0], current, holder.itemView.context)
                                }
                                getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
                            }
                        }
                        else -> {
                            Shared.addToPlaylist(
                                playlists[index - 2],
                                current,
                                holder.itemView.context
                            )
                        }
                    }
                }
            }
        }

        holder.deleteFromDisk.setOnClickListener {
            MaterialDialog(holder.itemView.context).show {
                title(text = holder.itemView.context.getString(R.string.confirmation))
                message(
                    text = holder.itemView.context.getString(R.string.res_confirm_txt)
                        .format(current.name, current.filePath)
                )
                positiveButton(text = "Delete") {
                    val curFile = File(current.filePath)
                    val curArt =
                        File(
                            Constants.ableSongDir.absolutePath + "/album_art",
                            curFile.nameWithoutExtension
                        )

                    curFile.delete()
                    curArt.delete()
                    songList.removeAt(position)
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(current.filePath), null, null
                    )
                    notifyDataSetChanged()
                }
                negativeButton(text = holder.itemView.context.getString(R.string.cancel))
            }
        }
        if (!showArt) {
            holder.itemView.setOnLongClickListener {
                holder.buttonsPanel.visibility =
                    if (holder.buttonsPanel.visibility == View.VISIBLE) View.GONE
                    else View.VISIBLE
                true
            }
        }
    }

    class RVVH(itemView: View, showArt: Boolean) : RecyclerView.ViewHolder(itemView) {
        val songName = itemView.findViewById<TextView>(R.id.item_header)!!
        val artistName = itemView.findViewById<TextView>(R.id.item_artist)!!
        val buttonsPanel = itemView.findViewById<LinearLayout>(R.id.buttonsPanel)!!
        val addToPlaylist = itemView.findViewById<MaterialButton>(R.id.add_to_playlist)!!
        val deleteFromDisk = itemView.findViewById<MaterialButton>(R.id.delete_from_disk)!!
        var albumArt: ImageView? = if (showArt) itemView.findViewById(R.id.song_art) else null

        fun getContext(): Context = itemView.context
    }

    fun update(songList: ArrayList<Song>) {
        this.songList = songList
        originalLength = songList.size
        notifyDataSetChanged()
    }

    override fun playStateChanged(state: SongState) {}

    override fun songChanged() {
        val service = wr?.get()?.mService?.value
        if (service != null) {
            playingSong = service.run {
                this.getPlayQueue()[this.getCurrentIndex()]
            }
            launch(Dispatchers.Main) {
                notifyItemChanged(service.getPreviousIndex())
                notifyItemChanged(service.getCurrentIndex())
            }
        }
    }

    override fun durationChanged(duration: Int) {}

    override fun isExiting() {}

    override fun queueChanged(arrayList: ArrayList<Song>) {
        if (!showArt) songList = arrayList
    }

    override fun shuffleRepeatChanged(onShuffle: Boolean, onRepeat: Boolean) {
        this.onShuffle = onShuffle
    }

    override fun indexChanged(index: Int) {
        currentIndex = index
    }

    override fun isLoading(doLoad: Boolean) {}

    override fun spotifyImportChange(starting: Boolean) {}

    override fun serviceStarted() {}
}


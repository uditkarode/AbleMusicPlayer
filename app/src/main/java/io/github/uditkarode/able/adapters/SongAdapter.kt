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

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import com.google.android.material.button.MaterialButton
import io.github.uditkarode.able.R
import io.github.uditkarode.able.events.*
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Constants
import io.github.uditkarode.able.utils.Shared
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

class SongAdapter(private var songList: ArrayList<Song>, private val wr: WeakReference<Home>? = null): RecyclerView.Adapter<SongAdapter.RVVH>() {
    private var registered = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RVVH {
        if(!registered){
            registered = true
            EventBus.getDefault().register(this)
        }
        return RVVH(LayoutInflater.from(parent.context).inflate(R.layout.rv_item, parent, false))
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

        if(currentIndex > 0 && currentIndex < songList.size && songList.size != 0){
            if(current.placeholder) holder.songName.setTextColor(Color.parseColor("#66bb6a"))
            else {
                if(Shared.serviceLinked()){
                    if(current.filePath == Shared.mService.playQueue[Shared.mService.currentIndex].filePath) {
                        holder.songName.setTextColor(Color.parseColor("#5e92f3"))
                    }
                    else holder.songName.setTextColor(Color.parseColor("#fbfbfb"))
                }
            }
        }

        holder.itemView.setOnClickListener {
            if(!current.placeholder){
                if(!Shared.serviceRunning(MusicService::class.java, holder.itemView.context)){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        holder.itemView.context.startForegroundService(Intent(holder.itemView.context, MusicService::class.java))
                    } else {
                        holder.itemView.context.startService(Intent(holder.itemView.context, MusicService::class.java))
                    }

                    wr?.get()!!.bindEvent()
                }

                thread {
                    val mService: MusicService = if(Shared.serviceLinked()){
                        Shared.mService
                    } else {
                        @Suppress("ControlFlowWithEmptyBody")
                        while(!wr?.get()!!.isBound){}
                        wr.get()!!.mService!!
                    }

                    if(currentIndex != position){
                        currentIndex = position
                        (holder.itemView.context as Activity).runOnUiThread {
                            if(onShuffle){
                                mService.addToQueue(current)
                                mService.setNextPrevious(next = true)
                            } else {
                                currentIndex = position
                                mService.setQueue(songList)
                                mService.setIndex(position)
                                mService.setPlayPause(SongState.playing)
                            }

                            notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        holder.addToPlaylist.setOnClickListener {
            val playlists = Shared.getPlaylists()
            val names = playlists.run {
                ArrayList<String>().also { for(playlist in this) it.add(playlist.name.replace(".json", "")) }
            }

            names.add(0, "playing queue")
            names.add(1, "create playlist")

            MaterialDialog(holder.itemView.context).show {
                listItems(items = names){ _, index, _ ->
                    when(index){
                        0 -> wr?.get()?.mService!!.addToQueue(current)
                        1 -> {
                            MaterialDialog(holder.itemView.context).show {
                                title(text = "Enter the name of your new playlist")
                                input("Name"){ _, charSequence ->
                                    Shared.createPlaylist(charSequence.toString(), holder.itemView.context)
                                    Shared.addToPlaylist(Shared.getPlaylists().filter {
                                        it.name == "$charSequence.json"
                                    }[0], current, holder.itemView.context)
                                }
                                getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
                            }
                        }
                        else -> {
                            Shared.addToPlaylist(playlists[index-2], current, holder.itemView.context)
                        }
                    }
                }
            }
        }

        holder.deleteFromDisk.setOnClickListener {
            MaterialDialog(holder.itemView.context).show {
                title(text = "Confirmation")
                message(text = "Are you sure you want to delete ${current.name} (${current.filePath}) " +
                        "from disk?")
                positiveButton(text = "Delete"){
                    File(current.filePath).delete()
                    songList.removeAt(position)
                    notifyDataSetChanged()
                }
                negativeButton(text = "Cancel")
            }
        }

        holder.itemView.setOnLongClickListener {
            holder.buttonsPanel.visibility =
                if(holder.buttonsPanel.visibility == View.VISIBLE) View.GONE
                else View.VISIBLE
            true
        }
    }

    inner class RVVH(itemView: View): RecyclerView.ViewHolder(itemView) {
        val songName = itemView.findViewById<TextView>(R.id.item_header)!!
        val artistName = itemView.findViewById<TextView>(R.id.item_artist)!!
        val buttonsPanel = itemView.findViewById<LinearLayout>(R.id.buttonsPanel)!!
        val addToPlaylist = itemView.findViewById<MaterialButton>(R.id.add_to_playlist)!!
        val deleteFromDisk = itemView.findViewById<MaterialButton>(R.id.delete_from_disk)!!
    }

    fun update(songList: ArrayList<Song>){
        this.songList = songList
        originalLength = songList.size
        EventBus.getDefault().post(GetQueueEvent(songList))
        notifyDataSetChanged()
    }

    @Subscribe
    fun setupShuffleRepeat(songEvent: GetShuffleRepeatEvent){
        onShuffle = songEvent.onShuffle
    }

    @Subscribe
    fun indexUpdate(indexEvent: GetIndexEvent) {
        currentIndex = indexEvent.index
    }

    @Subscribe
    fun metadataUpdate(metaDataUpdate: GetMetaDataEvent) {
        songList = Shared.getSongList(Constants.ableSongDir)
        notifyDataSetChanged()
    }

    @Subscribe
    fun indexUpdate(sce: GetSongChangedEvent) {
        sce.toString() /* because the IDE doesn't like it unused */
        playingSong = wr?.get()?.mService.run {
            this!!.playQueue[this.currentIndex]
        }
        notifyDataSetChanged()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun songUpdate(getSong: GetSongEvent) {
        playingSong = getSong.song
        Handler().postDelayed({ notifyDataSetChanged() }, 100)
    }

    @Subscribe
    fun getQueueUpdate(songEvent: GetQueueEvent){
        songList = songEvent.queue
    }

    fun temp(song: Song){
        if(originalLength == songList.size){
            songList.add(song.run { song.placeholder = true ; song })
        } else {
            songList[originalLength] = song
        }

        notifyDataSetChanged()
    }
}
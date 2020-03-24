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

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import io.github.uditkarode.able.R
import io.github.uditkarode.able.events.*
import io.github.uditkarode.able.models.Playlist
import io.github.uditkarode.able.models.SongState
import io.github.uditkarode.able.services.MusicService
import io.github.uditkarode.able.utils.Shared
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class PlaylistAdapter(private var playlists: ArrayList<Playlist>): RecyclerView.Adapter<PlaylistAdapter.PLVH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PLVH {
        return PLVH(LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false))
    }

    override fun getItemCount() = playlists.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: PLVH, position: Int) {
        val current = playlists[position]
        val songs = Shared.getSongsFromPlaylist(current)
        val context = holder.itemView.context

        holder.playlistNameTv.text = current.name.replace(".json", "")
        holder.numberSongsTv.text = "${current.songs.length()} Songs"

        holder.playlistNameTv.typeface =
            Typeface.createFromAsset(context.assets, "fonts/interbold.otf")

        holder.numberSongsTv.typeface =
            Typeface.createFromAsset(context.assets, "fonts/inter.otf")

        holder.itemView.setOnClickListener {
            if(!Shared.serviceRunning(MusicService::class.java, holder.itemView.context)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.itemView.context.startForegroundService(Intent(holder.itemView.context, MusicService::class.java))
                } else {
                    holder.itemView.context.startService(Intent(holder.itemView.context, MusicService::class.java))
                }
            }

            //EventBus.getDefault().post(QueueEvent(Shared.getSongsFromPlaylist(current)))
            //EventBus.getDefault().post(IndexEvent(0))
            //EventBus.getDefault().post(PlayPauseEvent(SongState.playing))
        }

        holder.itemView.setOnLongClickListener {
            val songNames = ArrayList<String>()
            Shared.getSongsFromPlaylist(current).also { for (song in it) songNames.add(song.name) }

            MaterialDialog(holder.itemView.context).show {
                title(text = "Choose song to delete")
                listItems(items = songNames){ _, index, _ ->
                    Shared.removeFromPlaylist(current, songs[index])
                }
            }
            false
        }
    }

    fun update(newPlaylists: ArrayList<Playlist>){
        playlists = newPlaylists
        notifyDataSetChanged()
    }

    inner class PLVH(itemView: View): RecyclerView.ViewHolder(itemView){
        val playlistNameTv = itemView.findViewById<TextView>(R.id.playlist_name)!!
        val numberSongsTv = itemView.findViewById<TextView>(R.id.number_songs)!!
    }
}
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
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.uditkarode.able.R
import io.github.uditkarode.able.activities.LocalPlaylist
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.utils.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import java.lang.ref.WeakReference

/**
 * Lists songs in a local playlist.
 */
@ExperimentalCoroutinesApi
class LocalPlaylistAdapter(private val songList: ArrayList<Song>,
                           private val wr: WeakReference<LocalPlaylist>): RecyclerView.Adapter<LocalPlaylistAdapter.RVVH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RVVH =
        RVVH(LayoutInflater.from(parent.context).inflate(R.layout.rv_ytm_result, parent, false))

    override fun getItemCount() = songList.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RVVH, position: Int) {
        val current = songList[position]
        holder.songName.text = current.name
        holder.songUploader.text = "Song • " + current.artist

        holder.songAlbumArt.run {
            File(Constants.ableSongDir.absolutePath + "/album_art",
                File(current.filePath).nameWithoutExtension).also {
                if(it.exists())
                    Glide.with(holder.getContext())
                        .load(it)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .into(this)
                else
                {
                        val sArtworkUri =
                            Uri.parse("content://media/external/audio/albumart")
                        val albumArtURi =
                            ContentUris.withAppendedId(sArtworkUri, current.albumId)
                        if(current.albumId.toString() != "-1")
                        Glide
                            .with(holder.getContext())
                            .load(albumArtURi)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .into(this)
                        else
                            Glide.with(holder.getContext())
                                .load(
                                    BitmapFactory.decodeResource(
                                        holder.getContext().resources,
                                        R.drawable.def_albart
                                    )
                                )
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .into(this)
                }
            }
        }

        holder.songName.typeface =
            Typeface.createFromAsset(holder.songName.context.assets, "fonts/interbold.otf")

        holder.songUploader.typeface =
            Typeface.createFromAsset(holder.songName.context.assets, "fonts/inter.otf")

        holder.itemView.setOnClickListener {
            wr.get()?.itemPressed(songList, position)
        }
    }

    class RVVH(itemView: View): RecyclerView.ViewHolder(itemView) {
        val songName = itemView.findViewById<TextView>(R.id.vid_song)!!
        val songUploader = itemView.findViewById<TextView>(R.id.vid_uploader)!!
        val songAlbumArt = itemView.findViewById<ImageView>(R.id.vid_albart)!!

        fun getContext(): Context = itemView.context
    }
}
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

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.R

/**
 * Adapter for the Library fragment. Shows artist or album groups with the
 * number of songs in each; taps invoke [onClick] with the group label.
 */
class LibraryGroupAdapter(
    private var items: List<Group>,
    private val onClick: (Group) -> Unit
) : RecyclerView.Adapter<LibraryGroupAdapter.VH>() {

    /** A single row in the library list — either an artist or an album. */
    data class Group(val label: String, val count: Int)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.library_group_item, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.label.ifBlank { "Unknown" }
        holder.count.text = if (item.count == 1)
            holder.itemView.context.getString(R.string.one_song)
        else
            holder.itemView.context.getString(R.string.song_count, item.count)

        holder.name.typeface =
            Typeface.createFromAsset(holder.itemView.context.assets, "fonts/interbold.otf")
        holder.count.typeface =
            Typeface.createFromAsset(holder.itemView.context.assets, "fonts/inter.otf")

        holder.itemView.setOnClickListener { onClick(item) }
    }

    fun update(newItems: List<Group>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.lib_group_name)
        val count: TextView = itemView.findViewById(R.id.lib_group_count)
    }
}

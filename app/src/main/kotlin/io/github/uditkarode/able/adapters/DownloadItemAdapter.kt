package io.github.uditkarode.able.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.R

class DownloadItemAdapter(
    private var items: List<DownloadItem> = emptyList()
) : RecyclerView.Adapter<DownloadItemAdapter.VH>() {

    data class DownloadItem(
        val name: String,
        val artist: String,
        val status: String,
        val isActive: Boolean
    )

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.dl_item_name)
        val artist: TextView = view.findViewById(R.id.dl_item_artist)
        val status: TextView = view.findViewById(R.id.dl_item_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.download_item, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.artist.text = item.artist
        holder.status.text = item.status
        holder.status.setTextColor(
            if (item.isActive) Color.parseColor("#5e92f3")
            else Color.parseColor("#80fbfbfb")
        )
    }

    fun update(newItems: List<DownloadItem>) {
        items = newItems
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }
}

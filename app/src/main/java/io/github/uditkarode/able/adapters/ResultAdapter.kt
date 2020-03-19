package io.github.uditkarode.able.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.R
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.fragments.Search
import java.lang.ref.WeakReference

class ResultAdapter(private val songList: ArrayList<Song>, private val wr: WeakReference<Search>): RecyclerView.Adapter<ResultAdapter.RVVH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RVVH =
        RVVH(LayoutInflater.from(parent.context).inflate(R.layout.rv_dialog_item, parent, false))

    override fun getItemCount() = songList.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RVVH, position: Int) {
        val current = songList[position]
        holder.vidName.text = " " + current.name
        holder.vidChannel.text = " " + current.artist

        holder.vidName.typeface =
            Typeface.createFromAsset(holder.vidName.context.assets, "fonts/inter.otf")

        holder.vidChannel.typeface =
            Typeface.createFromAsset(holder.vidName.context.assets, "fonts/inter.otf")

        holder.titleTxt.typeface =
            Typeface.createFromAsset(holder.vidName.context.assets, "fonts/interbold.otf")

        holder.uploaderTxt.typeface =
            Typeface.createFromAsset(holder.vidName.context.assets, "fonts/interbold.otf")

        holder.titleTxt.isSelected = true
        holder.uploaderTxt.isSelected = true

        holder.itemView.setOnClickListener {
            wr.get()?.itemPressed(songList[position])
        }
    }

    inner class RVVH(itemView: View): RecyclerView.ViewHolder(itemView) {
        val vidName = itemView.findViewById<TextView>(R.id.vid_name)!!
        val vidChannel = itemView.findViewById<TextView>(R.id.vid_uploader)!!
        val titleTxt = itemView.findViewById<TextView>(R.id.title_txt)!!
        val uploaderTxt = itemView.findViewById<TextView>(R.id.uploader_txt)!!
    }
}
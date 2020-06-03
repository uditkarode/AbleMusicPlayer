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

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.ResultAdapter
import io.github.uditkarode.able.models.Song
import kotlinx.android.synthetic.main.search.*
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Collections.singletonList
import kotlin.concurrent.thread

class Search : Fragment() {
    private lateinit var itemPressed: SongCallback

    interface SongCallback {
        fun sendItem(song: Song)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.search, container, false)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            itemPressed = context as Activity as SongCallback
        } catch (e: ClassCastException) {
            throw ClassCastException(
                activity.toString()
                        + " must implement SongCallback"
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchBar: EditText = view.findViewById(R.id.search_bar)
        val searchRv: RecyclerView = view.findViewById(R.id.search_rv)

        loading_view.enableMergePathsForKitKatAndAbove(true)

        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == 6) {
                loading_view.progress = 0.3080229f
                loading_view.playAnimation()

                if(searchRv.visibility == View.VISIBLE){
                    searchRv.animate().alpha(0f).duration = 200
                    searchRv.visibility = View.GONE
                }
                val text = searchBar.text
                if(loading_view.visibility == View.GONE){
                    loading_view.alpha = 0f
                    loading_view.visibility = View.VISIBLE
                    loading_view.animate().alpha(1f).duration = 200
                }

                hideKeyboard(activity as Activity)
                val resultArray = ArrayList<Song>()

                try {
                    if(text.toString().startsWith("!")){
                        Log.e("INFO", "Searching YTMusic")
                        thread {
                            val query = text.replaceFirst(Regex("!\\s*"), "")
                            val extractor = YouTube.getSearchExtractor(query, singletonList(
                                YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
                            extractor.fetchPage()

                            for(song in extractor.initialPage.items) {
                                val ex = song as StreamInfoItem
                                resultArray.add(Song(
                                    name = ex.name,
                                    artist = ex.uploaderName,
                                    youtubeLink = ex.url
                                ))
                            }

                            activity?.runOnUiThread {
                                searchRv.adapter =
                                    ResultAdapter(resultArray, WeakReference(this@Search))
                                searchRv.layoutManager = LinearLayoutManager(activity as Context)
                                loading_view.visibility = View.GONE
                                loading_view.pauseAnimation()
                                searchRv.alpha = 0f
                                searchRv.visibility = View.VISIBLE
                                searchRv.animate().alpha(1f).duration = 200
                            }
                        }
                    } else {
                        thread {
                            val extractor = YouTube.getSearchExtractor(text.toString(), singletonList(
                                YoutubeSearchQueryHandlerFactory.VIDEOS), "")
                            extractor.fetchPage()

                            for(song in extractor.initialPage.items) {
                                val ex = song as StreamInfoItem
                                resultArray.add(Song(
                                    name = ex.name,
                                    artist = ex.uploaderName,
                                    youtubeLink = ex.url
                                ))
                            }

                            activity?.runOnUiThread {
                                searchRv.adapter =
                                    ResultAdapter(resultArray, WeakReference(this@Search))
                                searchRv.layoutManager = LinearLayoutManager(activity as Context)
                                loading_view.visibility = View.GONE
                                loading_view.pauseAnimation()
                                searchRv.alpha = 0f
                                searchRv.visibility = View.VISIBLE
                                searchRv.animate().alpha(1f).duration = 200
                            }
                        }
                    }
                } catch (e: IOException) {
                    Toast.makeText(activity as Context, "Something failed!", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            false
        }
    }

    fun itemPressed(song: Song) {
        itemPressed.sendItem(song)
    }

    private fun hideKeyboard(activity: Activity) {
        val imm: InputMethodManager =
            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = activity.currentFocus ?: View(activity)

        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

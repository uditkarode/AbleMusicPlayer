package io.github.uditkarode.able.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.PlaylistAdapter
import io.github.uditkarode.able.events.PlaylistEvent
import io.github.uditkarode.able.utils.Shared
import kotlinx.android.synthetic.main.playlists.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe


class Playlists: Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
        return inflater.inflate(R.layout.playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playlists_rv.adapter = PlaylistAdapter(Shared.getPlaylists())
        playlists_rv.layoutManager = LinearLayoutManager(activity as Context)
    }

    @Subscribe
    fun updateEvent(playlistEvent: PlaylistEvent){
        (playlists_rv.adapter as PlaylistAdapter).update(playlistEvent.playlists)
    }

    override fun onStop() {
        super.onStop()
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
    }
}
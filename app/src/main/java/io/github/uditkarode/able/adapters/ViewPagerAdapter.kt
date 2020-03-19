package io.github.uditkarode.able.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.fragments.Search
import io.github.uditkarode.able.fragments.Playlists

class ViewPagerAdapter(fm: FragmentManager, private val home: Home):
    FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    override fun getItem(position: Int): Fragment {
        return when(position) {
            0 -> home
            1 -> Search()
            else -> Playlists()
        }
    }

    override fun getCount() = 3
}
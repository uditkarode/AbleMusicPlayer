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

package io.github.uditkarode.able.activities

import android.Manifest
import android.os.Bundle
import io.github.dreierf.materialintroscreen.MaterialIntroActivity
import io.github.dreierf.materialintroscreen.SlideFragmentBuilder
import io.github.uditkarode.able.R
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The welcome screen that shows up when the required permissions haven't been granted.
 */
@ExperimentalCoroutinesApi
class Welcome: MaterialIntroActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addSlide(
            SlideFragmentBuilder()
                .backgroundColor(R.color.welcome_bg)
                .buttonsColor(R.color.colorAccent)
                .neededPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
                .image(io.github.dreierf.materialintroscreen.R.drawable.ic_next)
                .title(getString(R.string.welcome).format("AbleMusic"))
                .description(getString(R.string.storage_perm))
                .build()
        )

        addSlide(
            SlideFragmentBuilder()
                .backgroundColor(R.color.welcome_bg)
                .buttonsColor(R.color.colorAccent)
                .image(R.drawable.welcome_icon  )
                .title("Thanks for installing!")
                .description("Able is free and open source. Please make sure you downloaded and installed Able from GitHub or" +
                        " the telegram channel @ableci or @AbleMusicPlayer")
                .build()
        )
    }
}
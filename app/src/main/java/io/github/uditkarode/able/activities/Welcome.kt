package io.github.uditkarode.able.activities

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.view.View
import io.github.dreierf.materialintroscreen.MaterialIntroActivity
import io.github.dreierf.materialintroscreen.MessageButtonBehaviour
import io.github.dreierf.materialintroscreen.SlideFragmentBuilder
import io.github.uditkarode.able.R


class Welcome: MaterialIntroActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addSlide(
            SlideFragmentBuilder()
                .backgroundColor(R.color.white)
                .buttonsColor(R.color.colorAccent)
                .neededPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
                .image(io.github.dreierf.materialintroscreen.R.drawable.ic_next)
                .title("Welcome to AbleMusic!")
                .description("The storage permission is required to proceed. This is needed" +
                        " so that Able can download songs to your storage.")
                .build()
        )
    }
}
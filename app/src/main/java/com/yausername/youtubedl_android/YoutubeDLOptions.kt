package com.yausername.youtubedl_android

import java.util.*

class YoutubeDLOptions {
    private val options: MutableMap<String, String?> =
        HashMap()

    fun setOption(key: String, value: String): YoutubeDLOptions {
        options[key] = value
        return this
    }

    fun setOption(key: String, value: Number): YoutubeDLOptions {
        options[key] = value.toString()
        return this
    }

    fun setOption(key: String): YoutubeDLOptions {
        options[key] = null
        return this
    }

    fun buildOptions(): List<String> {
        val optionsList: MutableList<String> =
            ArrayList()
        for ((name, value) in options) {
            optionsList.add(name)
            if (null != value) optionsList.add(value)
        }
        return optionsList
    }
}
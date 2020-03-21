package com.yausername.youtubedl_android

import java.util.*

class YoutubeDLRequest {
    private var urls: List<String>
    private val options = YoutubeDLOptions()

    constructor(url: String) {
        urls = Arrays.asList(url)
    }

    constructor(urls: List<String>) {
        this.urls = urls
    }

    fun setOption(key: String, value: String): YoutubeDLRequest {
        options.setOption(key, value)
        return this
    }

    fun setOption(key: String, value: Number): YoutubeDLRequest {
        options.setOption(key, value)
        return this
    }

    fun setOption(key: String?): YoutubeDLRequest {
        options.setOption(key!!)
        return this
    }

    fun buildCommand(): List<String> {
        val command: MutableList<String> =
            ArrayList()
        command.addAll(options.buildOptions())
        command.addAll(urls)
        return command
    }
}
package com.yausername.youtubedl_android.utils

import com.orhanobut.logger.Logger
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

class StreamGobbler(private val buffer: StringBuffer, private val stream: InputStream) :
    Thread() {
    override fun run() {
        try {
            val `in`: Reader = InputStreamReader(stream, "UTF-8")
            var nextChar: Int
            while (`in`.read().also { nextChar = it } != -1) {
                buffer.append(nextChar.toChar())
            }
        } catch (e: IOException) {
            Logger.e(e, "failed to read stream")
        }
    }

    init {
        start()
    }
}
package com.yausername.youtubedl_android

import android.app.Application
import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.yausername.youtubedl_android.utils.YoutubeDLUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel

object YoutubeDLUpdater {
    private const val releasesUrl =
        "https://api.github.com/repos/yausername/youtubedl-lazy/releases/latest"
    private const val sharedPrefsName = "youtubedl-android"
    private const val youtubeDLVersionKey = "youtubeDLVersion"

    @Throws(IOException::class, YoutubeDLException::class)
    internal fun update(application: Application): UpdateStatus {
        val json = checkForUpdate(application) ?: return UpdateStatus.ALREADY_UP_TO_DATE
        val downloadUrl = getDownloadUrl(json)
        val file = download(application, downloadUrl)
        var youtubeDLDir: File? = null
        try {
            youtubeDLDir = getYoutubeDLDir(application)
            //purge older version
            YoutubeDLUtils.delete(youtubeDLDir)
            //install newer version
            youtubeDLDir.mkdirs()
            YoutubeDLUtils.unzip(file, youtubeDLDir)
        } catch (e: Exception) {
            //if something went wrong restore default version
            YoutubeDLUtils.delete(youtubeDLDir!!)
            YoutubeDL.getYtdlInstance().initYoutubeDL(application, youtubeDLDir!!)
            throw e
        } finally {
            file.delete()
        }
        updateSharedPrefs(application, getTag(json))
        return UpdateStatus.DONE
    }

    private fun updateSharedPrefs(
        application: Application,
        tag: String
    ) {
        val pref = application.getSharedPreferences(
            sharedPrefsName,
            Context.MODE_PRIVATE
        )
        val editor = pref.edit()
        editor.putString(youtubeDLVersionKey, tag)
        editor.apply()
    }

    @Throws(IOException::class)
    private fun checkForUpdate(application: Application): JsonNode? {
        val url = URL(releasesUrl)
        val json = YoutubeDL.objectMapper.readTree(url)
        val newVersion = getTag(json)
        val pref = application.getSharedPreferences(
            sharedPrefsName,
            Context.MODE_PRIVATE
        )
        val oldVersion = pref.getString(youtubeDLVersionKey, null)
        return if (newVersion == oldVersion) {
            null
        } else json
    }

    private fun getTag(json: JsonNode): String {
        return json["tag_name"].asText()
    }

    @Throws(IOException::class, YoutubeDLException::class)
    private fun getDownloadUrl(json: JsonNode): String {
        val assets =
            json["assets"] as ArrayNode
        var downloadUrl = ""
        for (asset in assets) {
            if (YoutubeDL.youtubeDLFile == asset["name"].asText()) {
                downloadUrl = asset["browser_download_url"].asText()
                break
            }
        }
        if (downloadUrl.isEmpty()) throw YoutubeDLException("unable to get download url")
        return downloadUrl
    }

    @Throws(YoutubeDLException::class, IOException::class)
    private fun download(application: Application, url: String): File {
        var file: File? = null
        var `in`: InputStream? = null
        var out: FileOutputStream? = null
        var inChannel: ReadableByteChannel? = null
        var outChannel: FileChannel? = null
        try {
            val downloadUrl = URL(url)
            `in` = downloadUrl.openStream()
            inChannel = Channels.newChannel(`in`!!)
            file = File.createTempFile("youtube_dl", "zip", application.cacheDir)
            out = FileOutputStream(file!!)
            outChannel = out.channel
            var bytesRead: Long
            var transferPosition: Long = 0
            while (outChannel.transferFrom(
                        inChannel, transferPosition, (1 shl 24.toLong()
                            .toInt()).toLong()
                    )
                    .also { bytesRead = it } > 0
            ) {
                transferPosition += bytesRead
            }
        } catch (e: Exception) {
            // delete temp file if something went wrong
            if (null != file && file.exists()) {
                file.delete()
            }
            throw e
        } finally {
            `in`?.close()
            inChannel?.close()
            out?.close()
            outChannel?.close()
        }
        return file!!
    }

    private fun getYoutubeDLDir(application: Application): File {
        val baseDir = File(application.filesDir, YoutubeDL.baseName)
        return File(baseDir, YoutubeDL.youtubeDLName)
    }

    enum class UpdateStatus {
        DONE, ALREADY_UP_TO_DATE
    }
}
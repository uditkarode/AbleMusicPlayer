package com.yausername.youtubedl_android

import android.app.Application
import com.fasterxml.jackson.databind.ObjectMapper
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.BuildConfig
import com.orhanobut.logger.Logger
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLUpdater.UpdateStatus
import com.yausername.youtubedl_android.mapper.VideoInfo
import com.yausername.youtubedl_android.utils.StreamGobbler
import com.yausername.youtubedl_android.utils.StreamProcessExtractor
import com.yausername.youtubedl_android.utils.YoutubeDLUtils
import io.github.uditkarode.able.R
import java.io.File
import java.io.IOException
import java.util.*

class YoutubeDL private constructor() {
    private var initialized = false
    private var pythonPath: File? = null
    private var youtubeDLPath: File? = null
    private var binDir: File? = null
    private var ENV_LD_LIBRARY_PATH: String? = null
    private var ENV_SSL_CERT_FILE: String? = null

    @Synchronized
    @Throws(YoutubeDLException::class)
    fun init(application: Application) {
        if (initialized) return
        initLogger()
        val baseDir =
            File(application.filesDir, baseName)
        if (!baseDir.exists()) baseDir.mkdir()
        val packagesDir = File(baseDir, packagesRoot)
        binDir = File(packagesDir, "usr/bin")
        pythonPath = File(packagesDir, pythonBin)
        val youtubeDLDir = File(baseDir, youtubeDLName)
        youtubeDLPath = File(youtubeDLDir, youtubeDLBin)
        ENV_LD_LIBRARY_PATH = packagesDir.absolutePath + "/usr/lib"
        ENV_SSL_CERT_FILE = packagesDir.absolutePath + "/usr/etc/tls/cert.pem"
        initPython(application, packagesDir)
        initYoutubeDL(application, youtubeDLDir)
        initialized = true
    }

    @Throws(YoutubeDLException::class)
    fun initYoutubeDL(
        application: Application,
        youtubeDLDir: File
    ) {
        if (!youtubeDLDir.exists()) {
            youtubeDLDir.mkdirs()
            try {
                YoutubeDLUtils.unzip(
                    application.resources.openRawResource(R.raw.youtube_dl),
                    youtubeDLDir
                )
            } catch (e: IOException) {
                YoutubeDLUtils.delete(youtubeDLDir)
                throw YoutubeDLException("failed to initialize", e)
            }
        }
    }

    @Throws(YoutubeDLException::class)
    protected fun initPython(
        application: Application,
        packagesDir: File
    ) {
        if (!pythonPath!!.exists()) {
            if (!packagesDir.exists()) {
                packagesDir.mkdirs()
            }
            try {
                YoutubeDLUtils.unzip(
                    application.resources.openRawResource(R.raw.python3_7_arm), packagesDir
                )
            } catch (e: IOException) {
                // delete for recovery later
                YoutubeDLUtils.delete(pythonPath)
                throw YoutubeDLException("failed to initialize", e)
            }
            pythonPath!!.setExecutable(true)
        }
    }

    private fun initLogger() {
        Logger.addLogAdapter(object : AndroidLogAdapter() {
            override fun isLoggable(priority: Int, tag: String?): Boolean {
                return BuildConfig.DEBUG
            }
        })
    }

    private fun assertInit() {
        check(initialized) { "instance not initialized" }
    }

    @Throws(YoutubeDLException::class, InterruptedException::class)
    fun getInfo(url: String?): VideoInfo {
        val request = YoutubeDLRequest(url!!)
        request.setOption("--dump-json")
        val response = execute(request, null)
        val videoInfo: VideoInfo
        videoInfo = try {
            objectMapper.readValue(
                response.out,
                VideoInfo::class.java
            )
        } catch (e: IOException) {
            throw YoutubeDLException("Unable to parse video information", e)
        }
        return videoInfo
    }

    @JvmOverloads
    @Throws(YoutubeDLException::class, InterruptedException::class)
    fun execute(
        request: YoutubeDLRequest,
        callback: DownloadProgressCallback? = null
    ): YoutubeDLResponse {
        assertInit()
        val youtubeDLResponse: YoutubeDLResponse
        val process: Process
        val exitCode: Int
        val outBuffer = StringBuffer() //stdout
        val errBuffer = StringBuffer() //stderr
        val startTime = System.currentTimeMillis()
        val args = request.buildCommand()
        val command: MutableList<String> =
            ArrayList()
        command.addAll(
            Arrays.asList(
                pythonPath!!.absolutePath,
                youtubeDLPath!!.absolutePath
            )
        )
        command.addAll(args)
        val processBuilder = ProcessBuilder(command)
        val env =
            processBuilder.environment()
        env["LD_LIBRARY_PATH"] = ENV_LD_LIBRARY_PATH
        env["SSL_CERT_FILE"] = ENV_SSL_CERT_FILE
        env["PATH"] = System.getenv("PATH") + ":" + binDir!!.absolutePath
        process = try {
            processBuilder.start()
        } catch (e: IOException) {
            throw YoutubeDLException(e)
        }
        val outStream = process.inputStream
        val errStream = process.errorStream
        val stdOutProcessor =
            StreamProcessExtractor(outBuffer, outStream, callback)
        val stdErrProcessor = StreamGobbler(errBuffer, errStream)
        exitCode = try {
            stdOutProcessor.join()
            stdErrProcessor.join()
            process.waitFor()
        } catch (e: InterruptedException) {
            process.destroy()
            throw e
        }
        val out = outBuffer.toString()
        val err = errBuffer.toString()
        if (exitCode > 0) {
            throw YoutubeDLException(err)
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        youtubeDLResponse = YoutubeDLResponse(command, exitCode, elapsedTime, out, err)
        return youtubeDLResponse
    }

    @Synchronized
    @Throws(YoutubeDLException::class)
    fun updateYoutubeDL(application: Application): UpdateStatus {
        return try {
            YoutubeDLUpdater.update(application)
        } catch (e: IOException) {
            throw YoutubeDLException("failed to update youtube-dl", e)
        }
    }

    companion object {
        val instance = YoutubeDL()
        const val baseName = "youtubedl-android"
        private const val packagesRoot = "packages"
        private const val pythonBin = "usr/bin/python"
        const val youtubeDLName = "youtube-dl"
        private const val youtubeDLBin = "__main__.py"
        const val youtubeDLFile = "youtube_dl.zip"
        @JvmField
        val objectMapper = ObjectMapper()

        fun getYtdlInstance() = instance
    }
}
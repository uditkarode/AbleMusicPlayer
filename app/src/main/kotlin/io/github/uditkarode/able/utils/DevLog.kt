/*
    Copyright 2026 AbleMusicPlayer contributors

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

package io.github.uditkarode.able.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Developer logging. When the "Developer logs" settings switch is on, a
 * background thread tails this app's logcat stream (`logcat -v time -T 1`,
 * own-UID entries only — Android never exposes other apps' logs) and appends
 * every line to a daily file under [Constants.logsDir]. API calls add
 * structured lines through [ApiLoggingInterceptor]; fatal crashes are written
 * synchronously by an uncaught-exception hook so they survive process death.
 *
 * Files are named `devlog-YYYY-MM-DD.log` (rollover suffix `-2`, `-3`, …
 * past [MAX_FILE_BYTES]) and pruned after [RETENTION_DAYS]. Retrieve them
 * with `adb pull <logsDir>` or the Settings "Export logs" entry.
 */
object DevLog {
    private const val TAG = "DevLog"
    private const val KEY = "developer_logs"
    private const val PREFIX = "devlog-"
    private const val EXT = ".log"
    private const val RETENTION_DAYS = 7
    private const val MAX_FILE_BYTES = 5L * 1024 * 1024

    /** Last.fm keys travel in the query string; masked in every written line. */
    private val API_KEY_RE = Regex("""api_key=[^&\s"']+""")

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val stampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** True while capture runs; [ApiLoggingInterceptor] gates on this. */
    @Volatile
    var active = false
        private set

    private var process: Process? = null
    private var readerThread: Thread? = null
    private var writer: FileWriter? = null
    private var currentFile: File? = null
    private var logsDir: File? = null
    private var appVersion = "?"

    /** Settings switch state (default off). */
    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false)

    /**
     * Called once from AbleApplication. Primes the version string, installs
     * the crash hook and resumes capture when the toggle is on.
     */
    fun init(context: Context) {
        appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: PackageManager.NameNotFoundException) { "?" }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (active) runCatching { writeCrash(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }

        if (isEnabled(context)) start(context)
    }

    /** Starts logcat capture (no-op when already running). */
    @Synchronized
    fun start(context: Context) {
        if (active) return
        try {
            // -T 1 skips the pre-existing buffer and streams new entries only.
            val proc = ProcessBuilder("logcat", "-v", "time", "-T", "1")
                .redirectErrorStream(true)
                .start()
            process = proc
            active = true
            logsDir = Constants.logsDir.apply { mkdirs() }
            cleanup(logsDir!!)
            append(stamp("start | Able $appVersion | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | ${Build.MANUFACTURER} ${Build.MODEL}"))

            readerThread = Thread({
                try {
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { append(it) }
                    }
                } catch (_: Exception) {
                    // Stream torn down (stop() or process death); state handled below.
                }
                synchronized(this@DevLog) {
                    if (active) {
                        // logcat exited on its own — flip off so the API
                        // interceptor stops logging into a void.
                        active = false
                        closeWriter()
                        Log.w(TAG, "logcat stream ended unexpectedly")
                    }
                }
            }, "DevLog-reader").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "logcat capture unavailable: $e")
            active = false
        }
    }

    /** Stops capture, writes the end marker and closes the day's file. */
    @Synchronized
    fun stop() {
        if (!active) return
        active = false
        runCatching { process?.destroy() }
        process = null
        append(stamp("stop"))
        closeWriter()
    }

    /** Masks API keys so the log is safe to share. */
    fun sanitize(text: String): String = text.replace(API_KEY_RE, "api_key=***")

    /** Zip of every retained log file (newest first) in the internal cache. */
    fun exportZip(context: Context): File? {
        val files = Constants.logsDir
            .listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(EXT) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        if (files.isEmpty()) return null

        val outDir = File(context.cacheDir, "devlog").apply { mkdirs() }
        val zip = File(outDir, "devlog-export.zip")
        return try {
            ZipOutputStream(zip.outputStream().buffered()).use { zos ->
                files.forEach { file ->
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            zip
        } catch (e: Exception) {
            Log.w(TAG, "export failed: $e")
            zip.delete()
            null
        }
    }

    /** Single writer entry point: rotation + sanitize + flush per line. */
    @Synchronized
    private fun append(raw: String) {
        val dir = logsDir ?: return
        try {
            val today = dayFormat.format(Date())
            var file = currentFile
            if (file == null || fileDate(file) != today || file.length() >= MAX_FILE_BYTES) {
                closeWriter()
                file = nextFile(dir, today).also { currentFile = it }
                writer = FileWriter(file, true)
            }
            writer?.let {
                it.write(sanitize(raw))
                it.write("\n")
                it.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "write failed: $e")
        }
    }

    /** Session/crash marker in logcat's time format so the file reads uniformly. */
    @Synchronized
    private fun stamp(text: String): String =
        "${stampFormat.format(Date())} I/$TAG: $text"

    /** Fatal exceptions bypass logcat (the reader dies with the process). */
    private fun writeCrash(thread: Thread, throwable: Throwable) {
        append(stamp("=== CRASH on thread '${thread.name}' ==="))
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        sw.toString().split('\n').forEach { if (it.isNotBlank()) append(it) }
        append(stamp("=== end crash ==="))
    }

    /** Next file for [date]: reuse the open, non-full one, else roll the suffix. */
    private fun nextFile(dir: File, date: String): File {
        var candidate = File(dir, "$PREFIX$date$EXT")
        var index = 1
        while (candidate.exists() && candidate.length() >= MAX_FILE_BYTES) {
            index++
            candidate = File(dir, "$PREFIX$date-$index$EXT")
        }
        return candidate
    }

    /** Calendar day encoded in a log file name. */
    private fun fileDate(file: File): String =
        file.name.removePrefix(PREFIX).removeSuffix(EXT).substringBeforeLast('-')

    private fun closeWriter() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
    }

    /** Deletes log files older than [RETENTION_DAYS]. */
    private fun cleanup(dir: File) {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 3600 * 1000
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(EXT) }
            ?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }
}

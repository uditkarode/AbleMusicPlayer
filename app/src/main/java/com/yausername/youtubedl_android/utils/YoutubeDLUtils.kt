package com.yausername.youtubedl_android.utils

import com.orhanobut.logger.Logger
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object YoutubeDLUtils {
    @Throws(IOException::class)
    fun unzip(zipFile: File?, targetDirectory: File?) {
        unzip(FileInputStream(zipFile), targetDirectory)
    }

    @Throws(IOException::class)
    fun unzip(inputStream: InputStream?, targetDirectory: File?) {
        val zis = ZipInputStream(
            BufferedInputStream(inputStream)
        )
        try {
            var ze: ZipEntry
            var count: Int
            val buffer = ByteArray(8192)
            while (zis.nextEntry.also { ze = it } != null) {
                val file = File(targetDirectory, ze.name)
                val dir = if (ze.isDirectory) file else file.parentFile
                if (!dir.isDirectory && !dir.mkdirs()) throw FileNotFoundException(
                    "Failed to ensure directory: " +
                            dir.absolutePath
                )
                if (ze.isDirectory) continue
                val fout = FileOutputStream(file)
                try {
                    while (zis.read(buffer).also { count = it } != -1) fout.write(
                        buffer,
                        0,
                        count
                    )
                } finally {
                    fout.close()
                }
            }
        } finally {
            zis.close()
        }
    }

    @Throws(FileNotFoundException::class)
    fun deleteIfExists(file: File) {
        if (file.isDirectory) {
            for (c in file.listFiles()) deleteIfExists(c)
        }
        if (!file.delete()) throw FileNotFoundException("Failed to delete file: $file")
    }

    fun delete(file: File): Boolean {
        try {
            deleteIfExists(file)
        } catch (e: FileNotFoundException) {
            Logger.e(e, "unable to delete file")
            return false
        }
        return true
    }
}
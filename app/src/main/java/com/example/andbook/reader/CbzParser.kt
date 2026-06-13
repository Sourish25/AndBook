package com.example.andbook.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object CbzParser {

    private var cachedUri: String? = null
    private var cachedFile: File? = null

    @Synchronized
    private fun getLocalFile(context: Context, uriString: String): File? {
        if (cachedUri == uriString && cachedFile?.exists() == true) {
            return cachedFile
        }

        try {
            val uri = Uri.parse(uriString)
            val hash = uriString.hashCode().toString()
            val tempFile = File(context.cacheDir, "cbz_cache_$hash.cbz")

            if (tempFile.exists() && tempFile.length() > 0) {
                cachedUri = uriString
                cachedFile = tempFile
                return tempFile
            }

            // Clear old cache files
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("cbz_cache_")) {
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Copy file to local cache
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                cachedUri = uriString
                cachedFile = tempFile
                return tempFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getPages(context: Context, uriString: String): List<String> {
        val pages = mutableListOf<String>()
        val file = getLocalFile(context, uriString)
        if (file != null) {
            try {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory) {
                            val name = entry.name.lowercase()
                            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                                name.endsWith(".png") || name.endsWith(".webp")) {
                                pages.add(entry.name)
                            }
                        }
                    }
                }
                return pages.sortedWith(String.CASE_INSENSITIVE_ORDER)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback to sequential read if local copy fails
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val zis = ZipInputStream(input)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.lowercase()
                        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                            name.endsWith(".png") || name.endsWith(".webp")) {
                            pages.add(entry.name)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return pages.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun renderPage(context: Context, uriString: String, entryName: String): Bitmap? {
        val file = getLocalFile(context, uriString)
        if (file != null) {
            try {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry(entryName)
                    if (entry != null) {
                        zip.getInputStream(entry).use { zipInput ->
                            return BitmapFactory.decodeStream(zipInput)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback to sequential read if local copy fails
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val zis = ZipInputStream(input)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        val bos = ByteArrayOutputStream()
                        zis.copyTo(bos)
                        val bytes = bos.toByteArray()
                        zis.closeEntry()
                        zis.close()
                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

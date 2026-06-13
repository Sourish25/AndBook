package com.example.andbook.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.andbook.data.Book
import com.example.andbook.data.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object BookCoverHelper {

    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(this.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun getCover(context: Context, book: Book): Bitmap? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "book_covers")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val cacheFile = File(cacheDir, book.uri.md5() + ".jpg")
        
        if (cacheFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) return@withContext bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Cache miss: Extract/Render cover
        val bitmap = try {
            val uri = Uri.parse(book.uri)
            when (book.format) {
                BookFormat.PDF -> PdfRendererHelper.renderPage(context, book.uri, 0)
                BookFormat.CBZ -> {
                    val pages = CbzParser.getPages(context, book.uri)
                    if (pages.isNotEmpty()) CbzParser.renderPage(context, book.uri, pages[0]) else null
                }
                BookFormat.EPUB -> EpubParser.getCover(context, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        // Save to cache
        if (bitmap != null) {
            try {
                cacheFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext bitmap
    }
}

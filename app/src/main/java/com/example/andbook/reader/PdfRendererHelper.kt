package com.example.andbook.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri

object PdfRendererHelper {

    fun getPageCount(context: Context, uriString: String): Int {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                renderer.close()
                count
            } ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun renderPage(context: Context, uriString: String, pageIndex: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val renderer = PdfRenderer(pfd)
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                    renderer.close()
                    return null
                }
                val page = renderer.openPage(pageIndex)
                
                // Target a high DPI matching the device's screen density (minimum 300 DPI, capped at 400 DPI)
                val screenDpi = context.resources.displayMetrics.densityDpi
                val targetDpi = screenDpi.coerceIn(300, 400)
                
                // Convert PDF points (1/72 inch) to target DPI pixels
                val scaleFactor = targetDpi / 72f
                val width = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                val height = (page.height * scaleFactor).toInt().coerceAtLeast(1)
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE) // PDFs can have transparent backgrounds
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

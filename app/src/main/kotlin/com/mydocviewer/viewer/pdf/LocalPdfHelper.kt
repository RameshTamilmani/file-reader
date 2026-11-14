package com.mydocviewer.viewer.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface PdfInspectionResult {
    data class Ready(val cachedFile: File, val pageCount: Int) : PdfInspectionResult
    data class PasswordRequired(val cachedFile: File) : PdfInspectionResult
    data class Error(val message: String) : PdfInspectionResult
}

object LocalPdfHelper {

    suspend fun prepareLocalCopy(context: Context, uri: Uri): PdfInspectionResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
                val outFile = File(cacheDir, "doc_${uri.toString().hashCode()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                } ?: return@withContext PdfInspectionResult.Error("Cannot open PDF file stream.")

                if (outFile.length() == 0L) {
                    return@withContext PdfInspectionResult.Error("PDF file is empty (0 bytes).")
                }

                // Inspect with Android framework PdfRenderer (throws SecurityException if password-protected)
                try {
                    ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            PdfInspectionResult.Ready(outFile, renderer.pageCount)
                        }
                    }
                } catch (sec: SecurityException) {
                    PdfInspectionResult.PasswordRequired(outFile)
                }
            }.getOrElse { ex ->
                PdfInspectionResult.Error(ex.localizedMessage ?: "Failed to read PDF document.")
            }
        }

    suspend fun renderThumbnail(file: File, pageIndex: Int, widthPx: Int = 180): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                        renderer.openPage(pageIndex).use { page ->
                            val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                            val heightPx = (widthPx * ratio).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
}

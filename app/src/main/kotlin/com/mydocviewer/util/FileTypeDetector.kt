package com.mydocviewer.util

import android.content.ContentResolver
import android.net.Uri
import com.mydocviewer.domain.model.DocumentType
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object FileTypeDetector {

    private const val MAX_ZIP_ENTRIES = 256
    private const val MAX_SCAN_BYTES = 8 * 1024 * 1024
    private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun detect(
        contentResolver: ContentResolver,
        uri: Uri,
        displayName: String?,
        reportedMimeType: String?
    ): DocumentType {
        if (displayName?.endsWith(".pptx", ignoreCase = true) == true ||
            reportedMimeType.equals(PPTX_MIME_TYPE, ignoreCase = true)
        ) {
            return DocumentType.POWERPOINT
        }
        return runCatching {
            contentResolver.openInputStream(uri)?.use(::detectByContent)
                ?: DocumentType.UNSUPPORTED
        }.getOrDefault(DocumentType.UNSUPPORTED)
    }

    internal fun detectByContent(input: InputStream): DocumentType {
        val stream = BufferedInputStream(input)
        stream.mark(ZIP_HEADER.size)
        val header = ByteArray(ZIP_HEADER.size)
        var headerSize = 0
        while (headerSize < header.size) {
            val count = stream.read(header, headerSize, header.size - headerSize)
            if (count < 0) break
            headerSize += count
        }
        stream.reset()
        if (headerSize != ZIP_HEADER.size || !header.contentEquals(ZIP_HEADER)) {
            return DocumentType.UNSUPPORTED
        }

        return ZipInputStream(stream).use { zip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var entryCount = 0
            var scannedBytes = 0
            while (entryCount < MAX_ZIP_ENTRIES) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entry.name.trimStart('/') == "ppt/presentation.xml") {
                    return@use DocumentType.POWERPOINT
                }
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    scannedBytes += count
                    if (scannedBytes > MAX_SCAN_BYTES) return@use DocumentType.UNSUPPORTED
                }
                zip.closeEntry()
            }
            DocumentType.UNSUPPORTED
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    const val PPTX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
}

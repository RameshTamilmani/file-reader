package com.mydocviewer.util

import android.content.ContentResolver
import android.net.Uri
import com.mydocviewer.domain.model.DocumentType

object FileTypeDetector {

    fun detect(
        contentResolver: ContentResolver,
        uri: Uri,
        displayName: String?,
        reportedMimeType: String?
    ): DocumentType {
        val ext = displayName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()

        val byExt = when (ext) {
            "pdf" -> DocumentType.PDF
            "doc", "docx" -> DocumentType.WORD
            "xls", "xlsx" -> DocumentType.EXCEL
            "csv" -> DocumentType.CSV
            "ppt", "pptx" -> DocumentType.POWERPOINT
            "txt", "md", "markdown", "rtf", "log", "json", "xml" -> DocumentType.TEXT
            "jpg", "jpeg", "png", "webp", "gif" -> DocumentType.IMAGE
            "epub" -> DocumentType.EPUB
            "zip" -> DocumentType.ARCHIVE
            else -> null
        }
        if (byExt != null) return byExt

        val mime = reportedMimeType?.lowercase().orEmpty()
        val byMime = when {
            mime == "application/pdf" -> DocumentType.PDF
            mime == "application/msword" ||
                mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DocumentType.WORD
            mime == "application/vnd.ms-excel" ||
                mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> DocumentType.EXCEL
            mime == "text/csv" || mime == "text/comma-separated-values" || mime == "application/csv" -> DocumentType.CSV
            mime == "application/vnd.ms-powerpoint" ||
                mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> DocumentType.POWERPOINT
            mime.startsWith("text/") || mime == "application/rtf" -> DocumentType.TEXT
            mime.startsWith("image/") -> DocumentType.IMAGE
            mime == "application/epub+zip" -> DocumentType.EPUB
            mime == "application/zip" || mime == "application/x-zip-compressed" -> DocumentType.ARCHIVE
            else -> null
        }
        if (byMime != null) return byMime

        return detectByMagicBytes(contentResolver, uri)
    }

    private fun detectByMagicBytes(contentResolver: ContentResolver, uri: Uri): DocumentType {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(16)
                val read = stream.read(header)
                if (read <= 0) return DocumentType.UNSUPPORTED

                // %PDF-
                if (read >= 5 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
                ) {
                    return DocumentType.PDF
                }
                // PNG
                if (read >= 4 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
                ) {
                    return DocumentType.IMAGE
                }
                // JPEG
                if (read >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() &&
                    header[2] == 0xFF.toByte()
                ) {
                    return DocumentType.IMAGE
                }
                // GIF
                if (read >= 3 && header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte()
                ) {
                    return DocumentType.IMAGE
                }
                // ZIP / OOXML
                if (read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                ) {
                    return DocumentType.ARCHIVE
                }
                // Plain UTF-8 text heuristic
                val printable = header.take(read).all { byte ->
                    val b = byte.toInt() and 0xFF
                    b == 0x09 || b == 0x0A || b == 0x0D || (b in 0x20..0x7E)
                }
                if (printable) DocumentType.TEXT else DocumentType.UNSUPPORTED
            } ?: DocumentType.UNSUPPORTED
        }.getOrDefault(DocumentType.UNSUPPORTED)
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
}

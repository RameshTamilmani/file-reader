package com.mydocviewer.util

import android.content.ContentResolver
import android.net.Uri
import com.mydocviewer.domain.model.DocumentType
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

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
            else -> null
        }
        if (byMime != null) return byMime

        val detected = detectByMagicBytes(contentResolver, uri)
        return if (
            detected == DocumentType.UNSUPPORTED &&
            (mime == "application/zip" || mime == "application/x-zip-compressed")
        ) {
            DocumentType.ARCHIVE
        } else {
            detected
        }
    }

    private fun detectByMagicBytes(contentResolver: ContentResolver, uri: Uri): DocumentType {
        return runCatching {
            contentResolver.openInputStream(uri)?.use(::detectByContent)
                ?: DocumentType.UNSUPPORTED
        }.getOrDefault(DocumentType.UNSUPPORTED)
    }

    internal fun detectByContent(input: InputStream): DocumentType {
        val stream = BufferedInputStream(input)
        stream.mark(PDF_HEADER_SCAN_BYTES)
        val header = ByteArray(PDF_HEADER_SCAN_BYTES)
        var headerSize = 0
        while (headerSize < header.size) {
            val count = stream.read(header, headerSize, header.size - headerSize)
            if (count < 0) break
            headerSize += count
        }
        stream.reset()

        if (headerSize >= PDF_HEADER.size && header.indexOf(PDF_HEADER, headerSize) >= 0) {
            return DocumentType.PDF
        }
        if (headerSize >= OLE_HEADER.size && header.copyOfRange(0, OLE_HEADER.size).contentEquals(OLE_HEADER)) {
            return detectCompoundOfficeType(stream)
        }
        if (headerSize >= ZIP_HEADER.size && header.copyOfRange(0, ZIP_HEADER.size).contentEquals(ZIP_HEADER)) {
            return detectOfficeZipType(stream) ?: DocumentType.ARCHIVE
        }
        if (headerSize >= 4 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
        ) {
            return DocumentType.IMAGE
        }
        if (headerSize >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return DocumentType.IMAGE
        }
        if (headerSize >= 3 && header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte()
        ) {
            return DocumentType.IMAGE
        }
        val printable = header.take(headerSize).all { byte ->
            val value = byte.toInt() and 0xFF
            value == 0x09 || value == 0x0A || value == 0x0D || value in 0x20..0x7E
        }
        return if (printable && headerSize > 0) DocumentType.TEXT else DocumentType.UNSUPPORTED
    }

    private fun detectOfficeZipType(input: InputStream): DocumentType? = runCatching {
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var entryCount = 0
            var scannedBytes = 0
            while (entryCount < MAX_ZIP_ENTRIES) {
                val entry = zip.nextEntry ?: break
                entryCount++
                when (entry.name.trimStart('/').lowercase()) {
                    "ppt/presentation.xml" -> return@use DocumentType.POWERPOINT
                    "xl/workbook.xml" -> return@use DocumentType.EXCEL
                    "word/document.xml" -> return@use DocumentType.WORD
                }
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    scannedBytes += count
                    if (scannedBytes > MAX_ZIP_SCAN_BYTES) return@use null
                }
                zip.closeEntry()
            }
            null
        }
    }.getOrNull()

    private fun detectCompoundOfficeType(input: InputStream): DocumentType {
        val signatures = listOf(
            "PowerPoint Document" to DocumentType.POWERPOINT,
            "Workbook" to DocumentType.EXCEL,
            "Book" to DocumentType.EXCEL,
            "WordDocument" to DocumentType.WORD
        ).flatMap { (name, type) ->
            listOf(
                name.toByteArray(Charsets.US_ASCII) to type,
                name.toByteArray(Charsets.UTF_16LE) to type
            )
        }
        val maxSignatureLength = signatures.maxOf { it.first.size }
        var tail = ByteArray(0)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var scannedBytes = 0
        while (scannedBytes < MAX_OLE_SCAN_BYTES) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_OLE_SCAN_BYTES - scannedBytes))
            if (count < 0) break
            scannedBytes += count
            val window = tail + buffer.copyOf(count)
            signatures.firstOrNull { (signature, _) -> window.contains(signature) }
                ?.let { return it.second }
            tail = window.copyOfRange((window.size - maxSignatureLength + 1).coerceAtLeast(0), window.size)
        }
        return DocumentType.UNSUPPORTED
    }

    private fun ByteArray.contains(pattern: ByteArray): Boolean {
        if (pattern.size > size) return false
        return (0..size - pattern.size).any { start ->
            pattern.indices.all { offset -> this[start + offset] == pattern[offset] }
        }
    }

    private fun ByteArray.indexOf(pattern: ByteArray, length: Int): Int {
        if (pattern.size > length) return -1
        return (0..length - pattern.size).firstOrNull { start ->
            pattern.indices.all { offset -> this[start + offset] == pattern[offset] }
        } ?: -1
    }

    private const val PDF_HEADER_SCAN_BYTES = 1024
    private const val MAX_ZIP_ENTRIES = 256
    private const val MAX_ZIP_SCAN_BYTES = 8 * 1024 * 1024
    private const val MAX_OLE_SCAN_BYTES = 16 * 1024 * 1024
    private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val OLE_HEADER = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )

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

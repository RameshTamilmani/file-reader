package com.mydocviewer

import com.mydocviewer.domain.model.DocumentType
import com.mydocviewer.util.FileTypeDetector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class FileTypeDetectorTest {

    @Test
    fun formatFileSizeFormatsCorrectly() {
        assertEquals("0 B", FileTypeDetector.formatFileSize(0))
        assertEquals("512 B", FileTypeDetector.formatFileSize(512))
        assertEquals("2 KB", FileTypeDetector.formatFileSize(2048))
        assertEquals("1.5 MB", FileTypeDetector.formatFileSize(1_572_864))
    }

    @Test
    fun documentTypeCategoriesMatchExpectedFilters() {
        assertEquals("PDF", DocumentType.PDF.categoryLabel)
        assertEquals("Word", DocumentType.WORD.categoryLabel)
        assertEquals("Excel", DocumentType.EXCEL.categoryLabel)
        assertEquals("Excel", DocumentType.CSV.categoryLabel)
        assertEquals("PowerPoint", DocumentType.POWERPOINT.categoryLabel)
    }

    @Test
    fun detectsPdfWhenHeaderIsNotAtByteZero() {
        val bytes = ByteArray(12) + "%PDF-1.7".toByteArray()

        assertEquals(
            DocumentType.PDF,
            FileTypeDetector.detectByContent(ByteArrayInputStream(bytes))
        )
    }

    @Test
    fun detectsOfficeTypeInsideZipWhenExtensionAndMimeAreUnavailable() {
        assertEquals(DocumentType.POWERPOINT, detectZipType("ppt/presentation.xml"))
        assertEquals(DocumentType.EXCEL, detectZipType("xl/workbook.xml"))
        assertEquals(DocumentType.WORD, detectZipType("word/document.xml"))
        assertEquals(DocumentType.ARCHIVE, detectZipType("images/photo.png"))
    }

    @Test
    fun detectsLegacyOfficeTypeFromCompoundFileContents() {
        val header = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
        )
        val marker = "PowerPoint Document".toByteArray(Charsets.UTF_16LE)

        assertEquals(
            DocumentType.POWERPOINT,
            FileTypeDetector.detectByContent(ByteArrayInputStream(header + marker))
        )
    }

    private fun detectZipType(entryName: String): DocumentType {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        return FileTypeDetector.detectByContent(ByteArrayInputStream(output.toByteArray()))
    }
}

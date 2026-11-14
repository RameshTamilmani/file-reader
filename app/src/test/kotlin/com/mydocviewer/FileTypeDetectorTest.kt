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
    fun documentTypeIsPowerPointOnly() {
        assertEquals("PowerPoint", DocumentType.POWERPOINT.categoryLabel)
        assertEquals(2, DocumentType.entries.size)
    }

    @Test
    fun detectsPptxPackageWhenExtensionAndMimeAreUnavailable() {
        assertEquals(DocumentType.POWERPOINT, detectZipType("ppt/presentation.xml"))
        assertEquals(
            DocumentType.UNSUPPORTED,
            detectZipType("xl/workbook.xml")
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

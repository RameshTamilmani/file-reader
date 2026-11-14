package com.mydocviewer

import com.mydocviewer.domain.model.DocumentType
import com.mydocviewer.util.FileTypeDetector
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
}

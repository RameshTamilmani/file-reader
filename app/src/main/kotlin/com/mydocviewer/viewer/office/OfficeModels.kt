package com.mydocviewer.viewer.office

data class StyledSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val colorHex: String? = null
)

sealed interface WordBlock {
    data class Heading(val level: Int, val text: String) : WordBlock
    data class Paragraph(val spans: List<StyledSpan>, val isBullet: Boolean = false) : WordBlock
    data class Table(val rows: List<List<String>>) : WordBlock
    data class EmbeddedImage(val bytes: ByteArray, val mimeType: String) : WordBlock
}

data class ParsedWordDocument(
    val blocks: List<WordBlock>,
    val isSimplifiedView: Boolean = true
)

data class SheetData(
    val name: String,
    val rows: List<List<String>>
)

data class ParsedSpreadsheet(
    val sheets: List<SheetData>,
    val isSimplifiedView: Boolean = true
)

data class SlideData(
    val index: Int,
    val title: String,
    val bodyLines: List<String>,
    val tables: List<List<List<String>>> = emptyList(),
    val images: List<ByteArray> = emptyList(),
    val notes: String = "",
    val backgroundHex: String? = null
)

data class ParsedPresentation(
    val slides: List<SlideData>,
    val isSimplifiedView: Boolean = true
)

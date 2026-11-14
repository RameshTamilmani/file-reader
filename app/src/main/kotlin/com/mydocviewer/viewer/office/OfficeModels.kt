package com.mydocviewer.viewer.office

data class SlideTextRun(
    val text: String,
    val fontSizePoints: Float = 18f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val colorHex: String? = null
)

data class SlideParagraph(
    val runs: List<SlideTextRun>,
    val alignment: String = "l",
    val bullet: String? = null
)

data class SlideElement(
    val x: Long = 0,
    val y: Long = 0,
    val width: Long = 0,
    val height: Long = 0,
    val rotationDegrees: Float = 0f,
    val fillHex: String? = null,
    val lineHex: String? = null,
    val shapeKind: String = "rect",
    val paragraphs: List<SlideParagraph> = emptyList(),
    val imageBytes: ByteArray? = null,
    val tableRows: List<List<String>> = emptyList(),
    val leftInset: Long = 91440,
    val topInset: Long = 45720,
    val rightInset: Long = 91440,
    val bottomInset: Long = 45720,
    val verticalAnchor: String = "t"
)

data class SlideData(
    val index: Int,
    val title: String,
    val elements: List<SlideElement>,
    val backgroundHex: String? = null
)

data class ParsedPresentation(
    val slides: List<SlideData>,
    val slideWidth: Long,
    val slideHeight: Long
) {
    val aspectRatio: Float
        get() = slideWidth.toFloat() / slideHeight.coerceAtLeast(1L)
}

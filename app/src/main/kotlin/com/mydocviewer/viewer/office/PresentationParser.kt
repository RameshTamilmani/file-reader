package com.mydocviewer.viewer.office

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

object PresentationParser {

    private const val MAX_ARCHIVE_BYTES = 100 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 30 * 1024 * 1024
    private const val DEFAULT_FONT_SIZE = 18f

    suspend fun parse(context: Context, uri: Uri): ParsedPresentation =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open PowerPoint stream")
            stream.use { input ->
                val entries = readArchive(input)
                val presentationPath = "ppt/presentation.xml"
                val presentationXml = entries[presentationPath]
                    ?: throw IllegalArgumentException("Invalid PowerPoint file: presentation.xml is missing")
                val dimensions = parseSlideDimensions(presentationXml)
                val relationships = parseRelationships(entries["ppt/_rels/presentation.xml.rels"])
                val theme = entries["ppt/theme/theme1.xml"]?.let(::parseTheme).orEmpty()
                val slidePaths = parseSlideOrder(presentationXml)
                    .mapNotNull { relationships[it] }
                    .map { resolveTarget(presentationPath, it) }
                    .filter { it in entries }
                    .ifEmpty {
                        entries.keys
                            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                            .sortedBy { it.substringAfterLast("slide").substringBefore(".xml").toIntOrNull() ?: Int.MAX_VALUE }
                    }

                val slides = slidePaths.mapIndexedNotNull { index, path ->
                    coroutineContext.ensureActive()
                    val xml = entries[path] ?: return@mapIndexedNotNull null
                    val slideRelationships = parseRelationships(entries[relationshipsPath(path)])
                    parseSlide(index + 1, path, xml, slideRelationships, entries, theme)
                }
                if (slides.isEmpty()) {
                    throw IllegalArgumentException("Invalid PowerPoint file: no slides were found")
                }
                ParsedPresentation(slides, dimensions.first, dimensions.second)
            }
        }

    private fun readArchive(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val bytes = readLimited(zip, MAX_ENTRY_BYTES)
                    totalBytes += bytes.size
                    if (totalBytes > MAX_ARCHIVE_BYTES) {
                        throw IllegalArgumentException("PowerPoint file exceeds the supported size")
                    }
                    entries[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IllegalArgumentException("PowerPoint file exceeds the supported size")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun parseSlideDimensions(xml: ByteArray): Pair<Long, Long> {
        var width = 9144000L
        var height = 6858000L
        forEachTag(xml) { parser, event ->
            if (event == XmlPullParser.START_TAG && parser.localName() == "sldSz") {
                width = parser.getAttributeValue(null, "cx")?.toLongOrNull() ?: width
                height = parser.getAttributeValue(null, "cy")?.toLongOrNull() ?: height
            }
        }
        return width.coerceAtLeast(1L) to height.coerceAtLeast(1L)
    }

    private fun parseSlideOrder(xml: ByteArray): List<String> {
        val result = mutableListOf<String>()
        forEachTag(xml) { parser, event ->
            if (event == XmlPullParser.START_TAG && parser.localName() == "sldId") {
                parser.getAttributeValue(RELATIONSHIP_NAMESPACE, "id")?.let(result::add)
            }
        }
        return result
    }

    private fun parseRelationships(xml: ByteArray?): Map<String, String> {
        if (xml == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        forEachTag(xml) { parser, event ->
            if (event == XmlPullParser.START_TAG && parser.localName() == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                val mode = parser.getAttributeValue(null, "TargetMode")
                if (id != null && target != null && mode != "External") result[id] = target
            }
        }
        return result
    }

    private fun parseTheme(xml: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var currentKey: String? = null
        forEachTag(xml) { parser, event ->
            if (event == XmlPullParser.START_TAG) {
                when (parser.localName()) {
                    "dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3",
                    "accent4", "accent5", "accent6", "hlink", "folHlink" ->
                        currentKey = parser.localName()
                    "srgbClr" -> {
                        val value = parser.getAttributeValue(null, "val")
                        if (currentKey != null && value != null) result[currentKey!!] = "#$value"
                    }
                    "sysClr" -> {
                        val value = parser.getAttributeValue(null, "lastClr")
                        if (currentKey != null && value != null) result[currentKey!!] = "#$value"
                    }
                }
            } else if (event == XmlPullParser.END_TAG &&
                parser.localName() in setOf(
                    "dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3",
                    "accent4", "accent5", "accent6", "hlink", "folHlink"
                )
            ) {
                currentKey = null
            }
        }
        return result
    }

    private class ElementBuilder(val kind: String) {
        var x = 0L
        var y = 0L
        var width = 0L
        var height = 0L
        var rotation = 0f
        var fillHex: String? = null
        var lineHex: String? = null
        var shapeKind = "rect"
        var imageRelationshipId: String? = null
        var leftInset = 91440L
        var topInset = 45720L
        var rightInset = 91440L
        var bottomInset = 45720L
        var verticalAnchor = "t"
        val paragraphs = mutableListOf<SlideParagraph>()
        val tableRows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var cellText = StringBuilder()
    }

    private class ParagraphBuilder {
        val runs = mutableListOf<SlideTextRun>()
        var alignment = "l"
        var bullet: String? = null
        var fontSizePoints = DEFAULT_FONT_SIZE
        var bold = false
        var italic = false
        var colorHex: String? = null
    }

    private class RunBuilder(
        var fontSizePoints: Float,
        var bold: Boolean,
        var italic: Boolean,
        var colorHex: String?
    ) {
        val text = StringBuilder()

        fun build(): SlideTextRun = SlideTextRun(
            text = text.toString(),
            fontSizePoints = fontSizePoints,
            bold = bold,
            italic = italic,
            colorHex = colorHex
        )
    }

    private enum class FillTarget { SHAPE, LINE, TEXT }

    private fun parseSlide(
        index: Int,
        path: String,
        xml: ByteArray,
        relationships: Map<String, String>,
        entries: Map<String, ByteArray>,
        theme: Map<String, String>
    ): SlideData {
        val elements = mutableListOf<SlideElement>()
        var element: ElementBuilder? = null
        var paragraph: ParagraphBuilder? = null
        var run: RunBuilder? = null
        var backgroundHex: String? = null
        var inTransform = false
        var inShapeProperties = false
        var inLineProperties = false
        var inTextBody = false
        var inRunProperties = false
        var inText = false
        var inTable = false
        var inCell = false
        var fillTarget: FillTarget? = null
        var defaultFontSize = DEFAULT_FONT_SIZE
        var defaultBold = false
        var defaultItalic = false
        var defaultTextColor: String? = null

        fun addRunText(text: String) {
            if (text.isEmpty()) return
            if (inCell && element != null) {
                element!!.cellText.append(text)
            } else if (inTextBody && paragraph != null) {
                if (run != null) run!!.text.append(text)
                else {
                    paragraph!!.runs += SlideTextRun(
                        text = text,
                        fontSizePoints = paragraph!!.fontSizePoints,
                        bold = paragraph!!.bold,
                        italic = paragraph!!.italic,
                        colorHex = paragraph!!.colorHex
                    )
                }
            }
        }

        fun finishParagraph() {
            val current = paragraph ?: return
            run?.let { activeRun ->
                if (activeRun.text.isNotEmpty()) current.runs += activeRun.build()
            }
            run = null
            if (current.runs.isNotEmpty()) {
                element?.paragraphs?.add(
                    SlideParagraph(current.runs.toList(), current.alignment, current.bullet)
                )
            }
            paragraph = null
        }

        fun applyColor(value: String?) {
            if (value == null) return
            val color = if (value.startsWith("#")) value else theme[value] ?: return
            when (fillTarget) {
                FillTarget.SHAPE -> element?.fillHex = color
                FillTarget.LINE -> element?.lineHex = color
                FillTarget.TEXT -> {
                    if (run != null) run?.colorHex = color
                    else paragraph?.colorHex = color
                }
                null -> if (inRunProperties) paragraph?.colorHex = color else backgroundHex = color
            }
        }

        forEachTag(xml) { parser, event ->
            val name = parser.localName()
            if (event == XmlPullParser.START_TAG) {
                when (name) {
                    "sp", "pic", "graphicFrame" -> {
                        element = ElementBuilder(name)
                        inTransform = false
                        inShapeProperties = false
                        inLineProperties = false
                        inTextBody = false
                        inRunProperties = false
                        inTable = false
                    }
                    "bg" -> fillTarget = FillTarget.SHAPE
                    "xfrm" -> {
                        inTransform = true
                        parser.getAttributeValue(null, "rot")?.toFloatOrNull()?.let {
                            element?.rotation = it / 60000f
                        }
                    }
                    "off" -> if (inTransform) {
                        element?.x = parser.getAttributeValue(null, "x")?.toLongOrNull() ?: 0L
                        element?.y = parser.getAttributeValue(null, "y")?.toLongOrNull() ?: 0L
                    }
                    "ext" -> if (inTransform) {
                        element?.width = parser.getAttributeValue(null, "cx")?.toLongOrNull() ?: 0L
                        element?.height = parser.getAttributeValue(null, "cy")?.toLongOrNull() ?: 0L
                    }
                    "spPr" -> inShapeProperties = true
                    "ln" -> inLineProperties = true
                    "prstGeom" -> parser.getAttributeValue(null, "prst")?.let {
                        element?.shapeKind = it
                    }
                    "bodyPr" -> {
                        element?.leftInset = parser.getAttributeValue(null, "lIns")?.toLongOrNull()
                            ?: element?.leftInset ?: 91440L
                        element?.rightInset = parser.getAttributeValue(null, "rIns")?.toLongOrNull()
                            ?: element?.rightInset ?: 91440L
                        element?.topInset = parser.getAttributeValue(null, "tIns")?.toLongOrNull()
                            ?: element?.topInset ?: 45720L
                        element?.bottomInset = parser.getAttributeValue(null, "bIns")?.toLongOrNull()
                            ?: element?.bottomInset ?: 45720L
                        element?.verticalAnchor = parser.getAttributeValue(null, "anchor") ?: "t"
                    }
                    "txBody" -> inTextBody = true
                    "p" -> if (inTextBody && !inTable) {
                        paragraph = ParagraphBuilder().apply {
                            fontSizePoints = defaultFontSize
                            bold = defaultBold
                            italic = defaultItalic
                            colorHex = defaultTextColor
                        }
                    }
                    "pPr" -> {
                        parser.getAttributeValue(null, "algn")?.let { paragraph?.alignment = it }
                    }
                    "buChar" -> parser.getAttributeValue(null, "char")?.let {
                        paragraph?.bullet = it
                    }
                    "buAutoNum" -> paragraph?.bullet = "•"
                    "r" -> paragraph?.let { current ->
                        run = RunBuilder(
                            current.fontSizePoints,
                            current.bold,
                            current.italic,
                            current.colorHex
                        )
                    }
                    "rPr", "defRPr" -> {
                        inRunProperties = true
                        parser.getAttributeValue(null, "sz")?.toFloatOrNull()?.div(100f)?.let { size ->
                            if (name == "defRPr") defaultFontSize = size
                            if (name == "defRPr") paragraph?.fontSizePoints = size
                            else run?.fontSizePoints = size
                        }
                        parser.getAttributeValue(null, "b")?.let { value ->
                            if (name == "defRPr") defaultBold = value == "1"
                            if (name == "defRPr") paragraph?.bold = value == "1"
                            else run?.bold = value == "1"
                        }
                        parser.getAttributeValue(null, "i")?.let { value ->
                            if (name == "defRPr") defaultItalic = value == "1"
                            if (name == "defRPr") paragraph?.italic = value == "1"
                            else run?.italic = value == "1"
                        }
                    }
                    "solidFill" -> {
                        fillTarget = when {
                            inRunProperties -> FillTarget.TEXT
                            inShapeProperties && inLineProperties -> FillTarget.LINE
                            inShapeProperties -> FillTarget.SHAPE
                            else -> null
                        }
                    }
                    "srgbClr" -> applyColor(parser.getAttributeValue(null, "val")?.let { "#$it" })
                    "schemeClr" -> applyColor(parser.getAttributeValue(null, "val"))
                    "blip" -> element?.imageRelationshipId =
                        parser.getAttributeValue(RELATIONSHIP_NAMESPACE, "embed")
                    "t" -> inText = true
                    "br" -> run?.text?.append('\n') ?: paragraph?.runs?.add(
                        SlideTextRun(
                            text = "\n",
                            fontSizePoints = paragraph?.fontSizePoints ?: DEFAULT_FONT_SIZE
                        )
                    )
                    "tbl" -> inTable = true
                    "tr" -> if (inTable) element?.row = mutableListOf()
                    "tc" -> if (inTable) {
                        inCell = true
                        element?.cellText = StringBuilder()
                    }
                }
            } else if (event == XmlPullParser.TEXT && inText) {
                addRunText(parser.text)
            } else if (event == XmlPullParser.END_TAG) {
                when (name) {
                    "t" -> inText = false
                    "xfrm" -> inTransform = false
                    "spPr" -> inShapeProperties = false
                    "ln" -> inLineProperties = false
                    "txBody" -> {
                        finishParagraph()
                        inTextBody = false
                        defaultFontSize = DEFAULT_FONT_SIZE
                        defaultBold = false
                        defaultItalic = false
                        defaultTextColor = null
                    }
                    "rPr", "defRPr" -> inRunProperties = false
                    "r" -> {
                        run?.let { activeRun ->
                            if (activeRun.text.isNotEmpty()) paragraph?.runs?.add(activeRun.build())
                        }
                        run = null
                    }
                    "solidFill" -> fillTarget = null
                    "p" -> if (inTextBody && !inTable) finishParagraph()
                    "tc" -> if (inTable && inCell) {
                        element?.row?.add(element?.cellText?.toString().orEmpty())
                        inCell = false
                    }
                    "tr" -> if (inTable) element?.tableRows?.add(element?.row?.toList().orEmpty())
                    "tbl" -> inTable = false
                    "sp", "pic", "graphicFrame" -> {
                        val current = element
                        if (current != null) {
                            val image = current.imageRelationshipId
                                ?.let(relationships::get)
                                ?.let { resolveTarget(path, it) }
                                ?.let(entries::get)
                            elements += SlideElement(
                                x = current.x,
                                y = current.y,
                                width = current.width,
                                height = current.height,
                                rotationDegrees = current.rotation,
                                fillHex = current.fillHex,
                                lineHex = current.lineHex,
                                shapeKind = current.shapeKind,
                                paragraphs = current.paragraphs.toList(),
                                imageBytes = image,
                                tableRows = current.tableRows.toList(),
                                leftInset = current.leftInset,
                                topInset = current.topInset,
                                rightInset = current.rightInset,
                                bottomInset = current.bottomInset,
                                verticalAnchor = current.verticalAnchor
                            )
                        }
                        element = null
                        inTextBody = false
                        inTable = false
                    }
                    "bg" -> fillTarget = null
                }
            }
        }

        val title = elements
            .flatMap { it.paragraphs }
            .flatMap { it.runs }
            .firstOrNull { it.text.isNotBlank() }
            ?.text
            ?.lineSequence()
            ?.firstOrNull()
            .orEmpty()
        return SlideData(index, title, elements, backgroundHex)
    }

    private fun forEachTag(xml: ByteArray, action: (XmlPullParser, Int) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            action(parser, event)
            event = parser.next()
        }
    }

    private fun XmlPullParser.localName(): String = name?.substringAfter(':').orEmpty()

    private fun relationshipsPath(partPath: String): String =
        "${partPath.substringBeforeLast('/')}/_rels/${partPath.substringAfterLast('/')}.rels"

    private fun resolveTarget(sourcePath: String, target: String): String {
        val combined = if (target.startsWith('/')) target.drop(1) else {
            "${sourcePath.substringBeforeLast('/')}/$target"
        }
        val normalized = mutableListOf<String>()
        combined.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
                else -> normalized += segment
            }
        }
        return normalized.joinToString("/")
    }

    private const val RELATIONSHIP_NAMESPACE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
}

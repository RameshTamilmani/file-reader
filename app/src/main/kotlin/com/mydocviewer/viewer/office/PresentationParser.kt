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
    private const val MAX_LEGACY_FILE_BYTES = 30 * 1024 * 1024

    suspend fun parse(context: Context, uri: Uri, fileName: String): ParsedPresentation =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open presentation stream")

            stream.use { input ->
                if (fileName.endsWith(".ppt", ignoreCase = true)) {
                    return@withContext parseLegacyPresentation(readLimited(input, MAX_LEGACY_FILE_BYTES))
                }

                val entries = readArchive(input)
                val presentationPath = "ppt/presentation.xml"
                val presentation = entries[presentationPath]
                    ?: throw IllegalArgumentException("Invalid PowerPoint file: presentation.xml is missing")
                val presentationRels = parseRelationships(entries["ppt/_rels/presentation.xml.rels"])
                val slidePaths = parseSlideOrder(presentation)
                    .mapNotNull { relationshipId -> presentationRels[relationshipId] }
                    .ifEmpty {
                        entries.keys
                            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                            .sortedBy { path -> path.substringAfterLast("slide").substringBefore(".xml").toIntOrNull() ?: Int.MAX_VALUE }
                    }

                val slides = slidePaths.mapIndexedNotNull { index, slidePath ->
                    coroutineContext.ensureActive()
                    val slideXml = entries[slidePath] ?: return@mapIndexedNotNull null
                    val relationships = parseRelationships(entries[relationshipsPath(slidePath)])
                    val parsedSlide = parseSlide(slideXml)
                    val images = parsedSlide.imageRelationshipIds.mapNotNull { relationshipId ->
                        relationships[relationshipId]
                            ?.let { resolveTarget(slidePath, it) }
                            ?.let(entries::get)
                    }
                    val notesPath = relationships.values
                        .firstOrNull { it.contains("/notesSlides/") || it.startsWith("../notesSlides/") }
                        ?.let { resolveTarget(slidePath, it) }
                    val notes = notesPath?.let(entries::get)?.let(::parseNotes).orEmpty()
                    SlideData(
                        index = index + 1,
                        title = parsedSlide.title.ifBlank { "Slide ${index + 1}" },
                        bodyLines = parsedSlide.bodyLines,
                        tables = parsedSlide.tables,
                        images = images,
                        notes = notes,
                        backgroundHex = parsedSlide.backgroundHex
                    )
                }

                if (slides.isEmpty()) {
                    throw IllegalArgumentException("Invalid PowerPoint file: no slides were found")
                }
                ParsedPresentation(slides = slides)
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

    private data class ParsedSlide(
        val title: String,
        val bodyLines: List<String>,
        val tables: List<List<List<String>>>,
        val imageRelationshipIds: List<String>,
        val backgroundHex: String?
    )

    private data class TextShape(val text: String, val placeholderType: String?)

    private fun parseSlide(xml: ByteArray): ParsedSlide {
        val shapes = mutableListOf<TextShape>()
        val tables = mutableListOf<List<List<String>>>()
        val imageIds = mutableListOf<String>()
        var backgroundHex: String? = null
        var inBackground = false
        var inTextShape = false
        var inTextElement = false
        var placeholderType: String? = null
        var shapeText = StringBuilder()
        var shapeParagraphs = mutableListOf<String>()
        var paragraphText = StringBuilder()
        var inTable = false
        var tableRows = mutableListOf<List<String>>()
        var tableRow = mutableListOf<String>()
        var inCell = false
        var cellText = StringBuilder()

        forEachTag(xml) { parser, event ->
            val name = parser.localName()
            if (event == XmlPullParser.START_TAG) {
                when (name) {
                    "bg" -> inBackground = true
                    "sp" -> {
                        inTextShape = true
                        placeholderType = null
                        shapeText = StringBuilder()
                        shapeParagraphs = mutableListOf()
                    }
                    "t" -> if (inTextShape || inCell) inTextElement = true
                    "ph" -> if (inTextShape) {
                        placeholderType = parser.getAttributeValue(null, "type")
                    }
                    "p" -> if (inTextShape) paragraphText = StringBuilder()
                    "tbl" -> {
                        inTable = true
                        tableRows = mutableListOf()
                    }
                    "tr" -> if (inTable) tableRow = mutableListOf()
                    "tc" -> if (inTable) {
                        inCell = true
                        cellText = StringBuilder()
                    }
                    "blip" -> parser.getAttributeValue(RELATIONSHIP_NAMESPACE, "embed")?.let(imageIds::add)
                    "srgbClr" -> if (inBackground && backgroundHex == null && parser.getAttributeValue(null, "val") != null) {
                        backgroundHex = "#" + parser.getAttributeValue(null, "val")!!.uppercase()
                    }
                }
            } else if (event == XmlPullParser.TEXT) {
                when {
                    inTextShape && inTextElement -> {
                        shapeText.append(parser.text)
                        paragraphText.append(parser.text)
                    }
                    inCell && inTextElement -> cellText.append(parser.text)
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (name) {
                    "t" -> inTextElement = false
                    "bg" -> inBackground = false
                    "p" -> if (inTextShape) {
                        paragraphText.toString().trim().takeIf(String::isNotEmpty)?.let(shapeParagraphs::add)
                    }
                    "sp" -> if (inTextShape) {
                        shapes += TextShape(
                            text = shapeParagraphs.joinToString("\n").ifBlank { shapeText.toString().trim() },
                            placeholderType = placeholderType
                        )
                        inTextShape = false
                    }
                    "tc" -> if (inTable && inCell) {
                        tableRow += cellText.toString().trim()
                        inCell = false
                    }
                    "tr" -> if (inTable) tableRows += tableRow
                    "tbl" -> if (inTable) {
                        if (tableRows.isNotEmpty()) tables += tableRows
                        inTable = false
                    }
                }
            }
        }

        val nonEmptyShapes = shapes.filter { it.text.isNotBlank() }
        val titleShape = nonEmptyShapes.firstOrNull {
            it.placeholderType == "title" || it.placeholderType == "ctrTitle"
        } ?: nonEmptyShapes.firstOrNull()
        val title = titleShape?.text.orEmpty().lines().firstOrNull().orEmpty()
        val bodyLines = nonEmptyShapes
            .filterNot { it === titleShape }
            .flatMap { it.text.lines() }
            .filter(String::isNotBlank)

        return ParsedSlide(title, bodyLines, tables, imageIds, backgroundHex)
    }

    private fun parseNotes(xml: ByteArray): String {
        val notes = mutableListOf<String>()
        var inShape = false
        var inTextElement = false
        var ignoredShape = false
        var shapeText = StringBuilder()
        var paragraphText = StringBuilder()
        var shapeParagraphs = mutableListOf<String>()
        forEachTag(xml) { parser, event ->
            val name = parser.localName()
            if (event == XmlPullParser.START_TAG) {
                when (name) {
                    "sp" -> {
                        inShape = true
                        ignoredShape = false
                        shapeText = StringBuilder()
                        shapeParagraphs = mutableListOf()
                    }
                    "t" -> if (inShape) inTextElement = true
                    "ph" -> if (inShape) {
                        ignoredShape = parser.getAttributeValue(null, "type") in
                            setOf("slideImage", "sldNum", "dt", "hdr", "ftr")
                    }
                    "p" -> if (inShape) paragraphText = StringBuilder()
                }
            } else if (event == XmlPullParser.TEXT && inShape && inTextElement) {
                shapeText.append(parser.text)
                paragraphText.append(parser.text)
            } else if (event == XmlPullParser.END_TAG) {
                when (name) {
                    "t" -> inTextElement = false
                    "p" -> if (inShape) paragraphText.toString().trim()
                        .takeIf(String::isNotEmpty)?.let(shapeParagraphs::add)
                    "sp" -> if (inShape) {
                        if (!ignoredShape) {
                            val text = shapeParagraphs.joinToString("\n").ifBlank { shapeText.toString().trim() }
                            if (text.isNotBlank()) notes += text
                        }
                        inShape = false
                    }
                }
            }
        }
        return notes.joinToString("\n").trim()
    }

    private fun parseLegacyPresentation(bytes: ByteArray): ParsedPresentation {
        val extracted = extractLegacyText(bytes)
        val lines = extracted
            .flatMap { it.lines() }
            .map(String::trim)
            .filter { it.length >= 2 && it.any(Char::isLetterOrDigit) }
            .distinct()
        return ParsedPresentation(
            slides = listOf(
                SlideData(
                    index = 1,
                    title = "Legacy PowerPoint",
                    bodyLines = lines.ifEmpty {
                        listOf("This legacy .ppt file could not be fully parsed on this device.")
                    }
                )
            )
        )
    }

    private fun extractLegacyText(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        fun collect(readChar: (Int) -> Char?, step: Int) {
            val current = StringBuilder()
            var offset = 0
            while (offset < bytes.size) {
                val char = readChar(offset)
                if (char != null && (char.isLetterOrDigit() || char.isWhitespace() || char in ".,;:!?'-()")) {
                    current.append(char)
                } else {
                    if (current.length >= 4) strings += current.toString().trim()
                    current.clear()
                }
                offset += step
            }
            if (current.length >= 4) strings += current.toString().trim()
        }
        collect({ offset ->
            val value = bytes[offset].toInt() and 0xff
            value.takeIf { it in 32..126 || it in 9..13 }?.toChar()
        }, 1)
        collect({ offset ->
            if (offset + 1 >= bytes.size || bytes[offset + 1].toInt() != 0) null
            else (bytes[offset].toInt() and 0xff).toChar()
        }, 2)
        return strings.distinct()
    }

    private inline fun forEachTag(xml: ByteArray, action: (XmlPullParser, Int) -> Unit) {
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

    private fun relationshipsPath(partPath: String): String {
        val directory = partPath.substringBeforeLast('/')
        val fileName = partPath.substringAfterLast('/')
        return "$directory/_rels/$fileName.rels"
    }

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

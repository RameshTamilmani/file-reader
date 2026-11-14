package com.mydocviewer.viewer.office

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

object SpreadsheetParser {

    private const val MAX_ROWS_PER_SHEET = 2000
    private const val MAX_COLUMNS_PER_SHEET = 256
    private const val MAX_ENTRY_BYTES = 30 * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 100 * 1024 * 1024

    suspend fun parse(context: Context, uri: Uri, fileName: String): ParsedSpreadsheet =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open spreadsheet stream")
            input.use { raw ->
                val stream = BufferedInputStream(raw)
                stream.mark(8)
                val header = ByteArray(8)
                var headerSize = 0
                while (headerSize < header.size) {
                    val count = stream.read(header, headerSize, header.size - headerSize)
                    if (count < 0) break
                    headerSize += count
                }
                stream.reset()
                val isZip = headerSize >= XLSX_HEADER.size &&
                    header.copyOfRange(0, XLSX_HEADER.size).contentEquals(XLSX_HEADER)
                val isLegacy = headerSize == OLE_HEADER.size && header.contentEquals(OLE_HEADER)
                val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
                val isCsv = fileName.endsWith(".csv", ignoreCase = true) ||
                    mimeType == "text/csv" ||
                    mimeType == "text/comma-separated-values" ||
                    mimeType == "application/csv"

                when {
                    isCsv -> {
                        val rows = parseCsv(BufferedReader(InputStreamReader(stream)))
                        ParsedSpreadsheet(
                            sheets = listOf(
                                SheetData(
                                    name = fileName.substringBeforeLast('.', fileName),
                                    rows = rows
                                )
                            ),
                            isSimplifiedView = false
                        )
                    }
                    isZip -> parseXlsx(stream)
                    isLegacy -> parseLegacySpreadsheet(stream)
                    else -> throw IllegalArgumentException("Unrecognized spreadsheet format")
                }
            }
        }

    private fun parseXlsx(input: InputStream): ParsedSpreadsheet {
        val entries = readWorkbookEntries(input)
        val workbookXml = entries["xl/workbook.xml"]
            ?: throw IllegalArgumentException("Invalid Excel file: workbook.xml is missing")
        val workbookRelationships = parseRelationships(entries["xl/_rels/workbook.xml.rels"])
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetRefs = parseWorkbookSheets(workbookXml)
        val sheets = sheetRefs.mapIndexedNotNull { index, sheet ->
            val path = workbookRelationships[sheet.relationshipId]
                ?.let { resolveTarget("xl/workbook.xml", it) }
                ?: return@mapIndexedNotNull null
            val xml = entries[path] ?: return@mapIndexedNotNull null
            SheetData(
                name = sheet.name.ifBlank { "Sheet ${index + 1}" },
                rows = parseWorksheet(xml, sharedStrings)
            )
        }
        if (sheets.isEmpty()) {
            throw IllegalArgumentException("Invalid Excel file: no worksheets were found")
        }
        return ParsedSpreadsheet(sheets = sheets)
    }

    private fun readWorkbookEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val isWorkbookPart = name == "xl/workbook.xml" ||
                    name == "xl/_rels/workbook.xml.rels" ||
                    name == "xl/sharedStrings.xml" ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                if (!entry.isDirectory && isWorkbookPart) {
                    val bytes = readLimited(zip, MAX_ENTRY_BYTES)
                    totalBytes += bytes.size
                    if (totalBytes > MAX_ARCHIVE_BYTES) {
                        throw IllegalArgumentException("Excel file exceeds the supported size")
                    }
                    entries[name] = bytes
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private data class SheetReference(val name: String, val relationshipId: String)

    private fun parseWorkbookSheets(xml: ByteArray): List<SheetReference> {
        val result = mutableListOf<SheetReference>()
        forEachTag(xml) { parser, event ->
            if (event == XmlPullParser.START_TAG && parser.localName() == "sheet") {
                val name = parser.getAttributeValue(null, "name").orEmpty()
                val relationshipId = parser.getAttributeValue(RELATIONSHIP_NAMESPACE, "id")
                if (relationshipId != null) result += SheetReference(name, relationshipId)
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

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var inItem = false
        var inText = false
        var current = StringBuilder()
        forEachTag(xml) { parser, event ->
            val name = parser.localName()
            when {
                event == XmlPullParser.START_TAG && name == "si" -> {
                    inItem = true
                    current = StringBuilder()
                }
                event == XmlPullParser.START_TAG && name == "t" && inItem -> inText = true
                event == XmlPullParser.TEXT && inItem && inText -> current.append(parser.text)
                event == XmlPullParser.END_TAG && name == "t" -> inText = false
                event == XmlPullParser.END_TAG && name == "si" -> {
                    result += current.toString()
                    inItem = false
                }
            }
        }
        return result
    }

    private fun parseWorksheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<MutableMap<Int, String>>()
        var rowIndex = -1
        var currentRow = mutableMapOf<Int, String>()
        var currentColumn = -1
        var cellType = ""
        var cellValue = StringBuilder()
        var inCellValue = false
        var inInlineText = false
        var inInlineString = false

        forEachTag(xml) { parser, event ->
            val name = parser.localName()
            if (event == XmlPullParser.START_TAG) {
                when (name) {
                    "row" -> {
                        val parsedIndex = parser.getAttributeValue(null, "r")?.toIntOrNull()?.minus(1)
                        rowIndex = (parsedIndex ?: rowIndex + 1).coerceIn(0, MAX_ROWS_PER_SHEET)
                        while (rows.size <= rowIndex && rows.size < MAX_ROWS_PER_SHEET) {
                            rows += mutableMapOf()
                        }
                        currentRow = rows.getOrNull(rowIndex) ?: mutableMapOf()
                    }
                    "c" -> {
                        currentColumn = columnIndex(parser.getAttributeValue(null, "r"))
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellValue = StringBuilder()
                        inCellValue = false
                        inInlineString = false
                    }
                    "v" -> inCellValue = true
                    "is" -> inInlineString = true
                    "t" -> if (inInlineString) inInlineText = true
                }
            } else if (event == XmlPullParser.TEXT) {
                if (inCellValue || (inInlineString && inInlineText)) cellValue.append(parser.text)
            } else if (event == XmlPullParser.END_TAG) {
                when (name) {
                    "v" -> inCellValue = false
                    "t" -> inInlineText = false
                    "is" -> inInlineString = false
                    "c" -> {
                        if (currentColumn in 0 until MAX_COLUMNS_PER_SHEET) {
                            val rawValue = cellValue.toString()
                            currentRow[currentColumn] = when (cellType) {
                                "s" -> sharedStrings.getOrNull(rawValue.toIntOrNull() ?: -1).orEmpty()
                                "b" -> if (rawValue == "1") "TRUE" else "FALSE"
                                else -> rawValue
                            }
                        }
                        currentColumn = -1
                    }
                }
            }
        }

        return rows.take(MAX_ROWS_PER_SHEET).map { row ->
            val columnCount = (row.keys.maxOrNull()?.plus(1) ?: 0).coerceAtMost(MAX_COLUMNS_PER_SHEET)
            List(columnCount) { column -> row[column].orEmpty() }
        }
    }

    private fun columnIndex(cellReference: String?): Int {
        if (cellReference.isNullOrBlank()) return -1
        var index = 0
        for (char in cellReference) {
            if (!char.isLetter()) break
            index = index * 26 + (char.uppercaseChar() - 'A' + 1)
        }
        return (index - 1).takeIf { it >= 0 } ?: -1
    }

    private suspend fun parseCsv(reader: BufferedReader): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var maxCols = 0
        reader.useLines { sequence ->
            for (line in sequence) {
                coroutineContext.ensureActive()
                if (rows.size >= MAX_ROWS_PER_SHEET) break
                val parsed = parseCsvLine(line)
                maxCols = maxOf(maxCols, parsed.size)
                rows += parsed
            }
        }
        return rows.map { row ->
            if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        result += current.toString()
        return result
    }

    private fun parseLegacySpreadsheet(input: InputStream): ParsedSpreadsheet {
        val bytes = readLimited(input, MAX_ENTRY_BYTES)
        val values = mutableListOf<String>()
        collectLegacyText(bytes, utf16 = false, values)
        collectLegacyText(bytes, utf16 = true, values)
        val rows = values.distinct().map { listOf(it) }
        return ParsedSpreadsheet(
            sheets = listOf(
                SheetData(
                    name = "Sheet 1",
                    rows = rows.ifEmpty { listOf(listOf("This legacy Excel file could not be fully parsed on this device.")) }
                )
            )
        )
    }

    private fun collectLegacyText(bytes: ByteArray, utf16: Boolean, output: MutableList<String>) {
        val step = if (utf16) 2 else 1
        val current = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            val value = bytes[offset].toInt() and 0xff
            val char = if (utf16) {
                if (offset + 1 < bytes.size && bytes[offset + 1].toInt() == 0) value.toChar() else null
            } else {
                value.takeIf { it in 32..126 || it in 9..13 }?.toChar()
            }
            if (char != null && (char.isLetterOrDigit() || char.isWhitespace() || char in ".,;:!?'-()")) {
                current.append(char)
            } else {
                if (current.length >= 4) output += current.toString().trim()
                current.clear()
            }
            offset += step
        }
        if (current.length >= 4) output += current.toString().trim()
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IllegalArgumentException("Excel file exceeds the supported size")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
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

    private val XLSX_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val OLE_HEADER = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )
    private const val RELATIONSHIP_NAMESPACE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
}

package com.mydocviewer.viewer.office

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.coroutineContext

object SpreadsheetParser {

    private const val MAX_ROWS_PER_SHEET = 2000

    suspend fun parse(context: Context, uri: Uri, fileName: String): ParsedSpreadsheet =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open spreadsheet stream")

            stream.use { input ->
                if (fileName.lowercase().endsWith(".csv")) {
                    val rows = parseCsv(BufferedReader(InputStreamReader(input)))
                    return@withContext ParsedSpreadsheet(
                        sheets = listOf(SheetData(name = fileName.substringBeforeLast('.'), rows = rows)),
                        isSimplifiedView = false
                    )
                }

                val workbook = WorkbookFactory.create(input)
                val formatter = DataFormatter()
                val sheets = mutableListOf<SheetData>()

                for (i in 0 until workbook.numberOfSheets) {
                    coroutineContext.ensureActive()
                    val sheet = workbook.getSheetAt(i)
                    val rows = mutableListOf<List<String>>()
                    var maxCols = 0
                    val rowCount = minOf(sheet.lastRowNum + 1, MAX_ROWS_PER_SHEET)

                    for (r in 0 until rowCount) {
                        coroutineContext.ensureActive()
                        val row = sheet.getRow(r)
                        if (row == null) {
                            rows.add(emptyList())
                            continue
                        }
                        val lastCell = row.lastCellNum.toInt().coerceAtLeast(0)
                        maxCols = maxOf(maxCols, lastCell)
                        val cells = (0 until lastCell).map { c ->
                            val cell = row.getCell(c)
                            if (cell == null) "" else formatter.formatCellValue(cell)
                        }
                        rows.add(cells)
                    }
                    val normalized = rows.map { row ->
                        if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row
                    }
                    sheets += SheetData(name = sheet.sheetName ?: "Sheet ${i + 1}", rows = normalized)
                }
                workbook.close()
                ParsedSpreadsheet(sheets = sheets, isSimplifiedView = true)
            }
        }

    private suspend fun parseCsv(reader: BufferedReader): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var maxCols = 0
        var lineCount = 0
        reader.useLines { sequence ->
            for (line in sequence) {
                coroutineContext.ensureActive()
                if (lineCount >= MAX_ROWS_PER_SHEET) break
                val parsed = parseCsvLine(line)
                maxCols = maxOf(maxCols, parsed.size)
                rows += parsed
                lineCount++
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
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result += current.toString()
        return result
    }
}

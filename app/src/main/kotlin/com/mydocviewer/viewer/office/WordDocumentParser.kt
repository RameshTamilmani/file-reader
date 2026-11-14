package com.mydocviewer.viewer.office

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import kotlin.coroutines.coroutineContext

object WordDocumentParser {

    suspend fun parse(context: Context, uri: Uri, fileName: String): ParsedWordDocument =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open document stream")

            stream.use { input ->
                val isLegacyDoc = fileName.lowercase().endsWith(".doc")
                if (isLegacyDoc) {
                    val doc = HWPFDocument(input)
                    val extractor = WordExtractor(doc)
                    val blocks = extractor.paragraphText.mapNotNull { raw ->
                        coroutineContext.ensureActive()
                        val cleaned = raw.trim()
                        if (cleaned.isEmpty()) null
                        else WordBlock.Paragraph(listOf(StyledSpan(cleaned)))
                    }
                    doc.close()
                    return@withContext ParsedWordDocument(blocks = blocks, isSimplifiedView = true)
                }

                val docx = XWPFDocument(input)
                val blocks = mutableListOf<WordBlock>()
                for (element: IBodyElement in docx.bodyElements) {
                    coroutineContext.ensureActive()
                    when (element) {
                        is XWPFParagraph -> {
                            val text = element.text.orEmpty()
                            if (text.isNotBlank()) {
                                val style = element.style.orEmpty()
                                val headingLevel = when {
                                    style.contains("Heading1", ignoreCase = true) || style == "1" -> 1
                                    style.contains("Heading2", ignoreCase = true) || style == "2" -> 2
                                    style.contains("Heading3", ignoreCase = true) || style == "3" -> 3
                                    else -> 0
                                }
                                if (headingLevel > 0) {
                                    blocks += WordBlock.Heading(headingLevel, text.trim())
                                } else {
                                    val spans = element.runs.mapNotNull { run ->
                                        val runText = run.text()
                                        if (runText.isNullOrEmpty()) null
                                        else StyledSpan(
                                            text = runText,
                                            bold = run.isBold,
                                            italic = run.isItalic,
                                            colorHex = run.color
                                        )
                                    }.ifEmpty { listOf(StyledSpan(text)) }
                                    val isBullet = element.numID != null
                                    blocks += WordBlock.Paragraph(spans, isBullet = isBullet)
                                }
                            }
                            element.runs.forEach { run ->
                                run.embeddedPictures.forEach { pic ->
                                    val data = pic.pictureData?.data
                                    if (data != null && data.isNotEmpty()) {
                                        blocks += WordBlock.EmbeddedImage(
                                            bytes = data,
                                            mimeType = pic.pictureData.suggestFileExtension() ?: "png"
                                        )
                                    }
                                }
                            }
                        }
                        is XWPFTable -> {
                            val rows = element.rows.map { row ->
                                row.tableCells.map { cell -> cell.text.orEmpty().trim() }
                            }
                            if (rows.isNotEmpty()) {
                                blocks += WordBlock.Table(rows)
                            }
                        }
                    }
                }
                docx.close()
                ParsedWordDocument(blocks = blocks, isSimplifiedView = true)
            }
        }
}

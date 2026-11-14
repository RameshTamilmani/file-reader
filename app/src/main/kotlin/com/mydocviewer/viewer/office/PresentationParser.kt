package com.mydocviewer.viewer.office

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import kotlin.coroutines.coroutineContext

object PresentationParser {

    suspend fun parse(context: Context, uri: Uri, fileName: String): ParsedPresentation =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open presentation stream")

            stream.use { input ->
                if (fileName.lowercase().endsWith(".ppt")) {
                    val ppt = HSLFSlideShow(input)
                    val slides = ppt.slides.mapIndexed { idx, slide ->
                        coroutineContext.ensureActive()
                        val lines = slide.shapes
                            .filterIsInstance<org.apache.poi.hslf.usermodel.HSLFTextShape>()
                            .mapNotNull { it.text?.trim() }
                            .filter { it.isNotEmpty() }
                        SlideData(
                            index = idx + 1,
                            title = slide.title ?: "Slide ${idx + 1}",
                            bodyLines = lines
                        )
                    }
                    ppt.close()
                    return@withContext ParsedPresentation(slides = slides, isSimplifiedView = true)
                }

                val pptx = XMLSlideShow(input)
                val slides = pptx.slides.mapIndexed { idx, slide ->
                    coroutineContext.ensureActive()
                    val title = slide.title?.trim().orEmpty().ifEmpty { "Slide ${idx + 1}" }
                    val bodyLines = mutableListOf<String>()
                    val tables = mutableListOf<List<List<String>>>()
                    val images = mutableListOf<ByteArray>()

                    slide.shapes.forEach { shape ->
                        when (shape) {
                            is XSLFTextShape -> {
                                val t = shape.text?.trim().orEmpty()
                                if (t.isNotEmpty() && t != title) {
                                    bodyLines += t.lines().filter { it.isNotBlank() }
                                }
                            }
                            is XSLFTable -> {
                                tables += shape.rows.map { row ->
                                    row.cells.map { cell -> cell.text.orEmpty().trim() }
                                }
                            }
                            is XSLFPictureShape -> {
                                shape.pictureData?.data?.let { images += it }
                            }
                        }
                    }

                    val bgColor = slide.background?.fillColor?.let { color ->
                        String.format("#%02X%02X%02X", color.red, color.green, color.blue)
                    }

                    SlideData(
                        index = idx + 1,
                        title = title,
                        bodyLines = bodyLines,
                        tables = tables,
                        images = images,
                        backgroundHex = bgColor
                    )
                }
                pptx.close()
                ParsedPresentation(slides = slides, isSimplifiedView = true)
            }
        }
}

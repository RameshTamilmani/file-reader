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
                                val text = shape.text?.trim().orEmpty()
                                if (text.isNotEmpty() && text != title) {
                                    bodyLines += text.lines().filter { it.isNotBlank() }
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

                    // Avoid statically referencing java.awt.Color, which is unavailable on Android.
                    val bgColor = try {
                        val background = slide.background
                        val color = background?.javaClass
                            ?.methods
                            ?.firstOrNull { it.name == "getFillColor" && it.parameterCount == 0 }
                            ?.invoke(background)

                        fun colorComponent(methodName: String): Int? = color
                            ?.javaClass
                            ?.methods
                            ?.firstOrNull { it.name == methodName && it.parameterCount == 0 }
                            ?.invoke(color)
                            ?.let { it as? Number }
                            ?.toInt()

                        val red = colorComponent("getRed")
                        val green = colorComponent("getGreen")
                        val blue = colorComponent("getBlue")
                        if (red != null && green != null && blue != null) {
                            String.format("#%02X%02X%02X", red, green, blue)
                        } else {
                            null
                        }
                    } catch (_: Exception) {
                        null
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

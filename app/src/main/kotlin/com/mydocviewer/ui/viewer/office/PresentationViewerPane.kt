package com.mydocviewer.ui.viewer.office

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun PresentationViewerPane(
    presentation: ParsedPresentation,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTapContent: () -> Unit
) {
    val slides = presentation.slides
    if (slides.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = (initialPage - 1).coerceIn(0, slides.lastIndex),
        pageCount = { slides.size }
    )
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage + 1)
        scale = 1f
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFD6D6D6))
                .transformable(transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTapContent() },
                        onDoubleTap = { scale = if (scale > 1.2f) 1f else 2f }
                    )
                }
        ) { pageIndex ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val slideWidth = minOf(maxWidth, maxHeight * presentation.aspectRatio)
                val slideHeight = slideWidth / presentation.aspectRatio
                SlideCanvas(
                    slide = slides[pageIndex],
                    presentation = presentation,
                    modifier = Modifier
                        .width(slideWidth)
                        .height(slideHeight)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            itemsIndexed(slides, key = { _, slide -> slide.index }) { index, slide ->
                val selected = index == pagerState.currentPage
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .width(108.dp)
                        .aspectRatio(presentation.aspectRatio)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    SlideCanvas(
                        slide = slide,
                        presentation = presentation,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideCanvas(
    slide: SlideData,
    presentation: ParsedPresentation,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RectangleShape)
            .background(slide.backgroundHex.toComposeColor(Color.White))
    ) {
        val slideWidth = presentation.slideWidth.toFloat().coerceAtLeast(1f)
        val slideHeight = presentation.slideHeight.toFloat().coerceAtLeast(1f)
        slide.elements.forEach { element ->
            val left = maxWidth * (element.x / slideWidth)
            val top = maxHeight * (element.y / slideHeight)
            val width = maxWidth * (element.width / slideWidth)
            val height = maxHeight * (element.height / slideHeight)
            if (width <= 0.dp || height <= 0.dp) return@forEach
            SlideElementView(
                element = element,
                slideWidth = slideWidth,
                availableWidth = maxWidth,
                modifier = Modifier
                    .offset(left, top)
                    .size(width, height)
                    .graphicsLayer(rotationZ = element.rotationDegrees)
            )
        }
    }
}

@Composable
private fun SlideElementView(
    element: SlideElement,
    slideWidth: Float,
    availableWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val shape = when {
        element.shapeKind == "ellipse" || element.shapeKind == "arc" -> CircleShape
        element.shapeKind == "roundRect" -> RoundedCornerShape(4.dp)
        else -> RectangleShape
    }
    val background = element.fillHex.toComposeColor(Color.Transparent)
    val borderColor = element.lineHex?.toComposeColorOrNull()
    val borderWidth = if (borderColor != null) 0.7.dp else 0.dp
    val image = remember(element.imageBytes) { decodeSlideImage(element.imageBytes) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier)
    ) {
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (element.tableRows.isNotEmpty()) {
            SlideTable(element.tableRows, Modifier.fillMaxSize())
        } else if (element.paragraphs.isNotEmpty()) {
            val leftInset = availableWidth * (element.leftInset / slideWidth)
            val rightInset = availableWidth * (element.rightInset / slideWidth)
            val topInset = availableWidth * (element.topInset / slideWidth)
            val bottomInset = availableWidth * (element.bottomInset / slideWidth)
            val verticalAlignment = when (element.verticalAnchor) {
                "ctr" -> Arrangement.Center
                "b" -> Arrangement.Bottom
                else -> Arrangement.Top
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = leftInset,
                        top = topInset,
                        end = rightInset,
                        bottom = bottomInset
                    ),
                verticalArrangement = verticalAlignment
            ) {
                element.paragraphs.forEach { paragraph ->
                    SlideParagraphText(
                        paragraph = paragraph,
                        slideWidth = slideWidth,
                        availableWidth = availableWidth
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideParagraphText(
    paragraph: SlideParagraph,
    slideWidth: Float,
    availableWidth: androidx.compose.ui.unit.Dp
) {
    val alignment = when (paragraph.alignment) {
        "ctr" -> TextAlign.Center
        "r" -> TextAlign.Right
        "just" -> TextAlign.Justify
        else -> TextAlign.Left
    }
    val text = buildAnnotatedString {
        paragraph.bullet?.let {
            append(it)
            append(" ")
        }
        paragraph.runs.forEach { run ->
            pushStyle(
                SpanStyle(
                    fontSize = (run.fontSizePoints * EMU_PER_POINT * availableWidth.value / slideWidth)
                        .coerceIn(1.5f, 96f).sp,
                    fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    color = run.colorHex.toComposeColor(Color.Unspecified)
                )
            )
            append(run.text)
            pop()
        }
    }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = alignment
    )
}

@Composable
private fun SlideTable(rows: List<List<String>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        rows.forEach { row ->
            Row(modifier = Modifier.weight(1f)) {
                row.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .border(0.5.dp, Color.DarkGray)
                            .padding(2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = cell,
                            fontSize = 6.sp,
                            maxLines = 3,
                            lineHeight = 7.sp
                        )
                    }
                }
            }
        }
    }
}

private fun decodeSlideImage(bytes: ByteArray?): Bitmap? {
    if (bytes == null) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sampleSize = generateSequence(1) { it * 2 }
            .takeWhile { bounds.outWidth / it > MAX_DECODED_IMAGE_SIZE || bounds.outHeight / it > MAX_DECODED_IMAGE_SIZE }
            .lastOrNull()
            ?: 1
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: run {
            Log.w("PptxViewer", "Unable to decode an embedded slide image")
            null
        }
    } catch (exception: RuntimeException) {
        Log.w("PptxViewer", "Unable to decode an embedded slide image", exception)
        null
    } catch (error: OutOfMemoryError) {
        Log.w("PptxViewer", "Not enough memory to decode an embedded slide image", error)
        null
    }
}

private fun String?.toComposeColor(fallback: Color): Color =
    toComposeColorOrNull() ?: fallback

private fun String?.toComposeColorOrNull(): Color? {
    if (this == null) return null
    return try {
        Color(AndroidColor.parseColor(this))
    } catch (_: IllegalArgumentException) {
        null
    }
}

private const val EMU_PER_POINT = 12700f
private const val MAX_DECODED_IMAGE_SIZE = 2048

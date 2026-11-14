package com.mydocviewer.ui.viewer.office

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydocviewer.R
import com.mydocviewer.viewer.office.ParsedPresentation
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
        scale = (scale * zoomChange).coerceIn(1f, 3f)
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage + 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (presentation.isSimplifiedView) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.simplified_view_notice),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .transformable(transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTapContent() },
                        onDoubleTap = { scale = if (scale > 1.2f) 1f else 2f }
                    )
                }
        ) { pageIdx ->
            val slide = slides[pageIdx]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        slide.bodyLines.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        slide.tables.forEach { table ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                table.forEach { row ->
                                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        row.forEach { cell ->
                                            Text(
                                                text = cell,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                                    .padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        slide.images.forEachIndexed { imageIndex, imgBytes ->
                            val bmp = remember(imgBytes) {
                                try {
                                    BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                                        ?: run {
                                            Log.w(
                                                "PresentationViewer",
                                                "Unable to decode image ${imageIndex + 1} on slide ${slide.index}"
                                            )
                                            null
                                        }
                                } catch (exception: RuntimeException) {
                                    Log.w(
                                        "PresentationViewer",
                                        "Unable to decode image ${imageIndex + 1} on slide ${slide.index}",
                                        exception
                                    )
                                    null
                                } catch (error: OutOfMemoryError) {
                                    Log.w(
                                        "PresentationViewer",
                                        "Not enough memory to decode image ${imageIndex + 1} on slide ${slide.index}",
                                        error
                                    )
                                    null
                                }
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Slide illustration",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                )
                            }
                        }
                        if (slide.notes.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.presentation_notes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = slide.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            itemsIndexed(slides) { idx, slide ->
                val selected = idx == pagerState.currentPage
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .width(108.dp)
                        .height(64.dp)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(idx) }
                        }
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(
                            text = "${slide.index}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

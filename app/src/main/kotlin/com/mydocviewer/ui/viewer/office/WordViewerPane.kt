package com.mydocviewer.ui.viewer.office

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mydocviewer.R
import com.mydocviewer.viewer.office.ParsedWordDocument
import com.mydocviewer.viewer.office.WordBlock

@Composable
fun WordViewerPane(
    document: ParsedWordDocument,
    onTapContent: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3f)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (document.isSimplifiedView) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTapContent() },
                        onDoubleTap = { scale = if (scale > 1.2f) 1f else 2f }
                    )
                }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(document.blocks) { block ->
                    when (block) {
                        is WordBlock.Heading -> {
                            val style = when (block.level) {
                                1 -> MaterialTheme.typography.headlineSmall
                                2 -> MaterialTheme.typography.titleLarge
                                else -> MaterialTheme.typography.titleMedium
                            }
                            Text(text = block.text, style = style, fontWeight = FontWeight.SemiBold)
                        }
                        is WordBlock.Paragraph -> {
                            val annotated = buildAnnotatedString {
                                if (block.isBullet) append("•  ")
                                block.spans.forEach { span ->
                                    val color = span.colorHex?.let { hex ->
                                        runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrNull()
                                    } ?: Color.Unspecified
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal,
                                            color = color
                                        )
                                    ) {
                                        append(span.text)
                                    }
                                }
                            }
                            Text(text = annotated, style = MaterialTheme.typography.bodyLarge)
                        }
                        is WordBlock.Table -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                block.rows.forEachIndexed { rowIdx, row ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        row.forEach { cell ->
                                            Text(
                                                text = cell,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (rowIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                    .padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is WordBlock.EmbeddedImage -> {
                            val bitmap = remember(block.bytes) {
                                BitmapFactory.decodeByteArray(block.bytes, 0, block.bytes.size)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Embedded document image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

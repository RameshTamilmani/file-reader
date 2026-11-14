package com.mydocviewer.ui.viewer.text

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydocviewer.viewer.text.ParsedTextDocument

@Composable
fun TextViewerPane(
    document: ParsedTextDocument,
    nightReadingMode: Boolean,
    onTapContent: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 2.5f)
    }

    val bgColor = if (nightReadingMode) Color(0xFF14110C) else MaterialTheme.colorScheme.background
    val textColor = if (nightReadingMode) Color(0xFFE7DEC8) else MaterialTheme.colorScheme.onBackground

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .transformable(transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapContent() },
                    onDoubleTap = { scale = if (scale > 1.15f) 1f else 1.4f }
                )
            },
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(document.lines) { line ->
            val trimmed = line.trimStart()
            val isHeading = document.isMarkdown && trimmed.startsWith("#")
            val isCode = document.isMarkdown && trimmed.startsWith("```")

            Text(
                text = if (isHeading) trimmed.trimStart('#').trimStart() else line,
                color = textColor,
                fontSize = ((if (isHeading) 20f else 15f) * scale).sp,
                lineHeight = ((if (isHeading) 28f else 23f) * scale).sp,
                fontWeight = if (isHeading) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

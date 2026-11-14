package com.mydocviewer.ui.viewer.pdf

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.mydocviewer.R
import com.mydocviewer.viewer.pdf.LocalPdfHelper
import kotlinx.coroutines.launch
import java.io.File

/**
 * Local PDF Viewer backed by AndroidX PDF (PdfViewerFragment) on Android 12+ (API 31+, Ext 13+)
 * with a native PdfRenderer fallback on Android 10-11 (API 29-30) so minSdk 29 runs safely.
 */
@Composable
fun PdfViewerPane(
    cachedFile: File,
    pageCount: Int,
    requiresPasswordInitially: Boolean,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    showThumbnails: Boolean,
    onTapContent: () -> Unit
) {
    var showPasswordDialog by remember { mutableStateOf(requiresPasswordInitially) }
    var passwordInput by remember { mutableStateOf("") }
    var thumbnails by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var selectedThumbPage by remember(currentPage) { mutableIntStateOf(currentPage) }

    val supportsAndroidXPdf = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13
    }

    LaunchedEffect(showThumbnails, cachedFile, pageCount) {
        if (showThumbnails && !requiresPasswordInitially) {
            val loaded = mutableMapOf<Int, Bitmap>()
            val limit = minOf(pageCount, 30)
            for (idx in 0 until limit) {
                LocalPdfHelper.renderThumbnail(cachedFile, idx)?.let { bmp ->
                    loaded[idx + 1] = bmp
                }
            }
            thumbnails = loaded
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val fileUri = remember(cachedFile) { Uri.fromFile(cachedFile) }

        if (supportsAndroidXPdf) {
            AndroidFragment<PdfViewerFragment>(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onTapContent),
                arguments = Bundle().apply {
                    putParcelable("documentUri", fileUri)
                },
                onUpdate = { fragment ->
                    fragment.documentUri = fileUri
                }
            )
        } else {
            LegacyPdfRendererView(
                cachedFile = cachedFile,
                pageCount = pageCount,
                currentPage = currentPage,
                onPageChange = onPageChange,
                onTapContent = onTapContent
            )
        }

        if (showThumbnails && pageCount > 0) {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((1..minOf(pageCount, 30)).toList()) { pageNum ->
                        val bmp = thumbnails[pageNum]
                        val active = pageNum == selectedThumbPage
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(
                                    if (active) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                )
                                .clickable {
                                    selectedThumbPage = pageNum
                                    onPageChange(pageNum)
                                }
                                .padding(6.dp)
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page $pageNum",
                                    modifier = Modifier
                                        .width(54.dp)
                                        .height(72.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(54.dp)
                                        .height(72.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$pageNum", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$pageNum", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(stringResource(R.string.password_required_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.password_required_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPasswordDialog = false }) {
                    Text(stringResource(R.string.unlock))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LegacyPdfRendererView(
    cachedFile: File,
    pageCount: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onTapContent: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3f)
    }

    LaunchedEffect(currentPage) {
        val target = (currentPage - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (listState.firstVisibleItemIndex != target) {
            scope.launch { listState.animateScrollToItem(target) }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageChange(listState.firstVisibleItemIndex + 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0))
            .transformable(transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapContent() },
                    onDoubleTap = { scale = if (scale > 1.2f) 1f else 2f }
                )
            },
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pageCount) { pageIdx ->
            var pageBitmap by remember(cachedFile, pageIdx) { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(cachedFile, pageIdx) {
                pageBitmap = LocalPdfHelper.renderPage(cachedFile, pageIdx, widthPx = 1080)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                contentAlignment = Alignment.Center
            ) {
                val bmp = pageBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIdx + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

package com.mydocviewer.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mydocviewer.R
import com.mydocviewer.domain.model.DocumentType
import com.mydocviewer.ui.viewer.archive.ArchiveViewerPane
import com.mydocviewer.ui.viewer.archive.EpubViewerPane
import com.mydocviewer.ui.viewer.image.ImageViewerPane
import com.mydocviewer.ui.viewer.office.PresentationViewerPane
import com.mydocviewer.ui.viewer.office.SpreadsheetViewerPane
import com.mydocviewer.ui.viewer.office.WordViewerPane
import com.mydocviewer.ui.viewer.pdf.PdfViewerPane
import com.mydocviewer.ui.viewer.text.TextViewerPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    uriString: String,
    onBack: () -> Unit,
    viewModel: DocumentViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showMenu by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showThumbnails by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }
    var imageRotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uriString) {
        viewModel.openDocument(uriString)
    }

    LaunchedEffect(state.statusBanner) {
        val msg = state.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    // Immersive full-screen mode controller
    LaunchedEffect(state.isImmersive) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (state.isImmersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val doc = state.document

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!state.isImmersive) {
                TopAppBar(
                    title = {
                        Text(
                            text = doc?.displayName ?: "Document",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (doc?.isFavorite == true) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.add_to_favorites)
                            )
                        }
                        IconButton(onClick = viewModel::addBookmarkAtCurrentPosition) {
                            Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Bookmark")
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (doc?.documentType == DocumentType.TEXT) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.night_reading_mode)) },
                                        leadingIcon = { Icon(Icons.Outlined.DarkMode, null) },
                                        onClick = {
                                            viewModel.toggleNightReadingMode()
                                            showMenu = false
                                        }
                                    )
                                }
                                if (doc?.documentType == DocumentType.PDF) {
                                    DropdownMenuItem(
                                        text = { Text("Thumbnails") },
                                        onClick = {
                                            showThumbnails = !showThumbnails
                                            showMenu = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rotate_screen)) },
                                    leadingIcon = { Icon(Icons.Outlined.ScreenRotation, null) },
                                    onClick = {
                                        if (doc?.documentType == DocumentType.IMAGE) {
                                            imageRotation = (imageRotation + 90f) % 360f
                                        } else if (activity != null) {
                                            val isLandscape = activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                            activity.requestedOrientation = if (isLandscape) {
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                            }
                                        }
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_file)) },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                    onClick = {
                                        showMenu = false
                                        if (doc != null) shareUri(context, Uri.parse(doc.uriString), doc.mimeType)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_location)) },
                                    onClick = {
                                        showMenu = false
                                        openDownloadsOrFiles(context)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.print_document)) },
                                    onClick = {
                                        showMenu = false
                                        if (doc != null) printSimpleSummary(context, doc.displayName)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!state.isImmersive && (doc?.documentType == DocumentType.PDF || doc?.documentType == DocumentType.POWERPOINT)) {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.page_of, state.currentPage, state.totalPages),
                            style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(onClick = {
                            jumpInput = state.currentPage.toString()
                            showJumpDialog = true
                        }) {
                            Text(stringResource(R.string.jump_to_page))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val content = state.content) {
                is ViewerContent.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ViewerContent.Pdf -> {
                    PdfViewerPane(
                        cachedFile = content.cachedFile,
                        pageCount = content.pageCount,
                        requiresPasswordInitially = content.requiresPassword,
                        currentPage = state.currentPage,
                        onPageChange = { p -> viewModel.updatePage(p, content.pageCount) },
                        showThumbnails = showThumbnails,
                        onTapContent = viewModel::toggleImmersive
                    )
                }
                is ViewerContent.Word -> {
                    WordViewerPane(document = content.document, onTapContent = viewModel::toggleImmersive)
                }
                is ViewerContent.Spreadsheet -> {
                    SpreadsheetViewerPane(workbook = content.workbook)
                }
                is ViewerContent.Presentation -> {
                    PresentationViewerPane(
                        presentation = content.presentation,
                        initialPage = state.currentPage,
                        onPageChanged = { p -> viewModel.updatePage(p, content.presentation.slides.size) },
                        onTapContent = viewModel::toggleImmersive
                    )
                }
                is ViewerContent.Text -> {
                    TextViewerPane(
                        document = content.textDoc,
                        nightReadingMode = state.nightReadingMode,
                        onTapContent = viewModel::toggleImmersive
                    )
                }
                is ViewerContent.Image -> {
                    ImageViewerPane(
                        uri = content.uri,
                        rotationDegrees = imageRotation,
                        onTapContent = viewModel::toggleImmersive
                    )
                }
                is ViewerContent.Archive -> {
                    ArchiveViewerPane(
                        entries = content.entries,
                        onExtractSelected = viewModel::extractArchiveEntries
                    )
                }
                is ViewerContent.Epub -> {
                    EpubViewerPane(chapters = content.chapters)
                }
                is ViewerContent.Unsupported -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.unsupported_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.unsupported_body),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = {
                            openWithAnotherApp(context, Uri.parse(content.item.uriString), content.item.mimeType)
                        }) {
                            Text(stringResource(R.string.open_with_another_app))
                        }
                    }
                }
                is ViewerContent.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.error_corrupted_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = content.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (content.item != null) {
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedButton(onClick = {
                                openWithAnotherApp(context, Uri.parse(content.item.uriString), content.item.mimeType)
                            }) {
                                Text(stringResource(R.string.open_with_another_app))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text(stringResource(R.string.jump_to_page)) },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { jumpInput = it.filter(Char::isDigit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = jumpInput.toIntOrNull() ?: state.currentPage
                    viewModel.updatePage(target, state.totalPages)
                    showJumpDialog = false
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun shareUri(context: Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_file)))
}

private fun openWithAnotherApp(context: Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with_another_app)))
}

private fun openDownloadsOrFiles(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        type = "vnd.android.document/directory"
    }
    runCatching { context.startActivity(intent) }
}

private fun printSimpleSummary(context: Context, title: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val webView = WebView(context)
    webView.loadDataWithBaseURL(null, "<html><body><h2>$title</h2></body></html>", "text/html", "UTF-8", null)
    val adapter = webView.createPrintDocumentAdapter(title)
    printManager.print(title, adapter, PrintAttributes.Builder().build())
}

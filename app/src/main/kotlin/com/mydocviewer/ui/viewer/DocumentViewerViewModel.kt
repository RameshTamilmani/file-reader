package com.mydocviewer.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydocviewer.data.local.entity.BookmarkEntity
import com.mydocviewer.data.preferences.UserPreferencesRepository
import com.mydocviewer.data.repository.DocumentRepository
import com.mydocviewer.domain.model.DocumentItem
import com.mydocviewer.domain.model.DocumentType
import com.mydocviewer.viewer.archive.ArchiveAndEpubParser
import com.mydocviewer.viewer.archive.ArchiveEntryItem
import com.mydocviewer.viewer.office.ParsedPresentation
import com.mydocviewer.viewer.office.ParsedSpreadsheet
import com.mydocviewer.viewer.office.ParsedWordDocument
import com.mydocviewer.viewer.office.PresentationParser
import com.mydocviewer.viewer.office.SpreadsheetParser
import com.mydocviewer.viewer.office.WordDocumentParser
import com.mydocviewer.viewer.pdf.LocalPdfHelper
import com.mydocviewer.viewer.pdf.PdfInspectionResult
import com.mydocviewer.viewer.text.ParsedTextDocument
import com.mydocviewer.viewer.text.TextDocumentParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface ViewerContent {
    data object Loading : ViewerContent
    data class Pdf(val cachedFile: File, val pageCount: Int, val requiresPassword: Boolean = false) : ViewerContent
    data class Word(val document: ParsedWordDocument) : ViewerContent
    data class Spreadsheet(val workbook: ParsedSpreadsheet) : ViewerContent
    data class Presentation(val presentation: ParsedPresentation) : ViewerContent
    data class Text(val textDoc: ParsedTextDocument) : ViewerContent
    data class Image(val uri: Uri) : ViewerContent
    data class Archive(val entries: List<ArchiveEntryItem>) : ViewerContent
    data class Epub(val chapters: List<Pair<String, String>>) : ViewerContent
    data class Unsupported(val item: DocumentItem) : ViewerContent
    data class Error(val message: String, val item: DocumentItem?) : ViewerContent
}

data class DocumentViewerUiState(
    val document: DocumentItem? = null,
    val content: ViewerContent = ViewerContent.Loading,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isImmersive: Boolean = false,
    val nightReadingMode: Boolean = false,
    val statusBanner: String? = null
)

@HiltViewModel
class DocumentViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentViewerUiState())
    val uiState: StateFlow<DocumentViewerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var bookmarksJob: Job? = null

    fun openDocument(uriString: String) {
        loadJob?.cancel()
        bookmarksJob?.cancel()

        loadJob = viewModelScope.launch {
            val nightMode = prefsRepo.preferences.first().nightReadingMode
            _uiState.value = DocumentViewerUiState(
                content = ViewerContent.Loading,
                nightReadingMode = nightMode
            )

            val item = runCatching {
                repository.recordOpen(Uri.parse(uriString))
            }.getOrElse { ex ->
                _uiState.value = _uiState.value.copy(
                    content = ViewerContent.Error(
                        ex.localizedMessage ?: "Cannot access selected file.",
                        null
                    )
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                document = item,
                currentPage = (item.lastPage).coerceAtLeast(1),
                totalPages = item.totalPages.coerceAtLeast(1)
            )

            bookmarksJob = viewModelScope.launch {
                repository.observeBookmarks(item.uriString).collect { list ->
                    _uiState.value = _uiState.value.copy(bookmarks = list)
                }
            }

            if (item.sizeBytes == 0L && item.documentType != DocumentType.UNSUPPORTED) {
                _uiState.value = _uiState.value.copy(
                    content = ViewerContent.Error("This file is empty (0 bytes).", item)
                )
                return@launch
            }

            val uri = Uri.parse(item.uriString)
            val result = runCatching {
                when (item.documentType) {
                    DocumentType.PDF -> {
                        when (val pdf = LocalPdfHelper.prepareLocalCopy(context, uri)) {
                            is PdfInspectionResult.Ready -> {
                                _uiState.value = _uiState.value.copy(totalPages = pdf.pageCount.coerceAtLeast(1))
                                ViewerContent.Pdf(pdf.cachedFile, pdf.pageCount, requiresPassword = false)
                            }
                            is PdfInspectionResult.PasswordRequired -> {
                                ViewerContent.Pdf(pdf.cachedFile, 1, requiresPassword = true)
                            }
                            is PdfInspectionResult.Error -> ViewerContent.Error(pdf.message, item)
                        }
                    }
                    DocumentType.WORD -> ViewerContent.Word(WordDocumentParser.parse(context, uri, item.displayName))
                    DocumentType.EXCEL, DocumentType.CSV -> {
                        ViewerContent.Spreadsheet(SpreadsheetParser.parse(context, uri, item.displayName))
                    }
                    DocumentType.POWERPOINT -> {
                        val pres = PresentationParser.parse(context, uri, item.displayName)
                        _uiState.value = _uiState.value.copy(totalPages = pres.slides.size.coerceAtLeast(1))
                        ViewerContent.Presentation(pres)
                    }
                    DocumentType.TEXT -> ViewerContent.Text(TextDocumentParser.parse(context, uri, item.displayName))
                    DocumentType.IMAGE -> ViewerContent.Image(uri)
                    DocumentType.ARCHIVE -> ViewerContent.Archive(ArchiveAndEpubParser.listZipEntries(context, uri))
                    DocumentType.EPUB -> ViewerContent.Epub(ArchiveAndEpubParser.extractEpubChapters(context, uri))
                    DocumentType.UNSUPPORTED -> ViewerContent.Unsupported(item)
                }
            }.getOrElse { ex ->
                ViewerContent.Error(
                    ex.localizedMessage ?: "File appears corrupted or unreadable.",
                    item
                )
            }

            _uiState.value = _uiState.value.copy(content = result)
        }
    }

    fun toggleImmersive() {
        _uiState.value = _uiState.value.copy(isImmersive = !_uiState.value.isImmersive)
    }

    fun toggleNightReadingMode() {
        val next = !_uiState.value.nightReadingMode
        _uiState.value = _uiState.value.copy(nightReadingMode = next)
        viewModelScope.launch { prefsRepo.setNightReadingMode(next) }
    }

    fun updatePage(page: Int, total: Int) {
        val safePage = page.coerceIn(1, total.coerceAtLeast(1))
        _uiState.value = _uiState.value.copy(currentPage = safePage, totalPages = total.coerceAtLeast(1))
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            repository.updateReadingPosition(doc.uriString, safePage, 0, total)
        }
    }

    fun addBookmarkAtCurrentPosition() {
        val doc = _uiState.value.document ?: return
        val page = _uiState.value.currentPage
        viewModelScope.launch {
            repository.addBookmark(doc.uriString, page, 0, "Page $page")
            _uiState.value = _uiState.value.copy(statusBanner = "Bookmark saved at page $page")
        }
    }

    fun toggleFavorite() {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            repository.toggleFavorite(doc)
            _uiState.value = _uiState.value.copy(document = doc.copy(isFavorite = !doc.isFavorite))
        }
    }

    fun extractArchiveEntries(selectedNames: Set<String>, destTreeUri: Uri) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            runCatching {
                ArchiveAndEpubParser.extractSelectedEntries(
                    context,
                    Uri.parse(doc.uriString),
                    selectedNames,
                    destTreeUri
                )
            }.onSuccess { count ->
                _uiState.value = _uiState.value.copy(statusBanner = "Extracted $count file(s)")
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(statusBanner = ex.localizedMessage ?: "Extraction failed")
            }
        }
    }

    fun clearBanner() {
        _uiState.value = _uiState.value.copy(statusBanner = null)
    }
}

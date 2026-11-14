package com.mydocviewer.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydocviewer.data.local.entity.BookmarkEntity
import com.mydocviewer.data.repository.DocumentRepository
import com.mydocviewer.domain.model.DocumentItem
import com.mydocviewer.domain.model.DocumentType
import com.mydocviewer.viewer.office.ParsedPresentation
import com.mydocviewer.viewer.office.PresentationParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ViewerContent {
    data object Loading : ViewerContent
    data class Presentation(val presentation: ParsedPresentation) : ViewerContent
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
    val statusBanner: String? = null
)

@HiltViewModel
class DocumentViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentViewerUiState())
    val uiState: StateFlow<DocumentViewerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var bookmarksJob: Job? = null

    fun openDocument(uriString: String) {
        loadJob?.cancel()
        bookmarksJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = DocumentViewerUiState(content = ViewerContent.Loading)
            val item = runCatching {
                repository.recordOpen(Uri.parse(uriString))
            }.getOrElse { exception ->
                _uiState.value = _uiState.value.copy(
                    content = ViewerContent.Error(
                        exception.localizedMessage ?: "Cannot access the selected file.",
                        null
                    )
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                document = item,
                currentPage = item.lastPage.coerceAtLeast(1),
                totalPages = item.totalPages.coerceAtLeast(1)
            )

            bookmarksJob = viewModelScope.launch {
                repository.observeBookmarks(item.uriString).collect { bookmarks ->
                    _uiState.value = _uiState.value.copy(bookmarks = bookmarks)
                }
            }

            if (item.documentType != DocumentType.POWERPOINT) {
                _uiState.value = _uiState.value.copy(content = ViewerContent.Unsupported(item))
                return@launch
            }
            if (item.sizeBytes == 0L) {
                _uiState.value = _uiState.value.copy(
                    content = ViewerContent.Error("The selected PowerPoint file is empty.", item)
                )
                return@launch
            }

            val result = runCatching {
                PresentationParser.parse(context, Uri.parse(item.uriString))
            }.fold(
                onSuccess = { presentation ->
                    _uiState.value = _uiState.value.copy(
                        totalPages = presentation.slides.size.coerceAtLeast(1)
                    )
                    ViewerContent.Presentation(presentation)
                },
                onFailure = { exception ->
                    ViewerContent.Error(
                        exception.localizedMessage ?: "Unable to read this PowerPoint presentation.",
                        item
                    )
                }
            )
            _uiState.value = _uiState.value.copy(content = result)
        }
    }

    fun toggleImmersive() {
        _uiState.value = _uiState.value.copy(isImmersive = !_uiState.value.isImmersive)
    }

    fun updatePage(page: Int, total: Int) {
        val safePage = page.coerceIn(1, total.coerceAtLeast(1))
        _uiState.value = _uiState.value.copy(
            currentPage = safePage,
            totalPages = total.coerceAtLeast(1)
        )
        val document = _uiState.value.document ?: return
        viewModelScope.launch {
            repository.updateReadingPosition(document.uriString, safePage, 0, total)
        }
    }

    fun addBookmarkAtCurrentPosition() {
        val document = _uiState.value.document ?: return
        val page = _uiState.value.currentPage
        viewModelScope.launch {
            repository.addBookmark(document.uriString, page, 0, "Slide $page")
            _uiState.value = _uiState.value.copy(statusBanner = "Bookmark saved at slide $page")
        }
    }

    fun toggleFavorite() {
        val document = _uiState.value.document ?: return
        viewModelScope.launch {
            repository.toggleFavorite(document)
            _uiState.value = _uiState.value.copy(
                document = document.copy(isFavorite = !document.isFavorite)
            )
        }
    }

    fun clearBanner() {
        _uiState.value = _uiState.value.copy(statusBanner = null)
    }
}

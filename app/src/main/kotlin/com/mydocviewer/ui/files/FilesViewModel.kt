package com.mydocviewer.ui.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydocviewer.data.preferences.UserPreferencesRepository
import com.mydocviewer.data.repository.DocumentRepository
import com.mydocviewer.domain.model.DocumentItem
import com.mydocviewer.domain.model.DocumentType
import com.mydocviewer.domain.model.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilesUiState(
    val files: List<DocumentItem> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val selectedUris: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val currentFolderUri: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    private val rawFiles = MutableStateFlow<List<DocumentItem>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(SortOption.DATE_DESC)
    private val selectedUris = MutableStateFlow<Set<String>>(emptySet())
    private val isLoading = MutableStateFlow(false)
    private val currentFolderUri = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val savedFolder = prefs.preferences.first().lastFolderTreeUri
            if (savedFolder != null) {
                loadFolder(Uri.parse(savedFolder))
            }
        }
    }

    val uiState: StateFlow<FilesUiState> = combine(
        rawFiles,
        searchQuery,
        sortOption,
        selectedUris,
        isLoading,
        currentFolderUri,
        errorMessage,
        repository.observeFavorites()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val list = values[0] as List<DocumentItem>
        val query = values[1] as String
        val sort = values[2] as SortOption
        @Suppress("UNCHECKED_CAST")
        val selected = values[3] as Set<String>
        val loading = values[4] as Boolean
        val folder = values[5] as String?
        val err = values[6] as String?
        @Suppress("UNCHECKED_CAST")
        val favs = (values[7] as List<DocumentItem>).map { it.uriString }.toSet()

        val merged = list
            .filter { it.documentType == DocumentType.POWERPOINT }
            .map { it.copy(isFavorite = it.uriString in favs) }
            .filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
            .let { filtered ->
                when (sort) {
                    SortOption.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
                    SortOption.DATE_DESC -> filtered.sortedByDescending { it.lastModifiedAt }
                    SortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
                    SortOption.TYPE_ASC -> filtered.sortedBy { it.documentType.categoryLabel }
                }
            }

        FilesUiState(
            files = merged,
            searchQuery = query,
            sortOption = sort,
            selectedUris = selected,
            isLoading = loading,
            currentFolderUri = folder,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilesUiState()
    )

    fun loadFolder(treeUri: Uri) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            currentFolderUri.value = treeUri.toString()
            prefs.setLastFolderTreeUri(treeUri.toString())
            runCatching {
                repository.browseFolder(treeUri)
            }.onSuccess { children ->
                rawFiles.value = children
                selectedUris.value = emptySet()
            }.onFailure { ex ->
                errorMessage.value = ex.localizedMessage ?: "Unable to read selected folder"
            }
            isLoading.value = false
        }
    }

    fun onSearchChange(query: String) {
        searchQuery.value = query
    }

    fun onSortChange(option: SortOption) {
        sortOption.value = option
    }

    fun toggleSelect(uriString: String) {
        val current = selectedUris.value.toMutableSet()
        if (!current.add(uriString)) current.remove(uriString)
        selectedUris.value = current
    }

    fun clearSelection() {
        selectedUris.value = emptySet()
    }

    fun favoriteSelected() {
        viewModelScope.launch {
            val targets = rawFiles.value.filter {
                it.documentType == DocumentType.POWERPOINT && it.uriString in selectedUris.value
            }
            repository.setFavoritesBatch(targets, favorite = true)
            selectedUris.value = emptySet()
        }
    }
}

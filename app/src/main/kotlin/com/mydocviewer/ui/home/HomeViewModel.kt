package com.mydocviewer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydocviewer.data.repository.DocumentRepository
import com.mydocviewer.domain.model.DocumentFilter
import com.mydocviewer.domain.model.DocumentItem
import com.mydocviewer.domain.model.DocumentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val searchQuery: String = "",
    val activeFilter: DocumentFilter = DocumentFilter.ALL,
    val recentDocuments: List<DocumentItem> = emptyList(),
    val favoriteDocuments: List<DocumentItem> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val activeFilter = MutableStateFlow(DocumentFilter.ALL)

    val uiState: StateFlow<HomeUiState> = combine(
        searchQuery,
        activeFilter,
        repository.observeRecents(),
        repository.observeFavorites()
    ) { query, filter, recents, favorites ->
        val filteredRecents = recents.filter { doc ->
            val matchesQuery = query.isBlank() ||
                doc.displayName.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                DocumentFilter.ALL -> true
                DocumentFilter.PDF -> doc.documentType == DocumentType.PDF
                DocumentFilter.WORD -> doc.documentType == DocumentType.WORD
                DocumentFilter.EXCEL -> doc.documentType == DocumentType.EXCEL || doc.documentType == DocumentType.CSV
                DocumentFilter.POWERPOINT -> doc.documentType == DocumentType.POWERPOINT
                DocumentFilter.TEXT -> doc.documentType == DocumentType.TEXT
                DocumentFilter.IMAGES -> doc.documentType == DocumentType.IMAGE
            }
            matchesQuery && matchesFilter
        }
        HomeUiState(
            searchQuery = query,
            activeFilter = filter,
            recentDocuments = filteredRecents,
            favoriteDocuments = favorites
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun onSearchChange(query: String) {
        searchQuery.value = query
    }

    fun onFilterChange(filter: DocumentFilter) {
        activeFilter.value = filter
    }

    fun toggleFavorite(item: DocumentItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }
}

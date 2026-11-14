package com.mydocviewer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydocviewer.data.repository.DocumentRepository
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
    val recentDocuments: List<DocumentItem> = emptyList(),
    val favoriteDocuments: List<DocumentItem> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        searchQuery,
        repository.observeRecents(),
        repository.observeFavorites()
    ) { query, recents, favorites ->
        val filteredRecents = recents.filter { doc ->
            doc.documentType == DocumentType.POWERPOINT &&
                (query.isBlank() || doc.displayName.contains(query, ignoreCase = true))
        }
        HomeUiState(
            searchQuery = query,
            recentDocuments = filteredRecents,
            favoriteDocuments = favorites.filter { it.documentType == DocumentType.POWERPOINT }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun onSearchChange(query: String) {
        searchQuery.value = query
    }

    fun toggleFavorite(item: DocumentItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }
}

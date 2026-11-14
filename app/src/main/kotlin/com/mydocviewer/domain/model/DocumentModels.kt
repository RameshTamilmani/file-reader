package com.mydocviewer.domain.model

enum class DocumentType(val categoryLabel: String) {
    POWERPOINT("PowerPoint"),
    UNSUPPORTED("Unsupported")
}

enum class SortOption {
    NAME_ASC,
    DATE_DESC,
    SIZE_DESC,
    TYPE_ASC
}

data class DocumentItem(
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val documentType: DocumentType,
    val sizeBytes: Long,
    val lastModifiedAt: Long,
    val lastOpenedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val lastPage: Int = 0,
    val lastScrollOffset: Int = 0,
    val totalPages: Int = 0
)

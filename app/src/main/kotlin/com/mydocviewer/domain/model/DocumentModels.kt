package com.mydocviewer.domain.model

enum class DocumentType(val categoryLabel: String) {
    PDF("PDF"),
    WORD("Word"),
    EXCEL("Excel"),
    CSV("Excel"),
    POWERPOINT("PowerPoint"),
    TEXT("Text"),
    IMAGE("Images"),
    EPUB("E-Book"),
    ARCHIVE("Archive"),
    UNSUPPORTED("Unsupported")
}

enum class DocumentFilter(val labelResName: String) {
    ALL("filter_all"),
    PDF("filter_pdf"),
    WORD("filter_word"),
    EXCEL("filter_excel"),
    POWERPOINT("filter_ppt"),
    TEXT("filter_text"),
    IMAGES("filter_images")
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

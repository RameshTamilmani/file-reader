package com.mydocviewer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mydocviewer.domain.model.DocumentItem
import com.mydocviewer.domain.model.DocumentType

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    val mimeType: String,
    val documentType: String,
    val sizeBytes: Long,
    val lastModifiedAt: Long,
    val lastOpenedAt: Long,
    val isFavorite: Boolean = false,
    val lastPage: Int = 0,
    val lastScrollOffset: Int = 0,
    val totalPages: Int = 0
) {
    fun toDomain(): DocumentItem = DocumentItem(
        uriString = uriString,
        displayName = displayName,
        mimeType = mimeType,
        documentType = runCatching { DocumentType.valueOf(documentType) }
            .getOrDefault(DocumentType.UNSUPPORTED),
        sizeBytes = sizeBytes,
        lastModifiedAt = lastModifiedAt,
        lastOpenedAt = lastOpenedAt,
        isFavorite = isFavorite,
        lastPage = lastPage,
        lastScrollOffset = lastScrollOffset,
        totalPages = totalPages
    )
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentUri: String,
    val pageNumber: Int,
    val scrollOffset: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

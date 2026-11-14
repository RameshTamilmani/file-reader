package com.mydocviewer.data.repository

import android.content.Context
import android.net.Uri
import com.mydocviewer.data.local.dao.DocumentDao
import com.mydocviewer.data.local.entity.BookmarkEntity
import com.mydocviewer.data.local.entity.RecentDocumentEntity
import com.mydocviewer.domain.model.DocumentItem
import com.mydocviewer.util.FileMetadataReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao
) {
    fun observeRecents(): Flow<List<DocumentItem>> =
        documentDao.observeRecentDocuments().map { list -> list.map { it.toDomain() } }

    fun observeFavorites(): Flow<List<DocumentItem>> =
        documentDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    fun observeBookmarks(uriString: String): Flow<List<BookmarkEntity>> =
        documentDao.observeBookmarksForDocument(uriString)

    suspend fun recordOpen(uri: Uri): DocumentItem = withContext(Dispatchers.IO) {
        val fresh = FileMetadataReader.readMetadata(context, uri)
        val existing = documentDao.getByUri(fresh.uriString)
        val merged = RecentDocumentEntity(
            uriString = fresh.uriString,
            displayName = fresh.displayName,
            mimeType = fresh.mimeType,
            documentType = fresh.documentType.name,
            sizeBytes = fresh.sizeBytes,
            lastModifiedAt = fresh.lastModifiedAt,
            lastOpenedAt = System.currentTimeMillis(),
            isFavorite = existing?.isFavorite ?: false,
            lastPage = existing?.lastPage ?: 0,
            lastScrollOffset = existing?.lastScrollOffset ?: 0,
            totalPages = existing?.totalPages ?: 0
        )
        documentDao.upsertDocument(merged)
        merged.toDomain()
    }

    suspend fun getOrInspectDocument(uriString: String): DocumentItem = withContext(Dispatchers.IO) {
        val existing = documentDao.getByUri(uriString)
        if (existing != null) return@withContext existing.toDomain()
        FileMetadataReader.readMetadata(context, Uri.parse(uriString))
    }

    suspend fun toggleFavorite(item: DocumentItem) = withContext(Dispatchers.IO) {
        val existing = documentDao.getByUri(item.uriString)
        if (existing != null) {
            documentDao.setFavorite(item.uriString, !existing.isFavorite)
        } else {
            documentDao.upsertDocument(
                RecentDocumentEntity(
                    uriString = item.uriString,
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    documentType = item.documentType.name,
                    sizeBytes = item.sizeBytes,
                    lastModifiedAt = item.lastModifiedAt,
                    lastOpenedAt = System.currentTimeMillis(),
                    isFavorite = true
                )
            )
        }
    }

    suspend fun setFavoritesBatch(items: List<DocumentItem>, favorite: Boolean) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            val existing = documentDao.getByUri(item.uriString)
            if (existing != null) {
                documentDao.setFavorite(item.uriString, favorite)
            } else {
                documentDao.upsertDocument(
                    RecentDocumentEntity(
                        uriString = item.uriString,
                        displayName = item.displayName,
                        mimeType = item.mimeType,
                        documentType = item.documentType.name,
                        sizeBytes = item.sizeBytes,
                        lastModifiedAt = item.lastModifiedAt,
                        lastOpenedAt = System.currentTimeMillis(),
                        isFavorite = favorite
                    )
                )
            }
        }
    }

    suspend fun updateReadingPosition(uriString: String, page: Int, scrollOffset: Int, totalPages: Int) =
        withContext(Dispatchers.IO) {
            documentDao.updateReadingPosition(uriString, page, scrollOffset, totalPages)
        }

    suspend fun addBookmark(uriString: String, pageNumber: Int, scrollOffset: Int, label: String) =
        withContext(Dispatchers.IO) {
            documentDao.insertBookmark(
                BookmarkEntity(
                    documentUri = uriString,
                    pageNumber = pageNumber,
                    scrollOffset = scrollOffset,
                    label = label
                )
            )
        }

    suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) {
        documentDao.deleteBookmark(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        documentDao.clearAllRecents()
        documentDao.clearAllBookmarks()
    }

    suspend fun browseFolder(treeUri: Uri): List<DocumentItem> = withContext(Dispatchers.IO) {
        FileMetadataReader.listFolderChildren(context, treeUri)
    }
}

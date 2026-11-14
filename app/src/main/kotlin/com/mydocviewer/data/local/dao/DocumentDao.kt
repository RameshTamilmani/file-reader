package com.mydocviewer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mydocviewer.data.local.entity.BookmarkEntity
import com.mydocviewer.data.local.entity.RecentDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC")
    fun observeRecentDocuments(): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents WHERE isFavorite = 1 ORDER BY lastOpenedAt DESC")
    fun observeFavorites(): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents WHERE uriString = :uriString LIMIT 1")
    suspend fun getByUri(uriString: String): RecentDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: RecentDocumentEntity)

    @Query("UPDATE recent_documents SET isFavorite = :isFavorite WHERE uriString = :uriString")
    suspend fun setFavorite(uriString: String, isFavorite: Boolean)

    @Query("UPDATE recent_documents SET lastPage = :page, lastScrollOffset = :scrollOffset, totalPages = :totalPages WHERE uriString = :uriString")
    suspend fun updateReadingPosition(uriString: String, page: Int, scrollOffset: Int, totalPages: Int)

    @Query("DELETE FROM recent_documents")
    suspend fun clearAllRecents()

    @Query("SELECT * FROM bookmarks WHERE documentUri = :documentUri ORDER BY pageNumber ASC, createdAt DESC")
    fun observeBookmarksForDocument(documentUri: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAllBookmarks()
}

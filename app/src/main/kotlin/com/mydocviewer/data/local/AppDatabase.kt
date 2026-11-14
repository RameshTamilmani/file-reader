package com.mydocviewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mydocviewer.data.local.dao.DocumentDao
import com.mydocviewer.data.local.entity.BookmarkEntity
import com.mydocviewer.data.local.entity.RecentDocumentEntity

@Database(
    entities = [RecentDocumentEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}

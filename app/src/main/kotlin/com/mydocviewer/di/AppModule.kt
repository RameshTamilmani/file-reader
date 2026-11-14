package com.mydocviewer.di

import android.content.Context
import androidx.room.Room
import com.mydocviewer.data.local.AppDatabase
import com.mydocviewer.data.local.dao.DocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "my_document_viewer.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()
}

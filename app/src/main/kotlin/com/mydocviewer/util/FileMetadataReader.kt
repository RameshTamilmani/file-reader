package com.mydocviewer.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import android.content.Context
import com.mydocviewer.domain.model.DocumentItem
import com.mydocviewer.domain.model.DocumentType

object FileMetadataReader {

    fun readMetadata(context: Context, uri: Uri): DocumentItem {
        val resolver: ContentResolver = context.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        var displayName = "document"
        var sizeBytes = 0L
        var lastModified = System.currentTimeMillis()

        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        runCatching {
            DocumentFile.fromSingleUri(context, uri)?.let { doc ->
                if (doc.name != null) displayName = doc.name!!
                if (doc.length() > 0) sizeBytes = doc.length()
                if (doc.lastModified() > 0) lastModified = doc.lastModified()
            }
        }

        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val docType = FileTypeDetector.detect(resolver, uri, displayName, mimeType)

        return DocumentItem(
            uriString = uri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            documentType = docType,
            sizeBytes = sizeBytes,
            lastModifiedAt = lastModified,
            lastOpenedAt = System.currentTimeMillis()
        )
    }

    fun listFolderChildren(context: Context, treeUri: Uri): List<DocumentItem> {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return root.listFiles()
            .filter { it.isFile }
            .map { doc ->
                val name = doc.name ?: "Untitled"
                val mime = doc.type ?: "application/octet-stream"
                val type = FileTypeDetector.detect(context.contentResolver, doc.uri, name, mime)
                DocumentItem(
                    uriString = doc.uri.toString(),
                    displayName = name,
                    mimeType = mime,
                    documentType = type,
                    sizeBytes = doc.length(),
                    lastModifiedAt = doc.lastModified()
                )
            }
            .filter { it.documentType == DocumentType.POWERPOINT }
    }
}

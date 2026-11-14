package com.mydocviewer.viewer.archive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

data class ArchiveEntryItem(
    val name: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val isDirectory: Boolean
)

object ArchiveAndEpubParser {

    suspend fun listZipEntries(context: Context, uri: Uri): List<ArchiveEntryItem> =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open archive")

            val items = mutableListOf<ArchiveEntryItem>()
            stream.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        items += ArchiveEntryItem(
                            name = entry.name,
                            compressedSize = entry.compressedSize.coerceAtLeast(0),
                            uncompressedSize = entry.size.coerceAtLeast(0),
                            isDirectory = entry.isDirectory
                        )
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            items
        }

    suspend fun extractSelectedEntries(
        context: Context,
        archiveUri: Uri,
        selectedNames: Set<String>,
        destinationTreeUri: Uri
    ): Int = withContext(Dispatchers.IO) {
        val destDir = DocumentFile.fromTreeUri(context, destinationTreeUri)
            ?: throw IllegalStateException("Invalid destination folder")
        val stream = context.contentResolver.openInputStream(archiveUri)
            ?: throw IllegalStateException("Unable to open archive")

        var extractedCount = 0
        stream.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    if (!entry.isDirectory && entry.name in selectedNames) {
                        val safeName = entry.name.substringAfterLast('/')
                        val outFile = destDir.createFile("application/octet-stream", safeName)
                        if (outFile != null) {
                            context.contentResolver.openOutputStream(outFile.uri)?.use { out ->
                                zis.copyTo(out)
                            }
                            extractedCount++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        extractedCount
    }

    suspend fun extractEpubChapters(context: Context, uri: Uri): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open EPUB")

            val chapters = mutableListOf<Pair<String, String>>()
            stream.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        val lower = entry.name.lowercase()
                        if (!entry.isDirectory && (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm"))) {
                            val html = zis.bufferedReader(Charsets.UTF_8).readText()
                            val plain = html
                                .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("<(p|div|h[1-6]|li|br)[^>]*>", RegexOption.IGNORE_CASE), "\n")
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&nbsp;", " ")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString("\n\n")
                            if (plain.isNotBlank()) {
                                val title = entry.name.substringAfterLast('/').substringBeforeLast('.')
                                chapters += title to plain
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            chapters
        }
}

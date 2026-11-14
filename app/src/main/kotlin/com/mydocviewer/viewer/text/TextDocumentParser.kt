package com.mydocviewer.viewer.text

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.coroutineContext

data class ParsedTextDocument(
    val lines: List<String>,
    val isMarkdown: Boolean,
    val isTruncated: Boolean = false
)

object TextDocumentParser {

    private const val MAX_LINES = 25_000

    suspend fun parse(context: Context, uri: Uri, fileName: String): ParsedTextDocument =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open text stream")

            val ext = fileName.substringAfterLast('.', "").lowercase()
            val rawText = stream.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    val collected = mutableListOf<String>()
                    var count = 0
                    var truncated = false
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = reader.readLine() ?: break
                        if (count >= MAX_LINES) {
                            truncated = true
                            break
                        }
                        collected += line
                        count++
                    }
                    Pair(collected, truncated)
                }
            }

            if (ext == "rtf") {
                val stripped = stripRtf(rawText.first.joinToString("\n"))
                return@withContext ParsedTextDocument(
                    lines = stripped.lines(),
                    isMarkdown = false,
                    isTruncated = rawText.second
                )
            }

            ParsedTextDocument(
                lines = rawText.first,
                isMarkdown = ext == "md" || ext == "markdown",
                isTruncated = rawText.second
            )
        }

    private fun stripRtf(rtf: String): String {
        return rtf
            .replace(Regex("\\\\par\\b"), "\n")
            .replace(Regex("\\\\line\\b"), "\n")
            .replace(Regex("\\\\tab\\b"), "\t")
            .replace(Regex("\\{\\\\\\*\\\\[^{}]*\\}"), "")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d*\\s?"), "")
            .replace("{", "")
            .replace("}", "")
            .trim()
    }
}

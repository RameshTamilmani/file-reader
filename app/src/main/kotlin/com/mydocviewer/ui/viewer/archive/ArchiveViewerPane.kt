package com.mydocviewer.ui.viewer.archive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mydocviewer.R
import com.mydocviewer.util.FileTypeDetector
import com.mydocviewer.viewer.archive.ArchiveEntryItem

@Composable
fun ArchiveViewerPane(
    entries: List<ArchiveEntryItem>,
    onExtractSelected: (Set<String>, Uri) -> Unit
) {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    val destLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null && selected.isNotEmpty()) {
            onExtractSelected(selected, treeUri)
            selected = emptySet()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.selected_count, selected.size),
                    style = MaterialTheme.typography.titleSmall
                )
                Button(onClick = { destLauncher.launch(null) }) {
                    Text(stringResource(R.string.extract_selected))
                }
            }
            HorizontalDivider()
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.name }) { entry ->
                val isChecked = entry.name in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !entry.isDirectory) {
                            selected = if (isChecked) selected - entry.name else selected + entry.name
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!entry.isDirectory) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                selected = if (isChecked) selected - entry.name else selected + entry.name
                            }
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(text = entry.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (entry.isDirectory) "Folder" else FileTypeDetector.formatFileSize(entry.uncompressedSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun EpubViewerPane(
    chapters: List<Pair<String, String>>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(chapters) { (title, body) ->
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

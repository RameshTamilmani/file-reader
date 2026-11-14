package com.mydocviewer.ui.viewer.office

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydocviewer.R
import com.mydocviewer.viewer.office.ParsedSpreadsheet

@Composable
fun SpreadsheetViewerPane(
    workbook: ParsedSpreadsheet
) {
    var activeSheetIdx by remember { mutableIntStateOf(0) }
    var freezeHeader by remember { mutableStateOf(true) }
    var fitToWidth by remember { mutableStateOf(false) }

    val sheet = workbook.sheets.getOrNull(activeSheetIdx) ?: return
    val horizontalScroll = rememberScrollState()
    val headerRow = sheet.rows.firstOrNull().orEmpty()
    val bodyRows = if (freezeHeader && sheet.rows.isNotEmpty()) sheet.rows.drop(1) else sheet.rows
    val colCount = headerRow.size.coerceAtLeast(1)
    val cellWidth = if (fitToWidth) (360 / colCount).coerceIn(72, 160).dp else 130.dp

    Column(modifier = Modifier.fillMaxSize()) {
        if (workbook.isSimplifiedView) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.simplified_view_notice),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = freezeHeader,
                onClick = { freezeHeader = !freezeHeader },
                label = { Text(stringResource(R.string.freeze_header)) }
            )
            FilterChip(
                selected = fitToWidth,
                onClick = { fitToWidth = !fitToWidth },
                label = { Text(stringResource(R.string.fit_to_width)) }
            )
        }

        if (workbook.sheets.size > 1) {
            ScrollableTabRow(selectedTabIndex = activeSheetIdx) {
                workbook.sheets.forEachIndexed { idx, s ->
                    Tab(
                        selected = idx == activeSheetIdx,
                        onClick = { activeSheetIdx = idx },
                        text = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll)
        ) {
            if (freezeHeader && headerRow.isNotEmpty()) {
                Row(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    headerRow.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(cellWidth)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(bodyRows) { idx, row ->
                    val bg = if (idx % 2 == 1) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    Row(modifier = Modifier.background(bg)) {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .width(cellWidth)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

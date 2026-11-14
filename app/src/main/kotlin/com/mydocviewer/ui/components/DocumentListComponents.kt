package com.mydocviewer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydocviewer.domain.model.DocumentItem
import com.mydocviewer.domain.model.DocumentType
import com.mydocviewer.util.FileTypeDetector
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListRow(
    item: DocumentItem,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showOpenedDate: Boolean = true
) {
    val timestamp = if (showOpenedDate && item.lastOpenedAt > 0L) item.lastOpenedAt else item.lastModifiedAt
    val formattedDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    val formattedSize = FileTypeDetector.formatFileSize(item.sizeBytes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        val (icon, tint) = iconForType(item.documentType)
        Surface(
            color = tint.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = item.documentType.categoryLabel,
                tint = tint,
                modifier = Modifier.padding(10.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.documentType.categoryLabel} · $formattedSize · $formattedDate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onToggleFavorite != null && !isSelectionMode) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.semantics {
                    contentDescription = if (item.isFavorite) "Remove favorite" else "Add favorite"
                }
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (item.isFavorite) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 74.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

fun iconForType(type: DocumentType): Pair<ImageVector, Color> = when (type) {
    DocumentType.PDF -> Icons.Outlined.PictureAsPdf to Color(0xFFDC2626)
    DocumentType.WORD -> Icons.Outlined.Description to Color(0xFF2563EB)
    DocumentType.EXCEL, DocumentType.CSV -> Icons.Outlined.TableChart to Color(0xFF16A34A)
    DocumentType.POWERPOINT -> Icons.Outlined.Slideshow to Color(0xFFEA580C)
    DocumentType.TEXT -> Icons.Outlined.Description to Color(0xFF475569)
    DocumentType.IMAGE -> Icons.Outlined.Image to Color(0xFF0D9488)
    DocumentType.EPUB -> Icons.Outlined.MenuBook to Color(0xFF7C3AED)
    DocumentType.ARCHIVE -> Icons.Outlined.FolderZip to Color(0xFFB45309)
    DocumentType.UNSUPPORTED -> Icons.Outlined.InsertDriveFile to Color(0xFF64748B)
}

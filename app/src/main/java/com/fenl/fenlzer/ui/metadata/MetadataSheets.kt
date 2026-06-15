package com.fenl.fenlzer.ui.metadata

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fenl.fenlzer.data.repository.OriginalMetadata
import com.fenl.fenlzer.data.repository.SongDetails
import com.fenl.fenlzer.data.repository.TrackMetadataDraft
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsSheet(
    details: SongDetails,
    onPlay: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(details = details)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = details.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = details.metadata.artist.ifBlank { "Unknown artist" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { onPlay(details.trackId) }) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(text = "Play", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { onEdit(details.trackId) }) {
                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = null)
                    Text(text = "Edit Tags", modifier = Modifier.padding(start = 8.dp))
                }
                IconButton(onClick = { onDelete(details.trackId) }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete from Fenlzer"
                    )
                }
            }

            DetailSection(title = "Metadata") {
                DetailLine("Title", details.metadata.title)
                DetailLine("Artist", details.metadata.artist)
                DetailLine("Album Artist", details.metadata.albumArtist)
                DetailLine("Album", details.metadata.album)
                DetailLine("Genre", details.metadata.genre)
                DetailLine("Year", details.metadata.year.orEmpty())
                DetailLine("Track", details.metadata.trackNumber?.toString().orEmpty())
                DetailLine("Disc", details.metadata.discNumber?.toString().orEmpty())
                DetailLine("Notes", details.metadata.notes)
                DetailLine("Favourite", if (details.isFavourite) "Yes" else "No")
                DetailLine("Favourited", details.favouritedAt?.formatDateTime().orEmpty())
                DetailLine("Imported", details.importedAt.formatDateTime())
            }

            DetailSection(title = "Statistics") {
                DetailLine("Plays", details.stats.playCount.toString())
                DetailLine("Skips", details.stats.skipCount.toString())
                DetailLine("Completions", details.stats.completionCount.toString())
                DetailLine("Listened", details.stats.totalListenedMs.formatDuration())
                DetailLine("First played", details.stats.firstPlayedAt?.formatDateTime().orEmpty())
                DetailLine("Last played", details.stats.lastPlayedAt?.formatDateTime().orEmpty())
                DetailLine(
                    "Average completion",
                    "${(details.stats.averageCompletionPercent * 100f).toInt()}%"
                )
            }

            DetailSection(title = "Appears In") {
                if (details.playlistNames.isEmpty()) {
                    DetailLine("Playlists", "None")
                } else {
                    details.playlistNames.forEach { playlist -> DetailLine("Playlist", playlist) }
                }
            }

            details.originalMetadata?.let { original ->
                DetailSection(title = "Original Metadata") {
                    DetailLine("Title", original.title)
                    DetailLine("Artist", original.artist)
                    DetailLine("Album Artist", original.albumArtist)
                    DetailLine("Album", original.album)
                    DetailLine("Genre", original.genre)
                    DetailLine("Year", original.year.orEmpty())
                    DetailLine("Track", original.trackNumber?.toString().orEmpty())
                    DetailLine("Disc", original.discNumber?.toString().orEmpty())
                    DetailLine("Thumbnail", original.thumbnailKind)
                }
            }

            DetailSection(title = "Technical Details") {
                DetailLine("File", details.technical.internalFilename)
                DetailLine("Hash", details.technical.audioHash)
                DetailLine("Format", details.technical.finalAudioFormat)
                DetailLine("Size", details.technical.fileSizeBytes.formatBytes())
                DetailLine("Duration", details.technical.durationMs.formatDuration())
            }

            DetailSection(title = "Source Information") {
                DetailLine("Source", details.source.sourceType)
                DetailLine("Import reason", details.source.importReason.orEmpty())
                DetailLine("Requested format", details.source.requestedDownloadFormat.orEmpty())
                DetailLine("Original filename", details.source.originalFilename.orEmpty())
                DetailLine("YouTube ID", details.source.youtubeVideoId.orEmpty())
                DetailLine("URL", details.source.sourceUrl.orEmpty())
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorSheet(
    details: SongDetails,
    onSave: (String, TrackMetadataDraft) -> Unit,
    onResetAll: (String, Boolean) -> Unit,
    onPickThumbnail: (String) -> Unit,
    onClearThumbnail: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(details.trackId) { mutableStateOf(details.metadata.title) }
    var artist by rememberSaveable(details.trackId) { mutableStateOf(details.metadata.artist) }
    var albumArtist by rememberSaveable(details.trackId) { mutableStateOf(details.metadata.albumArtist) }
    var album by rememberSaveable(details.trackId) { mutableStateOf(details.metadata.album) }
    var genre by rememberSaveable(details.trackId) { mutableStateOf(details.metadata.genre) }
    var year by rememberSaveable(details.trackId) { mutableStateOf(details.metadata.year.orEmpty()) }
    var trackNumber by rememberSaveable(details.trackId) {
        mutableStateOf(details.metadata.trackNumber?.toString().orEmpty())
    }
    var discNumber by rememberSaveable(details.trackId) {
        mutableStateOf(details.metadata.discNumber?.toString().orEmpty())
    }
    var notes by rememberSaveable(details.trackId) { mutableStateOf(details.metadata.notes) }
    var showDiscardWarning by remember { mutableStateOf(false) }
    var showResetAllWarning by remember { mutableStateOf(false) }
    var resetThumbnail by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val original = details.originalMetadata
    val draft = TrackMetadataDraft(
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        genre = genre,
        year = year.takeIf { it.isNotBlank() },
        trackNumber = trackNumber.toIntOrNull(),
        discNumber = discNumber.toIntOrNull(),
        notes = notes
    )
    val changed = draft != details.metadata

    if (showDiscardWarning) {
        AlertDialog(
            onDismissRequest = { showDiscardWarning = false },
            title = { Text(text = "Discard changes?") },
            text = { Text(text = "Unsaved metadata edits will be lost.") },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardWarning = false }) {
                    Text(text = "Keep editing")
                }
            }
        )
    }

    if (showResetAllWarning) {
        AlertDialog(
            onDismissRequest = { showResetAllWarning = false },
            title = { Text(text = "Reset all metadata?") },
            text = {
                Column {
                    Text(text = "Current edited fields will return to their original import values.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = resetThumbnail,
                            onCheckedChange = { resetThumbnail = it }
                        )
                        Text(text = "Also reset custom thumbnail")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAllWarning = false
                        onResetAll(details.trackId, resetThumbnail)
                    }
                ) {
                    Text(text = "Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllWarning = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (changed) showDiscardWarning = true else onDismiss()
        },
        sheetState = sheetState,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Edit Tags",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPickThumbnail(details.trackId) }) {
                    Icon(imageVector = Icons.Rounded.Image, contentDescription = null)
                    Text(text = "Thumbnail", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { onClearThumbnail(details.trackId) }) {
                    Text(text = "Use Source Art")
                }
            }
            EditorField("Title", title, original?.title, { title = it })
            EditorField("Artist", artist, original?.artist, { artist = it })
            EditorField("Album Artist", albumArtist, original?.albumArtist, { albumArtist = it })
            EditorField("Album", album, original?.album, { album = it })
            EditorField("Genre", genre, original?.genre, { genre = it })
            EditorField("Year", year, original?.year, { year = it.filter(Char::isDigit).take(4) })
            EditorField(
                label = "Track",
                value = trackNumber,
                originalValue = original?.trackNumber?.toString(),
                onValueChange = { trackNumber = it.filter(Char::isDigit).take(3) }
            )
            EditorField(
                label = "Disc",
                value = discNumber,
                originalValue = original?.discNumber?.toString(),
                onValueChange = { discNumber = it.filter(Char::isDigit).take(3) }
            )
            EditorField(
                label = "Notes",
                value = notes,
                originalValue = "",
                onValueChange = { notes = it },
                singleLine = false
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showResetAllWarning = true },
                    enabled = original != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = null)
                    Text(text = "Reset All", modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = {
                        onSave(details.trackId, draft)
                        onDismiss()
                    },
                    enabled = changed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Save")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Artwork(details: SongDetails) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (details.thumbnailUri != null) {
            AsyncImage(
                model = details.thumbnailUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f)
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.66f)
        )
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    originalValue: String?,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true
) {
    val edited = originalValue != null && value != originalValue
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(text = if (edited) "$label *" else label)
            },
            singleLine = singleLine,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onValueChange(originalValue.orEmpty()) },
            enabled = originalValue != null
        ) {
            Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = "Reset $label")
        }
    }
}

private fun Long.formatDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))

private fun Long.formatDuration(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun Long.formatBytes(): String {
    if (this < 1024L) return "$this B"
    val units = listOf("KB", "MB", "GB")
    var value = this / 1024.0
    var unit = units.first()
    for (candidate in units) {
        unit = candidate
        if (value < 1024.0 || candidate == units.last()) break
        value /= 1024.0
    }
    return String.format(Locale.US, "%.1f %s", value, unit)
}

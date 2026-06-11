package com.fenl.fenlzer.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fenl.fenlzer.data.repository.StatisticsRankedArtist
import com.fenl.fenlzer.data.repository.StatisticsRankedTrack
import com.fenl.fenlzer.data.repository.StatisticsRecentEvent
import com.fenl.fenlzer.data.repository.StatisticsSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatisticsScreen(
    summary: StatisticsSummary,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("statisticsScreen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            StatGrid(summary = summary)
        }
        item {
            SectionTitle("Top")
        }
        item {
            RankedTrackRow(
                label = "Most listened song",
                rankedTrack = summary.mostListenedSong,
                valueFormatter = { value -> value.formatDuration() }
            )
        }
        item {
            RankedArtistRow(
                label = "Most listened artist",
                rankedArtist = summary.mostListenedArtist,
                valueFormatter = { value -> value.formatDuration() }
            )
        }
        item {
            RankedTrackRow(
                label = "Most skipped song",
                rankedTrack = summary.mostSkippedSong,
                valueFormatter = { value -> "$value skips" }
            )
        }
        item {
            RankedArtistRow(
                label = "Favourite artist",
                rankedArtist = summary.favouriteArtist,
                valueFormatter = { value -> "$value favourites" }
            )
        }
        item {
            SectionTitle("Listening time")
        }
        item {
            TimeByDay(summary.listeningTimeByDay)
        }
        item {
            TimeByHour(summary.listeningTimeByHour)
        }
        item {
            SectionTitle("Rediscovered")
        }
        if (summary.recentlyRediscoveredSongs.isEmpty()) {
            item {
                EmptyValue("No rediscovered songs yet")
            }
        } else {
            items(summary.recentlyRediscoveredSongs, key = { it.trackId }) { track ->
                RankedTrackRow(
                    label = track.title,
                    rankedTrack = track,
                    valueFormatter = { "Recently played" }
                )
            }
        }
        item {
            SectionTitle("Recent history")
        }
        if (summary.recentEvents.isEmpty()) {
            item {
                EmptyValue("No listening history yet")
            }
        } else {
            items(summary.recentEvents, key = { "${it.trackId}-${it.startedAt}" }) { event ->
                RecentEventRow(event = event)
            }
        }
    }
}

@Composable
private fun StatGrid(summary: StatisticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Listening",
                value = summary.totalListeningMs.formatDuration(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Songs",
                value = summary.totalSongsImported.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Playlists",
                value = summary.totalPlaylists.toString(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Never played",
                value = summary.songsNeverPlayed.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Streak",
                value = "${summary.listeningStreakDays} days",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Longest session",
                value = summary.longestSessionMs.formatDuration(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RankedTrackRow(
    label: String,
    rankedTrack: StatisticsRankedTrack?,
    valueFormatter: (Long) -> String
) {
    StatisticRow(
        title = rankedTrack?.title ?: label,
        subtitle = rankedTrack?.artist?.takeIf { it.isNotBlank() } ?: "None yet",
        value = rankedTrack?.value?.let(valueFormatter).orEmpty()
    )
}

@Composable
private fun RankedArtistRow(
    label: String,
    rankedArtist: StatisticsRankedArtist?,
    valueFormatter: (Long) -> String
) {
    StatisticRow(
        title = rankedArtist?.name ?: label,
        subtitle = if (rankedArtist == null) "None yet" else label,
        value = rankedArtist?.value?.let(valueFormatter).orEmpty()
    )
}

@Composable
private fun RecentEventRow(event: StatisticsRecentEvent) {
    val badges = listOfNotNull(
        "valid".takeIf { event.validListen },
        "skip".takeIf { event.skip },
        "complete".takeIf { event.completion }
    ).joinToString(" / ")

    StatisticRow(
        title = event.title,
        subtitle = listOf(event.artist, badges)
            .filter { it.isNotBlank() }
            .joinToString(" - "),
        value = event.listenedMs.formatDuration()
    )
}

@Composable
private fun TimeByDay(timeByDay: Map<LocalDate, Long>) {
    if (timeByDay.isEmpty()) {
        EmptyValue("No day history yet")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        timeByDay.entries.take(7).forEach { (day, listenedMs) ->
            StatisticRow(
                title = day.format(DateTimeFormatter.ISO_LOCAL_DATE),
                subtitle = "Listening by day",
                value = listenedMs.formatDuration()
            )
        }
    }
}

@Composable
private fun TimeByHour(timeByHour: Map<Int, Long>) {
    if (timeByHour.isEmpty()) {
        EmptyValue("No hourly history yet")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        timeByHour.entries
            .sortedByDescending { it.value }
            .take(6)
            .forEach { (hour, listenedMs) ->
                StatisticRow(
                    title = "%02d:00".format(hour),
                    subtitle = "Listening by hour",
                    value = listenedMs.formatDuration()
                )
            }
    }
}

@Composable
private fun StatisticRow(
    title: String,
    subtitle: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun EmptyValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun Long.formatDuration(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

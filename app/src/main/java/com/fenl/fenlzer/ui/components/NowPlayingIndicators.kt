package com.fenl.fenlzer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun nowPlayingRowColor(
    isNowPlaying: Boolean,
    isSelected: Boolean = false
): Color = when {
    isNowPlaying -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isSelected) 0.92f else 0.78f)
    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f)
    else -> MaterialTheme.colorScheme.surface
}

@Composable
fun nowPlayingContentColor(isNowPlaying: Boolean): Color = if (isNowPlaying) {
    MaterialTheme.colorScheme.onSecondaryContainer
} else {
    MaterialTheme.colorScheme.onSurface
}

@Composable
fun nowPlayingSecondaryContentColor(isNowPlaying: Boolean): Color = if (isNowPlaying) {
    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun NowPlayingStatusBadge(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isPlaying) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isPlaying) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(percent = 50),
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (isPlaying) "Playing" else "Paused",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun NowPlayingArtworkOverlay(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isPlaying) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isPlaying) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(percent = 50),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        modifier = modifier.size(22.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = if (isPlaying) "Currently playing" else "Currently paused",
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

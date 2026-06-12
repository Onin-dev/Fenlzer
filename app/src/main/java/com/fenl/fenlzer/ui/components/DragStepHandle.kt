package com.fenl.fenlzer.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Reliable manual reorder control used by queue and playlist rows.
 *
 * It supports both drag gestures on the handle and explicit up/down buttons.
 * The buttons are intentionally kept because they make the feature testable,
 * accessible, and reliable across Compose/gesture versions.
 */
@Composable
fun DragStepHandle(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = canMoveUp || canMoveDown,
    contentDescription: String = "Reorder"
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val stepThresholdPx = 34f

    Row(
        modifier = modifier.testTag("dragStepHandle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onMoveUp,
                enabled = enabled && canMoveUp,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "$contentDescription up",
                    tint = if (enabled && canMoveUp) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = contentDescription,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
                modifier = Modifier
                    .size(28.dp)
                    .pointerInput(enabled, canMoveUp, canMoveDown) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onDragEnd = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount.y
                            if (abs(accumulatedDrag) >= stepThresholdPx) {
                                if (accumulatedDrag < 0 && canMoveUp) {
                                    onMoveUp()
                                } else if (accumulatedDrag > 0 && canMoveDown) {
                                    onMoveDown()
                                }
                                accumulatedDrag = 0f
                            }
                        }
                    }
            )
            IconButton(
                onClick = onMoveDown,
                enabled = enabled && canMoveDown,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "$contentDescription down",
                    tint = if (enabled && canMoveDown) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
        }
    }
}

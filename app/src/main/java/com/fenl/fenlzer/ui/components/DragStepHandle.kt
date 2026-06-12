package com.fenl.fenlzer.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
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
 * Deezer-like reorder handle used by queue and playlist rows.
 *
 * This is intentionally handle-only: there are no up/down buttons. The user holds
 * the grip and drags vertically. The implementation translates the vertical drag
 * into repeated one-step moves so it stays dependency-free and works for both
 * QueueScreen and PlaylistsScreen.
 */
@Composable
fun DragStepHandle(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canMoveUp: Boolean = enabled,
    canMoveDown: Boolean = enabled,
    contentDescription: String = "Reorder",
    testTag: String = "dragStepHandle"
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val stepThresholdPx = 28f
    val effectiveCanMoveUp = enabled && canMoveUp
    val effectiveCanMoveDown = enabled && canMoveDown
    val effectiveEnabled = effectiveCanMoveUp || effectiveCanMoveDown

    Box(
        modifier = modifier
            .size(48.dp)
            .testTag(testTag)
            .pointerInput(effectiveEnabled, effectiveCanMoveUp, effectiveCanMoveDown) {
                if (!effectiveEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDragEnd = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount.y
                    while (abs(accumulatedDrag) >= stepThresholdPx) {
                        if (accumulatedDrag < 0f) {
                            if (effectiveCanMoveUp) {
                                onMoveUp()
                            }
                            accumulatedDrag += stepThresholdPx
                        } else {
                            if (effectiveCanMoveDown) {
                                onMoveDown()
                            }
                            accumulatedDrag -= stepThresholdPx
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = contentDescription,
            tint = if (effectiveEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            }
        )
    }
}

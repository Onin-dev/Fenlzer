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
 * The component intentionally supports both:
 * - explicit up/down buttons, which are reliable and testable;
 * - vertical drag gestures on the center handle, which match the visual affordance.
 *
 * It is backward-compatible with older call sites that only pass [enabled],
 * [onMoveUp], [onMoveDown], and [testTag]. Those call sites compile because
 * [canMoveUp] and [canMoveDown] default to [enabled]. Newer call sites should
 * pass precise move bounds so first/last rows disable the correct arrow.
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
    val stepThresholdPx = 34f
    val effectiveCanMoveUp = enabled && canMoveUp
    val effectiveCanMoveDown = enabled && canMoveDown
    val effectiveEnabled = effectiveCanMoveUp || effectiveCanMoveDown

    Row(
        modifier = modifier.testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onMoveUp,
                enabled = effectiveCanMoveUp,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "$contentDescription up",
                    tint = if (effectiveCanMoveUp) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }

            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = contentDescription,
                tint = if (effectiveEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
                modifier = Modifier
                    .size(28.dp)
                    .pointerInput(effectiveEnabled, effectiveCanMoveUp, effectiveCanMoveDown) {
                        if (!effectiveEnabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onDragEnd = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount.y
                            if (abs(accumulatedDrag) >= stepThresholdPx) {
                                if (accumulatedDrag < 0 && effectiveCanMoveUp) {
                                    onMoveUp()
                                } else if (accumulatedDrag > 0 && effectiveCanMoveDown) {
                                    onMoveDown()
                                }
                                accumulatedDrag = 0f
                            }
                        }
                    }
            )

            IconButton(
                onClick = onMoveDown,
                enabled = effectiveCanMoveDown,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "$contentDescription down",
                    tint = if (effectiveCanMoveDown) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
        }
    }
}

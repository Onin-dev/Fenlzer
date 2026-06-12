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

@Composable
fun DragStepHandle(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canMoveUp: Boolean = enabled,
    canMoveDown: Boolean = enabled,
    contentDescription: String = "Reorder",
    testTag: String = "dragStepHandle",
    onDragStart: (() -> Unit)? = null,
    onDragDelta: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val stepThresholdPx = 42f
    val effectiveCanMoveUp = enabled && canMoveUp
    val effectiveCanMoveDown = enabled && canMoveDown
    val effectiveEnabled = effectiveCanMoveUp || effectiveCanMoveDown

    Box(
        modifier = modifier
            .size(48.dp)
            .testTag(testTag)
            .pointerInput(
                effectiveEnabled,
                effectiveCanMoveUp,
                effectiveCanMoveDown,
                onDragStart,
                onDragDelta,
                onDragEnd
            ) {
                if (!effectiveEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        accumulatedDrag = 0f
                        onDragStart?.invoke()
                    },
                    onDragCancel = {
                        accumulatedDrag = 0f
                        onDragEnd?.invoke()
                    },
                    onDragEnd = {
                        accumulatedDrag = 0f
                        onDragEnd?.invoke()
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val liveDrag = onDragDelta
                    if (liveDrag != null) {
                        val deltaY = dragAmount.y
                        if ((deltaY < 0f && effectiveCanMoveUp) || (deltaY > 0f && effectiveCanMoveDown)) {
                            liveDrag(deltaY)
                        }
                    } else {
                        accumulatedDrag += dragAmount.y
                        if (abs(accumulatedDrag) >= stepThresholdPx) {
                            if (accumulatedDrag < 0f && effectiveCanMoveUp) {
                                onMoveUp()
                            } else if (accumulatedDrag > 0f && effectiveCanMoveDown) {
                                onMoveDown()
                            }
                            accumulatedDrag = 0f
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

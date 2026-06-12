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
import androidx.compose.runtime.rememberUpdatedState
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

    val latestOnMoveUp by rememberUpdatedState(onMoveUp)
    val latestOnMoveDown by rememberUpdatedState(onMoveDown)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestCanMoveUp by rememberUpdatedState(enabled && canMoveUp)
    val latestCanMoveDown by rememberUpdatedState(enabled && canMoveDown)
    val visualEnabled = enabled && (canMoveUp || canMoveDown)

    Box(
        modifier = modifier
            .size(48.dp)
            .testTag(testTag)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                detectDragGestures(
                    onDragStart = {
                        accumulatedDrag = 0f
                        latestOnDragStart?.invoke()
                    },
                    onDragCancel = {
                        accumulatedDrag = 0f
                        latestOnDragEnd?.invoke()
                    },
                    onDragEnd = {
                        accumulatedDrag = 0f
                        latestOnDragEnd?.invoke()
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val liveDrag = latestOnDragDelta

                    if (liveDrag != null) {
                        val deltaY = dragAmount.y
                        when {
                            deltaY < 0f && latestCanMoveUp -> liveDrag(deltaY)
                            deltaY > 0f && latestCanMoveDown -> liveDrag(deltaY)
                            else -> Unit
                        }
                    } else {
                        accumulatedDrag += dragAmount.y
                        if (abs(accumulatedDrag) >= stepThresholdPx) {
                            if (accumulatedDrag < 0f && latestCanMoveUp) {
                                latestOnMoveUp()
                            } else if (accumulatedDrag > 0f && latestCanMoveDown) {
                                latestOnMoveDown()
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
            tint = if (visualEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            }
        )
    }
}

package com.fenl.fenlzer.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private const val DRAG_STEP_THRESHOLD_PX = 48f

/**
 * A small drag handle used by queue and playlist rows.
 *
 * Compose does not provide reorderable LazyColumn support directly in the current
 * dependency set, so this handle intentionally performs deterministic one-row
 * moves as the user drags vertically. It keeps the implementation dependency-free
 * and easy to test while still matching Fenlzer's manual drag-handle UX.
 */
@Composable
fun DragStepHandle(
    enabled: Boolean,
    contentDescription: String,
    testTag: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDrag by remember { mutableStateOf(0f) }
    val dragModifier = if (enabled) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = { accumulatedDrag = 0f },
                onDragCancel = { accumulatedDrag = 0f }
            ) { change, dragAmount ->
                change.consume()
                accumulatedDrag += dragAmount
                when {
                    accumulatedDrag <= -DRAG_STEP_THRESHOLD_PX -> {
                        accumulatedDrag = 0f
                        onMoveUp()
                    }
                    accumulatedDrag >= DRAG_STEP_THRESHOLD_PX -> {
                        accumulatedDrag = 0f
                        onMoveDown()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    IconButton(
        onClick = {},
        enabled = enabled,
        modifier = modifier
            .then(dragModifier)
            .size(40.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
    }
}

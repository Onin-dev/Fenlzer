package com.fenl.fenlzer.data.repository

import com.fenl.fenlzer.data.local.entity.QueueItemEntity
import kotlin.random.Random

object QueueListEditor {
    const val STATE_PREVIOUS = "PREVIOUS"
    const val STATE_CURRENT = "CURRENT"
    const val STATE_UPCOMING = "UPCOMING"
    const val INSERTED_BY_TAP = "TAP"
    const val INSERTED_BY_PLAY_NEXT = "PLAY_NEXT"
    const val INSERTED_BY_ADD_TO_QUEUE = "ADD_TO_QUEUE"
    const val INSERTED_BY_PLAYLIST_START = "PLAYLIST_START"
    const val INSERTED_BY_SMART_PLAYLIST_START = "SMART_PLAYLIST_START"
    const val INSERTED_BY_DISCOVER_START = "DISCOVER_START"

    fun playFromHome(
        existingItems: List<QueueItemEntity>,
        newCurrent: QueueItemEntity
    ): EditedQueue {
        val remainingOldQueue = existingItems
            .filterNot { it.sameMediaAs(newCurrent) }
            .sortedBy { it.position }
        val items = listOf(newCurrent.copy(state = STATE_CURRENT, insertedBy = INSERTED_BY_TAP)) +
            remainingOldQueue.map { item ->
                item.copy(state = STATE_UPCOMING)
            }

        return EditedQueue(
            items = renumber(items, newCurrent.queueItemId),
            currentQueueItemId = newCurrent.queueItemId,
            changed = true,
            message = null
        )
    }

    fun playNext(
        existingItems: List<QueueItemEntity>,
        currentQueueItemId: String?,
        newItem: QueueItemEntity
    ): EditedQueue {
        val sorted = existingItems.sortedBy { it.position }
        val current = sorted.firstOrNull { it.queueItemId == currentQueueItemId }
        if (current?.sameMediaAs(newItem) == true) {
            return EditedQueue(
                items = renumber(sorted, currentQueueItemId),
                currentQueueItemId = currentQueueItemId,
                changed = false,
                message = "Already playing"
            )
        }

        if (current == null) {
            val currentItem = newItem.copy(state = STATE_CURRENT, insertedBy = INSERTED_BY_TAP)
            return EditedQueue(
                items = renumber(listOf(currentItem), currentItem.queueItemId),
                currentQueueItemId = currentItem.queueItemId,
                changed = true,
                message = null
            )
        }

        val withoutDuplicate = sorted.filterNot { it.sameMediaAs(newItem) }
        val currentIndex = withoutDuplicate.indexOfFirst { it.queueItemId == current.queueItemId }
        val playNextCount = withoutDuplicate
            .drop(currentIndex + 1)
            .takeWhile { it.insertedBy == INSERTED_BY_PLAY_NEXT && it.state == STATE_UPCOMING }
            .size
        val insertionIndex = currentIndex + 1 + playNextCount
        val edited = withoutDuplicate
            .toMutableList()
            .apply {
                add(
                    insertionIndex,
                    newItem.copy(state = STATE_UPCOMING, insertedBy = INSERTED_BY_PLAY_NEXT)
                )
            }

        return EditedQueue(
            items = renumber(edited, current.queueItemId),
            currentQueueItemId = current.queueItemId,
            changed = true,
            message = null
        )
    }

    fun addToQueue(
        existingItems: List<QueueItemEntity>,
        currentQueueItemId: String?,
        newItem: QueueItemEntity
    ): EditedQueue {
        val sorted = existingItems.sortedBy { it.position }
        val current = sorted.firstOrNull { it.queueItemId == currentQueueItemId }
        if (current?.sameMediaAs(newItem) == true) {
            return EditedQueue(
                items = renumber(sorted, currentQueueItemId),
                currentQueueItemId = currentQueueItemId,
                changed = false,
                message = "Already playing"
            )
        }

        if (current == null) {
            val currentItem = newItem.copy(state = STATE_CURRENT, insertedBy = INSERTED_BY_TAP)
            return EditedQueue(
                items = renumber(listOf(currentItem), currentItem.queueItemId),
                currentQueueItemId = currentItem.queueItemId,
                changed = true,
                message = null
            )
        }

        val edited = sorted
            .filterNot { it.sameMediaAs(newItem) }
            .toMutableList()
            .apply {
                add(newItem.copy(state = STATE_UPCOMING, insertedBy = INSERTED_BY_ADD_TO_QUEUE))
            }

        return EditedQueue(
            items = renumber(edited, current.queueItemId),
            currentQueueItemId = current.queueItemId,
            changed = true,
            message = null
        )
    }

    fun markCurrent(
        existingItems: List<QueueItemEntity>,
        currentQueueItemId: String?
    ): EditedQueue {
        return EditedQueue(
            items = renumber(existingItems.sortedBy { it.position }, currentQueueItemId),
            currentQueueItemId = currentQueueItemId,
            changed = true,
            message = null
        )
    }

    fun removeItem(
        existingItems: List<QueueItemEntity>,
        currentQueueItemId: String?,
        queueItemIdToRemove: String
    ): EditedQueue {
        val sorted = existingItems.sortedBy { it.position }
        val removedIndex = sorted.indexOfFirst { it.queueItemId == queueItemIdToRemove }
        if (removedIndex == -1) {
            return EditedQueue(
                items = renumber(sorted, currentQueueItemId),
                currentQueueItemId = currentQueueItemId,
                changed = false,
                message = null
            )
        }

        val removingCurrent = queueItemIdToRemove == currentQueueItemId
        val remaining = sorted.filterNot { it.queueItemId == queueItemIdToRemove }
        val nextCurrentId = if (removingCurrent) {
            sorted.drop(removedIndex + 1).firstOrNull()?.queueItemId
        } else {
            currentQueueItemId
        }

        return EditedQueue(
            items = renumber(remaining, nextCurrentId),
            currentQueueItemId = nextCurrentId,
            changed = true,
            message = null
        )
    }

    fun clearUpcoming(
        existingItems: List<QueueItemEntity>,
        currentQueueItemId: String?
    ): EditedQueue {
        val kept = existingItems
            .sortedBy { it.position }
            .filter { it.state != STATE_UPCOMING }

        return EditedQueue(
            items = renumber(kept, currentQueueItemId),
            currentQueueItemId = currentQueueItemId,
            changed = true,
            message = null
        )
    }


    fun moveItem(
        existingItems: List<QueueItemEntity>,
        currentQueueItemId: String?,
        queueItemIdToMove: String,
        offset: Int
    ): EditedQueue {
        val sorted = existingItems.sortedBy { it.position }
        if (offset == 0 || sorted.size < 2) {
            return EditedQueue(
                items = renumber(sorted, currentQueueItemId),
                currentQueueItemId = currentQueueItemId,
                changed = false,
                message = null
            )
        }
        val fromIndex = sorted.indexOfFirst { it.queueItemId == queueItemIdToMove }
        if (fromIndex == -1) {
            return EditedQueue(
                items = renumber(sorted, currentQueueItemId),
                currentQueueItemId = currentQueueItemId,
                changed = false,
                message = null
            )
        }
        if (queueItemIdToMove == currentQueueItemId) {
            return EditedQueue(
                items = renumber(sorted, currentQueueItemId),
                currentQueueItemId = currentQueueItemId,
                changed = false,
                message = "Current song stays fixed"
            )
        }

        val mutable = sorted.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        val currentIndexAfterRemoval = mutable.indexOfFirst { it.queueItemId == currentQueueItemId }
        var targetIndex = (fromIndex + offset).coerceIn(0, mutable.size)

        // The current song itself is never moved. If a drag step targets the
        // current row, place the moved row on the other side of it instead.
        if (currentIndexAfterRemoval != -1 && targetIndex == currentIndexAfterRemoval) {
            targetIndex = if (fromIndex < sorted.indexOfFirst { it.queueItemId == currentQueueItemId }) {
                (currentIndexAfterRemoval + 1).coerceAtMost(mutable.size)
            } else {
                (currentIndexAfterRemoval - 1).coerceAtLeast(0)
            }
        }

        mutable.add(targetIndex, moved)
        return EditedQueue(
            items = renumber(mutable, currentQueueItemId),
            currentQueueItemId = currentQueueItemId,
            changed = true,
            message = null
        )
    }

    fun shuffleEntireQueue(
        existingItems: List<QueueItemEntity>,
        currentQueueItemId: String?,
        random: Random = Random.Default
    ): EditedQueue {
        val sorted = existingItems.sortedBy { it.position }
        if (sorted.size < 2 || currentQueueItemId == null) {
            return EditedQueue(
                items = renumber(sorted, currentQueueItemId),
                currentQueueItemId = currentQueueItemId,
                changed = false,
                message = null
            )
        }
        val current = sorted.firstOrNull { it.queueItemId == currentQueueItemId }
            ?: return EditedQueue(renumber(sorted, currentQueueItemId), currentQueueItemId, false, null)
        val shuffled = sorted
            .filterNot { it.queueItemId == currentQueueItemId }
            .let { others -> ensureActuallyReordered(others, others.shuffled(random)) }
            .toMutableList()
        val currentIndex = sorted.indexOfFirst { it.queueItemId == currentQueueItemId }
            .coerceIn(0, shuffled.size)
        shuffled.add(currentIndex, current)
        return EditedQueue(
            items = renumber(shuffled, currentQueueItemId),
            currentQueueItemId = currentQueueItemId,
            changed = true,
            message = "Queue shuffled"
        )
    }

    fun shuffleUpcoming(
        existingItems: List<QueueItemEntity>,
        currentQueueItemId: String?,
        random: Random = Random.Default
    ): EditedQueue {
        val sorted = existingItems.sortedBy { it.position }
        val currentIndex = sorted.indexOfFirst { it.queueItemId == currentQueueItemId }
        if (currentIndex == -1 || currentIndex >= sorted.lastIndex) {
            return EditedQueue(
                items = renumber(sorted, currentQueueItemId),
                currentQueueItemId = currentQueueItemId,
                changed = false,
                message = "No upcoming songs to shuffle"
            )
        }
        val beforeAndCurrent = sorted.take(currentIndex + 1)
        val upcoming = sorted.drop(currentIndex + 1)
        val shuffledUpcoming = ensureActuallyReordered(upcoming, upcoming.shuffled(random))
        return EditedQueue(
            items = renumber(beforeAndCurrent + shuffledUpcoming, currentQueueItemId),
            currentQueueItemId = currentQueueItemId,
            changed = true,
            message = "Upcoming songs shuffled"
        )
    }

    fun replaceWithTrackIds(
        trackIds: List<String>,
        startTrackId: String?,
        queueStateId: String,
        insertedBy: String,
        now: () -> Long,
        idFactory: () -> String
    ): EditedQueue {
        val uniqueTrackIds = trackIds.distinct()
        if (uniqueTrackIds.isEmpty()) {
            return EditedQueue(
                items = emptyList(),
                currentQueueItemId = null,
                changed = true,
                message = "No songs to play"
            )
        }

        val currentTrackId = startTrackId
            ?.takeIf { it in uniqueTrackIds }
            ?: uniqueTrackIds.first()
        val items = uniqueTrackIds.map { trackId ->
            QueueItemEntity(
                queueItemId = idFactory(),
                queueStateId = queueStateId,
                trackId = trackId,
                remoteItemId = null,
                position = Int.MAX_VALUE,
                state = STATE_UPCOMING,
                insertedBy = insertedBy,
                addedAt = now()
            )
        }
        val currentQueueItemId = items.first { it.trackId == currentTrackId }.queueItemId

        return EditedQueue(
            items = renumber(items, currentQueueItemId),
            currentQueueItemId = currentQueueItemId,
            changed = true,
            message = null
        )
    }

    private fun renumber(
        items: List<QueueItemEntity>,
        currentQueueItemId: String?
    ): List<QueueItemEntity> {
        val currentIndex = items.indexOfFirst { it.queueItemId == currentQueueItemId }
        return items.mapIndexed { index, item ->
            val state = when {
                currentIndex == -1 -> STATE_PREVIOUS
                index < currentIndex -> STATE_PREVIOUS
                index == currentIndex -> STATE_CURRENT
                else -> STATE_UPCOMING
            }
            item.copy(position = index, state = state)
        }
    }

    private fun QueueItemEntity.sameMediaAs(other: QueueItemEntity): Boolean =
        when {
            trackId != null && other.trackId != null -> trackId == other.trackId
            remoteItemId != null && other.remoteItemId != null -> remoteItemId == other.remoteItemId
            else -> false
        }
}

data class EditedQueue(
    val items: List<QueueItemEntity>,
    val currentQueueItemId: String?,
    val changed: Boolean,
    val message: String?
)

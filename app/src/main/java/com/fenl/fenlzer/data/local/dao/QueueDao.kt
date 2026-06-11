package com.fenl.fenlzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.fenl.fenlzer.data.local.entity.QueueItemEntity
import com.fenl.fenlzer.data.local.entity.QueueStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_states WHERE queueStateId = :queueStateId")
    fun observeQueueState(queueStateId: String = DEFAULT_QUEUE_STATE_ID): Flow<QueueStateEntity?>

    @Query(
        """
        SELECT * FROM queue_items
        WHERE queueStateId = :queueStateId
        ORDER BY position ASC
        """
    )
    fun observeQueueItems(queueStateId: String = DEFAULT_QUEUE_STATE_ID): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_states WHERE queueStateId = :queueStateId")
    suspend fun getQueueState(queueStateId: String = DEFAULT_QUEUE_STATE_ID): QueueStateEntity?

    @Query(
        """
        SELECT * FROM queue_items
        WHERE queueStateId = :queueStateId
        ORDER BY position ASC
        """
    )
    suspend fun getQueueItems(queueStateId: String = DEFAULT_QUEUE_STATE_ID): List<QueueItemEntity>

    @Upsert
    suspend fun upsertQueueState(queueState: QueueStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQueueItem(queueItem: QueueItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(queueItems: List<QueueItemEntity>)

    @Query("DELETE FROM queue_items WHERE queueStateId = :queueStateId")
    suspend fun deleteQueueItems(queueStateId: String = DEFAULT_QUEUE_STATE_ID)

    @Transaction
    suspend fun replaceQueue(
        queueState: QueueStateEntity,
        queueItems: List<QueueItemEntity>,
        queueStateId: String = DEFAULT_QUEUE_STATE_ID
    ) {
        deleteQueueItems(queueStateId)
        upsertQueueState(queueState)
        insertQueueItems(queueItems)
    }

    @Query("DELETE FROM queue_items WHERE queueItemId = :queueItemId")
    suspend fun deleteQueueItem(queueItemId: String)

    @Query("DELETE FROM queue_items WHERE queueStateId = :queueStateId AND state = 'UPCOMING'")
    suspend fun clearUpcoming(queueStateId: String = DEFAULT_QUEUE_STATE_ID)

    @Query(
        """
        UPDATE queue_items
        SET trackId = :trackId,
            remoteItemId = NULL
        WHERE remoteItemId = :remoteItemId
        """
    )
    suspend fun convertRemoteItemToTrack(remoteItemId: String, trackId: String)

    companion object {
        const val DEFAULT_QUEUE_STATE_ID = "default_queue"
    }
}

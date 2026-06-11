package com.fenl.fenlzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiDiagnosticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ApiDiagnosticEntryEntity)

    @Query("SELECT * FROM api_diagnostic_entries ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<ApiDiagnosticEntryEntity>>

    @Query(
        """
        DELETE FROM api_diagnostic_entries
        WHERE diagnosticId NOT IN (
            SELECT diagnosticId FROM api_diagnostic_entries
            ORDER BY startedAt DESC
            LIMIT :limit
        )
        """
    )
    suspend fun trimToLatest(limit: Int = 500)

    @Transaction
    suspend fun insertAndTrim(entry: ApiDiagnosticEntryEntity, limit: Int = 500) {
        insert(entry)
        trimToLatest(limit)
    }
}

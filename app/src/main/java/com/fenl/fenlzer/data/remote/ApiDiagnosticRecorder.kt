package com.fenl.fenlzer.data.remote

import com.fenl.fenlzer.data.local.dao.ApiDiagnosticDao
import com.fenl.fenlzer.data.local.entity.ApiDiagnosticEntryEntity

interface ApiDiagnosticRecorder {
    suspend fun record(entry: ApiDiagnosticEntryEntity)
}

class RoomApiDiagnosticRecorder(
    private val dao: ApiDiagnosticDao
) : ApiDiagnosticRecorder {
    override suspend fun record(entry: ApiDiagnosticEntryEntity) {
        dao.insertAndTrim(entry)
    }
}

class NoOpApiDiagnosticRecorder : ApiDiagnosticRecorder {
    override suspend fun record(entry: ApiDiagnosticEntryEntity) = Unit
}

class InMemoryApiDiagnosticRecorder : ApiDiagnosticRecorder {
    private val mutableEntries = mutableListOf<ApiDiagnosticEntryEntity>()

    val entries: List<ApiDiagnosticEntryEntity>
        get() = mutableEntries.toList()

    override suspend fun record(entry: ApiDiagnosticEntryEntity) {
        mutableEntries += entry
    }
}

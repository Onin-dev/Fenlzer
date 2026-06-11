package com.fenl.fenlzer.data.remote

import java.util.UUID

object IdempotencyKeyFactory {
    fun create(): String = "fenlzer_${UUID.randomUUID()}"
}

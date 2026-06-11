package com.fenl.fenlzer.importing.local

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {
    @Test
    fun hashesBytesUsingLowercaseHex() {
        val hash = Sha256.hashBytes("Fenlzer".encodeToByteArray())

        assertEquals(
            "6101a990aa8ebe75424659370c16bb5337483a62be9f74504a86c73f2658147e",
            hash
        )
    }
}

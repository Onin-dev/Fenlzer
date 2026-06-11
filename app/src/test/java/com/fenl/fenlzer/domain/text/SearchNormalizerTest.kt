package com.fenl.fenlzer.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNormalizerTest {
    @Test
    fun sortKeyLowercasesTrimsAndRemovesAccents() {
        assertEquals(
            "the cafe del mar",
            SearchNormalizer.sortKey("  The  Café del Mar  ")
        )
    }
}

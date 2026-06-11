package com.fenl.fenlzer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveBreakpointsTest {
    @Test
    fun mediumWidthStartsAt840dp() {
        assertFalse(AdaptiveBreakpoints.isMediumOrWider(839))
        assertTrue(AdaptiveBreakpoints.isMediumOrWider(840))
    }

    @Test
    fun horizontalPaddingGrowsWithAvailableWidth() {
        assertEquals(16, AdaptiveBreakpoints.horizontalContentPaddingDp(399))
        assertEquals(20, AdaptiveBreakpoints.horizontalContentPaddingDp(600))
        assertEquals(24, AdaptiveBreakpoints.horizontalContentPaddingDp(840))
    }

    @Test
    fun landscapeUsesDenserVerticalPadding() {
        assertEquals(12, AdaptiveBreakpoints.verticalContentPaddingDp(isLandscape = true))
        assertEquals(20, AdaptiveBreakpoints.verticalContentPaddingDp(isLandscape = false))
    }
}

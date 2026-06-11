package com.fenl.fenlzer.ui.components

/**
 * Shared width and density rules used by the final V1 adaptive/polish pass.
 *
 * Keep these values centralized so landscape/tablet behavior stays consistent
 * across Settings, Diagnostics, library, import, Discover, and statistics screens.
 */
object AdaptiveBreakpoints {
    const val COMPACT_WIDTH_DP = 600
    const val MEDIUM_WIDTH_DP = 840
    const val DEFAULT_MAX_CONTENT_WIDTH_DP = 1040
    const val WIDE_LIBRARY_MAX_CONTENT_WIDTH_DP = 1180

    fun isMediumOrWider(widthDp: Int): Boolean = widthDp >= MEDIUM_WIDTH_DP

    fun horizontalContentPaddingDp(widthDp: Int): Int = when {
        widthDp >= MEDIUM_WIDTH_DP -> 24
        widthDp >= COMPACT_WIDTH_DP -> 20
        else -> 16
    }

    fun verticalContentPaddingDp(isLandscape: Boolean): Int = if (isLandscape) 12 else 20
}

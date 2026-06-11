package com.fenl.fenlzer.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
data class FenlzerAdaptiveInfo(
    val widthDp: Int,
    val heightDp: Int,
    val isLandscape: Boolean,
    val isMediumOrWider: Boolean,
    val horizontalPadding: Dp,
    val verticalPadding: Dp
)

@Composable
fun rememberFenlzerAdaptiveInfo(): FenlzerAdaptiveInfo {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthDp = configuration.screenWidthDp
    return FenlzerAdaptiveInfo(
        widthDp = widthDp,
        heightDp = configuration.screenHeightDp,
        isLandscape = isLandscape,
        isMediumOrWider = AdaptiveBreakpoints.isMediumOrWider(widthDp),
        horizontalPadding = AdaptiveBreakpoints.horizontalContentPaddingDp(widthDp).dp,
        verticalPadding = AdaptiveBreakpoints.verticalContentPaddingDp(isLandscape).dp
    )
}

/**
 * Centers wide utility screens without imposing an artificial width on phones.
 */
@Composable
fun FenlzerCenteredPane(
    modifier: Modifier = Modifier,
    maxWidth: Dp = AdaptiveBreakpoints.DEFAULT_MAX_CONTENT_WIDTH_DP.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
        ) {
            content()
        }
    }
}

/**
 * Shared scroll container for Settings-like screens.
 *
 * It prevents landscape layouts from stretching edge-to-edge, keeps content
 * centered on wider devices, and uses a smaller vertical padding in landscape
 * so controls remain reachable above the mini-player/navigation areas.
 */
@Composable
fun FenlzerAdaptiveScrollableColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = AdaptiveBreakpoints.DEFAULT_MAX_CONTENT_WIDTH_DP.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.(FenlzerAdaptiveInfo) -> Unit
) {
    val adaptiveInfo = rememberFenlzerAdaptiveInfo()
    FenlzerCenteredPane(modifier = modifier, maxWidth = maxWidth) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        horizontal = adaptiveInfo.horizontalPadding,
                        vertical = adaptiveInfo.verticalPadding
                    )
                ),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment
        ) {
            content(adaptiveInfo)
        }
    }
}

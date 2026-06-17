package com.fenl.fenlzer.ui.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

object FenlzerMotion {
    const val PLAYER_EXPAND_MS = 460
    const val PLAYER_COLLAPSE_MS = 390
    const val GESTURE_SETTLE_MS = 120
    const val MINI_TRACK_ENTER_MS = 180
    const val MINI_TRACK_EXIT_MS = 140
    const val LOADING_SHIMMER_MS = 1_250

    val PlayerEasing = Easing { fraction ->
        0.5f - 0.5f * cos(PI.toFloat() * fraction)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            repeatDelayMillis = 1_200
        )
    )
}

@Composable
fun AnimatedLoadingPlaceholder(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "loadingPlaceholder")
    val offset by transition.animateFloat(
        initialValue = -400f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = FenlzerMotion.LOADING_SHIMMER_MS),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingPlaceholderOffset"
    )
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    )
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = colors,
                start = Offset(offset, 0f),
                end = Offset(offset + 400f, 0f)
            )
        )
    )
}

@Composable
fun rememberAverageArtworkColor(
    thumbnailUri: Uri?,
    fallback: Color
): Color {
    val context = LocalContext.current
    var color by remember(thumbnailUri) { mutableStateOf<Color?>(null) }

    LaunchedEffect(thumbnailUri) {
        color = null
        val uri = thumbnailUri ?: return@LaunchedEffect
        color = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(64, 64)
                    .build()
                val image = (context.imageLoader.execute(request) as? SuccessResult)
                    ?.image
                    ?: return@runCatching null
                image.toBitmap(32, 32).averageColor()
            }.getOrNull()
        }
    }

    return color ?: fallback
}

private fun Bitmap.averageColor(): Color? {
    val stepX = max(width / 12, 1)
    val stepY = max(height / 12, 1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha > 32) {
                red += android.graphics.Color.red(pixel)
                green += android.graphics.Color.green(pixel)
                blue += android.graphics.Color.blue(pixel)
                count++
            }
            x += stepX
        }
        y += stepY
    }

    if (count == 0L) return null
    return Color(
        red = (red / count) / 255f,
        green = (green / count) / 255f,
        blue = (blue / count) / 255f,
        alpha = 1f
    )
}

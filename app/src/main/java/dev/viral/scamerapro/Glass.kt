package dev.viral.scamerapro

import android.view.View
import android.view.ViewGroup
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A tiny live snapshot of the preview + where the preview sits on screen. */
data class GlassData(val bitmap: ImageBitmap? = null, val rect: Rect = Rect.Zero)

val LocalGlass = compositionLocalOf<State<GlassData>> { mutableStateOf(GlassData()) }

/**
 * Frosted glass: draws a heavily upscaled (= blurred) mini-copy of the camera preview
 * behind the element, then a dark tint, a soft top highlight and a thin border.
 * If there is no snapshot yet it falls back to a plain translucent tint.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Color = Color(0x80202020)): Modifier {
    val g = LocalGlass.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .clip(shape)
        .drawBehind {
            val data = g.value
            val bmp = data.bitmap
            if (GLASS_BLUR && bmp != null && data.rect.width > 0f && data.rect.height > 0f) {
                translate(data.rect.left - bounds.left, data.rect.top - bounds.top) {
                    drawImage(
                        bmp,
                        dstSize = IntSize(data.rect.width.roundToInt(), data.rect.height.roundToInt()),
                        filterQuality = FilterQuality.Low
                    )
                }
            }
            drawRect(tint)
            drawRect(Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x00FFFFFF))))
        }
        .border(0.6.dp, Color(0x2EFFFFFF), shape)
}

fun findTextureView(v: View?): TextureView? {
    if (v is TextureView) return v
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            findTextureView(v.getChildAt(i))?.let { return it }
        }
    }
    return null
}

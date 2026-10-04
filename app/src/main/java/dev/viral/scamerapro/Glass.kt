package dev.viral.scamerapro

import android.graphics.Bitmap
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** Blurred live snapshot of the preview + where the preview sits on screen. */
data class GlassData(val bitmap: ImageBitmap? = null, val rect: Rect = Rect.Zero, val version: Int = 0)

val LocalGlass = compositionLocalOf<State<GlassData>> { mutableStateOf(GlassData()) }

private val SatFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.45f) })

/**
 * Real backdrop blur: the camera image behind the element is Gaussian-blurred (two box-blur passes
 * on a small live copy, then smoothly scaled up), slightly over-saturated and tinted - like the
 * One UI widgets on the home screen. Without a snapshot it falls back to a plain translucent tint.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Color = Color(0x4D0C0C0C)): Modifier {
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
                        filterQuality = FilterQuality.Medium,
                        colorFilter = SatFilter
                    )
                }
            }
            drawRect(tint)
            drawRect(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x00FFFFFF))))
        }
        .border(0.6.dp, Color(0x33FFFFFF), shape)
}

/** Grabs a tiny copy of the preview (TextureView) and blurs it on the CPU. */
class BackdropSampler {
    private var bw = 0
    private var bh = 0
    private var raw: Bitmap? = null
    private val out = arrayOfNulls<Bitmap>(2)
    private var which = 0
    private var px = IntArray(0)
    private var tmp = IntArray(0)

    fun sample(tv: TextureView, aspect: Float): ImageBitmap? {
        if (!tv.isAvailable || aspect <= 0f) return null
        val w = 72
        val h = max(8, (w / aspect).roundToInt())
        if (w != bw || h != bh) {
            bw = w
            bh = h
            raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out[0] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out[1] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            px = IntArray(w * h)
            tmp = IntArray(w * h)
        }
        val r = raw ?: return null
        tv.getBitmap(r)
        r.getPixels(px, 0, w, 0, 0, w, h)
        boxBlur(px, tmp, w, h, 3)
        boxBlur(px, tmp, w, h, 3)
        which = 1 - which
        val o = out[which] ?: return null
        o.setPixels(px, 0, w, 0, 0, w, h)
        return o.asImageBitmap()
    }
}

/** Separable box blur with edge clamping; reads and writes [px], uses [tmp] as scratch. */
private fun boxBlur(px: IntArray, tmp: IntArray, w: Int, h: Int, r: Int) {
    val div = 2 * r + 1
    // horizontal: px -> tmp
    for (y in 0 until h) {
        val row = y * w
        var sr = 0
        var sg = 0
        var sb = 0
        for (i in -r..r) {
            val c = px[row + i.coerceIn(0, w - 1)]
            sr += (c shr 16) and 255
            sg += (c shr 8) and 255
            sb += c and 255
        }
        for (x in 0 until w) {
            tmp[row + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val add = px[row + (x + r + 1).coerceAtMost(w - 1)]
            val sub = px[row + (x - r).coerceAtLeast(0)]
            sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
            sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
            sb += (add and 255) - (sub and 255)
        }
    }
    // vertical: tmp -> px
    for (x in 0 until w) {
        var sr = 0
        var sg = 0
        var sb = 0
        for (i in -r..r) {
            val c = tmp[i.coerceIn(0, h - 1) * w + x]
            sr += (c shr 16) and 255
            sg += (c shr 8) and 255
            sb += c and 255
        }
        for (y in 0 until h) {
            px[y * w + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val add = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
            val sub = tmp[(y - r).coerceAtLeast(0) * w + x]
            sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
            sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
            sb += (add and 255) - (sub and 255)
        }
    }
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

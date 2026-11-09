package dev.viral.scamerapro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max
import kotlin.math.min

/** Digital zoom for saved photos: crops the centre by [factor] and scales it back to full size. */
fun digitalCrop(ctx: Context, uri: Uri, factor: Float) {
    try {
        val cr = ctx.contentResolver
        val orient = cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        } ?: 1
        val result: Bitmap = cr.openInputStream(uri)?.use { s ->
            val dec = BitmapRegionDecoder.newInstance(s, false) ?: return@use null
            val w = dec.width
            val h = dec.height
            val cw = (w / factor).toInt().coerceAtLeast(2)
            val ch = (h / factor).toInt().coerceAtLeast(2)
            val l = (w - cw) / 2
            val t = (h - ch) / 2
            val part = dec.decodeRegion(Rect(l, t, l + cw, t + ch), null)
            dec.recycle()
            val m = Matrix()
            when (orient) {
                6 -> m.postRotate(90f)
                3 -> m.postRotate(180f)
                8 -> m.postRotate(270f)
            }
            val turned = orient == 6 || orient == 3 || orient == 8
            val rotated = if (turned) Bitmap.createBitmap(part, 0, 0, part.width, part.height, m, true) else part
            val swap = orient == 6 || orient == 8
            Bitmap.createScaledBitmap(rotated, if (swap) h else w, if (swap) w else h, true)
        } ?: return
        val out = if (result.isMutable) result else result.copy(Bitmap.Config.ARGB_8888, true)
        sharpen(out, (0.6f + 0.05f * factor).coerceAtMost(1.2f), if (factor > 3.5f) 3 else 2)
        cr.openOutputStream(uri, "wt")?.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    } catch (e: Exception) {
        android.util.Log.e("SCameraPro", "digital crop failed", e)
    }
}

/** Unsharp mask on brightness, processed in strips so it does not need much memory. */
internal fun sharpen(bmp: Bitmap, amount: Float, r: Int) {
    val w = bmp.width
    val h = bmp.height
    val div = 2f * r + 1f
    var y0 = 0
    while (y0 < h) {
        val y1 = min(h, y0 + 192)
        val ya = max(0, y0 - r)
        val yb = min(h, y1 + r)
        val bh = yb - ya
        val px = IntArray(w * bh)
        bmp.getPixels(px, 0, w, 0, ya, w, bh)
        val lum = FloatArray(w * bh) {
            ((px[it] shr 16) and 255) * 0.299f + ((px[it] shr 8) and 255) * 0.587f + (px[it] and 255) * 0.114f
        }
        val tmp = FloatArray(w * bh)
        for (y in 0 until bh) {
            val row = y * w
            var sum = 0f
            for (i in -r..r) sum += lum[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / div
                sum += lum[row + (x + r + 1).coerceAtMost(w - 1)] - lum[row + (x - r).coerceAtLeast(0)]
            }
        }
        val blur = FloatArray(w * bh)
        for (x in 0 until w) {
            var sum = 0f
            for (i in -r..r) sum += tmp[i.coerceIn(0, bh - 1) * w + x]
            for (y in 0 until bh) {
                blur[y * w + x] = sum / div
                sum += tmp[(y + r + 1).coerceAtMost(bh - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        for (y in (y0 - ya) until (y1 - ya)) for (x in 0 until w) {
            val i = y * w + x
            val d = (lum[i] - blur[i]) * amount
            val c = px[i]
            val rr = (((c shr 16) and 255) + d).toInt().coerceIn(0, 255)
            val gg = (((c shr 8) and 255) + d).toInt().coerceIn(0, 255)
            val bb = ((c and 255) + d).toInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        bmp.setPixels(px, (y0 - ya) * w, w, 0, y0, w, y1 - y0)
        y0 = y1
    }
}

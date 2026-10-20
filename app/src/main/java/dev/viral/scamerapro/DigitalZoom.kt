package dev.viral.scamerapro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri

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
        cr.openOutputStream(uri, "wt")?.use { result.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    } catch (e: Exception) {
        android.util.Log.e("SCameraPro", "digital crop failed", e)
    }
}

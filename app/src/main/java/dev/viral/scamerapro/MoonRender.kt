package dev.viral.scamerapro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Draws the Moon from the NASA map (assets/moon_map.jpg): right phase, right tilt for this moment and place. */
object MoonRender {
    private var px = IntArray(0)
    private var mw = 0
    private var mh = 0

    private fun load(ctx: Context): Boolean {
        if (px.isNotEmpty()) return true
        return try {
            val b = ctx.assets.open("moon_map.jpg").use { BitmapFactory.decodeStream(it) } ?: return false
            mw = b.width
            mh = b.height
            val tmp = IntArray(mw * mh)
            b.getPixels(tmp, 0, mw, 0, 0, mw, mh)
            px = tmp
            true
        } catch (e: Exception) {
            false
        }
    }

    fun render(ctx: Context, info: MoonInfo, size: Int, mirror: Boolean = false): Bitmap? {
        if (!load(ctx)) return null
        val out = IntArray(size * size)
        val r = size / 2f
        val alpha = acos((2 * info.illum - 1).coerceIn(-1.0, 1.0))
        val phi = Math.toRadians(info.brightLimbDeg)
        val lx = (sin(alpha) * -sin(phi)).toFloat()
        val ly = (sin(alpha) * cos(phi)).toFloat()
        val lz = cos(alpha).toFloat()
        val nRot = Math.toRadians(info.northDeg)
        val cN = cos(nRot).toFloat()
        val sN = sin(nRot).toFloat()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val nx = (x + 0.5f - r) / r
                val ny = (r - (y + 0.5f)) / r
                val d2 = nx * nx + ny * ny
                if (d2 > 1f) continue
                val nz = sqrt(1f - d2)
                val shade = nx * lx + ny * ly + nz * lz
                val s = ((shade + 0.05f) / 0.30f).coerceIn(0f, 1f).let { 0.04f + 0.96f * it * it * (3f - 2f * it) }
                val mx = nx * cN + ny * sN
                val my = -nx * sN + ny * cN
                val mz = sqrt(max(0f, 1f - mx * mx - my * my))
                val lat = asin(my.toDouble().coerceIn(-1.0, 1.0))
                val lon = atan2(mx.toDouble(), mz.toDouble()).let { if (mirror) -it else it }
                val ix = ((0.5 + lon / (2 * PI)) * mw).toInt().coerceIn(0, mw - 1)
                val iy = ((0.5 - lat / PI) * mh).toInt().coerceIn(0, mh - 1)
                val c = px[iy * mw + ix]
                val lum = (((c shr 16) and 255) * 0.3f + ((c shr 8) and 255) * 0.59f + (c and 255) * 0.11f)
                val g = (lum * s).toInt().coerceIn(0, 255)
                out[y * size + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
    }
}

package dev.viral.scamerapro

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Live Moon in the preview: finds how the map must be turned to match the real Moon, and draws the overlay bitmap. */
object MoonLive {
    @Volatile var illum = 1.0

    data class Orient(val angle: Double, val mirror: Boolean, val score: Double)

    private fun phiBright(hit: MoonHit, info: MoonInfo): Double =
        if (hit.phi.isNaN()) info.brightLimbDeg
        else (Math.toDegrees(atan2(-cos(hit.phi.toDouble()), -sin(hit.phi.toDouble()))) + 360.0) % 360.0

    fun orientation(ctx: Context, info: MoonInfo, snap: Bitmap, hit: MoonHit): Orient? {
        val n = 64
        val side = (hit.r * snap.width * 2f).toInt().coerceAtLeast(16)
        val x0 = (hit.cx * snap.width - side / 2f).toInt().coerceIn(0, max(0, snap.width - side))
        val y0 = (hit.cy * snap.height - side / 2f).toInt().coerceIn(0, max(0, snap.height - side))
        val w = min(side, snap.width - x0)
        val h = min(side, snap.height - y0)
        if (w < 8 || h < 8) return null
        val real = Bitmap.createScaledBitmap(Bitmap.createBitmap(snap, x0, y0, w, h), n, n, true)
        val px = IntArray(n * n)
        real.getPixels(px, 0, n, 0, 0, n, n)
        val rl = IntArray(n * n) { (px[it] shr 8) and 255 }
        val mask = BooleanArray(n * n) {
            val nx = ((it % n) + 0.5f) / n * 2f - 1f
            val ny = ((it / n) + 0.5f) / n * 2f - 1f
            nx * nx + ny * ny < 0.5f
        }
        val phi = phiBright(hit, info)
        fun score(a: Double, mir: Boolean): Double {
            val r = MoonRender.render(ctx, info.copy(northDeg = a, brightLimbDeg = phi), n, mir) ?: return -1.0
            val rp = IntArray(n * n)
            r.getPixels(rp, 0, n, 0, 0, n, n)
            return ncc(rl, IntArray(n * n) { (rp[it] shr 8) and 255 }, mask)
        }
        var bestA = 0.0
        var bestS = -2.0
        var bestM = false
        for (mir in listOf(false, true)) {
            var a = 0.0
            while (a < 360.0) {
                val s = score(a, mir)
                if (s > bestS) { bestS = s; bestA = a; bestM = mir }
                a += 6.0
            }
        }
        var a2 = bestA - 6.0
        val base = bestA
        while (a2 <= base + 6.0) {
            val s = score(a2, bestM)
            if (s > bestS) { bestS = s; bestA = a2 }
            a2 += 2.0
        }
        return Orient(bestA, bestM, bestS)
    }

    fun overlay(ctx: Context, info: MoonInfo, o: Orient, hit: MoonHit): Bitmap? =
        MoonRender.render(ctx, info.copy(northDeg = o.angle, brightLimbDeg = phiBright(hit, info)), 320, o.mirror)

    private fun ncc(a: IntArray, b: IntArray, m: BooleanArray): Double {
        var n = 0; var sa = 0.0; var sb = 0.0
        for (i in a.indices) if (m[i]) { n++; sa += a[i]; sb += b[i] }
        if (n == 0) return -1.0
        val ma = sa / n
        val mb = sb / n
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) if (m[i]) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y; da += x * x; db += y * y
        }
        return if (da < 1e-6 || db < 1e-6) -1.0 else num / sqrt(da * db)
    }
}

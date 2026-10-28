package dev.viral.scamerapro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.util.Random
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Adds real Moon detail (NASA map) to a saved photo: finds the Moon, matches rotation, blends detail + grain. */
object MoonEnhance {
    fun run(ctx: Context, uri: Uri, info: MoonInfo): String = try {
        work(ctx, uri, info)
    } catch (e: Throwable) {
        Log.e("SCameraPro", "moon enhance failed", e)
        ("ошибка: " + e.javaClass.simpleName + " " + (e.message ?: "") + " @" + (e.stackTrace.firstOrNull()?.let { it.methodName + ":" + it.lineNumber } ?: "")).take(110)
    }

    private fun work(ctx: Context, uri: Uri, info: MoonInfo): String {
        if (info.illum < 0.8) return "фаза " + (info.illum * 100).toInt() + "% < 80%"
        if (MoonRender.render(ctx, info, 8) == null) return "нет moon_map.jpg в assets"
        val cr = ctx.contentResolver
        val orient = cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        } ?: 1

        // 1) find the Moon on a 1/4 size copy
        val small = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
        } ?: return "не удалось прочитать фото"
        val sw = small.width
        val sh = small.height
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        small.recycle()
        val lum = IntArray(px.size) {
            (((px[it] shr 16) and 255) * 77 + ((px[it] shr 8) and 255) * 150 + (px[it] and 255) * 29) shr 8
        }
        var mx = 0
        for (v in lum) if (v > mx) mx = v
        if (mx < 80) return "кадр тёмный (макс $mx)"
        val thr = max(40, mx / 2)
        val seen = BooleanArray(lum.size)
        val stack = IntArray(lum.size)
        var best = 0
        var bx0 = 0; var bx1 = 0; var by0 = 0; var by1 = 0
        for (s0 in lum.indices) {
            if (seen[s0] || lum[s0] < thr) continue
            var sp = 0
            stack[sp++] = s0
            seen[s0] = true
            var cnt = 0
            var x0 = sw; var x1 = 0; var y0 = sh; var y1 = 0
            while (sp > 0) {
                val i = stack[--sp]
                val x = i % sw
                val y = i / sw
                cnt++
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                if (x > 0 && !seen[i - 1] && lum[i - 1] >= thr) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < sw - 1 && !seen[i + 1] && lum[i + 1] >= thr) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && !seen[i - sw] && lum[i - sw] >= thr) { seen[i - sw] = true; stack[sp++] = i - sw }
                if (y < sh - 1 && !seen[i + sw] && lum[i + sw] >= thr) { seen[i + sw] = true; stack[sp++] = i + sw }
            }
            if (cnt > best) { best = cnt; bx0 = x0; bx1 = x1; by0 = y0; by1 = y1 }
        }
        val bw = bx1 - bx0 + 1
        val bh = by1 - by0 + 1
        if (best < 200 || bw.toFloat() / bh !in 0.7f..1.4f) return "пятно не круглое: ${bw}x$bh, $best"
        val cxF = ((bx0 + bx1 + 1) / 2f * 4f).toInt()
        val cyF = ((by0 + by1 + 1) / 2f * 4f).toInt()
        val rad = max(bw, bh) * 2f // radius in full-res pixels (bbox/2 * 4)
        val dia = (rad * 2).toInt()
        if (dia < 64 || dia > 2000) return "диск $dia px вне 64..2000"

        // 2) cut out a square around the Moon at full resolution
        val side = (dia * 1.45f).toInt()
        val dec = cr.openInputStream(uri)?.use { BitmapRegionDecoder.newInstance(it, false) } ?: return "не удалось прочитать фото"
        val fw = dec.width
        val fh = dec.height
        val left = (cxF - side / 2).coerceIn(0, max(0, fw - side))
        val top = (cyF - side / 2).coerceIn(0, max(0, fh - side))
        val rb0 = dec.decodeRegion(
            Rect(left, top, min(fw, left + side), min(fh, top + side)),
            BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        )
        dec.recycle()
        val rb = if (rb0.isMutable) rb0 else rb0.copy(Bitmap.Config.ARGB_8888, true)
        val rw = rb.width
        val rh = rb.height
        var ccx = cxF - left
        var ccy = cyF - top
        val rpx = IntArray(rw * rh)
        rb.getPixels(rpx, 0, rw, 0, 0, rw, rh)

        // 3) find the rotation: compare a 64x64 view of the real Moon with rendered candidates
        val n = 64
        val ox = (ccx - rad).toInt().coerceIn(0, rw - 2)
        val oy = (ccy - rad).toInt().coerceIn(0, rh - 2)
        val crop = Bitmap.createBitmap(rb, ox, oy, min(dia, rw - ox), min(dia, rh - oy))
        val real = Bitmap.createScaledBitmap(crop, n, n, true)
        val realPx = IntArray(n * n)
        real.getPixels(realPx, 0, n, 0, 0, n, n)
        val realL = IntArray(n * n) { (realPx[it] shr 8) and 255 }
        val mask = BooleanArray(n * n) {
            val nx = ((it % n) + 0.5f) / n * 2f - 1f
            val ny = ((it / n) + 0.5f) / n * 2f - 1f
            nx * nx + ny * ny < 0.5f
        }
        fun score(angle: Double, mir: Boolean): Double {
            val r = MoonRender.render(ctx, info.copy(northDeg = angle), n, mir) ?: return -1.0
            val rp = IntArray(n * n)
            r.getPixels(rp, 0, n, 0, 0, n, n)
            return ncc(realL, IntArray(n * n) { (rp[it] shr 8) and 255 }, mask)
        }
        var bestA = 0.0
        var bestS = -2.0
        var bestM = false
        for (mir in listOf(false, true)) {
            var ang = 0.0
            while (ang < 360.0) {
                val s = score(ang, mir)
                if (s > bestS) { bestS = s; bestA = ang; bestM = mir }
                ang += 6.0
            }
        }
        val base = bestA
        var ang2 = base - 6.0
        while (ang2 <= base + 6.0) {
            val s = score(ang2, bestM)
            if (s > bestS) { bestS = s; bestA = ang2 }
            ang2 += 2.0
        }
        if (bestS < 0.15) return "нет совпадения с картой (" + String.format("%.2f", bestS) + ")"

        // 3b) fine centre: try small shifts of the real crop against the matched reference
        val refSmall = MoonRender.render(ctx, info.copy(northDeg = bestA), n, bestM)
        if (refSmall != null) {
            val rp = IntArray(n * n)
            refSmall.getPixels(rp, 0, n, 0, 0, n, n)
            val rL = IntArray(n * n) { (rp[it] shr 8) and 255 }
            var bs = -2.0
            var bdx = 0
            var bdy = 0
            for (sy in -2..2) for (sx in -2..2) {
                val px0 = (ccx - rad + sx * 0.03f * dia).toInt().coerceIn(0, max(0, rw - dia))
                val py0 = (ccy - rad + sy * 0.03f * dia).toInt().coerceIn(0, max(0, rh - dia))
                val c2 = Bitmap.createBitmap(rb, px0, py0, min(dia, rw - px0), min(dia, rh - py0))
                val s2 = Bitmap.createScaledBitmap(c2, n, n, true)
                val sp = IntArray(n * n)
                s2.getPixels(sp, 0, n, 0, 0, n, n)
                val sc2 = ncc(IntArray(n * n) { (sp[it] shr 8) and 255 }, rL, mask)
                if (sc2 > bs) { bs = sc2; bdx = sx; bdy = sy }
            }
            ccx += (bdx * 0.03f * dia).toInt()
            ccy += (bdy * 0.03f * dia).toInt()
        }

        // 4) full-size reference, high-pass detail
        val ref = MoonRender.render(ctx, info.copy(northDeg = bestA), dia, bestM) ?: return "не удалось прочитать фото"
        val rfPx = IntArray(dia * dia)
        ref.getPixels(rfPx, 0, dia, 0, 0, dia, dia)
        val rl = FloatArray(dia * dia) { ((rfPx[it] shr 8) and 255).toFloat() }
        val blurred = blur(rl, dia, dia, max(2, dia / 40))
        val detail = FloatArray(rl.size) { rl[it] - blurred[it] }

        // brightness match and sky grain
        var rs = 0.0; var rc = 0; var ps = 0.0; var pc = 0
        var ss = 0.0; var ss2 = 0.0; var sc = 0
        for (y in 0 until rh) for (x in 0 until rw) {
            val d = hypot(x + 0.5f - ccx, y + 0.5f - ccy) / rad
            val l = (((rpx[y * rw + x] shr 8) and 255)).toDouble()
            if (d < 0.9f) { ps += l; pc++ } else if (d > 1.1f) { ss += l; ss2 += l * l; sc++ }
        }
        for (i in rl.indices) if (rl[i] > 8f) { rs += rl[i]; rc++ }
        val gain = if (rc > 0 && pc > 0) (min(ps / pc, 190.0) / (rs / rc)).toFloat().coerceIn(0.3f, 3f) else 1f
        val skyStd = if (sc > 10) sqrt(max(0.0, ss2 / sc - (ss / sc) * (ss / sc))).toFloat() else 3f
        val grain = skyStd.coerceIn(1f, 10f) * 0.9f
        val rnd = Random(7)

        // 5) full replacement: repaint the old blurry disc with sky, then draw the new Moon with a clean edge
        var skR = 0L; var skG = 0L; var skB = 0L; var skN = 0
        val half = dia / 2f
        for (y in 0 until rh) for (x in 0 until rw) {
            if (hypot(x + 0.5f - ccx, y + 0.5f - ccy) / half > 1.32f) {
                val c = rpx[y * rw + x]
                skR += (c shr 16) and 255; skG += (c shr 8) and 255; skB += c and 255; skN++
            }
        }
        val sR = if (skN > 0) skR.toFloat() / skN else 2f
        val sG = if (skN > 0) skG.toFloat() / skN else 2f
        val sB = if (skN > 0) skB.toFloat() / skN else 2f
        val rox = ccx - dia / 2
        val roy = ccy - dia / 2
        for (y in 0 until rh) for (x in 0 until rw) {
            val d = hypot(x + 0.5f - ccx, y + 0.5f - ccy) / half
            if (d >= 1.28f) continue
            val c = rpx[y * rw + x]
            var r = ((c shr 16) and 255).toFloat()
            var g = ((c shr 8) and 255).toFloat()
            var b = (c and 255).toFloat()
            val wSky = 1f - smooth(1.10f, 1.28f, d)
            if (wSky > 0f) {
                val nz = rnd.nextGaussian().toFloat() * grain * 0.6f
                r += (sR + nz - r) * wSky
                g += (sG + nz - g) * wSky
                b += (sB + nz - b) * wSky
            }
            if (d < 1f) {
                val rx = x - rox
                val ry = y - roy
                if (rx in 0 until dia && ry in 0 until dia) {
                    val aEdge = 1f - smooth(0.955f, 1f, d)
                    val v = (rl[ry * dia + rx] * gain + rnd.nextGaussian().toFloat() * grain).coerceIn(0f, 255f)
                    r += (v * 1.03f - r) * aEdge
                    g += (v - g) * aEdge
                    b += (v * 0.96f - b) * aEdge
                }
            }
            rpx[y * rw + x] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
        rb.setPixels(rpx, 0, rw, 0, 0, rw, rh)

        // 6) put the piece back into the full photo and save
        val full = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inMutable = true })
        } ?: return "не удалось прочитать фото"
        val fullM = if (full.isMutable) full else full.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(fullM).drawBitmap(rb, left.toFloat(), top.toFloat(), null)
        cr.openOutputStream(uri, "wt")?.use { fullM.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        cr.openFileDescriptor(uri, "rw")?.use { pfd ->
            val ex = ExifInterface(pfd.fileDescriptor)
            ex.setAttribute(ExifInterface.TAG_ORIENTATION, orient.toString())
            ex.saveAttributes()
        }
        return "готово: совпадение " + String.format("%.2f", bestS) + ", угол " + bestA.toInt() + (if (bestM) " (зеркало)" else "")
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

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

    private fun blur(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val t = FloatArray(a.size)
        val o = FloatArray(a.size)
        val div = 2f * r + 1f
        for (y in 0 until h) {
            val row = y * w
            var s = 0f
            for (i in -r..r) s += a[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                t[row + x] = s / div
                s += a[row + (x + r + 1).coerceAtMost(w - 1)] - a[row + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var s = 0f
            for (i in -r..r) s += t[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                o[y * w + x] = s / div
                s += t[(y + r + 1).coerceAtMost(h - 1) * w + x] - t[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return o
    }
}

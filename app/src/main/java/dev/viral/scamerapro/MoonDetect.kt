package dev.viral.scamerapro

import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Moon found in the frame: centre and radius, 0..1 relative to the visible (upright) picture width/height. */
data class MoonHit(val cx: Float, val cy: Float, val r: Float, val phi: Float = Float.NaN)

object MoonDetect {
    /** Finds the biggest bright blob on a dark background (cheap: 64-wide grid on the Y plane). */
    fun find(image: ImageProxy, illum: Double = 1.0): MoonHit? {
        val crop = image.cropRect
        val cw = crop.width()
        val ch = crop.height()
        if (cw < 16 || ch < 16) return null
        val gw = 64
        val gh = max(8, gw * ch / cw)
        val lum = IntArray(gw * gh)
        val pl = image.planes[0]
        val buf = pl.buffer
        val rs = pl.rowStride
        val ps = pl.pixelStride
        var sum = 0L
        for (gy in 0 until gh) {
            val y = crop.top + ((gy + 0.5f) * ch / gh).toInt()
            for (gx in 0 until gw) {
                val x = crop.left + ((gx + 0.5f) * cw / gw).toInt()
                val v = buf.get(y * rs + x * ps).toInt() and 0xFF
                lum[gy * gw + gx] = v
                sum += v
            }
        }
        val mean = (sum / (gw * gh)).toInt()
        var mx = 0
        for (v in lum) if (v > mx) mx = v
        // dark sky with a clearly brighter object; threshold adapts to the current exposure
        if (mean > 90 || mx < 90 || mx - mean < 60) return null
        val thr = max(70, mx * 6 / 10)

        val seen = BooleanArray(gw * gh)
        val stack = IntArray(gw * gh)
        var bestCount = 0
        var bSx = 0L
        var bSy = 0L
        var bMinX = 0
        var bMaxX = 0
        var bMinY = 0
        var bMaxY = 0
        for (start in lum.indices) {
            if (seen[start] || lum[start] < thr) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var count = 0
            var sx = 0L
            var sy = 0L
            var minX = gw
            var maxX = 0
            var minY = gh
            var maxY = 0
            while (sp > 0) {
                val i = stack[--sp]
                val x = i % gw
                val y = i / gw
                count++
                sx += x
                sy += y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && !seen[i - 1] && lum[i - 1] >= thr) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < gw - 1 && !seen[i + 1] && lum[i + 1] >= thr) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && !seen[i - gw] && lum[i - gw] >= thr) { seen[i - gw] = true; stack[sp++] = i - gw }
                if (y < gh - 1 && !seen[i + gw] && lum[i + gw] >= thr) { seen[i + gw] = true; stack[sp++] = i + gw }
            }
            if (count > bestCount) {
                bestCount = count; bSx = sx; bSy = sy
                bMinX = minX; bMaxX = maxX; bMinY = minY; bMaxY = maxY
            }
        }
        if (bestCount < 6 || bestCount > gw * gh * 35 / 100) return null
        val bw = bMaxX - bMinX + 1
        val bh = bMaxY - bMinY + 1
        val full = illum >= 0.75
        if (max(bw, bh) < 3) return null
        if (full && bestCount.toFloat() / (bw * bh) < 0.3f) return null
        val cu: Float
        val cv: Float
        val rC: Float
        var dxb = 0f
        var dyb = 0f
        var hasPhi = false
        if (full) {
            cu = (bMinX + bMaxX + 1) / 2f
            cv = (bMinY + bMaxY + 1) / 2f
            rC = max(bw, bh) / 2f
        } else {
            val f = fitPartial(lum, gw, thr, bMinX, bMaxX, bMinY, bMaxY, illum) ?: return null
            cu = f[0]; cv = f[1]; rC = f[2]; dxb = f[3]; dyb = f[4]; hasPhi = true
        }
        val u = cu / gw
        val v = cv / gh
        val rot = image.imageInfo.rotationDegrees
        val (dxu, dyu) = when (rot) {
            90 -> -dyb to dxb
            270 -> dyb to -dxb
            180 -> -dxb to -dyb
            else -> dxb to dyb
        }
        val phi = if (hasPhi) atan2(dyu, dxu) else Float.NaN
        return when (rot) {
            90 -> MoonHit(1f - v, u, rC / gh, phi)
            270 -> MoonHit(v, 1f - u, rC / gh, phi)
            180 -> MoonHit(1f - u, 1f - v, rC / gw, phi)
            else -> MoonHit(u, v, rC / gw, phi)
        }
    }

    /** Partial phase: disc size from the lit area, centre and light direction from the lit shape. [cu, cv, r, dx, dy] */
    private fun fitPartial(
        lum: IntArray, gw: Int, thr: Int,
        bx0: Int, bx1: Int, by0: Int, by1: Int, illum: Double
    ): FloatArray? {
        val gh = lum.size / gw
        var cnt = 0
        var sx = 0.0
        var sy = 0.0
        for (y in by0..by1) for (x in bx0..bx1) if (lum[y * gw + x] >= thr) { cnt++; sx += x; sy += y }
        if (cnt < 8) return null
        val bxc = sx / cnt + 0.5
        val byc = sy / cnt + 0.5
        val rs = sqrt(cnt / (Math.PI * illum))
        val ap = acos((2 * illum - 1).coerceIn(-1.0, 1.0))
        val sa = sin(ap)
        val ca = cos(ap)
        var cm = 0.0
        var cmN = 0
        for (iy in -12..12) for (ix in -12..12) {
            val nx = ix / 12.0
            val ny = iy / 12.0
            val d2 = nx * nx + ny * ny
            if (d2 > 1.0) continue
            if (nx * sa + sqrt(1 - d2) * ca > 0) { cm += nx; cmN++ }
        }
        cm /= max(1, cmN)
        fun iou(deg: Double): Double {
            val ph = Math.toRadians(deg)
            val dx = cos(ph)
            val dy = sin(ph)
            val c2x = bxc - rs * cm * dx
            val c2y = byc - rs * cm * dy
            val lx = sa * dx
            val ly = sa * dy
            var inter = 0
            var model = 0
            for (y in (c2y - rs).toInt()..(c2y + rs).toInt()) for (x in (c2x - rs).toInt()..(c2x + rs).toInt()) {
                val nx = (x + 0.5 - c2x) / rs
                val ny = (y + 0.5 - c2y) / rs
                val d2 = nx * nx + ny * ny
                if (d2 > 1.0) continue
                if (nx * lx + ny * ly + sqrt(1 - d2) * ca > 0) {
                    model++
                    if (x in 0 until gw && y in 0 until gh && lum[y * gw + x] >= thr) inter++
                }
            }
            return if (model == 0) 0.0 else inter.toDouble() / (model + cnt - inter)
        }
        var bestPhi = 0.0
        var bestIou = -1.0
        var dg = 0.0
        while (dg < 360.0) { val q = iou(dg); if (q > bestIou) { bestIou = q; bestPhi = dg }; dg += 10.0 }
        var dg2 = bestPhi - 10.0
        val base = bestPhi
        while (dg2 <= base + 10.0) { val q = iou(dg2); if (q > bestIou) { bestIou = q; bestPhi = dg2 }; dg2 += 2.0 }
        if (bestIou < 0.3) return null
        val ph = Math.toRadians(bestPhi)
        return floatArrayOf(
            (bxc - rs * cm * cos(ph)).toFloat(), (byc - rs * cm * sin(ph)).toFloat(), rs.toFloat(),
            cos(ph).toFloat(), sin(ph).toFloat()
        )
    }
}

/** One analyzer for the camera: Moon search while Solar-Mode is on, text search otherwise. */
class CameraAnalyzer(
    private val moonOn: () -> Boolean,
    private val onMoon: (MoonHit?) -> Unit,
    onText: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val text = TextScanAnalyzer(onText)
    private val main = Handler(Looper.getMainLooper())
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        if (!moonOn()) {
            text.analyze(image)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - last < 8) {
            image.close()
            return
        }
        last = now
        val hit = try { MoonDetect.find(image, MoonLive.illum) } catch (e: Exception) { null }
        image.close()
        main.post { onMoon(hit) }
    }
}

/** Fixes ISO 100 and 1/4000 s while the Moon is in view (otherwise it burns out to a white blob). */
@OptIn(ExperimentalCamera2Interop::class)
fun applyMoonExposure(controller: LifecycleCameraController, on: Boolean, r: ProRanges?) {
    val cc = controller.cameraControl ?: return
    val b = CaptureRequestOptions.Builder()
    if (on) {
        val iso = if (r != null) 100.coerceIn(r.isoMin, r.isoMax) else 100
        val ns = if (r != null) 250_000L.coerceIn(r.expMin, r.expMax) else 250_000L
        b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, ns)
    }
    Camera2CameraControl.from(cc).setCaptureRequestOptions(b.build())
}

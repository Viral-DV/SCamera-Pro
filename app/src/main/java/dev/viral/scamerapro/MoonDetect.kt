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
import kotlin.math.max

/** Moon found in the frame: centre and radius, 0..1 relative to the visible (upright) picture width/height. */
data class MoonHit(val cx: Float, val cy: Float, val r: Float)

object MoonDetect {
    /** Finds the biggest bright blob on a dark background (cheap: 64-wide grid on the Y plane). */
    fun find(image: ImageProxy): MoonHit? {
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
        if (mean > 110) return null // not a dark sky
        val thr = max(170, mean + 80)

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
        if (bestCount.toFloat() / (bw * bh) < 0.2f) return null

        val u = (bSx.toFloat() / bestCount + 0.5f) / gw
        val v = (bSy.toFloat() / bestCount + 0.5f) / gh
        val rCells = max(bw, bh) / 2f
        val rot = image.imageInfo.rotationDegrees
        return when (rot) {
            90 -> MoonHit(1f - v, u, rCells / gh)
            270 -> MoonHit(v, 1f - u, rCells / gh)
            180 -> MoonHit(1f - u, 1f - v, rCells / gw)
            else -> MoonHit(u, v, rCells / gw)
        }
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
        if (now - last < 90) {
            image.close()
            return
        }
        last = now
        val hit = try { MoonDetect.find(image) } catch (e: Exception) { null }
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

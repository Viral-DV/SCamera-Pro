package dev.viral.scamerapro

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min

private val busy = AtomicBoolean(false)

private fun lumOf(c: Int) = (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8

/** Takes [count] shots in a row and stacks them: less noise, a little more detail, then sharpening. */
fun burstCapture(
    activity: ComponentActivity,
    controller: LifecycleCameraController,
    factor: Float,
    count: Int,
    onDone: (Uri?) -> Unit
) {
    if (!busy.compareAndSet(false, true)) return
    val shots = ArrayList<ByteArray>()
    val rots = ArrayList<Int>()
    fun finish(uri: Uri?) {
        busy.set(false)
        activity.runOnUiThread { onDone(uri) }
    }
    fun process() {
        Thread {
            val uri = try {
                if (shots.size < 2) null else stack(activity, shots, rots[0], factor)
            } catch (e: Throwable) {
                Log.e("SCameraPro", "stack failed", e)
                null
            }
            finish(uri)
        }.start()
    }
    fun next() {
        if (shots.size >= count) {
            process()
            return
        }
        controller.takePicture(
            ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buf = image.planes[0].buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    rots.add(image.imageInfo.rotationDegrees)
                    image.close()
                    shots.add(bytes)
                    next()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("SCameraPro", "burst shot failed", exception)
                    if (shots.size >= 2) process() else finish(null)
                }
            }
        )
    }
    next()
}

private fun stack(ctx: Context, shots: List<ByteArray>, rot: Int, f: Float): Uri? {
    val n = shots.size
    val d0 = BitmapRegionDecoder.newInstance(shots[0], 0, shots[0].size, false) ?: return null
    val w = d0.width
    val h = d0.height
    d0.recycle()
    val cw = (w / f).toInt().coerceAtLeast(64)
    val ch = (h / f).toInt().coerceAtLeast(64)
    val rw = min(w, (cw * 1.12f).toInt())
    val rh = min(h, (ch * 1.12f).toInt())
    val rl = (w - rw) / 2
    val rt = (h - rh) / 2
    val mx = (rw - cw) / 2
    val my = (rh - ch) / 2
    val frames = shots.map { bytes ->
        val dec = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) ?: return null
        val b = dec.decodeRegion(Rect(rl, rt, rl + rw, rt + rh), null)
        dec.recycle()
        b
    }
    // align every frame to the first one (brightness, 1/4 size, whole-frame shift)
    val sw = rw / 4
    val sh = rh / 4
    fun small(b: Bitmap): IntArray {
        val s = Bitmap.createScaledBitmap(b, sw, sh, true)
        val p = IntArray(sw * sh)
        s.getPixels(p, 0, sw, 0, 0, sw, sh)
        return IntArray(p.size) { lumOf(p[it]) }
    }
    val refS = small(frames[0])
    val rr = 10
    val shifts = IntArray(n * 2)
    for (k in 1 until n) {
        val cur = small(frames[k])
        var best = Long.MAX_VALUE
        var bdx = 0
        var bdy = 0
        for (dy in -rr..rr) for (dx in -rr..rr) {
            var sad = 0L
            var y = rr
            while (y < sh - rr) {
                val ro = y * sw
                val co = (y + dy) * sw + dx
                var x = rr
                while (x < sw - rr) {
                    sad += abs(refS[ro + x] - cur[co + x])
                    x += 2
                }
                y += 2
            }
            if (sad < best) { best = sad; bdx = dx; bdy = dy }
        }
        shifts[2 * k] = bdx * 4
        shifts[2 * k + 1] = bdy * 4
    }
    // average aligned frames, skipping pixels that moved (no ghosts)
    val fp = frames.map { b -> IntArray(rw * rh).also { b.getPixels(it, 0, rw, 0, 0, rw, rh) } }
    val outPx = IntArray(cw * ch)
    for (y in 0 until ch) for (x in 0 until cw) {
        val rx = x + mx
        val ry = y + my
        val c0 = fp[0][ry * rw + rx]
        val l0 = lumOf(c0)
        var sr = (c0 shr 16) and 255
        var sg = (c0 shr 8) and 255
        var sb = c0 and 255
        var wsum = 1
        for (k in 1 until n) {
            val xx = (rx + shifts[2 * k]).coerceIn(0, rw - 1)
            val yy = (ry + shifts[2 * k + 1]).coerceIn(0, rh - 1)
            val c = fp[k][yy * rw + xx]
            if (abs(lumOf(c) - l0) < 36) {
                sr += (c shr 16) and 255
                sg += (c shr 8) and 255
                sb += c and 255
                wsum++
            }
        }
        outPx[y * cw + x] = (0xFF shl 24) or ((sr / wsum) shl 16) or ((sg / wsum) shl 8) or (sb / wsum)
    }
    var crop = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
    crop.setPixels(outPx, 0, cw, 0, 0, cw, ch)
    if (rot != 0) {
        val m = Matrix()
        m.postRotate(rot.toFloat())
        crop = Bitmap.createBitmap(crop, 0, 0, cw, ch, m, true)
    }
    val swap = rot == 90 || rot == 270
    val scaled = Bitmap.createScaledBitmap(crop, if (swap) h else w, if (swap) w else h, true)
    val out = if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true)
    sharpen(out, (0.7f + 0.05f * f).coerceAtMost(1.3f), if (f > 3.5f) 3 else 2)

    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "SCP_$stamp.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SCameraPro")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    ctx.contentResolver.openOutputStream(uri)?.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    return uri
}

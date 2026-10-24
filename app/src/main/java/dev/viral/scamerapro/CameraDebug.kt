package dev.viral.scamerapro

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import java.util.Locale

/** Text report of the cameras Android exposes to this app (to find ultrawide / 50 MP access). */
fun cameraReport(context: Context): String {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val sb = StringBuilder()
    sb.appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MODEL}")
    sb.appendLine()
    for (id in cm.cameraIdList) {
        try {
            val c = cm.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                else -> "external"
            }
            sb.appendLine("ID $id  ($facing)")
            val focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            if (focals != null) {
                sb.appendLine("  focal: " + focals.joinToString { String.format(Locale.US, "%.2f mm", it) })
            }
            val apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            if (apertures != null) {
                sb.appendLine("  aperture: " + apertures.joinToString { String.format(Locale.US, "f/%.1f", it) })
            }
            val px = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            if (px != null) sb.appendLine("  sensor: ${px.width}x${px.height}")
            if (Build.VERSION.SDK_INT >= 30) {
                val zr = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (zr != null) sb.appendLine("  zoom ratio: ${zr.lower} .. ${zr.upper}")
            }
            if (Build.VERSION.SDK_INT >= 28) {
                val phys = c.physicalCameraIds
                if (phys.isNotEmpty()) sb.appendLine("  physical ids: " + phys.joinToString())
            }
            if (Build.VERSION.SDK_INT >= 31) {
                val maxMap = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                val big = maxMap?.getOutputSizes(ImageFormat.JPEG)
                    ?.maxByOrNull { it.width.toLong() * it.height }
                sb.appendLine(
                    if (big != null) "  max-res JPEG: ${big.width}x${big.height}"
                    else "  max-res mode: not exposed"
                )
            }
            val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)
                ?.maxByOrNull { it.width.toLong() * it.height }
            if (sizes != null) sb.appendLine("  default max JPEG: ${sizes.width}x${sizes.height}")
        } catch (e: Exception) {
            sb.appendLine("ID $id: error ${e.message}")
        }
        sb.appendLine()
    }
    return sb.toString()
}

package dev.viral.scamerapro

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import kotlin.math.atan

enum class Lens { MAIN, ULTRA }

data class LensInfo(val mainId: String?, val ultraId: String?, val frontId: String?)

/** Finds the main and the ultrawide back cameras by field of view (the widest one is the ultrawide). */
fun discoverLenses(context: Context): LensInfo {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val backs = mutableListOf<Pair<String, Double>>()
    var front: String? = null
    for (id in cm.cameraIdList) {
        val c = cm.getCameraCharacteristics(id)
        val facing = c.get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            if (front == null) front = id
        } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            val f = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            val sz = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            if (f != null && sz != null && f > 0f) {
                backs += id to 2.0 * atan(sz.width / (2.0 * f))
            }
        }
    }
    val main = backs.minByOrNull { it.first.toIntOrNull() ?: 99 }
    val ultra = backs
        .filter { it.first != main?.first }
        .maxByOrNull { it.second }
        ?.takeIf { main != null && it.second > main.second * 1.3 }
    return LensInfo(main?.first, ultra?.first, front)
}

@OptIn(ExperimentalCamera2Interop::class)
fun selectorForId(id: String): CameraSelector =
    CameraSelector.Builder()
        .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == id } }
        .build()

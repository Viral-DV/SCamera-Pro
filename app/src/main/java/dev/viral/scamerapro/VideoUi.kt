package dev.viral.scamerapro

import android.annotation.SuppressLint
import android.content.ContentValues
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.MediaStore
import android.util.Range
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VideoRes(val label: String, val quality: Quality) {
    UHD("UHD", Quality.UHD),
    FHD("FHD", Quality.FHD),
    HD("HD", Quality.HD)
}

@OptIn(ExperimentalCamera2Interop::class)
fun applyFps(controller: LifecycleCameraController, fps: Int) {
    val cc = controller.cameraControl ?: return
    val options = CaptureRequestOptions.Builder()
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
        .build()
    Camera2CameraControl.from(cc).setCaptureRequestOptions(options)
}

@SuppressLint("MissingPermission")
fun startVideo(
    activity: ComponentActivity,
    controller: LifecycleCameraController,
    withAudio: Boolean,
    onTick: (Int) -> Unit,
    onDone: (Uri?) -> Unit
): Recording {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "SCV_$stamp.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SCameraPro")
    }
    val opts = MediaStoreOutputOptions.Builder(
        activity.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(values).build()

    var startSec = 0L
    val pending = controller.startRecording(
        opts,
        if (withAudio) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED,
        ContextCompat.getMainExecutor(activity)
    ) { event ->
        when (event) {
            is VideoRecordEvent.Start -> {
                startSec = System.currentTimeMillis()
            }
            is VideoRecordEvent.Status -> {
                val elapsed = ((System.currentTimeMillis() - startSec) / 1000).toInt()
                onTick(elapsed)
            }
            is VideoRecordEvent.Finalize -> {
                if (!event.hasError()) {
                    onDone(event.outputResults.outputUri)
                } else {
                    onDone(null)
                }
            }
        }
    }
    return pending
}

@Composable
fun VideoSizePanel(
    res: VideoRes,
    fps: Int,
    frontCam: Boolean,
    onRes: (VideoRes) -> Unit,
    onFps: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoRes.values().forEach { r ->
            Text(
                r.label,
                color = if (res == r) Accent else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onRes(r); Vibro.click(modifier.javaClass.cast(null) ?: return@clickable) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.size(1.dp, 16.dp).background(Color(0x44FFFFFF)))
        listOf(30, 60).forEach { f ->
            val enabled = !frontCam && res != VideoRes.UHD || f == 30
            Text(
                "${f}FPS",
                color = if (fps == f) Accent else if (enabled) Color.White else Color.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { onFps(f) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

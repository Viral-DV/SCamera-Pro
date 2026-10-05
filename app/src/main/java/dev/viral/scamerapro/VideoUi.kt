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
import androidx.camera.video.AudioConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
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
import androidx.compose.ui.draw.alpha
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

/** Starts recording into Movies/SCameraPro; [onDone] gets the saved video (or null on error). */
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
        put(MediaStore.Video.Media.DISPLAY_NAME, "SCV_$stamp")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SCameraPro")
    }
    val opts = MediaStoreOutputOptions.Builder(
        activity.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(values).build()

    // AudioConfig управляет записью звука (в CameraX 1.4.x нет флага на контроллере)
    val audioConfig = AudioConfig.create(withAudio)

    return controller.startRecording(
        opts,
        audioConfig,
        ContextCompat.getMainExecutor(activity)
    ) { ev: VideoRecordEvent ->
        when (ev) {
            is VideoRecordEvent.Status ->
                onTick((ev.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt())
            is VideoRecordEvent.Finalize ->
                onDone(if (ev.hasError()) null else ev.outputResults.outputUri)
            else -> {}
        }
    }
}

/** Best effort: asks the camera for a fixed frame rate (30 or 60). */
@OptIn(ExperimentalCamera2Interop::class)
fun applyFps(controller: LifecycleCameraController, fps: Int) {
    val cc = controller.cameraControl ?: return
    val b = CaptureRequestOptions.Builder()
    b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
    Camera2CameraControl.from(cc).setCaptureRequestOptions(b.build())
}

/** "Video size" popup: UHD / FHD / HD and 60 / 30 fps (UHD and the front camera are 30 only). */
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
    val fps60 = res != VideoRes.UHD && !frontCam
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glass(RoundedCornerShape(26.dp), tint = Color(0x80202020))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Размер видео", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { CloseIcon(Modifier.size(11.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Размер", color = Muted, fontSize = 13.sp, modifier = Modifier.widthIn(min = 64.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoRes.values().forEach { r -> Chip(r.label, r == res, true) { onRes(r) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FPS", color = Muted, fontSize = 13.sp, modifier = Modifier.widthIn(min = 64.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("60", fps == 60, fps60) { onFps(60) }
                Chip("30", fps == 30, true) { onFps(30) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (frontCam) "Фронтальная камера пишет только 30 fps"
            else if (res == VideoRes.UHD) "UHD доступен только в 30 fps"
            else "Full HD с плавной частотой кадров",
            color = Color(0xCCFFFFFF), fontSize = 12.sp
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) PillSelected else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Accent else Color.White,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

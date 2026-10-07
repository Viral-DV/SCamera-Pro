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
import androidx.camera.video.FileOutputOptions
import android.content.Context
import android.os.Environment
import java.io.File
import androidx.camera.view.video.AudioConfig
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

/** Records into the app's own folder, then copies the finished video to Movies/SCameraPro. */
@SuppressLint("MissingPermission")
fun startVideo(
    activity: ComponentActivity,
    controller: LifecycleCameraController,
    withAudio: Boolean,
    onTick: (Int) -> Unit,
    onDone: (Uri?, Int) -> Unit
): Recording {
    val name = "SCV_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
    val dir = activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: activity.filesDir
    val file = File(dir, name)
    val opts = FileOutputOptions.Builder(file).build()
    return controller.startRecording(
        opts,
        AudioConfig.create(withAudio),
        ContextCompat.getMainExecutor(activity)
    ) { ev ->
        when (ev) {
            is VideoRecordEvent.Status ->
                onTick((ev.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt())
            is VideoRecordEvent.Finalize -> {
                if (ev.hasError()) {
                    file.delete()
                    onDone(null, ev.error)
                } else {
                    Thread {
                        val uri = saveToGallery(activity, file, name)
                        activity.runOnUiThread { onDone(uri, if (uri == null) -1 else 0) }
                    }.start()
                }
            }
            else -> {}
        }
    }
}

private fun saveToGallery(ctx: Context, file: File, name: String): Uri? = try {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SCameraPro")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    if (uri != null) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        ctx.contentResolver.update(uri, done, null, null)
        file.delete()
    }
    uri
} catch (e: Exception) {
    null
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
    val fps60 = false
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
            else "60 fps пока недоступно, запись в 30 fps",
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

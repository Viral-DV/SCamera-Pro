package dev.viral.scamerapro

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Pill = Color(0xFF2B2B2B)
private val PillSelected = Color(0xFF4A4A4A)

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    DisposableEffect(controller) {
        controller.bindToLifecycle(activity)
        onDispose { controller.unbind() }
    }

    val zoom by controller.zoomState.observeAsState()
    var flashOn by remember { mutableStateOf(false) }
    var backCamera by remember { mutableStateOf(true) }
    var mode by remember { mutableIntStateOf(1) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(lastUri) {
        val uri = lastUri ?: return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(256, 256), null).asImageBitmap()
            } catch (e: Exception) {
                Log.e("SCameraPro", "thumbnail failed", e)
                null
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(end = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚡",
                fontSize = 22.sp,
                color = Color.White.copy(alpha = if (flashOn) 1f else 0.4f),
                modifier = Modifier.clickable {
                    flashOn = !flashOn
                    controller.imageCaptureFlashMode =
                        if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                }
            )
            Text("12M", color = Color.White, fontSize = 17.sp)
        }

        // Viewfinder 3:4
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = controller
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }
            )
        }

        // Bottom area
        Column(
            Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            ZoomPill(zoom) { r -> controller.setZoomRatio(r) }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery thumbnail
                Box(
                    Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFF333333), CircleShape)
                        .clickable(enabled = lastUri != null) {
                            lastUri?.let { u ->
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(u, "image/jpeg")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            }
                        }
                ) {
                    thumb?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Shutter {
                    takePhoto(activity, controller) { uri -> lastUri = uri }
                }

                // Flip camera
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Pill)
                        .clickable {
                            backCamera = !backCamera
                            controller.cameraSelector =
                                if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA
                                else CameraSelector.DEFAULT_FRONT_CAMERA
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⟳", color = Color.White, fontSize = 30.sp)
                }
            }

            // Mode carousel (only "Photo" works for now)
            val modes = listOf("ПОРТРЕТ", "ФОТОГРАФИЯ", "ВИДЕОЗАПИСЬ", "ЛУНА")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                modes.forEachIndexed { i, label ->
                    Text(
                        label,
                        fontSize = 15.sp,
                        color = if (i == mode) Color.White else Color(0xFF8A8A8A),
                        modifier = Modifier.clickable { mode = i }
                    )
                }
            }
        }
    }
}

@Composable
private fun Shutter(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.88f else 1f, label = "shutter")
    Box(
        Modifier
            .size(90.dp)
            .scale(s)
            .clip(CircleShape)
            .background(Color(0xFFF8F8F5))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    )
}

@Composable
private fun ZoomPill(zoom: ZoomState?, onSelect: (Float) -> Unit) {
    if (zoom == null) return
    val min = zoom.minZoomRatio
    val max = zoom.maxZoomRatio
    val cur = zoom.zoomRatio
    val presets = listOf(0.6f, 1f, 2f, 4f, 10f).filter { it >= min - 0.05f && it <= max + 0.05f }
    if (presets.isEmpty()) return
    val sel = presets.indices.minByOrNull { abs(presets[it] - cur) } ?: 0

    Row(
        Modifier.clip(RoundedCornerShape(24.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEachIndexed { i, p ->
            val selected = i == sel
            Box(
                Modifier
                    .height(46.dp)
                    .widthIn(min = 46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(if (selected) PillSelected else Pill)
                    .clickable { onSelect(p.coerceIn(min, max)) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (selected) fmtZoom(cur) + "×" else fmtZoom(p),
                    color = Color.White,
                    fontSize = 17.sp
                )
            }
        }
    }
}

private fun fmtZoom(v: Float): String {
    val s = if (abs(v - v.roundToInt()) < 0.05f) v.roundToInt().toString()
    else String.format(Locale.US, "%.1f", v)
    return if (s.startsWith("0.")) s.removePrefix("0") else s
}

private fun takePhoto(activity: ComponentActivity, controller: LifecycleCameraController, onSaved: (Uri) -> Unit) {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "SCP_$stamp.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SCameraPro")
    }
    val options = ImageCapture.OutputFileOptions.Builder(
        activity.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()

    controller.takePicture(
        options,
        ContextCompat.getMainExecutor(activity),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let(onSaved)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("SCameraPro", "capture failed", exception)
            }
        }
    )
}

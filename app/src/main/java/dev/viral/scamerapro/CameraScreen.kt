package dev.viral.scamerapro

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun currentZoom(c: LifecycleCameraController): Float = c.zoomState.value?.zoomRatio ?: 1f

private fun applyZoom(c: LifecycleCameraController, ratio: Float) {
    val zs = c.zoomState.value ?: return
    c.setZoomRatio(ratio.coerceIn(zs.minZoomRatio, zs.maxZoomRatio))
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val density = LocalDensity.current

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            // We handle pinch and tap ourselves (to draw the focus ring and the dial)
            isPinchToZoomEnabled = false
            isTapToFocusEnabled = false
        }
    }
    DisposableEffect(controller) {
        controller.bindToLifecycle(activity)
        onDispose { controller.unbind() }
    }

    val zoom by controller.zoomState.observeAsState()
    val zoomRatio = zoom?.zoomRatio ?: 1f

    var flashOn by remember { mutableStateOf(false) }
    var backCamera by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(Mode.PHOTO) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var showDebug by remember { mutableStateOf(false) }

    // Zoom dial visibility
    var dialVisible by remember { mutableStateOf(false) }
    var dialTick by remember { mutableIntStateOf(0) }
    val touchDial = { dialVisible = true; dialTick++ }
    LaunchedEffect(dialTick) {
        if (dialTick > 0) {
            delay(1800)
            dialVisible = false
        }
    }

    // Toast pill
    var toast by remember { mutableStateOf<String?>(null) }
    var toastN by remember { mutableIntStateOf(0) }
    val showToast = { m: String -> toast = m; toastN++ }
    LaunchedEffect(toastN) {
        if (toastN > 0) {
            delay(1800)
            toast = null
        }
    }
    LaunchedEffect(mode) {
        when (mode) {
            Mode.GRAND -> showToast("GRAND появится на этапе 5")
            Mode.VIDEO -> showToast("Видео появится на этапе 4")
            else -> {}
        }
    }

    // Tap-to-focus ring and exposure slider
    val pv = remember { arrayOfNulls<PreviewView>(1) }
    var focusPt by remember { mutableStateOf<Offset?>(null) }
    var focusN by remember { mutableIntStateOf(0) }
    var focusVisible by remember { mutableStateOf(false) }
    var evIndex by remember { mutableIntStateOf(0) }
    var vfSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(focusN) {
        if (focusN > 0) {
            focusVisible = true
            delay(2600)
            focusVisible = false
        }
    }
    val focusAlpha by animateFloatAsState(if (focusVisible) 1f else 0f, label = "focus")
    val evRange = controller.cameraInfo?.exposureState?.exposureCompensationRange

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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // ---- Top bar ----
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
                Text(
                    "12M",
                    color = Color.White,
                    fontSize = 17.sp,
                    modifier = Modifier.clickable { showToast("50M появится на этапе 3") }
                )
            }

            // ---- Viewfinder 3:4 ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .onSizeChanged { vfSize = it }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            this.controller = controller
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            pv[0] = this
                        }
                    }
                )

                // Gesture layer: tap = focus, pinch = zoom
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                focusPt = o
                                focusN++
                                pv[0]?.let { view ->
                                    val p = view.meteringPointFactory.createPoint(o.x, o.y)
                                    controller.cameraControl?.startFocusAndMetering(
                                        FocusMeteringAction.Builder(p).build()
                                    )
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, z, _ ->
                                if (z != 1f) {
                                    applyZoom(controller, currentZoom(controller) * z)
                                    touchDial()
                                }
                            }
                        }
                )

                // Focus ring
                focusPt?.let { p ->
                    if (focusAlpha > 0.01f) {
                        val r = with(density) { 32.dp.toPx() }
                        Canvas(
                            Modifier
                                .offset { IntOffset((p.x - r).roundToInt(), (p.y - r).roundToInt()) }
                                .size(64.dp)
                                .alpha(focusAlpha)
                        ) {
                            drawCircle(
                                Accent,
                                radius = size.minDimension / 2f - 2.dp.toPx(),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                        if (evRange != null && evRange.upper > evRange.lower) {
                            val sliderW = with(density) { 40.dp.toPx() }
                            val sliderH = with(density) { 150.dp.toPx() }
                            val gap = with(density) { 44.dp.toPx() }
                            val rawX = if (p.x + gap + sliderW < vfSize.width) p.x + gap else p.x - gap - sliderW
                            val x = rawX.coerceIn(0f, (vfSize.width - sliderW).coerceAtLeast(0f))
                            val y = (p.y - sliderH / 2f).coerceIn(0f, (vfSize.height - sliderH).coerceAtLeast(0f))
                            EvSlider(
                                lo = evRange.lower,
                                hi = evRange.upper,
                                value = evIndex,
                                alpha = focusAlpha,
                                modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                                onDelta = { d ->
                                    val r2 = controller.cameraInfo?.exposureState?.exposureCompensationRange
                                    if (r2 != null) {
                                        evIndex = (evIndex + d).coerceIn(r2.lower, r2.upper)
                                        controller.cameraControl?.setExposureCompensationIndex(evIndex)
                                        focusN++
                                    }
                                }
                            )
                        }
                    }
                }

                // Zoom value while the dial is up
                if (dialVisible) {
                    Text(
                        fmtZoom(zoomRatio) + " x",
                        color = Accent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                    )
                }

                // Toast pill
                toast?.let { t ->
                    Text(
                        t,
                        color = Accent,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 28.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xAA2B2B2B))
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    )
                }
            }

            // ---- Bottom area ----
            Column(
                Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (dialVisible) {
                        ZoomDial(
                            zoom = zoomRatio,
                            minZoom = zoom?.minZoomRatio ?: 1f,
                            maxZoom = zoom?.maxZoomRatio ?: 1f,
                            onZoom = { applyZoom(controller, it); touchDial() }
                        )
                    } else {
                        ZoomPill(
                            zoom = zoom,
                            presets = if (mode == Mode.PORTRAIT) listOf(1f, 2f)
                            else listOf(0.6f, 1f, 2f, 4f, 10f),
                            onSelect = { applyZoom(controller, it) },
                            onLongPress = { touchDial() }
                        )
                        DotsButton(
                            Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)
                        ) { showDebug = true }
                    }
                }

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

                    Shutter(isVideo = mode == Mode.VIDEO) {
                        if (mode == Mode.VIDEO) {
                            showToast("Запись видео появится на этапе 4")
                        } else {
                            takePhoto(activity, controller) { uri -> lastUri = uri }
                        }
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

                ModeCarousel(selected = mode, onSelect = { mode = it })
            }
        }

        // "More" sheet over the lower half of the screen
        if (mode == Mode.MORE) {
            MoreSheet(
                onPick = { name -> showToast("$name появится позже") },
                onEdit = { showToast("Редактор режимов появится позже") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp)
            )
        }

        if (showDebug) {
            val report = remember { cameraReport(context) }
            AlertDialog(
                onDismissRequest = { showDebug = false },
                confirmButton = { TextButton(onClick = { showDebug = false }) { Text("Закрыть") } },
                title = { Text("Диагностика камер") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(report, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }
    }
}

@Composable
private fun EvSlider(
    lo: Int,
    hi: Int,
    value: Int,
    alpha: Float,
    modifier: Modifier,
    onDelta: (Int) -> Unit
) {
    val acc = remember { floatArrayOf(0f) }
    Box(
        modifier
            .size(40.dp, 150.dp)
            .alpha(alpha)
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { acc[0] = 0f }) { change, dy ->
                    change.consume()
                    acc[0] += -dy / 28f
                    val steps = acc[0].toInt()
                    if (steps != 0) {
                        acc[0] -= steps
                        onDelta(steps)
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width / 2f
            val top = 28.dp.toPx()
            drawLine(
                Color.White.copy(alpha = 0.6f),
                Offset(x, top),
                Offset(x, size.height),
                strokeWidth = 2.dp.toPx()
            )
            val f = (value - lo).toFloat() / (hi - lo).coerceAtLeast(1)
            val y = top + (1f - f) * (size.height - top)
            drawCircle(Accent, 7.dp.toPx(), Offset(x, y))
        }
        Text("☀", color = Accent, fontSize = 18.sp, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun Shutter(isVideo: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.88f else 1f, label = "shutter")
    Box(
        Modifier
            .size(90.dp)
            .scale(s)
            .clip(CircleShape)
            .background(Color(0xFFF8F8F5))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isVideo) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE2603F)))
        }
    }
}

@Composable
private fun DotsButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(Pill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(18.dp)) {
            val r = 3.dp.toPx()
            for (x in listOf(size.width * 0.2f, size.width * 0.8f)) {
                for (y in listOf(size.height * 0.2f, size.height * 0.8f)) {
                    drawCircle(Color.White, r, Offset(x, y))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomPill(
    zoom: ZoomState?,
    presets: List<Float>,
    onSelect: (Float) -> Unit,
    onLongPress: () -> Unit
) {
    if (zoom == null) return
    val min = zoom.minZoomRatio
    val max = zoom.maxZoomRatio
    val cur = zoom.zoomRatio
    val list = presets.filter { it >= min - 0.05f && it <= max + 0.05f }
    if (list.isEmpty()) return
    val sel = list.indices.minByOrNull { abs(list[it] - cur) } ?: 0

    Row(
        Modifier.clip(RoundedCornerShape(30.dp)).background(Pill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        list.forEachIndexed { i, p ->
            val selected = i == sel
            Box(
                Modifier
                    .height(46.dp)
                    .widthIn(min = 46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(if (selected) PillSelected else Color.Transparent)
                    .combinedClickable(
                        onClick = { onSelect(p.coerceIn(min, max)) },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (selected) fmtZoom(cur) + "×" else fmtZoom(p),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
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

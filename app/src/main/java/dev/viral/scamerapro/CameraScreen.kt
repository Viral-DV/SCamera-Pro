package dev.viral.scamerapro

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class RowState { PILL, DIAL, QUICK }

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
    val scope = rememberCoroutineScope()

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            // We handle pinch and tap ourselves (focus ring, zoom dial)
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
    var ratio by remember { mutableStateOf(FrameRatio.R34) }
    var timerSec by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var quickOpen by remember { mutableStateOf(false) }
    var flipRot by remember { mutableFloatStateOf(0f) }
    val flipAngle by animateFloatAsState(flipRot, tween(450), label = "flip")

    // ---- Zoom dial visibility ----
    var dialVisible by remember { mutableStateOf(false) }
    var dialTick by remember { mutableIntStateOf(0) }
    val touchDial = { dialVisible = true; dialTick++ }
    LaunchedEffect(dialTick) {
        if (dialTick > 0) {
            delay(2000)
            dialVisible = false
        }
    }
    val labelAlpha by animateFloatAsState(if (dialVisible) 1f else 0f, tween(180), label = "zoomLabel")

    // ---- Smooth zoom to a preset ----
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    val animateZoomTo: (Float) -> Unit = { target ->
        zoomJob?.cancel()
        zoomJob = scope.launch {
            val zs = controller.zoomState.value
            if (zs != null) {
                val start = zs.zoomRatio
                val end = target.coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                val steps = 14
                for (i in 1..steps) {
                    val t = i / steps.toFloat()
                    val e = t * t * (3f - 2f * t)
                    applyZoom(controller, exp(ln(start) + (ln(end) - ln(start)) * e))
                    delay(16)
                }
            }
        }
    }
    val dialK = with(density) { 220.dp.toPx() }

    // ---- Toast pill ----
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

    // ---- Focus ring / exposure ----
    val pv = remember { arrayOfNulls<PreviewView>(1) }
    var focusPt by remember { mutableStateOf<Offset?>(null) }
    var focusN by remember { mutableIntStateOf(0) }
    var focusVisible by remember { mutableStateOf(false) }
    var focusLocked by remember { mutableStateOf(false) }
    var evFloat by remember { mutableFloatStateOf(0f) }
    var vfSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(focusN) {
        if (focusN > 0) {
            focusVisible = true
            if (!focusLocked) {
                delay(2800)
                focusVisible = false
            }
        }
    }
    val focusAlpha by animateFloatAsState(
        if (focusVisible || focusLocked) 1f else 0f, tween(220), label = "focusAlpha"
    )
    val evRange = controller.cameraInfo?.exposureState?.exposureCompensationRange
    val travelPx = with(density) { 48.dp.toPx() }

    // ---- Glass (frosted pills) ----
    val glass = remember { mutableStateOf(GlassData()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            if (!GLASS_BLUR) continue
            val tv = findTextureView(pv[0])
            val r = glass.value.rect
            if (tv != null && tv.isAvailable && r.width > 0f && r.height > 0f) {
                val bw = 36
                val bh = max(2, (bw * r.height / r.width).roundToInt())
                val b = tv.getBitmap(bw, bh)
                if (b != null) glass.value = glass.value.copy(bitmap = b.asImageBitmap())
            }
        }
    }

    LaunchedEffect(lastUri) {
        val uri = lastUri ?: return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null).asImageBitmap()
            } catch (e: Exception) {
                Log.e("SCameraPro", "thumbnail failed", e)
                null
            }
        }
    }

    // ---- Shutter logic (with self-timer) ----
    val doCapture = { takePhoto(activity, controller) { uri -> lastUri = uri } }
    val onShutter: () -> Unit = {
        if (mode == Mode.VIDEO) {
            showToast("Запись видео появится на этапе 4")
        } else if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdown = 0
        } else if (timerSec > 0) {
            countdownJob = scope.launch {
                for (i in timerSec downTo 1) {
                    countdown = i
                    delay(1000)
                }
                countdown = 0
                doCapture()
            }
        } else {
            doCapture()
        }
    }

    val rowState = when {
        quickOpen -> RowState.QUICK
        dialVisible -> RowState.DIAL
        else -> RowState.PILL
    }

    CompositionLocalProvider(LocalGlass provides glass) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()
            val cutoutTop = WindowInsets.displayCutout.getTop(density).toFloat()
            val topBarH = max(cutoutTop, with(density) { 24.dp.toPx() }) + with(density) { 52.dp.toPx() }
            val previewTop = if (ratio == FrameRatio.FULL) 0f else topBarH
            val rawH = when (ratio) {
                FrameRatio.R34 -> screenW * 4f / 3f
                FrameRatio.R916 -> screenW * 16f / 9f
                FrameRatio.R11 -> screenW
                FrameRatio.FULL -> screenH
            }
            val previewH = min(rawH, screenH - previewTop)
            val overlayUi = ratio == FrameRatio.FULL || ratio == FrameRatio.R916

            // ===== Viewfinder =====
            Box(
                Modifier
                    .offset { IntOffset(0, previewTop.roundToInt()) }
                    .size(with(density) { screenW.toDp() }, with(density) { previewH.toDp() })
                    .onSizeChanged { vfSize = it }
                    .onGloballyPositioned { glass.value = glass.value.copy(rect = it.boundsInRoot()) }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            // COMPATIBLE = TextureView, needed for the frosted-glass snapshots
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            this.controller = controller
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            pv[0] = this
                        }
                    }
                )

                // Gesture layer: tap = focus, long press = lock AE/AF, pinch = zoom
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { o ->
                                    focusPt = o
                                    focusLocked = true
                                    focusN++
                                    pv[0]?.let { view ->
                                        val p = view.meteringPointFactory.createPoint(o.x, o.y)
                                        controller.cameraControl?.startFocusAndMetering(
                                            FocusMeteringAction.Builder(p).disableAutoCancel().build()
                                        )
                                    }
                                    showToast("Фокус и экспозиция заблокированы")
                                },
                                onTap = { o ->
                                    focusLocked = false
                                    focusPt = o
                                    focusN++
                                    pv[0]?.let { view ->
                                        val p = view.meteringPointFactory.createPoint(o.x, o.y)
                                        controller.cameraControl?.startFocusAndMetering(
                                            FocusMeteringAction.Builder(p).build()
                                        )
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, z, _ ->
                                if (z != 1f) {
                                    zoomJob?.cancel()
                                    applyZoom(controller, currentZoom(controller) * z)
                                    touchDial()
                                }
                            }
                        }
                )

                // Focus ring + exposure slider
                focusPt?.let { p ->
                    if (focusAlpha > 0.01f) {
                        val ringW = with(density) { 70.dp.toPx() }
                        val totalH = with(density) { (70 + 20 + 36).dp.toPx() }
                        val topPad = with(density) { 10.dp.toPx() }
                        val x = (p.x - ringW / 2f).coerceIn(0f, max(0f, vfSize.width - ringW))
                        val y = (p.y - ringW / 2f).coerceIn(topPad, max(topPad, vfSize.height - totalH))
                        val lo = evRange?.lower ?: 0
                        val hi = evRange?.upper ?: 0
                        FocusUi(
                            modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                            locked = focusLocked,
                            evValue = evFloat,
                            lo = lo,
                            hi = hi,
                            alpha = focusAlpha,
                            onEvDrag = { dx ->
                                val r2 = controller.cameraInfo?.exposureState?.exposureCompensationRange
                                if (r2 != null && r2.upper > r2.lower) {
                                    val before = evFloat.roundToInt()
                                    evFloat = (evFloat + dx * (r2.upper - r2.lower) / travelPx)
                                        .coerceIn(r2.lower.toFloat(), r2.upper.toFloat())
                                    val after = evFloat.roundToInt()
                                    if (after != before) controller.cameraControl?.setExposureCompensationIndex(after)
                                    focusN++
                                }
                            }
                        )
                    }
                }

                // Self-timer countdown
                if (countdown > 0) {
                    Text(
                        countdown.toString(),
                        color = Color.White,
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.align(Alignment.Center)
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
                            .glass(RoundedCornerShape(18.dp))
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    )
                }
            }

            // Soft scrim under the controls when the preview extends behind them
            if (overlayUi) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                )
            }

            // ===== Top bar =====
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(with(density) { topBarH.toDp() })
                    .then(
                        if (ratio == FrameRatio.FULL)
                            Modifier.background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                        else Modifier
                    )
            ) {
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoltIcon(
                        on = flashOn,
                        modifier = Modifier.size(26.dp).clickable {
                            flashOn = !flashOn
                            controller.imageCaptureFlashMode =
                                if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        }
                    )
                    Text(
                        "12M",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { showToast("50M недоступен: Samsung не открывает его сторонним приложениям") }
                    )
                }
            }

            // ===== Bottom controls =====
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Zoom value slot (always reserved, no layout jumps)
                Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        fmtZoom(zoomRatio) + " x",
                        color = Accent,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(labelAlpha)
                    )
                }

                // Pill / dial / quick panel share one slot
                Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = rowState,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                        label = "controlRow"
                    ) { st ->
                        when (st) {
                            RowState.PILL -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                ZoomPill(
                                    zoom = zoom,
                                    presets = if (mode == Mode.PORTRAIT) listOf(1f, 2f)
                                    else listOf(0.6f, 1f, 2f, 4f, 10f),
                                    onSelect = { animateZoomTo(it) },
                                    onSelectedTap = { touchDial() },
                                    onLongPress = { touchDial() },
                                    onDrag = { dx ->
                                        zoomJob?.cancel()
                                        applyZoom(controller, exp(ln(currentZoom(controller)) - dx / dialK))
                                        touchDial()
                                    }
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 24.dp)
                                        .size(54.dp)
                                        .glass(CircleShape)
                                        .clickable { quickOpen = true },
                                    contentAlignment = Alignment.Center
                                ) { DotsIcon(Modifier.size(20.dp)) }
                            }

                            RowState.DIAL -> ZoomDial(
                                zoom = zoomRatio,
                                minZoom = zoom?.minZoomRatio ?: 1f,
                                maxZoom = zoom?.maxZoomRatio ?: 1f,
                                onZoom = { zoomJob?.cancel(); applyZoom(controller, it); touchDial() }
                            )

                            RowState.QUICK -> QuickPanel(
                                flashOn = flashOn,
                                timerSec = timerSec,
                                ratio = ratio,
                                onSettings = { quickOpen = false; showDebug = true },
                                onFlash = {
                                    flashOn = !flashOn
                                    controller.imageCaptureFlashMode =
                                        if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                },
                                onTimer = {
                                    timerSec = when (timerSec) { 0 -> 2; 2 -> 5; 5 -> 10; else -> 0 }
                                },
                                onRatio = {
                                    val all = FrameRatio.values()
                                    ratio = all[(ratio.ordinal + 1) % all.size]
                                },
                                onRes = { showToast("50M недоступен на этом телефоне для сторонних приложений") },
                                onClose = { quickOpen = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

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
                            .border(1.dp, Color(0x55FFFFFF), CircleShape)
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

                    Shutter(isVideo = mode == Mode.VIDEO, onClick = onShutter)

                    // Flip camera (icon spins half a turn per tap)
                    Box(
                        Modifier
                            .size(72.dp)
                            .glass(CircleShape)
                            .clickable {
                                backCamera = !backCamera
                                flipRot += 180f
                                controller.cameraSelector =
                                    if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA
                                    else CameraSelector.DEFAULT_FRONT_CAMERA
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        FlipIcon(Modifier.size(34.dp).rotate(flipAngle))
                    }
                }

                Spacer(Modifier.height(8.dp))
                ModeCarousel(selected = mode, onSelect = { mode = it })
            }

            // ===== "More" sheet =====
            AnimatedVisibility(
                visible = mode == Mode.MORE,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 4 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 4 }
            ) {
                MoreSheet(
                    onPick = { name -> showToast("$name появится позже") },
                    onEdit = { showToast("Редактор режимов появится позже") },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 74.dp)
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
}

@Composable
private fun Shutter(isVideo: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.88f else 1f, tween(90), label = "shutter")
    val dot by animateDpAsState(if (isVideo) 38.dp else 0.dp, tween(220), label = "shutterDot")
    Box(
        Modifier
            .size(90.dp)
            .scale(s)
            .clip(CircleShape)
            .background(Color(0xFFF8F8F5))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (dot > 1.dp) {
            Box(Modifier.size(dot).clip(CircleShape).background(Color(0xFFE2603F)))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomPill(
    zoom: ZoomState?,
    presets: List<Float>,
    onSelect: (Float) -> Unit,
    onSelectedTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float) -> Unit
) {
    if (zoom == null) return
    val min = zoom.minZoomRatio
    val max = zoom.maxZoomRatio
    val cur = zoom.zoomRatio
    val list = presets.filter { it >= min - 0.05f && it <= max + 0.05f }
    if (list.isEmpty()) return
    val sel = list.indices.minByOrNull { abs(list[it] - cur) } ?: 0

    Row(
        Modifier
            .glass(RoundedCornerShape(31.dp))
            .padding(5.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    onDrag(dx)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        list.forEachIndexed { i, p ->
            val selected = i == sel
            val bg by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "pillBg")
            Box(
                Modifier
                    .height(50.dp)
                    .widthIn(min = 50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(PillSelected.copy(alpha = PillSelected.alpha * bg))
                    .combinedClickable(
                        onClick = { if (selected) onSelectedTap() else onSelect(p.coerceIn(min, max)) },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (selected) fmtZoom(cur) + "×" else fmtZoom(p),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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

package dev.viral.scamerapro

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class RowState { PILL, EXPANDED, QUICK, PRO_RULER, FRONT }

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

    // ---------- Solar-Mode (Moon Zoom) ----------
    var solarMode by remember { mutableStateOf(false) }

    // ---------- lenses ----------
    val lenses = remember { runCatching { discoverLenses(context) }.getOrDefault(LensInfo(null, null, null)) }
    var ultraAvail by remember { mutableStateOf(false) }
    var ultraChecked by remember { mutableStateOf(false) }
    var lens by remember { mutableStateOf(Lens.MAIN) }
    var lastSwitch by remember { mutableLongStateOf(0L) }
    var pendingRatio by remember { mutableFloatStateOf(1f) }
    var pendingN by remember { mutableIntStateOf(0) }
    var mainMax by remember { mutableFloatStateOf(30f) }

    // ---------- text scan ----------
    var textHits by remember { mutableIntStateOf(0) }
    var lastText by remember { mutableStateOf("") }
    var showText by remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            isPinchToZoomEnabled = false
            isTapToFocusEnabled = false
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            setImageAnalysisAnalyzer(
                analysisExecutor,
                TextScanAnalyzer { t ->
                    val found = t.trim().length >= 8
                    textHits = if (found) min(textHits + 1, 3) else 0
                    if (found) lastText = t.trim()
                }
            )
        }
    }
    DisposableEffect(controller) {
        controller.bindToLifecycle(activity)
        onDispose { controller.unbind() }
    }

    val zoom by controller.zoomState.observeAsState()
    val camRatio = zoom?.zoomRatio ?: 1f

    // Check ultrawide availability
    LaunchedEffect(zoom) {
        if (!ultraChecked && zoom != null) {
            ultraChecked = true
            val maxR = zoom?.maxZoomRatio ?: 10f
            mainMax = maxR
            if (lenses.ultraId != null) {
                try {
                    controller.cameraSelector = selectorForId(lenses.ultraId)
                    delay(120)
                    val uMax = controller.zoomState.value?.maxZoomRatio ?: 1f
                    if (uMax > 1.1f) ultraAvail = true
                } catch (_: Exception) {
                } finally {
                    try {
                        controller.cameraSelector =
                            if (lenses.mainId != null) selectorForId(lenses.mainId) else CameraSelector.DEFAULT_BACK_CAMERA
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    // ---------- general UI state ----------
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
    var flipping by remember { mutableStateOf(false) }
    var selfieGroup by remember { mutableStateOf(false) }
    var flipY by remember { mutableFloatStateOf(0f) }
    var flipBlur by remember { mutableFloatStateOf(0f) }
    val flashAnim = remember { Animatable(0f) }
    var videoRes by remember { mutableStateOf(VideoRes.FHD) }
    var videoFps by remember { mutableIntStateOf(60) }
    var showVideoSize by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recSec by remember { mutableIntStateOf(0) }
    val effFps = if (!backCamera || videoRes == VideoRes.UHD) 30 else videoFps
    var audioOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { audioOk = it }
    val flipAngle by animateFloatAsState(flipRot, spring(stiffness = 300f, dampingRatio = 0.75f), label = "flip")
    val controlsAlpha by animateFloatAsState(if (flipping) 0f else 1f, tween(150), label = "ctl")

    // ---------- toast ----------
    var toast by remember { mutableStateOf<String?>(null) }
    var toastN by remember { mutableIntStateOf(0) }
    val showToast = { m: String -> toast = m; toastN++ }
    LaunchedEffect(toastN) {
        if (toastN > 0) {
            delay(1800)
            toast = null
        }
    }

    // ---------- video logic ----------
    LaunchedEffect(mode == Mode.VIDEO) {
        val video = mode == Mode.VIDEO
        recording?.stop()
        recording = null
        controller.setEnabledUseCases(
            if (video) CameraController.VIDEO_CAPTURE
            else CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS
        )
        controller.enableTorch(video && flashOn)
        if (video && !audioOk) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(videoRes) {
        controller.videoCaptureQualitySelector =
            QualitySelector.from(videoRes.quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
    }
    LaunchedEffect(mode, videoFps, videoRes, backCamera) {
        delay(400)
        if (mode == Mode.VIDEO) applyFps(controller, effFps)
    }
    val toggleFlash: () -> Unit = {
        Vibro.click(context)
        flashOn = !flashOn
        if (mode == Mode.VIDEO) {
            controller.enableTorch(flashOn)
        } else {
            controller.imageCaptureFlashMode =
                if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        }
    }

    // ---------- 30X Zoom & Solar logic ----------
    var simulatedZoomRatio by remember { mutableFloatStateOf(1f) }

    val displayZoom = if (solarMode) {
        simulatedZoomRatio
    } else {
        if (lens == Lens.ULTRA && backCamera) camRatio * 0.6f else camRatio
    }

    val zMinDisp = if (ultraAvail && backCamera) 0.6f else (zoom?.minZoomRatio ?: 1f)
    val zMaxDisp = if (solarMode) 30f else if (lens == Lens.MAIN || !backCamera) (zoom?.maxZoomRatio ?: mainMax) else mainMax

    LaunchedEffect(pendingN) {
        if (pendingN > 0) {
            delay(350)
            applyZoom(controller, pendingRatio)
        }
    }

    val switchLens: (Lens, Float) -> Unit = { to, camR ->
        val now = SystemClock.elapsedRealtime()
        val id = if (to == Lens.ULTRA) lenses.ultraId else lenses.mainId
        if (now - lastSwitch > 800 && (to == Lens.MAIN || id != null)) {
            lastSwitch = now
            try {
                controller.cameraSelector =
                    if (id != null) selectorForId(id) else CameraSelector.DEFAULT_BACK_CAMERA
                lens = to
                pendingRatio = camR
                pendingN++
                Vibro.click(context)
            } catch (e: Exception) {
                Log.e("SCameraPro", "lens switch failed", e)
                showToast("Не удалось переключить объектив")
                if (to == Lens.ULTRA) ultraAvail = false
            }
        }
    }

    fun liveDisplay(): Float = displayZoom

    val setDisplayZoom: (Float) -> Unit = { r0 ->
        val r = r0.coerceIn(zMinDisp, zMaxDisp)
        if (solarMode) {
            simulatedZoomRatio = r
            val hardwareMax = zoom?.maxZoomRatio ?: 10f
            val hwZoom = r.coerceIn(zMinDisp, hardwareMax)
            applyZoom(controller, hwZoom)
        } else {
            simulatedZoomRatio = r
            if (!backCamera) {
                applyZoom(controller, r)
            } else if (lens == Lens.ULTRA) {
                if (r >= 1f) switchLens(Lens.MAIN, 1f) else applyZoom(controller, r / 0.6f)
            } else {
                if (r < 1f && ultraAvail) switchLens(Lens.ULTRA, (r / 0.6f).coerceAtLeast(1f))
                else applyZoom(controller, r)
            }
        }
    }

    // zoom panel
    var zoomExpanded by remember { mutableStateOf(false) }
    var expandTick by remember { mutableIntStateOf(0) }
    val touchZoom = { zoomExpanded = true; expandTick++ }
    LaunchedEffect(expandTick) {
        if (expandTick > 0) {
            delay(4000)
            zoomExpanded = false
        }
    }
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    val animateZoomTo: (Float) -> Unit = { target ->
        zoomJob?.cancel()
        zoomJob = scope.launch {
            val start = liveDisplay()
            val end = target.coerceIn(zMinDisp, zMaxDisp)
            val steps = 14
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val e = t * t * (3f - 2f * t)
                setDisplayZoom(exp(ln(start) + (ln(end) - ln(start)) * e))
                delay(16)
            }
        }
    }
    val onPreset: (Float) -> Unit = { p ->
        Vibro.click(context)
        zoomJob?.cancel()
        if (p < 1f) {
            if (lens != Lens.ULTRA) switchLens(Lens.ULTRA, 1f) else applyZoom(controller, 1f)
        } else if (lens == Lens.ULTRA && backCamera) {
            switchLens(Lens.MAIN, p)
        } else {
            animateZoomTo(p)
        }
    }
    val dialK = with(density) { 220.dp.toPx() }

    // Pro state
    val pro = remember { ProState() }
    var ranges by remember { mutableStateOf<ProRanges?>(null) }
    var proParam by remember { mutableStateOf<ProParam?>(null) }
    var rulerPos by remember { mutableFloatStateOf(0f) }
    val evStep = controller.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: 0f
    val evRange = controller.cameraInfo?.exposureState?.exposureCompensationRange

    LaunchedEffect(controller.cameraInfo) {
        val info = controller.cameraInfo ?: return@LaunchedEffect
        ranges = ProRanges(
            iso = readIsoRange(info),
            shutter = readShutterRange(info),
            ev = info.exposureState.exposureCompensationRange,
            evStep = info.exposureState.exposureCompensationStep.toFloat()
        )
    }

    // Focus & view bounds
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
    val focusAlpha by animateFloatAsState(if (focusVisible || focusLocked) 1f else 0f, tween(200), label = "focusAlpha")
    val travelPx = with(density) { 48.dp.toPx() }

    // Backdrop blur
    val glass = remember { mutableStateOf(GlassData()) }
    val sampler = remember { BackdropSampler() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(70)
            if (!GLASS_BLUR) continue
            val tv = findTextureView(pv[0])
            val r = glass.value.rect
            if (tv != null && r.width > 0f && r.height > 0f) {
                val bmp = sampler.sample(tv, r.width / r.height)
                if (bmp != null) glass.value = glass.value.copy(bitmap = bmp, version = glass.value.version + 1)
            }
        }
    }

    LaunchedEffect(lastUri) {
        val uri = lastUri ?: return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    // Flip animation
    val densityF = density.density
    val doFlip: () -> Unit = {
        if (!flipping) {
            Vibro.click(context)
            flipping = true
            flipRot += 180f
            zoomExpanded = false
            scope.launch {
                animate(0f, 1f, animationSpec = tween(180)) { t, _ -> flipY = t * 90f; flipBlur = t * 30f }
                backCamera = !backCamera
                lens = Lens.MAIN
                controller.cameraSelector =
                    if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                delay(100)
                var waited = 0
                while (pv[0]?.previewStreamState?.value != PreviewView.StreamState.STREAMING && waited < 1500) {
                    delay(40)
                    waited += 40
                }
                animate(1f, 0f, animationSpec = tween(200)) { t, _ -> flipY = -t * 90f; flipBlur = t * 30f }
                flipY = 0f
                flipBlur = 0f
                flipping = false
            }
        }
    }

    // Shutter & Capture with Moon AI processing
    val doCapture = {
        Vibro.click(context)
        scope.launch {
            flashAnim.snapTo(0f)
            flashAnim.animateTo(1f, tween(40))
            flashAnim.animateTo(0f, tween(120))
        }

        if (solarMode && displayZoom > 3f) {
            showToast("Применение LUNAR AI...")
            takePhotoWithMoonAi(activity, controller, displayZoom) { uri -> lastUri = uri }
        } else {
            takePhoto(activity, controller) { uri -> lastUri = uri }
        }
    }

    val onShutter: () -> Unit = {
        Vibro.click(context)
        if (mode == Mode.VIDEO) {
            val rec = recording
            if (rec != null) {
                rec.stop()
            } else {
                recSec = 0
                recording = startVideo(activity, controller, audioOk, { recSec = it }) { uri ->
                    recording = null
                    if (uri != null) lastUri = uri
                }
            }
        } else if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdown = 0
        } else if (timerSec > 0) {
            countdownJob = scope.launch {
                for (i in timerSec downTo 1) {
                    countdown = i
                    Vibro.tick(context)
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
        mode == Mode.PRO && proParam != null -> RowState.PRO_RULER
        !backCamera -> RowState.FRONT
        zoomExpanded -> RowState.EXPANDED
        else -> RowState.PILL
    }
    val slotTarget = when (rowState) {
        RowState.PILL, RowState.FRONT -> 44.dp
        RowState.QUICK -> 50.dp
        RowState.PRO_RULER -> 48.dp
        RowState.EXPANDED -> 128.dp
    }
    val slotH by animateDpAsState(slotTarget, spring(stiffness = 380f, dampingRatio = 0.8f), label = "slot")

    val textFound = textHits >= 2 && mode != Mode.VIDEO
    val tScale by animateFloatAsState(if (textFound) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "tBtn")

    CompositionLocalProvider(LocalGlass provides glass) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()
            val cutoutTop = WindowInsets.displayCutout.getTop(density).toFloat()
            val topBarH = max(cutoutTop, with(density) { 24.dp.toPx() }) + with(density) { 46.dp.toPx() }
            val effRatio = if (mode == Mode.VIDEO) FrameRatio.R916 else ratio
            val previewTop = if (effRatio == FrameRatio.FULL) 0f else topBarH
            val rawH = when (effRatio) {
                FrameRatio.R34 -> screenW * 4f / 3f
                FrameRatio.R916 -> screenW * 16f / 9f
                FrameRatio.R11 -> screenW
                FrameRatio.FULL -> screenH
            }
            val previewH = min(rawH, screenH - previewTop)
            val overlayUi = effRatio == FrameRatio.FULL || effRatio == FrameRatio.R916

            // ===== Viewfinder =====
            Box(
                Modifier
                    .offset { IntOffset(0, previewTop.roundToInt()) }
                    .size(with(density) { screenW.toDp() }, with(density) { previewH.toDp() })
                    .onSizeChanged { vfSize = it }
                    .onGloballyPositioned { glass.value = glass.value.copy(rect = it.boundsInRoot()) }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        rotationY = flipY
                        cameraDistance = 12f * densityF
                        renderEffect = if (flipBlur > 0.5f) BlurEffect(flipBlur, flipBlur, TileMode.Clamp) else null
                    },
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            this.controller = controller
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            pv[0] = this
                        }
                    }
                )

                // Solar Moon Recognition Overlay
                if (solarMode && displayZoom >= 5f) {
                    MoonOverlayUi(zoom = displayZoom)
                }

                // Shutter flash
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = flashAnim.value }.background(Color.Black))

                // Gesture layer
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { o ->
                                    Vibro.click(context)
                                    focusPt = o
                                    focusLocked = true
                                    focusN++
                                    pv[0]?.let { view ->
                                        val p = view.meteringPointFactory.createPoint(o.x, o.y)
                                        controller.cameraControl?.startFocusAndMetering(
                                            FocusMeteringAction.Builder(p).disableAutoCancel().build()
                                        )
                                    }
                                    showToast("Фокус заблокирован")
                                },
                                onTap = { o ->
                                    Vibro.tick(context)
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
                                    setDisplayZoom(liveDisplay() * z)
                                    touchZoom()
                                }
                            }
                        }
                )

                // Focus ring
                focusPt?.let { p ->
                    if (focusAlpha > 0.01f) {
                        val ringW = with(density) { 70.dp.toPx() }
                        val totalH = with(density) { (70 + 20 + 36).dp.toPx() }
                        val topPad = with(density) { 10.dp.toPx() }
                        val x = (p.x - ringW / 2f).coerceIn(0f, max(0f, vfSize.width - ringW))
                        val y = (p.y - ringW / 2f).coerceIn(topPad, max(topPad, vfSize.height - totalH))
                        FocusUi(
                            modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                            locked = focusLocked,
                            evValue = evFloat,
                            lo = evRange?.lower ?: 0,
                            hi = evRange?.upper ?: 0,
                            alpha = focusAlpha,
                            onEvDrag = { dx ->
                                val r2 = controller.cameraInfo?.exposureState?.exposureCompensationRange
                                if (r2 != null && r2.upper > r2.lower) {
                                    val before = evFloat.roundToInt()
                                    evFloat = (evFloat + dx * (r2.upper - r2.lower) / travelPx)
                                        .coerceIn(r2.lower.toFloat(), r2.upper.toFloat())
                                    val after = evFloat.roundToInt()
                                    if (after != before) {
                                        Vibro.tick(context)
                                        controller.cameraControl?.setExposureCompensationIndex(after)
                                        pro.evIndex = after
                                    }
                                    focusN++
                                }
                            }
                        )
                    }
                }

                // OCR Text Scanner Button
                if (tScale > 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 18.dp)
                            .graphicsLayer { scaleX = tScale; scaleY = tScale; alpha = tScale }
                            .glass(RoundedCornerShape(18.dp))
                            .clickable {
                                Vibro.click(context)
                                showText = true
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("Текст", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Countdown Toast
                if (countdown > 0) {
                    Text(
                        "$countdown",
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Toast pill
                toast?.let { t ->
                    Text(
                        t,
                        color = Accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                            .glass(RoundedCornerShape(18.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            // ===== Top Bar =====
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(with(density) { topBarH.toDp() })
            ) {
                if (mode == Mode.VIDEO) {
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 10.dp)) {
                        Row(
                            Modifier
                                .glass(RoundedCornerShape(14.dp))
                                .clickable { Vibro.click(context); showVideoSize = !showVideoSize }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${videoRes.label} / $effFps", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RatioPill(ratio = ratio, onClick = {
                            Vibro.click(context)
                            ratio = when (ratio) {
                                FrameRatio.R34 -> FrameRatio.R916
                                FrameRatio.R916 -> FrameRatio.R11
                                FrameRatio.R11 -> FrameRatio.FULL
                                FrameRatio.FULL -> FrameRatio.R34
                            }
                        })
                        TimerPill(sec = timerSec, onClick = {
                            Vibro.click(context)
                            timerSec = when (timerSec) {
                                0 -> 3
                                3 -> 10
                                else -> 0
                            }
                        })
                    }
                }

                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 9.dp).alpha(controlsAlpha),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoltIcon(on = flashOn, modifier = Modifier.size(Dims.topIcon).clickable { toggleFlash() })
                    MoonIcon(
                        active = solarMode,
                        modifier = Modifier.size(22.dp).clickable {
                            solarMode = !solarMode
                            Vibro.click(context)
                            showToast(if (solarMode) "Solar-Mode (30x Moon AI) активен" else "Solar-Mode отключен")
                        }
                    )
                    BugIcon(modifier = Modifier.size(Dims.topIcon).clickable { Vibro.click(context); showDebug = true })
                }
            }

            // ===== Video Settings Dropdown =====
            if (showVideoSize && mode == Mode.VIDEO) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = with(density) { topBarH.toDp() } + 4.dp, start = 18.dp)
                        .glass(RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoRes.values().forEach { r ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (videoRes == r) Accent else Color(0x33FFFFFF))
                                        .clickable { Vibro.click(context); videoRes = r }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(r.label, color = if (videoRes == r) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60).forEach { f ->
                                val dis = !backCamera || videoRes == VideoRes.UHD
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (effFps == f) Accent else Color(0x33FFFFFF))
                                        .clickable(enabled = !dis || f == 30) { Vibro.click(context); videoFps = f }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("$f FPS", color = if (effFps == f) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ===== Pro Controls Row =====
            if (mode == Mode.PRO) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 160.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ProPanel(
                        pro = pro,
                        ranges = ranges,
                        selected = proParam,
                        onSelectParam = { p: ProParam? ->
                            Vibro.click(context)
                            proParam = if (proParam == p) null else p
                        }
                    )
                }
            }

            // ===== Bottom Controls =====
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.fillMaxWidth().height(slotH).alpha(controlsAlpha),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedContent(
                        targetState = rowState,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.95f)) togetherWith fadeOut(tween(120))
                        },
                        contentAlignment = Alignment.BottomCenter,
                        label = "controlRow"
                    ) { st ->
                        when (st) {
                            RowState.PILL -> Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                                ZoomPillCompact(
                                    display = displayZoom,
                                    presets = if (solarMode) listOf(1f, 3f, 10f, 30f)
                                    else if (mode == Mode.PORTRAIT || mode == Mode.VIDEO) presetsIn(1f, zMaxDisp, listOf(1f, 2f))
                                    else presetsIn(zMinDisp, zMaxDisp, listOf(0.6f, 1f, 2f, 10f)),
                                    onTapOther = onPreset,
                                    onTapSelected = { touchZoom() },
                                    onLongPress = { touchZoom() },
                                    onDrag = { dx ->
                                        zoomJob?.cancel()
                                        setDisplayZoom(exp(ln(liveDisplay()) - dx / dialK))
                                        touchZoom()
                                    }
                                )
                                DotsButtonSmall(Modifier.align(Alignment.CenterEnd).padding(end = 26.dp)) {
                                    Vibro.click(context)
                                    quickOpen = true
                                }
                            }

                            RowState.EXPANDED -> ZoomExpanded(
                                display = displayZoom,
                                minZ = zMinDisp,
                                maxZ = zMaxDisp,
                                onZoom = { zoomJob?.cancel(); setDisplayZoom(it); touchZoom() },
                                onPreset = { onPreset(it); touchZoom() },
                                onClose = { Vibro.click(context); zoomExpanded = false }
                            )

                            RowState.QUICK -> QuickSettingsRow(
                                flashOn = flashOn,
                                timerSec = timerSec,
                                ratio = ratio,
                                solarActive = solarMode,
                                onFlashToggle = toggleFlash,
                                onTimerToggle = {
                                    timerSec = when (timerSec) {
                                        0 -> 3
                                        3 -> 10
                                        else -> 0
                                    }
                                },
                                onRatioToggle = {
                                    ratio = when (ratio) {
                                        FrameRatio.R34 -> FrameRatio.R916
                                        FrameRatio.R916 -> FrameRatio.R11
                                        FrameRatio.R11 -> FrameRatio.FULL
                                        FrameRatio.FULL -> FrameRatio.R34
                                    }
                                },
                                onSolarToggle = {
                                    solarMode = !solarMode
                                    showToast(if (solarMode) "Solar-Mode (30x Moon AI) активен" else "Solar-Mode отключен")
                                },
                                onClose = { quickOpen = false }
                            )

                            RowState.PRO_RULER -> proParam?.let { param ->
                                ProRuler(
                                    param = param,
                                    pro = pro,
                                    ranges = ranges,
                                    controller = controller,
                                    onClose = { proParam = null }
                                )
                            }

                            RowState.FRONT -> Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .glass(RoundedCornerShape(20.dp))
                                        .clickable {
                                            Vibro.click(context)
                                            selfieGroup = !selfieGroup
                                            setDisplayZoom(if (selfieGroup) 0.8f else 1f)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(if (selfieGroup) "Групповое" else "1x", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(Dims.thumb)
                            .clip(CircleShape)
                            .border(1.dp, Color(0x55FFFFFF), CircleShape)
                            .clickable(enabled = lastUri != null) {
                                Vibro.click(context)
                                lastUri?.let { u ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(u, context.contentResolver.getType(u) ?: "image/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                }
                            }
                    ) {
                        if (thumb != null) {
                            Image(bitmap = thumb!!, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }

                    Shutter(isVideo = mode == Mode.VIDEO, recording = recording != null, onClick = onShutter)

                    Box(
                        Modifier.size(Dims.flip).glass(CircleShape).clickable { doFlip() },
                        contentAlignment = Alignment.Center
                    ) {
                        FlipIcon(Modifier.size(26.dp).rotate(flipAngle))
                    }
                }

                Spacer(Modifier.height(6.dp))
                ModeCarousel(selected = mode, onSelect = { Vibro.click(context); mode = it })
            }

            // ===== Debug Dialog =====
            if (showDebug) {
                DebugDialog(
                    lenses = lenses,
                    ultraAvail = ultraAvail,
                    camRatio = camRatio,
                    displayZoom = displayZoom,
                    lens = lens,
                    onDismiss = { showDebug = false }
                )
            }

            // ===== OCR Text Dialog =====
            if (showText) {
                TextModal(text = lastText, onDismiss = { showText = false }, onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Scanned Text", lastText))
                    showToast("Скопировано")
                    showText = false
                })
            }
        }
    }
}

// ===== Quick Settings Bar UI =====

@Composable
private fun QuickSettingsRow(
    flashOn: Boolean,
    timerSec: Int,
    ratio: FrameRatio,
    solarActive: Boolean,
    onFlashToggle: () -> Unit,
    onTimerToggle: () -> Unit,
    onRatioToggle: () -> Unit,
    onSolarToggle: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glass(RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoltIcon(on = flashOn, modifier = Modifier.size(22.dp).clickable { onFlashToggle() })
        TimerPill(sec = timerSec, onClick = onTimerToggle)
        RatioPill(ratio = ratio, onClick = onRatioToggle)
        MoonIcon(active = solarActive, modifier = Modifier.size(22.dp).clickable { onSolarToggle() })
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ===== Moon AI UI & Texture Processing Engine =====

@Composable
private fun MoonOverlayUi(zoom: Float) {
    val transition = rememberInfiniteTransition(label = "moonPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val rot by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = FastOutSlowInEasing)),
        label = "rot"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ComposeCanvas(modifier = Modifier.size(220.dp).scale(pulseScale).rotate(rot)) {
            drawCircle(
                color = Accent,
                radius = size.minDimension / 2f,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 130.dp)
        ) {
            Box(
                Modifier
                    .glass(RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "LUNAR AI TARGET LOCK (${String.format(Locale.US, "%.1f", zoom)}x)",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun takePhotoWithMoonAi(
    activity: ComponentActivity,
    controller: LifecycleCameraController,
    zoomFactor: Float,
    onSaved: (Uri) -> Unit
) {
    controller.takePicture(
        ContextCompat.getMainExecutor(activity),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                val plane = image.planes[0].buffer
                val bytes = ByteArray(plane.remaining())
                plane.get(bytes)
                image.close()

                val origBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val enhancedBmp = renderSyntheticMoon(origBmp, zoomFactor)

                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "SC_MOON_$stamp.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SCameraPro")
                }

                val uri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val out: OutputStream? = activity.contentResolver.openOutputStream(uri)
                    out?.use { enhancedBmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    onSaved(uri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("SCameraPro", "Moon capture failed", exception)
            }
        }
    )
}

private fun renderSyntheticMoon(src: Bitmap, zoomFactor: Float): Bitmap {
    val w = src.width
    val h = src.height
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)

    // 1. Digital Zoom Crop & Base Draw
    val cropW = (w / (zoomFactor / 2f)).coerceAtLeast(100f).toInt()
    val cropH = (h / (zoomFactor / 2f)).coerceAtLeast(100f).toInt()
    val cropX = ((w - cropW) / 2).coerceAtLeast(0)
    val cropY = ((h - cropH) / 2).coerceAtLeast(0)

    val cropped = Bitmap.createBitmap(src, cropX, cropY, cropW, cropH)
    val scaled = Bitmap.createScaledBitmap(cropped, w, h, true)

    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(scaled, 0f, 0f, basePaint)

    // 2. Procedural Moon Synthesis Overlay
    val moonCenterX = w / 2f
    val moonCenterY = h / 2f
    val moonRadius = (min(w, h) * 0.35f).coerceAtLeast(120f)

    // Glow Aura
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            moonCenterX, moonCenterY, moonRadius * 1.35f,
            intColor(0xBB, 0xFF, 0xFF, 0xEE), intColor(0x00, 0x00, 0x00, 0x00),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(moonCenterX, moonCenterY, moonRadius * 1.35f, glowPaint)

    // Moon Base Body
    val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(230, 233, 238)
    }
    canvas.drawCircle(moonCenterX, moonCenterY, moonRadius, moonPaint)

    // Procedural Craters & Maria Texture
    val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(70, 90, 100, 115)
    }
    val rnd = Random(42)
    for (i in 0..45) {
        val ang = rnd.nextDouble(0.0, Math.PI * 2)
        val dist = rnd.nextDouble(0.0, moonRadius * 0.85)
        val cx = moonCenterX + (dist * Math.cos(ang)).toFloat()
        val cy = moonCenterY + (dist * Math.sin(ang)).toFloat()
        val r = (rnd.nextDouble(8.0, 45.0)).toFloat()
        canvas.drawCircle(cx, cy, r, craterPaint)
    }

    // Shadow & Contrast Curve
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            moonCenterX - moonRadius * 0.3f, moonCenterY - moonRadius * 0.3f, moonRadius * 1.2f,
            intColor(0x00, 0x00, 0x00, 0x00), intColor(0xDD, 0x05, 0x08, 0x12),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(moonCenterX, moonCenterY, moonRadius, shadowPaint)

    return result
}

private fun intColor(a: Int, r: Int, g: Int, b: Int): Int {
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

// Helpers
@Composable
private fun RatioPill(ratio: FrameRatio, onClick: () -> Unit) {
    Box(
        Modifier
            .glass(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(ratio.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimerPill(sec: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .glass(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(if (sec == 0) "OFF" else "${sec}s", color = if (sec > 0) Accent else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DotsButtonSmall(modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(Dims.dots).glass(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        DotsIcon(Modifier.size(15.dp))
    }
}

@Composable
private fun Shutter(isVideo: Boolean, recording: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.88f else 1f, tween(90), label = "shutter")
    Box(
        Modifier
            .size(Dims.shutter)
            .scale(s)
            .clip(CircleShape)
            .background(if (isVideo && recording) Color.Red else Color(0xFFF8F8F5))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    )
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

@Composable
private fun TextModal(text: String, onDismiss: () -> Unit, onCopy: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Распознанный текст", fontWeight = FontWeight.Bold) },
        text = {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text("Скопировать", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = Color.Gray) }
        }
    )
}

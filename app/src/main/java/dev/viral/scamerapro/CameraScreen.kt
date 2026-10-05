package dev.viral.scamerapro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    // ---------- Solar-Mode ----------
    var solarMode by remember { mutableStateOf(false) }

    // ---------- lenses ----------
    val lenses = remember { runCatching { discoverLenses(context) }.getOrDefault(LensInfo(null, null, null)) }
    var ultraAvail by remember { mutableStateOf(false) }
    var ultraChecked by remember { mutableStateOf(false) }
    var lens by remember { mutableStateOf(Lens.MAIN) }
    var lastSwitch by remember { mutableLongStateOf(0L) }
    var pendingRatio by remember { mutableFloatStateOf(1f) }
    var pendingN by remember { mutableIntStateOf(0) }
    var mainMax by remember { mutableFloatStateOf(8f) }

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

    // ---------- video: use cases, quality, frame rate, torch ----------
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

    // ---------- lens switching and display zoom ----------
    val displayZoom = if (lens == Lens.ULTRA && backCamera) camRatio * 0.6f else camRatio
    val zMinDisp = if (ultraAvail && backCamera) 0.6f else (zoom?.minZoomRatio ?: 1f)
    LaunchedEffect(zoom) {
        if (lens == Lens.MAIN && backCamera) zoom?.let { mainMax = it.maxZoomRatio }
    }
    val zMaxDisp = if (lens == Lens.MAIN || !backCamera) (zoom?.maxZoomRatio ?: mainMax) else mainMax

    LaunchedEffect(zoom == null) {
        if (zoom != null && !ultraChecked) {
            ultraChecked = true
            val id = lenses.ultraId
            ultraAvail = id != null &&
                    runCatching { controller.hasCamera(selectorForId(id)) }.getOrDefault(false)
        }
    }
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

    fun liveDisplay(): Float {
        val r = controller.zoomState.value?.zoomRatio ?: 1f
        return if (lens == Lens.ULTRA && backCamera) r * 0.6f else r
    }

    val setDisplayZoom: (Float) -> Unit = { r0 ->
        val r = r0.coerceIn(zMinDisp, zMaxDisp)
        if (!backCamera) {
            applyZoom(controller, r)
        } else if (lens == Lens.ULTRA) {
            if (r >= 1f) switchLens(Lens.MAIN, 1f) else applyZoom(controller, r / 0.6f)
        } else {
            if (r < 1f && ultraAvail) switchLens(Lens.ULTRA, (r / 0.6f).coerceAtLeast(1f))
            else applyZoom(controller, r)
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
            val zs = controller.zoomState.value
            if (zs != null) {
                val start = zs.zoomRatio
                val end = target.coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                val steps = 12
                for (i in 1..steps) {
                    val t = i / steps.toFloat()
                    val e = t * t * (3f - 2f * t)
                    applyZoom(controller, exp(ln(start) + (ln(end) - ln(start)) * e))
                    delay(16)
                }
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

    // ---------- Pro mode ----------
    val pro = remember { ProState() }
    var ranges by remember { mutableStateOf<ProRanges?>(null) }
    var proParam by remember { mutableStateOf<ProParam?>(null) }
    var rulerPos by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(zoom == null, lens, backCamera) {
        delay(500)
        ranges = readRanges(controller)
    }
    LaunchedEffect(pro.version, mode, ranges) {
        applyPro(controller, pro, ranges, mode == Mode.PRO)
    }
    LaunchedEffect(lens, backCamera) {
        delay(700)
        applyPro(controller, pro, ranges, mode == Mode.PRO)
    }
    val evStep = controller.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: 0f
    val evRange = controller.cameraInfo?.exposureState?.exposureCompensationRange

    // ---------- focus ring / exposure ----------
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
        if (focusVisible || focusLocked) 1f else 0f, tween(200), label = "focusAlpha"
    )
    val travelPx = with(density) { 48.dp.toPx() }

    // ---------- backdrop blur ----------
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
                Log.e("SCameraPro", "thumbnail failed", e)
                null
            }
        }
    }

    // ---------- camera flip: icon spin + blur + 3D Y flip ----------
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

    // ---------- shutter ----------
    val doCapture = {
        Vibro.click(context)
        scope.launch {
            flashAnim.snapTo(0f)
            flashAnim.animateTo(1f, tween(40))
            flashAnim.animateTo(0f, tween(120))
        }
        takePhoto(activity, controller) { uri -> lastUri = uri }
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
    val tScale by animateFloatAsState(
        if (textFound) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "tBtn"
    )

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
                                    showToast("Фокус и экспозиция заблокированы")
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

                // Focus ring + exposure slider
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

                // Pro readouts
                if (mode == Mode.PRO) {
                    ProBar(
                        state = pro,
                        ranges = ranges,
                        evText = String.format(Locale.US, "%+.1f", pro.evIndex * evStep),
                        selected = proParam,
                        onSelect = { p ->
                            Vibro.click(context)
                            if (proParam == p) {
                                proParam = null
                            } else {
                                proParam = p
                                rulerPos = when (p) {
                                    ProParam.ISO -> pro.isoFrac * 40f
                                    ProParam.SHUTTER -> pro.shutterFrac * 40f
                                    ProParam.MF -> pro.mfFrac * 40f
                                    ProParam.EV -> (pro.evIndex - (evRange?.lower ?: 0)).toFloat()
                                    ProParam.WB -> (if (pro.wbIndex < 0) 2 else pro.wbIndex) * 6f
                                }
                            }
                        },
                        onReset = {
                            Vibro.click(context)
                            pro.reset()
                            controller.cameraControl?.setExposureCompensationIndex(0)
                            evFloat = 0f
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                    )
                }

                // Text-scan button
                if (tScale > 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = if (mode == Mode.PRO) 66.dp else 16.dp)
                            .size(42.dp)
                            .scale(tScale)
                            .alpha(min(1f, tScale))
                            .clip(CircleShape)
                            .background(Accent)
                            .clickable { Vibro.click(context); showText = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("T", color = Color.Black, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Timer countdown
                if (countdown > 0) {
                    Text(
                        countdown.toString(),
                        color = Color.White,
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (recording != null) {
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                            .glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFE2603F)))
                        Text(
                            String.format(Locale.US, "%02d:%02d", recSec / 60, recSec % 60),
                            color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                if (showVideoSize) {
                    VideoSizePanel(
                        res = videoRes,
                        fps = effFps,
                        frontCam = !backCamera,
                        onRes = { videoRes = it; if (it == VideoRes.UHD) videoFps = 30 },
                        onFps = { videoFps = it },
                        onClose = { showVideoSize = false },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    )
                }

                // Toast pill
                toast?.let { t ->
                    Text(
                        t,
                        color = Accent,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                            .glass(RoundedCornerShape(18.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            if (overlayUi) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(280.dp)
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
                        if (effRatio == FrameRatio.FULL)
                            Modifier.background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                        else Modifier
                    )
            ) {
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 9.dp).alpha(controlsAlpha),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoltIcon(
                        on = flashOn,
                        modifier = Modifier.size(Dims.topIcon).clickable { toggleFlash() }
                    )
                    if (mode == Mode.VIDEO) {
                        Text(
                            "${videoRes.label}\n$effFps",
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.clickable { Vibro.click(context); showVideoSize = true }
                        )
                    } else {
                        Text(
                            "12M",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { Vibro.click(context); showToast("50M недоступен: Samsung не открывает его сторонним приложениям") }
                        )
                    }
                    if (!backCamera) {
                        BeautyIcon(
                            Modifier.size(Dims.topIcon).clickable { Vibro.click(context); showToast("Эффекты для селфи появятся позже") }
                        )
                    }
                    // Solar-Mode (Lunar Icon)
                    MoonIcon(
                        active = solarMode,
                        modifier = Modifier.size(20.dp).clickable {
                            solarMode = !solarMode
                            Vibro.click(context)
                            showToast(if (solarMode) "Solar-Mode включен" else "Solar-Mode выключен")
                        }
                    )
                }
            }

            // ===== Bottom controls =====
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
                            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.95f)) togetherWith
                                    fadeOut(tween(120))
                        },
                        contentAlignment = Alignment.BottomCenter,
                        label = "controlRow"
                    ) { st ->
                        when (st) {
                            RowState.PILL -> Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                                ZoomPillCompact(
                                    display = displayZoom,
                                    presets = if (mode == Mode.PORTRAIT || mode == Mode.VIDEO) presetsIn(1f, zMaxDisp, listOf(1f, 2f))
                                    else presetsIn(zMinDisp, zMaxDisp, listOf(0.6f, 1f, 2f)),
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

                            RowState.FRONT -> Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                                Row(
                                    Modifier.glass(RoundedCornerShape(30.dp)).padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    SelfieChip(selected = !selfieGroup, onClick = { Vibro.click(context); selfieGroup = false }) {
                                        PersonIcon(Modifier.size(20.dp))
                                    }
                                    SelfieChip(selected = selfieGroup, onClick = { Vibro.click(context); selfieGroup = true }) {
                                        GroupIcon(Modifier.size(22.dp))
                                    }
                                }
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

                            RowState.QUICK -> QuickPanel(
                                flashOn = flashOn,
                                timerSec = timerSec,
                                ratio = ratio,
                                onSettings = { Vibro.click(context); quickOpen = false; showDebug = true },
                                onFlash = { toggleFlash() },
                                onTimer = {
                                    Vibro.click(context)
                                    timerSec = when (timerSec) { 0 -> 2; 2 -> 5; 5 -> 10; else -> 0 }
                                },
                                onRatio = {
                                    Vibro.click(context)
                                    val all = FrameRatio.values()
                                    ratio = all[(ratio.ordinal + 1) % all.size]
                                },
                                onRes = { Vibro.click(context); showToast("50M недоступен на этом телефоне для сторонних приложений") },
                                onClose = { Vibro.click(context); quickOpen = false }
                            )

                            RowState.PRO_RULER -> {
                                val p = proParam
                                val lo = evRange?.lower ?: 0
                                val hi = evRange?.upper ?: 0
                                when (p) {
                                    ProParam.ISO -> ValueRuler(rulerPos, 40, 5, {
                                        rulerPos = it; pro.isoManual = true; pro.isoFrac = it / 40f; pro.bump()
                                    })
                                    ProParam.SHUTTER -> ValueRuler(rulerPos, 40, 5, {
                                        rulerPos = it; pro.shutterManual = true; pro.shutterFrac = it / 40f; pro.bump()
                                    })
                                    ProParam.MF -> ValueRuler(rulerPos, 40, 5, {
                                        rulerPos = it; pro.mfManual = true; pro.mfFrac = it / 40f; pro.bump()
                                    })
                                    ProParam.EV -> ValueRuler(rulerPos, max(1, hi - lo), 6, {
                                        rulerPos = it
                                        val idx = it.roundToInt() + lo
                                        if (idx != pro.evIndex) {
                                            pro.evIndex = idx
                                            evFloat = idx.toFloat()
                                            controller.cameraControl?.setExposureCompensationIndex(idx)
                                        }
                                    })
                                    ProParam.WB -> ValueRuler(rulerPos, 24, 6, {
                                        rulerPos = it
                                        pro.wbIndex = (it / 6f).roundToInt().coerceIn(0, WB_PRESETS.lastIndex)
                                        pro.bump()
                                    })
                                    null -> Spacer(Modifier.height(1.dp))
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
                        AnimatedContent(
                            targetState = thumb,
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 1.2f)) togetherWith
                                        fadeOut(tween(150))
                            },
                            label = "thumb"
                        ) { bmp ->
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Shutter(isVideo = mode == Mode.VIDEO, recording = recording != null, onClick = onShutter)

                    Box(
                        Modifier
                            .size(Dims.flip)
                            .glass(CircleShape)
                            .clickable { doFlip() },
                        contentAlignment = Alignment.Center
                    ) {
                        FlipIcon(Modifier.size(26.dp).rotate(flipAngle))
                    }
                }

                Spacer(Modifier.height(6.dp))
                if (mode == Mode.PRO) {
                    Text(
                        "‹ ПРО",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .height(40.dp)
                            .clickable { Vibro.click(context); mode = Mode.PHOTO; proParam = null }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    )
                } else {
                    ModeCarousel(selected = mode, onSelect = {
                        Vibro.click(context)
                        mode = it
                    })
                }
            }

            AnimatedVisibility(
                visible = mode == Mode.MORE,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 4 }
            ) {
                MoreSheet(
                    onPick = { name ->
                        Vibro.click(context)
                        if (name == "ПРО") mode = Mode.PRO else showToast("$name появится позже")
                    },
                    onEdit = { Vibro.click(context); showToast("Редактор режимов появится позже") },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 62.dp)
                )
            }

            if (showText) {
                AlertDialog(
                    onDismissRequest = { showText = false },
                    title = { Text("Распознанный текст") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(lastText.ifEmpty { "Текст не найден" })
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            Vibro.click(context)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("scan", lastText))
                            showText = false
                            showToast("Текст скопирован")
                        }) { Text("Копировать") }
                    },
                    dismissButton = { TextButton(onClick = { showText = false }) { Text("Закрыть") } }
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
private fun SelfieChip(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val bg by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "selfieBg")
    Box(
        Modifier
            .size(Dims.pillBtn + 10.dp, Dims.pillBtn)
            .clip(RoundedCornerShape(Dims.pillBtn / 2))
            .background(PillSelected.copy(alpha = PillSelected.alpha * bg))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun DotsButtonSmall(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(Dims.dots).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { DotsIcon(Modifier.size(15.dp)) }
}

@Composable
private fun Shutter(isVideo: Boolean, recording: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.88f else 1f, tween(90), label = "shutter")
    val dot by animateDpAsState(if (recording) 26.dp else if (isVideo) 32.dp else 0.dp, spring(stiffness = 380f, dampingRatio = 0.8f), label = "shutterDot")
    Box(
        Modifier
            .size(Dims.shutter)
            .scale(s)
            .clip(CircleShape)
            .background(Color(0xFFF8F8F5))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (dot > 1.dp) {
            Box(Modifier.size(dot).clip(if (recording) RoundedCornerShape(7.dp) else CircleShape).background(Color(0xFFE2603F)))
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

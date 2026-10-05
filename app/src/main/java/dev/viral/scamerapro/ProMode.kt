package dev.viral.scamerapro

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ProParam { ISO, SHUTTER, EV, MF, WB }

/** White balance is set through Android's AWB presets (nearest preset to the shown Kelvin). */
val WB_PRESETS = listOf(
    2700 to CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
    4000 to CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
    5200 to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
    6200 to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
    7500 to CaptureRequest.CONTROL_AWB_MODE_SHADE
)

class ProState {
    var version by mutableIntStateOf(0)
    var isoManual by mutableStateOf(false)
    var isoFrac by mutableFloatStateOf(0.2f)
    var shutterManual by mutableStateOf(false)
    var shutterFrac by mutableFloatStateOf(0.55f)
    var evIndex by mutableIntStateOf(0)
    var mfManual by mutableStateOf(false)
    var mfFrac by mutableFloatStateOf(0.3f)
    var wbIndex by mutableIntStateOf(-1)

    fun bump() { version++ }

    fun reset() {
        isoManual = false
        shutterManual = false
        evIndex = 0
        mfManual = false
        wbIndex = -1
        bump()
    }
}

data class ProRanges(
    val isoMin: Int,
    val isoMax: Int,
    val expMin: Long,
    val expMax: Long,
    val minFocus: Float
)

@OptIn(ExperimentalCamera2Interop::class)
fun readRanges(controller: LifecycleCameraController): ProRanges? {
    val info = controller.cameraInfo ?: return null
    val c2 = Camera2CameraInfo.from(info)
    val iso = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return null
    val exp = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: return null
    val minFocus = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
    return ProRanges(iso.lower, iso.upper, exp.lower, exp.upper, minFocus)
}

fun isoOf(s: ProState, r: ProRanges): Int {
    val a = ln(r.isoMin.toFloat())
    val b = ln(r.isoMax.toFloat())
    return exp(a + (b - a) * s.isoFrac).roundToInt().coerceIn(r.isoMin, r.isoMax)
}

fun shutterNsOf(s: ProState, r: ProRanges): Long {
    val lo = max(r.expMin, 125_000L).toFloat()
    val hi = min(r.expMax, 1_000_000_000L).toFloat().coerceAtLeast(lo)
    return exp(ln(lo) + (ln(hi) - ln(lo)) * s.shutterFrac).toLong()
}

fun fmtShutter(ns: Long): String {
    val sec = ns / 1e9
    return if (sec >= 0.5) String.format(java.util.Locale.US, "%.1fs", sec)
    else "1/" + (1.0 / sec).roundToInt() + "s"
}

/** Pushes the manual values to the camera; with [enabled] = false everything goes back to auto. */
@OptIn(ExperimentalCamera2Interop::class)
fun applyPro(controller: LifecycleCameraController, s: ProState, r: ProRanges?, enabled: Boolean) {
    val cc = controller.cameraControl ?: return
    val b = CaptureRequestOptions.Builder()
    if (enabled && r != null) {
        if (s.isoManual || s.shutterManual) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoOf(s, r))
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterNsOf(s, r))
        }
        if (s.mfManual) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, s.mfFrac * r.minFocus)
        }
        if (s.wbIndex >= 0) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, WB_PRESETS[s.wbIndex].second)
        }
    }
    Camera2CameraControl.from(cc).setCaptureRequestOptions(b.build())
}

/** Readout strip shown over the viewfinder in Pro mode. */
@Composable
fun ProBar(
    state: ProState,
    ranges: ProRanges?,
    evText: String,
    selected: ProParam?,
    onSelect: (ProParam) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isoText = if (state.isoManual && ranges != null) isoOf(state, ranges).toString() else "AUTO"
    val shText = if (state.shutterManual && ranges != null) fmtShutter(shutterNsOf(state, ranges)) else "AUTO"
    val mfText = if (state.mfManual) "MF" else "AF"
    val wbText = if (state.wbIndex >= 0) "${WB_PRESETS[state.wbIndex].first}K" else "AWB"

    Row(
        modifier
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "↺", color = Color.White, fontSize = 20.sp,
            modifier = Modifier.clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 4.dp)
        )
        ProItem("ISO", isoText, state.isoManual, selected == ProParam.ISO) { onSelect(ProParam.ISO) }
        ProItem("", shText, state.shutterManual, selected == ProParam.SHUTTER) { onSelect(ProParam.SHUTTER) }
        ProItem("", evText, state.evIndex != 0, selected == ProParam.EV) { onSelect(ProParam.EV) }
        ProItem("", mfText, state.mfManual, selected == ProParam.MF) { onSelect(ProParam.MF) }
        ProItem("WB", wbText, state.wbIndex >= 0, selected == ProParam.WB) { onSelect(ProParam.WB) }
    }
}

@Composable
private fun ProItem(label: String, value: String, manual: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x33FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label.isNotEmpty()) {
            Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(end = 3.dp))
        }
        Text(
            value,
            color = if (manual) Accent else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Ruler for Pro values: ticks scroll under a fixed yellow pointer, drag to change. */
@Composable
fun ValueRuler(
    pos: Float,
    total: Int,
    major: Int,
    onPos: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val spacing = with(density) { 9.dp.toPx() }
    val posState by rememberUpdatedState(pos)
    val onPosState by rememberUpdatedState(onPos)
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp)
            .glass(RoundedCornerShape(24.dp))
            .pointerInput(total) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    onPosState((posState - dx / spacing).coerceIn(0f, total.toFloat()))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val edge = 16.dp.toPx()
            for (i in 0..total) {
                val x = cx + (i - pos) * spacing
                if (x < edge || x > size.width - edge) continue
                val isMajor = major > 0 && i % major == 0
                val fade = 1f - (abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f) * 0.55f
                val h = (if (isMajor) 20.dp else 12.dp).toPx()
                drawLine(
                    Color.White.copy(alpha = (if (isMajor) 0.95f else 0.55f) * fade),
                    Offset(x, cy - h / 2f), Offset(x, cy + h / 2f),
                    strokeWidth = (if (isMajor) 2.6.dp else 1.8.dp).toPx(), cap = StrokeCap.Round
                )
            }
            drawLine(
                Accent, Offset(cx, cy - 16.dp.toPx()), Offset(cx, cy + 16.dp.toPx()),
                strokeWidth = 2.8.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
}

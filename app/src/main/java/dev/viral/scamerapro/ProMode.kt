package dev.viral.scamerapro

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ProParam { ISO, SHUTTER, EV, MF, WB }

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
    val iso: Range<Int>?,
    val shutter: Range<Long>?,
    val ev: Range<Int>?,
    val evStep: Float
)

@OptIn(ExperimentalCamera2Interop::class)
fun readIsoRange(info: CameraInfo): Range<Int>? {
    return runCatching {
        val c2 = Camera2CameraInfo.from(info)
        c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    }.getOrNull()
}

@OptIn(ExperimentalCamera2Interop::class)
fun readShutterRange(info: CameraInfo): Range<Long>? {
    return runCatching {
        val c2 = Camera2CameraInfo.from(info)
        c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    }.getOrNull()
}

fun isoOf(s: ProState, r: ProRanges): Int {
    val minI = r.iso?.lower ?: 100
    val maxI = r.iso?.upper ?: 3200
    val a = ln(minI.toFloat())
    val b = ln(maxI.toFloat())
    return exp(a + (b - a) * s.isoFrac).roundToInt().coerceIn(minI, maxI)
}

fun shutterNsOf(s: ProState, r: ProRanges): Long {
    val minS = max(r.shutter?.lower ?: 125_000L, 125_000L).toFloat()
    val maxS = min(r.shutter?.upper ?: 1_000_000_000L, 1_000_000_000L).toFloat().coerceAtLeast(minS)
    return exp(ln(minS) + (ln(maxS) - ln(minS)) * s.shutterFrac).toLong()
}

fun fmtShutter(ns: Long): String {
    val sec = ns / 1e9
    return if (sec >= 0.5) String.format(Locale.US, "%.1fs", sec)
    else "1/" + (1.0 / sec).roundToInt() + "s"
}

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
        if (s.wbIndex >= 0) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, WB_PRESETS[s.wbIndex].second)
        }
    }
    Camera2CameraControl.from(cc).setCaptureRequestOptions(b.build())
}

@Composable
fun ProPanel(
    pro: ProState,
    ranges: ProRanges?,
    selected: ProParam?,
    onSelectParam: (ProParam?) -> Unit
) {
    val isoText = if (pro.isoManual && ranges != null) isoOf(pro, ranges).toString() else "AUTO"
    val shText = if (pro.shutterManual && ranges != null) fmtShutter(shutterNsOf(pro, ranges)) else "AUTO"
    val evText = if (pro.evIndex == 0) "0" else String.format(Locale.US, "%+d", pro.evIndex)
    val mfText = if (pro.mfManual) "MF" else "AF"
    val wbText = if (pro.wbIndex >= 0) "${WB_PRESETS[pro.wbIndex].first}K" else "AWB"

    Row(
        Modifier
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "↺", color = Color.White, fontSize = 20.sp,
            modifier = Modifier.clickable { pro.reset() }.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        ProItem("ISO", isoText, pro.isoManual, selected == ProParam.ISO) { onSelectParam(ProParam.ISO) }
        ProItem("", shText, pro.shutterManual, selected == ProParam.SHUTTER) { onSelectParam(ProParam.SHUTTER) }
        ProItem("EV", evText, pro.evIndex != 0, selected == ProParam.EV) { onSelectParam(ProParam.EV) }
        ProItem("", mfText, pro.mfManual, selected == ProParam.MF) { onSelectParam(ProParam.MF) }
        ProItem("WB", wbText, pro.wbIndex >= 0, selected == ProParam.WB) { onSelectParam(ProParam.WB) }
    }
}

@Composable
fun ProRuler(
    param: ProParam,
    pro: ProState,
    ranges: ProRanges?,
    controller: LifecycleCameraController,
    onClose: () -> Unit
) {
    var frac by mutableFloatStateOf(
        when (param) {
            ProParam.ISO -> pro.isoFrac
            ProParam.SHUTTER -> pro.shutterFrac
            ProParam.MF -> pro.mfFrac
            else -> 0.5f
        }
    )

    ValueRuler(
        pos = frac * 100f,
        total = 100,
        major = 10,
        onPos = { p ->
            frac = p / 100f
            when (param) {
                ProParam.ISO -> {
                    pro.isoFrac = frac
                    pro.isoManual = true
                }
                ProParam.SHUTTER -> {
                    pro.shutterFrac = frac
                    pro.shutterManual = true
                }
                ProParam.MF -> {
                    pro.mfFrac = frac
                    pro.mfManual = true
                }
                else -> {}
            }
            applyPro(controller, pro, ranges, true)
        }
    )
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
    Box(
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

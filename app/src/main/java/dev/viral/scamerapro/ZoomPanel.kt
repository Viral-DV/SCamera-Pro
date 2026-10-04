package dev.viral.scamerapro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlin.math.roundToInt

private val AllPresets = listOf(0.6f, 1f, 2f, 4f, 8f, 10f)

fun presetsIn(min: Float, max: Float, base: List<Float> = AllPresets): List<Float> =
    base.filter { it >= min - 0.05f && it <= max + 0.05f }

/** Compact floating pill: tap another value = smooth jump, tap the active one / swipe / hold = open the ruler. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomPillCompact(
    display: Float,
    presets: List<Float>,
    onTapOther: (Float) -> Unit,
    onTapSelected: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float) -> Unit
) {
    if (presets.isEmpty()) return
    val sel = presets.indices.minByOrNull { abs(presets[it] - display) } ?: 0
    Row(
        Modifier
            .glass(RoundedCornerShape(30.dp))
            .padding(3.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    onDrag(dx)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEachIndexed { i, p ->
            val selected = i == sel
            val bg by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "pillBg")
            Box(
                Modifier
                    .height(Dims.pillBtn)
                    .widthIn(min = Dims.pillBtn)
                    .clip(RoundedCornerShape(Dims.pillBtn / 2))
                    .background(PillSelected.copy(alpha = PillSelected.alpha * bg))
                    .combinedClickable(
                        onClick = { if (selected) onTapSelected() else onTapOther(p) },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (selected) fmtZoom(display) + "×" else fmtZoom(p),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/** Expanded two-level panel: readout + close, ruler, and fixed presets underneath. */
@Composable
fun ZoomExpanded(
    display: Float,
    minZ: Float,
    maxZ: Float,
    onZoom: (Float) -> Unit,
    onPreset: (Float) -> Unit,
    onClose: () -> Unit
) {
    val presets = presetsIn(minZ, maxZ)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .glass(RoundedCornerShape(26.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            Text(
                fmtZoom(display) + "x",
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { CloseIcon(Modifier.size(10.dp)) }
        }
        ZoomRuler(display, minZ, maxZ, onZoom, Modifier.fillMaxWidth().height(38.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { p ->
                val active = abs(ln(p / display)) < 0.04f
                val s by animateFloatAsState(if (active) 1.2f else 1f, spring(), label = "chip")
                Box(
                    Modifier
                        .height(Dims.pillBtn)
                        .widthIn(min = Dims.pillBtn)
                        .scale(s)
                        .clip(RoundedCornerShape(Dims.pillBtn / 2))
                        .background(if (active) PillSelected else Color.Transparent)
                        .clickable { onPreset(p) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        fmtZoom(p),
                        color = if (active) Accent else Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

private data class Tick(val value: Float, val major: Boolean)

private fun buildTicks(min: Float, max: Float): List<Tick> {
    val out = mutableListOf<Tick>()
    var z = (min * 100).roundToInt()
    val end = (max * 100).roundToInt()
    while (z <= end) {
        out += Tick(z / 100f, z % 100 == 0)
        z += when {
            z < 100 -> 10
            z < 200 -> 10
            z < 400 -> 20
            else -> 50
        }
    }
    return out
}

@Composable
private fun ZoomRuler(zoom: Float, minZ: Float, maxZ: Float, onZoom: (Float) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val k = with(density) { 220.dp.toPx() }
    val ticks = remember(minZ, maxZ) { buildTicks(minZ, maxZ) }
    val curState by rememberUpdatedState(zoom)
    val onZoomState by rememberUpdatedState(onZoom)
    Box(
        modifier.pointerInput(minZ, maxZ) {
            detectHorizontalDragGestures { change, dx ->
                change.consume()
                onZoomState(exp(ln(curState) - dx / k).coerceIn(minZ, maxZ))
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val lnCur = ln(zoom)
            val edge = 10.dp.toPx()
            for (t in ticks) {
                val x = cx + (ln(t.value) - lnCur) * k
                if (x < edge || x > size.width - edge) continue
                val fade = 1f - (abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f) * 0.55f
                val h = (if (t.major) 20.dp else 12.dp).toPx()
                drawLine(
                    Color.White.copy(alpha = (if (t.major) 0.95f else 0.6f) * fade),
                    Offset(x, cy - h / 2f), Offset(x, cy + h / 2f),
                    strokeWidth = (if (t.major) 2.6.dp else 1.8.dp).toPx(), cap = StrokeCap.Round
                )
            }
            drawLine(
                Accent, Offset(cx, cy - 16.dp.toPx()), Offset(cx, cy + 16.dp.toPx()),
                strokeWidth = 2.8.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
}

package dev.viral.scamerapro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

private data class Tick(val value: Float, val major: Boolean)

private fun buildTicks(min: Float, max: Float): List<Tick> {
    val out = mutableListOf<Tick>()
    var z = (min * 100).roundToInt()
    val end = (max * 100).roundToInt()
    while (z <= end) {
        out += Tick(z / 100f, z % 100 == 0)
        z += when {
            z < 200 -> 10
            z < 400 -> 20
            else -> 50
        }
    }
    return out
}

/** Samsung-style zoom ruler: ticks slide under a fixed yellow line, logarithmic scale. */
@Composable
fun ZoomDial(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val k = with(density) { 220.dp.toPx() } // pixels per ln-unit of zoom
    val ticks = remember(minZoom, maxZoom) { buildTicks(minZoom, maxZoom) }
    val curState by rememberUpdatedState(zoom)
    val onZoomState by rememberUpdatedState(onZoom)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Pill)
            .pointerInput(minZoom, maxZoom) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    val target = exp(ln(curState) - dx / k)
                    onZoomState(target.coerceIn(minZoom, maxZoom))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val lnCur = ln(zoom)
            val edge = 14.dp.toPx()
            for (t in ticks) {
                val x = cx + (ln(t.value) - lnCur) * k
                if (x < edge || x > size.width - edge) continue
                val h = (if (t.major) 22.dp else 14.dp).toPx()
                drawLine(
                    Color.White.copy(alpha = if (t.major) 0.95f else 0.55f),
                    Offset(x, cy - h / 2f),
                    Offset(x, cy + h / 2f),
                    strokeWidth = (if (t.major) 3.dp else 2.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawLine(
                Accent,
                Offset(cx, cy - 17.dp.toPx()),
                Offset(cx, cy + 17.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

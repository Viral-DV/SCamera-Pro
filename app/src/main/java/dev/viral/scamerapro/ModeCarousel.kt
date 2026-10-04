package dev.viral.scamerapro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mode strip: the selected mode glides to the center; swipe or tap to change. */
@Composable
fun ModeCarousel(selected: Mode, onSelect: (Mode) -> Unit, modifier: Modifier = Modifier) {
    val modes = Mode.values().toList()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(selected) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = { acc = 0f }
                ) { _, dx ->
                    acc += dx
                    if (acc < -80f) {
                        onSelect(modes[(selected.ordinal + 1).coerceAtMost(modes.lastIndex)])
                        acc = 0f
                    } else if (acc > 80f) {
                        onSelect(modes[(selected.ordinal - 1).coerceAtLeast(0)])
                        acc = 0f
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        val viewportPx = with(density) { maxWidth.toPx() }
        val half = maxWidth / 2
        val scroll = rememberScrollState()
        val xs = remember { FloatArray(modes.size) }
        val ws = remember { FloatArray(modes.size) }
        var ready by remember { mutableStateOf(false) }

        LaunchedEffect(selected, ready, viewportPx) {
            if (ready) {
                val i = selected.ordinal
                val target = (xs[i] + ws[i] / 2f - viewportPx / 2f).toInt().coerceAtLeast(0)
                scroll.animateScrollTo(target, tween(320))
            }
        }

        Row(
            Modifier.horizontalScroll(scroll, enabled = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(half))
            modes.forEachIndexed { i, m ->
                val isSel = m == selected
                val color by animateColorAsState(if (isSel) Color.White else Muted, tween(220), label = "modeColor")
                val scale by animateFloatAsState(if (isSel) 1.1f else 1f, tween(220), label = "modeScale")
                Text(
                    m.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    modifier = Modifier
                        .onGloballyPositioned {
                            xs[i] = it.positionInParent().x
                            ws[i] = it.size.width.toFloat()
                            if (i == modes.lastIndex) ready = true
                        }
                        .clickable { onSelect(m) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                )
            }
            Spacer(Modifier.width(half))
        }
    }
}

package dev.viral.scamerapro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Samsung-like focus UI: thin white ring with a padlock, and a horizontal exposure slider below it. */
@Composable
fun FocusUi(
    modifier: Modifier,
    locked: Boolean,
    evValue: Float,
    lo: Int,
    hi: Int,
    alpha: Float,
    onEvDrag: (Float) -> Unit
) {
    Column(modifier.alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(70.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 2.dp.toPx()
                drawArc(
                    Color.White,
                    startAngle = -90f + 17f,
                    sweepAngle = 360f - 34f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - 2 * inset, this.size.height - 2 * inset),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            LockIcon(
                locked,
                Modifier.align(Alignment.TopCenter).offset(y = (-9).dp).size(18.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .size(70.dp, 36.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        onEvDrag(dx)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val f = ((evValue - lo) / (hi - lo).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                val pad = 11.dp.toPx()
                val sx = pad + f * (this.size.width - 2 * pad)
                val cy = this.size.height / 2f
                val gap = 15.dp.toPx()
                val sw = 1.5.dp.toPx()
                drawLine(Color.White, Offset(0f, cy), Offset(max(0f, sx - gap), cy), sw, StrokeCap.Round)
                drawLine(Color.White, Offset(min(this.size.width, sx + gap), cy), Offset(this.size.width, cy), sw, StrokeCap.Round)
                drawSun(Offset(sx, cy), 10.dp.toPx(), Color.White, 1.5.dp.toPx())
            }
        }
    }
}

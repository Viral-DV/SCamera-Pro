package dev.viral.scamerapro

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawSun(c: Offset, r: Float, color: Color, stroke: Float) {
    drawCircle(color, radius = r * 0.42f, center = c)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            color,
            Offset(c.x + dx * r * 0.68f, c.y + dy * r * 0.68f),
            Offset(c.x + dx * r, c.y + dy * r),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MoonIcon(active: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mainCircle = Path().apply {
            addOval(Rect(0f, 0f, w, h))
        }
        val clipCircle = Path().apply {
            addOval(Rect(w * 0.3f, -h * 0.1f, w * 1.2f, h * 0.9f))
        }
        val moonPath = Path().apply {
            op(mainCircle, clipCircle, PathOperation.Difference)
        }
        drawPath(
            path = moonPath,
            color = if (active) Accent else color,
            style = Fill
        )
    }
}

@Composable
fun LockIcon(locked: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.16f, h * 0.46f),
            size = Size(w * 0.68f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.12f)
        )
        drawCircle(Color.Black.copy(alpha = 0.65f), radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.69f))
        val cy = h * 0.32f
        val rad = w * 0.2f
        val p = Path().apply {
            moveTo(w * 0.3f, h * 0.46f)
            lineTo(w * 0.3f, cy)
            arcTo(Rect(w * 0.3f, cy - rad, w * 0.7f, cy + rad), 180f, 180f, false)
            lineTo(w * 0.7f, if (locked) h * 0.46f else h * 0.36f)
        }
        drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun SunIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        drawSun(center, size.minDimension / 2f - 1.dp.toPx(), color, 1.6.dp.toPx())
    }
}

@Composable
fun GearIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val sw = 1.8.dp.toPx()
        drawCircle(color, radius = r * 0.30f, center = c, style = Stroke(sw))
        drawCircle(color, radius = r * 0.62f, center = c, style = Stroke(sw))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color,
                Offset(c.x + dx * r * 0.66f, c.y + dy * r * 0.66f),
                Offset(c.x + dx * r * 0.94f, c.y + dy * r * 0.94f),
                strokeWidth = sw * 1.7f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun BoltIcon(on: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.62f, h * 0.04f)
            lineTo(w * 0.22f, h * 0.56f)
            lineTo(w * 0.46f, h * 0.56f)
            lineTo(w * 0.38f, h * 0.96f)
            lineTo(w * 0.80f, h * 0.40f)
            lineTo(w * 0.55f, h * 0.40f)
            close()
        }
        if (on) {
            drawPath(p, color, style = Fill)
        } else {
            drawPath(p, color.copy(alpha = 0.8f), style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
            drawLine(
                color,
                Offset(w * 0.12f, h * 0.08f),
                Offset(w * 0.88f, h * 0.92f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun TimerIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.56f)
        val r = size.minDimension * 0.38f
        val sw = 1.8.dp.toPx()
        drawCircle(color, radius = r, center = c, style = Stroke(sw))
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.07f), Offset(size.width * 0.60f, size.height * 0.07f), sw, StrokeCap.Round)
        drawLine(color, c, Offset(c.x + r * 0.55f, c.y - r * 0.50f), sw, StrokeCap.Round)
    }
}

@Composable
fun FlipIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.34f
        val sw = 2.dp.toPx()
        val tl = Offset(c.x - r, c.y - r)
        val sz = Size(2 * r, 2 * r)
        drawArc(color, 200f, 140f, false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round))
        drawArc(color, 20f, 140f, false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round))
        for (deg in listOf(340.0, 160.0)) {
            val a = Math.toRadians(deg)
            val px = c.x + r * cos(a).toFloat()
            val py = c.y + r * sin(a).toFloat()
            val tx = -sin(a).toFloat()
            val ty = cos(a).toFloat()
            val nx = cos(a).toFloat()
            val ny = sin(a).toFloat()
            val len = r * 0.45f
            val wid = r * 0.36f
            val head = Path().apply {
                moveTo(px + tx * len, py + ty * len)
                lineTo(px + nx * wid, py + ny * wid)
                lineTo(px - nx * wid, py - ny * wid)
                close()
            }
            drawPath(head, color, style = Fill)
        }
    }
}

@Composable
fun CloseIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val sw = 2.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), sw, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), sw, StrokeCap.Round)
    }
}

@Composable
fun DotsIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val r = 3.dp.toPx()
        for (x in listOf(size.width * 0.2f, size.width * 0.8f)) {
            for (y in listOf(size.height * 0.2f, size.height * 0.8f)) {
                drawCircle(color, r, Offset(x, y))
            }
        }
    }
}

@Composable
fun PersonIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.32f), style = Stroke(sw))
        drawArc(color, 180f, 180f, false, topLeft = Offset(w * 0.16f, h * 0.58f), size = Size(w * 0.68f, w * 0.68f), style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
fun GroupIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        val xs = listOf(0.2f, 0.5f, 0.8f)
        val ys = listOf(0.40f, 0.28f, 0.40f)
        for (i in 0 until 3) {
            drawCircle(color, radius = w * 0.11f, center = Offset(w * xs[i], h * ys[i]), style = Stroke(sw))
            drawArc(
                color, 180f, 180f, false,
                topLeft = Offset(w * (xs[i] - 0.15f), h * (ys[i] + 0.20f)),
                size = Size(w * 0.30f, w * 0.30f),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun BeautyIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.7.dp.toPx()
        drawCircle(color, radius = w * 0.33f, center = Offset(w * 0.45f, h * 0.58f), style = Stroke(sw))
        drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.36f, h * 0.54f))
        drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.54f, h * 0.54f))
        drawArc(color, 20f, 140f, false, topLeft = Offset(w * 0.34f, h * 0.55f), size = Size(w * 0.22f, h * 0.22f), style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.82f, h * 0.06f), Offset(w * 0.82f, h * 0.30f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.70f, h * 0.18f), Offset(w * 0.94f, h * 0.18f), sw, StrokeCap.Round)
    }
}

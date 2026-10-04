package dev.viral.scamerapro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val PillSelected = Color(0x47FFFFFF)
val Accent = Color(0xFFF5C542)
val Muted = Color(0xFFA5A5A5)

/** Set to false to turn off the live backdrop blur (saves a little CPU). */
const val GLASS_BLUR = true

/** Control sizes, tuned to match One UI proportions. */
object Dims {
    val shutter = 74.dp
    val thumb = 52.dp
    val flip = 54.dp
    val dots = 38.dp
    val pillH = 40.dp
    val pillBtn = 34.dp
    val topIcon = 22.dp
}

enum class Mode(val label: String) {
    PORTRAIT("ПОРТРЕТ"),
    PHOTO("ФОТОГРАФИЯ"),
    GRAND("GRAND"),
    VIDEO("ВИДЕОЗАПИСЬ"),
    MORE("ЕЩЕ"),
    PRO("ПРО")
}

/** Modes shown in the swipe strip (PRO is entered from the "More" sheet). */
val carouselModes = listOf(Mode.PORTRAIT, Mode.PHOTO, Mode.GRAND, Mode.VIDEO, Mode.MORE)

enum class FrameRatio(val label: String) {
    R34("3:4"),
    R916("9:16"),
    R11("1:1"),
    FULL("Full")
}

fun fmtZoom(v: Float): String {
    val s = if (abs(v - v.roundToInt()) < 0.05f) v.roundToInt().toString()
    else String.format(Locale.US, "%.1f", v)
    return if (s.startsWith("0.")) s.removePrefix("0") else s
}

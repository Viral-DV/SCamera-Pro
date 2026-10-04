package dev.viral.scamerapro

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val Pill = Color(0xFF2B2B2B)
val PillSelected = Color(0x47FFFFFF)
val Accent = Color(0xFFF5C542)
val Muted = Color(0xFF9A9A9A)

/** Set to false to turn off the live frosted-glass sampling (saves a little CPU). */
const val GLASS_BLUR = true

enum class Mode(val label: String) {
    PORTRAIT("ПОРТРЕТ"),
    PHOTO("ФОТОГРАФИЯ"),
    GRAND("GRAND"),
    VIDEO("ВИДЕОЗАПИСЬ"),
    MORE("ЕЩЕ")
}

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

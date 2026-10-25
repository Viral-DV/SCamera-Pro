package dev.viral.scamerapro

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Moon for the observer: height/azimuth, lit fraction, waxing flag, and the bright-limb angle from "up". */
data class MoonInfo(
    val altDeg: Double,
    val azDeg: Double,
    val illum: Double,
    val waxing: Boolean,
    val brightLimbDeg: Double,
    val northDeg: Double,
    val approx: Boolean = false
)

private fun rad(d: Double) = Math.toRadians(d)
private fun deg(r: Double) = Math.toDegrees(r)
private fun norm360(d: Double) = ((d % 360.0) + 360.0) % 360.0

/** Low-precision Sun/Moon positions (about 0.3 degrees), enough for phase and orientation. */
fun moonInfoAt(jdUtc: Double, latDeg: Double, lonDeg: Double): MoonInfo {
    val d = jdUtc - 2451543.5
    // Sun
    val ws = 282.9404 + 4.70935e-5 * d
    val es = 0.016709 - 1.151e-9 * d
    val ms = norm360(356.0470 + 0.9856002585 * d)
    val eS = ms + deg(es) * sin(rad(ms)) * (1 + es * cos(rad(ms)))
    val xs = cos(rad(eS)) - es
    val ys = sqrt(1 - es * es) * sin(rad(eS))
    val lonSun = rad(deg(atan2(ys, xs)) + ws)
    // Moon
    val n = norm360(125.1228 - 0.0529538083 * d)
    val w = norm360(318.0634 + 0.1643573223 * d)
    val m = norm360(115.3654 + 13.0649929509 * d)
    val e = 0.0549
    var eM = m + deg(e) * sin(rad(m)) * (1 + e * cos(rad(m)))
    eM -= (eM - deg(e) * sin(rad(eM)) - m) / (1 - e * cos(rad(eM)))
    val xv = 60.2666 * (cos(rad(eM)) - e)
    val yv = 60.2666 * sqrt(1 - e * e) * sin(rad(eM))
    val r = hypot(xv, yv)
    val vw = rad(deg(atan2(yv, xv)) + w)
    val nr = rad(n)
    val ir = rad(5.1454)
    val xh = r * (cos(nr) * cos(vw) - sin(nr) * sin(vw) * cos(ir))
    val yh = r * (sin(nr) * cos(vw) + cos(nr) * sin(vw) * cos(ir))
    val zh = r * sin(vw) * sin(ir)
    var lonM = deg(atan2(yh, xh))
    var latM = deg(atan2(zh, hypot(xh, yh)))
    val lm = n + w + m
    val ls = ms + ws
    val dd = rad(lm - ls)
    val f = rad(lm - n)
    val mr = rad(m)
    val msr = rad(ms)
    lonM += -1.274 * sin(mr - 2 * dd) + 0.658 * sin(2 * dd) - 0.186 * sin(msr) -
        0.059 * sin(2 * mr - 2 * dd) - 0.057 * sin(mr - 2 * dd + msr) + 0.053 * sin(mr + 2 * dd) +
        0.046 * sin(2 * dd - msr) + 0.041 * sin(mr - msr) - 0.035 * sin(dd) - 0.031 * sin(mr + msr) -
        0.015 * sin(2 * f - 2 * dd) + 0.011 * sin(mr - 4 * dd)
    latM += -0.173 * sin(f - 2 * dd) - 0.055 * sin(mr - f - 2 * dd) - 0.046 * sin(mr + f - 2 * dd) +
        0.033 * sin(f + 2 * dd) + 0.017 * sin(2 * mr + f)

    val ecl = rad(23.4393 - 3.563e-7 * d)
    fun eq(lonR: Double, latR: Double): Pair<Double, Double> {
        val x = cos(lonR) * cos(latR)
        val y = sin(lonR) * cos(latR)
        val z = sin(latR)
        val ye = y * cos(ecl) - z * sin(ecl)
        val ze = y * sin(ecl) + z * cos(ecl)
        return atan2(ye, x) to atan2(ze, hypot(x, ye))
    }
    val (raS, decS) = eq(lonSun, 0.0)
    val (raM, decM) = eq(rad(lonM), rad(latM))

    val psi = acos((cos(decS) * cos(decM) * cos(raS - raM) + sin(decS) * sin(decM)).coerceIn(-1.0, 1.0))
    val illum = (1 - cos(psi)) / 2
    val waxing = sin(raM - raS) > 0
    val chi = atan2(
        cos(decS) * sin(raS - raM),
        sin(decS) * cos(decM) - cos(decS) * sin(decM) * cos(raS - raM)
    )

    val gmst = norm360(280.46061837 + 360.98564736629 * (jdUtc - 2451545.0))
    val h = rad(norm360(gmst + lonDeg)) - raM
    val phi = rad(latDeg)
    val alt = asin((sin(phi) * sin(decM) + cos(phi) * cos(decM) * cos(h)).coerceIn(-1.0, 1.0))
    val az = atan2(sin(h), cos(h) * sin(phi) - tan(decM) * cos(phi)) + PI
    val q = atan2(sin(h), tan(phi) * cos(decM) - sin(decM) * cos(h))
    return MoonInfo(deg(alt), norm360(deg(az)), illum, waxing, norm360(deg(chi - q)), deg(-q))
}

/** Last known approximate location if allowed; otherwise a default spot in Germany (marked approx). */
@SuppressLint("MissingPermission")
fun currentMoonInfo(ctx: Context, useLocation: Boolean = true): MoonInfo {
    var lat = 52.5
    var lon = 13.4
    var approx = true
    if (useLocation) {
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER).mapNotNull {
                try { lm.getLastKnownLocation(it) } catch (e: Exception) { null }
            }.maxByOrNull { it.time }
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                approx = false
            }
        } catch (e: Exception) {
        }
    }
    val jd = System.currentTimeMillis() / 86400000.0 + 2440587.5
    return moonInfoAt(jd, lat, lon).copy(approx = approx)
}

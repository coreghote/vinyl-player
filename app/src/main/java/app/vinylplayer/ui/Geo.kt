package app.vinylplayer.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * All screen geometry, derived from the reference screenshots (720x1560): the record is big and
 * bleeds off the left edge, the tonearm pivots at the top right.
 *
 * Tonearm angle convention: degrees, 0 = hanging straight down (parked), positive = swung clockwise
 * toward the record (tip moves left).
 */
class Geo(val w: Float, val h: Float) {
    val r = w * 0.607f                                    // record radius
    val cx = w * 0.275f                                   // record centre
    val cy = max(h * 0.36f, r + h * 0.03f)
    val discPx = (r * 2f).roundToInt()
    val labelR = r * VinylRenderer.LABEL

    val pivot = Offset(w * 0.905f, w * 0.32f)             // tonearm pivot
    val armLen = w * 0.9f                                 // pivot -> stylus

    /** Angle where the stylus touches the outermost groove / innermost groove. */
    val thetaOuter: Float = angleForRadius(r * 0.965f)
    val thetaInner: Float = max(angleForRadius(r * 0.50f), thetaOuter + 6f)

    /** Horizontal offsets that park the record+text group completely off-screen. */
    val offLeft = -w * 1.0f
    val offRight = w * 1.4f

    fun stylus(angleDeg: Float): Offset {
        val a = Math.toRadians(angleDeg.toDouble())
        return Offset(
            pivot.x - armLen * sin(a).toFloat(),
            pivot.y + armLen * cos(a).toFloat(),
        )
    }

    /** Arm angle that points at [p] (used while dragging the head). */
    fun angleAt(p: Offset): Float =
        Math.toDegrees(atan2((pivot.x - p.x).toDouble(), (p.y - pivot.y).toDouble())).toFloat()

    /** Arm angle for playback progress 0..1. */
    fun angleForProgress(progress: Float): Float =
        thetaOuter + progress.coerceIn(0f, 1f) * (thetaInner - thetaOuter)

    private fun angleForRadius(radius: Float): Float {
        var a = 0f
        while (a < 80f) {
            val s = stylus(a)
            if (hypot(s.x - cx, s.y - cy) <= radius) return a
            a += 0.1f
        }
        return 80f
    }
}

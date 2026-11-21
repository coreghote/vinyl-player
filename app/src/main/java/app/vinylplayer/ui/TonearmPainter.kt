package app.vinylplayer.ui

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

/** Draws the white tonearm + its translucent housing. Cheap enough to run every frame. */
class TonearmPainter(g: Geo) {
    private val w = g.w
    private val px = g.pivot.x
    private val py = g.pivot.y
    private val len = g.armLen
    private val tubeW = w * 0.017f

    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6F3EC.toInt() }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEDE9DF.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, w * 0.0025f)
        color = 0xFFD8D2C6.toInt()
    }
    private val tube = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            px - tubeW / 2f, 0f, px + tubeW / 2f, 0f,
            intArrayOf(0xFF8E9297.toInt(), 0xFFE8EAEC.toInt(), 0xFFB4B8BC.toInt(), 0xFF84888D.toInt()),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    private val housing = RectF(w * 0.722f, w * 0.174f, w * 0.989f, w * 0.569f)
    private val housingFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30FFFFFF }
    private val housingStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, w * 0.002f)
        color = 0x55808080
    }

    private val blockMain = RectF(px - w * 0.120f, py - w * 0.085f, px - w * 0.010f, py + w * 0.030f)
    private val blockSide = RectF(px - w * 0.060f, py - w * 0.030f, px + w * 0.040f, py + w * 0.085f)
    private val head = RectF(px - w * 0.042f, py + len - w * 0.095f, px + w * 0.038f, py + len + w * 0.020f)
    private val needle = RectF(px, py + len + w * 0.015f, px + w * 0.014f, py + len + w * 0.034f)
    private val corner = w * 0.018f

    /** @param angle degrees (0 = parked), @param lift 0 = stylus on the record, 1 = lifted. */
    fun draw(c: Canvas, angle: Float, lift: Float) {
        val rr = w * 0.125f
        c.drawRoundRect(housing, rr, rr, housingFill)
        c.drawRoundRect(housing, rr, rr, housingStroke)

        val scale = 1f + 0.035f * lift

        // shadow: further away and softer when lifted
        shadow.alpha = (70 - 20 * lift).toInt()
        shadow.maskFilter = BlurMaskFilter(w * (0.006f + 0.018f * lift), BlurMaskFilter.Blur.NORMAL)
        c.save()
        c.translate(w * (0.006f + 0.022f * lift), w * (0.010f + 0.035f * lift))
        c.scale(scale, scale, px, py)
        c.rotate(angle, px, py)
        shapes(c, shadow, shadow, shadow, null)
        c.restore()

        c.save()
        c.scale(scale, scale, px, py)
        c.rotate(angle, px, py)
        shapes(c, tube, body, shade, edge)
        c.restore()
    }

    private fun shapes(c: Canvas, tubeP: Paint, bodyP: Paint, shadeP: Paint, edgeP: Paint?) {
        // tube
        c.drawRect(px - tubeW / 2f, py + w * 0.04f, px + tubeW / 2f, py + len - w * 0.02f, tubeP)
        // pivot block (two overlapping boxes)
        c.drawRoundRect(blockMain, corner, corner, bodyP)
        edgeP?.let { c.drawRoundRect(blockMain, corner, corner, it) }
        c.drawRoundRect(blockSide, corner, corner, shadeP)
        edgeP?.let { c.drawRoundRect(blockSide, corner, corner, it) }
        // head shell, tilted a little relative to the tube
        c.save()
        c.rotate(14f, px, py + len)
        c.drawRoundRect(head, corner * 0.7f, corner * 0.7f, bodyP)
        edgeP?.let { c.drawRoundRect(head, corner * 0.7f, corner * 0.7f, it) }
        c.drawRect(needle, bodyP)
        c.restore()
    }
}

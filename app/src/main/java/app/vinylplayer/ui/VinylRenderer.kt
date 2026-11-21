package app.vinylplayer.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renders a flat top-down record (surface + grooves + rim + label with the cover) into a square bitmap.
 * The bitmap is rendered once per track and then just rotated by the GPU, so it must stay free of any
 * lighting that should NOT rotate (sheen and shadow are drawn separately in the UI).
 */
object VinylRenderer {

    /** Label radius as a fraction of the record radius (measured from the reference screenshot). */
    const val LABEL = 0.446f

    fun render(size: Int, spec: VinylSpec, art: Bitmap?, title: String): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val cx = s / 2f
        val r = s / 2f
        val labelR = r * LABEL
        val rnd = Random(spec.seed)

        // Paint the square surface, then clear everything outside the record circle.
        // (A DST_IN circle drawn over the surface would only touch the pixels *inside* it, i.e. it
        // would do nothing; CLEAR on the complement actually cuts the black corners away.)
        drawSurface(c, spec, cx, r, rnd)
        drawGrooves(c, cx, r, labelR)
        clearOutsideCircle(c, s, cx, r - 1f)

        drawRim(c, cx, r)
        drawLabel(c, spec, art, title, cx, labelR, r)
        return bmp
    }

    // ------------------------------------------------------------------ surface

    private fun clearOutsideCircle(c: Canvas, size: Float, cx: Float, r: Float) {
        val outside = Path().apply {
            addRect(0f, 0f, size, size, Path.Direction.CW)
            op(Path().apply { addCircle(cx, cx, r, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        c.drawPath(outside, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        })
    }

    private fun drawSurface(c: Canvas, spec: VinylSpec, cx: Float, r: Float, rnd: Random) {
        val size = cx * 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            cx, cx, r,
            intArrayOf(lighten(spec.base, 0.08f), spec.base, darken(spec.base, 0.12f)),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, size, size, p)
        p.shader = null

        when (spec.style) {
            VinylStyle.CLASSIC, VinylStyle.SOLID -> Unit
            VinylStyle.SPLATTER -> splatter(c, spec, cx, r, rnd)
            VinylStyle.MARBLE -> marble(c, spec, size, r, rnd)
            VinylStyle.SPLIT -> {
                c.save()
                c.rotate(rnd.nextFloat() * 360f, cx, cx)
                p.color = spec.accent
                c.drawRect(0f, 0f, cx, size, p)
                c.restore()
            }
            VinylStyle.SWIRL -> {
                val accent2 = ColorUtils.blendARGB(spec.accent, spec.base, 0.5f)
                val sweep = SweepGradient(
                    cx, cx,
                    intArrayOf(spec.base, spec.accent, spec.base, accent2, spec.base),
                    null,
                )
                val m = Matrix()
                m.setRotate(rnd.nextFloat() * 360f, cx, cx)
                sweep.setLocalMatrix(m)
                p.shader = sweep
                c.drawRect(0f, 0f, size, size, p)
                p.shader = null
            }
        }
    }

    private fun splatter(c: Canvas, spec: VinylSpec, cx: Float, r: Float, rnd: Random) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.accent }
        repeat(72) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val r0 = r * (0.40f + rnd.nextFloat() * 0.55f)
            val len = r * (0.05f + rnd.nextFloat() * 0.30f)
            val w = r * (0.006f + rnd.nextFloat() * 0.020f)
            val dx = cos(a)
            val dy = sin(a)
            val nx = -dy
            val ny = dx
            val x0 = cx + dx * r0
            val y0 = cx + dy * r0
            val x1 = cx + dx * (r0 + len)
            val y1 = cx + dy * (r0 + len)
            val path = Path().apply {
                moveTo(x0 + nx * w, y0 + ny * w)
                lineTo(x1, y1)
                lineTo(x0 - nx * w, y0 - ny * w)
                close()
            }
            paint.alpha = 190 + rnd.nextInt(65)
            c.drawPath(path, paint)
            c.drawCircle(x0, y0, w * 1.1f, paint)
            if (rnd.nextFloat() < 0.5f) {
                c.drawCircle(x1 + dx * len * 0.15f, y1 + dy * len * 0.15f, w * 0.9f, paint)
            }
        }
        repeat(120) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val rr = r * (0.38f + rnd.nextFloat() * 0.60f)
            paint.alpha = 200
            c.drawCircle(cx + cos(a) * rr, cx + sin(a) * rr, r * (0.003f + rnd.nextFloat() * 0.010f), paint)
        }
    }

    private fun marble(c: Canvas, spec: VinylSpec, size: Float, r: Float, rnd: Random) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        fun pt() = rnd.nextFloat() * size

        // broad soft clouds
        repeat(14) {
            val dark = rnd.nextBoolean()
            paint.color = if (dark) darken(spec.base, 0.35f) else lighten(spec.base, 0.35f)
            paint.alpha = 60 + rnd.nextInt(80)
            paint.strokeWidth = r * (0.03f + rnd.nextFloat() * 0.09f)
            paint.maskFilter = BlurMaskFilter(r * 0.03f, BlurMaskFilter.Blur.NORMAL)
            val path = Path().apply {
                moveTo(pt(), pt())
                cubicTo(pt(), pt(), pt(), pt(), pt(), pt())
            }
            c.drawPath(path, paint)
        }
        // thin dark veins
        repeat(10) {
            paint.color = darken(spec.base, 0.6f)
            paint.alpha = 90 + rnd.nextInt(60)
            paint.strokeWidth = r * (0.003f + rnd.nextFloat() * 0.006f)
            paint.maskFilter = BlurMaskFilter(r * 0.006f, BlurMaskFilter.Blur.NORMAL)
            val path = Path().apply {
                moveTo(pt(), pt())
                cubicTo(pt(), pt(), pt(), pt(), pt(), pt())
            }
            c.drawPath(path, paint)
        }
        paint.maskFilter = null
    }

    // ------------------------------------------------------------------ grooves, rim, label

    private fun drawGrooves(c: Canvas, cx: Float, r: Float, labelR: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, r / 450f)
        }
        // dark lead-in band next to the label
        p.strokeWidth = r * 0.04f
        p.color = Color.argb(46, 0, 0, 0)
        c.drawCircle(cx, cx, labelR + r * 0.03f, p)

        p.strokeWidth = max(1f, r / 450f)
        var rad = labelR + r * 0.06f
        var i = 0
        val step = max(2.6f, r / 150f)
        while (rad < r * 0.975f) {
            p.color = if (i % 2 == 0) Color.argb(20, 0, 0, 0) else Color.argb(10, 255, 255, 255)
            c.drawCircle(cx, cx, rad, p)
            rad += step * (0.8f + 0.2f * (i % 3))
            i++
        }
        // track gaps
        p.strokeWidth = r * 0.006f
        p.color = Color.argb(40, 0, 0, 0)
        for (f in floatArrayOf(0.58f, 0.74f, 0.88f)) c.drawCircle(cx, cx, r * f, p)

        // edge vignette
        val v = Paint(Paint.ANTI_ALIAS_FLAG)
        v.shader = RadialGradient(
            cx, cx, r,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(36, 0, 0, 0)),
            floatArrayOf(0f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, cx * 2f, cx * 2f, v)
    }

    private fun drawRim(c: Canvas, cx: Float, r: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        p.strokeWidth = r * 0.012f
        p.color = Color.argb(64, 255, 255, 255)
        c.drawCircle(cx, cx, r - r * 0.008f, p)
        p.strokeWidth = 1.5f
        p.color = Color.argb(64, 0, 0, 0)
        c.drawCircle(cx, cx, r - 1f, p)
    }

    private fun drawLabel(
        c: Canvas, spec: VinylSpec, art: Bitmap?, title: String,
        cx: Float, labelR: Float, r: Float,
    ) {
        val lp = Paint(Paint.ANTI_ALIAS_FLAG)
        if (art != null) {
            val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = max(2f * labelR / art.width, 2f * labelR / art.height)
            val m = Matrix()
            m.setScale(scale, scale)
            m.postTranslate(cx - art.width * scale / 2f, cx - art.height * scale / 2f)
            shader.setLocalMatrix(m)
            lp.shader = shader
            c.drawCircle(cx, cx, labelR, lp)
        } else {
            lp.color = if (spec.style == VinylStyle.CLASSIC) Color.rgb(200, 60, 50) else spec.accent
            c.drawCircle(cx, cx, labelR, lp)
            val letter = title.trim().firstOrNull()?.uppercaseChar()?.toString()
            if (letter != null) {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = labelR * 0.9f
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                c.drawText(letter, cx, cx + tp.textSize * 0.35f, tp)
            } else {
                // spindle hole for the empty placeholder record
                lp.color = Color.rgb(239, 237, 232)
                c.drawCircle(cx, cx, r * 0.016f, lp)
            }
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = r * 0.010f
            color = ColorUtils.setAlphaComponent(darken(spec.base, 0.55f), 220)
        }
        c.drawCircle(cx, cx, labelR, ring)
    }

    private fun lighten(color: Int, f: Float) = ColorUtils.blendARGB(color, Color.WHITE, f)
    private fun darken(color: Int, f: Float) = ColorUtils.blendARGB(color, Color.BLACK, f)
}

package app.vinylplayer.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.random.Random

/**
 * The "collection of records". To add a new look: add an enum entry, implement it in
 * [VinylRenderer.drawSurface], and add it to the pools in [VinylDesigner.design].
 */
enum class VinylStyle { CLASSIC, SOLID, SPLATTER, MARBLE, SPLIT, SWIRL }

data class VinylSpec(
    val style: VinylStyle,
    val base: Int,
    val accent: Int,
    val seed: Long,
)

/** Picks a style + colours for a track, deterministically (same album => same record). */
object VinylDesigner {

    fun design(art: Bitmap?, key: String, hasSession: Boolean): VinylSpec {
        val seed = key.hashCode().toLong()
        val rnd = Random(seed)

        if (!hasSession) {
            return VinylSpec(VinylStyle.CLASSIC, Color.rgb(22, 22, 22), Color.WHITE, seed)
        }

        if (art == null) {
            val hue = rnd.nextFloat() * 360f
            val pool = listOf(VinylStyle.SOLID, VinylStyle.SPLATTER, VinylStyle.MARBLE, VinylStyle.SWIRL)
            return VinylSpec(
                pool[rnd.nextInt(pool.size)],
                hsl(hue, 0.55f, 0.45f),
                hsl(hue, 0.25f, 0.94f),
                seed,
            )
        }

        val palette = Palette.from(art).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatch?.rgb ?: Color.DKGRAY, hsl)
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]
        val gray = s < 0.14f

        val base: Int
        val accent: Int
        val pool: List<VinylStyle>
        if (gray) {
            val light = l > 0.62f
            base = if (light) hsl(0f, 0f, 0.86f) else hsl(0f, 0f, 0.09f)
            accent = if (light) hsl(0f, 0f, 0.10f) else hsl(0f, 0f, 0.95f)
            pool = listOf(VinylStyle.CLASSIC, VinylStyle.CLASSIC, VinylStyle.SPLATTER, VinylStyle.MARBLE)
        } else {
            base = hsl(h, s.coerceIn(0.42f, 0.9f), l.coerceIn(0.30f, 0.60f))
            accent = when (rnd.nextInt(4)) {
                0 -> hsl(h, 0.25f, 0.94f)          // near white
                1 -> hsl(h, 0.60f, 0.82f)          // light tint
                2 -> hsl(h, 0.50f, 0.12f)          // near black
                else -> hsl(h + 180f, 0.60f, 0.55f) // complementary
            }
            pool = listOf(
                VinylStyle.SOLID, VinylStyle.SPLATTER, VinylStyle.MARBLE, VinylStyle.SPLIT,
                VinylStyle.SWIRL, VinylStyle.SPLATTER, VinylStyle.MARBLE,
            )
        }
        return VinylSpec(pool[rnd.nextInt(pool.size)], base, accent, seed)
    }

    private fun hsl(h: Float, s: Float, l: Float): Int =
        ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
}

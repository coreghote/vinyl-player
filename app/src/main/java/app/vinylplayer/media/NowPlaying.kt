package app.vinylplayer.media

import android.graphics.Bitmap
import android.os.SystemClock

/** Immutable snapshot of whatever another app is currently playing. */
data class NowPlaying(
    val hasSession: Boolean = false,
    val sourcePackage: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: Bitmap? = null,
    val durationMs: Long = 0L,
    /** Position reported by the player at [positionAtElapsedMs] (SystemClock.elapsedRealtime base). */
    val positionMs: Long = 0L,
    val positionAtElapsedMs: Long = 0L,
    /** 0 when not playing, otherwise playback speed (normally 1). */
    val speed: Float = 0f,
    val isPlaying: Boolean = false,
    val canSeek: Boolean = false,
) {
    /** Identity of the track; changes => new disc. Empty when there is no session. */
    val trackKey: String
        get() = if (!hasSession) "" else "$title|$artist|$album"

    /** Interpolated position right now. */
    fun positionNow(now: Long = SystemClock.elapsedRealtime()): Long {
        var p = positionMs + ((now - positionAtElapsedMs) * speed).toLong()
        if (durationMs > 0) p = p.coerceAtMost(durationMs)
        return p.coerceAtLeast(0L)
    }

    /** 0..1, or 0 when the duration is unknown. */
    fun progressNow(): Float =
        if (durationMs > 0) (positionNow().toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

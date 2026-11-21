package app.vinylplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** User preferences, persisted in SharedPreferences and observable from Compose. */
object AppSettings {
    const val SPEED_MIN = 0.25f
    const val SPEED_MAX = 2f

    private var prefs: android.content.SharedPreferences? = null

    /** Visual rotation speed factor; 1.0 = 100 % (the original speed). */
    var spinSpeed by mutableFloatStateOf(1f); private set
    var hideStatusBar by mutableStateOf(false); private set
    var keepScreenOn by mutableStateOf(false); private set
    var theme by mutableStateOf(ThemeMode.SYSTEM); private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        spinSpeed = p.getFloat("spin", 1f).coerceIn(SPEED_MIN, SPEED_MAX)
        hideStatusBar = p.getBoolean("hide_status", false)
        keepScreenOn = p.getBoolean("keep_screen_on", false)
        theme = runCatching { ThemeMode.valueOf(p.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun updateSpinSpeed(v: Float) {
        spinSpeed = v.coerceIn(SPEED_MIN, SPEED_MAX)
        prefs?.edit()?.putFloat("spin", spinSpeed)?.apply()
    }

    fun updateHideStatusBar(v: Boolean) {
        hideStatusBar = v
        prefs?.edit()?.putBoolean("hide_status", v)?.apply()
    }

    fun updateKeepScreenOn(v: Boolean) {
        keepScreenOn = v
        prefs?.edit()?.putBoolean("keep_screen_on", v)?.apply()
    }

    fun updateTheme(v: ThemeMode) {
        theme = v
        prefs?.edit()?.putString("theme", v.name)?.apply()
    }
}

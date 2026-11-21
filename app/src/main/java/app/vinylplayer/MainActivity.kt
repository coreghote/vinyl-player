package app.vinylplayer

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.vinylplayer.media.NowPlayingRepo
import app.vinylplayer.media.NowPlayingService
import app.vinylplayer.AppSettings
import app.vinylplayer.ui.VinylScreen

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NowPlayingRepo.init(this)
        AppSettings.init(this)
        // edge-to-edge must be applied before the first layout, otherwise the window keeps the
        // status-bar inset and a hidden status bar leaves a black strip on top
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val dark = when (AppSettings.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
                onDispose { }
            }
            val hide = AppSettings.hideStatusBar
            DisposableEffect(hide) {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (hide) c.hide(WindowInsetsCompat.Type.statusBars())
                else c.show(WindowInsetsCompat.Type.statusBars())
                onDispose { c.show(WindowInsetsCompat.Type.statusBars()) }
            }
            val keepOn = AppSettings.keepScreenOn
            DisposableEffect(keepOn) {
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            VinylScreen(hasAccess = hasAccess, dark = dark, onOpenSettings = ::openListenerSettings)
        }
    }

    override fun onResume() {
        super.onResume()
        // leave/return (e.g. the notification-access screen) can bring the bar back
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (AppSettings.hideStatusBar) c.hide(WindowInsetsCompat.Type.statusBars())
        else c.show(WindowInsetsCompat.Type.statusBars())
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (hasAccess && !NowPlayingRepo.serviceConnected) {
            // the system sometimes leaves a granted listener unbound (e.g. after an app update)
            NotificationListenerService.requestRebind(ComponentName(this, NowPlayingService::class.java))
        }
    }

    private fun openListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

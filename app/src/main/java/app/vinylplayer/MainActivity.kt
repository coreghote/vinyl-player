package app.vinylplayer

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import app.vinylplayer.media.NowPlayingRepo
import app.vinylplayer.media.NowPlayingService
import app.vinylplayer.ui.VinylScreen

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NowPlayingRepo.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            VinylScreen(hasAccess = hasAccess, onOpenSettings = ::openListenerSettings)
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = NotificationManagerCompat.getEnabledListenerPackageNames(this).contains(packageName)
        if (hasAccess && !NowPlayingRepo.serviceConnected) {
            // the system sometimes leaves a granted listener unbound (e.g. after an app update)
            NotificationListenerService.requestRebind(ComponentName(this, NowPlayingService::class.java))
        }
    }

    private fun openListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

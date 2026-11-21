package app.vinylplayer.media

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

/**
 * Exists only to hold "notification access". Once the user grants it, the system binds this service
 * and we may enumerate the active media sessions of other apps (title, artist, cover, state, controls).
 */
class NowPlayingService : NotificationListenerService() {

    private var manager: MediaSessionManager? = null
    private val component by lazy { ComponentName(this, NowPlayingService::class.java) }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            NowPlayingRepo.onSessions(controllers)
        }

    override fun onCreate() {
        super.onCreate()
        NowPlayingRepo.init(this)
    }

    override fun onListenerConnected() {
        NowPlayingRepo.serviceConnected = true
        val m = getSystemService(MediaSessionManager::class.java)
        manager = m
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, component)
            NowPlayingRepo.onSessions(m.getActiveSessions(component))
        } catch (e: SecurityException) {
            // access was revoked in between; the UI will ask again
        }
    }

    override fun onListenerDisconnected() {
        NowPlayingRepo.serviceConnected = false
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        NowPlayingRepo.onSessions(null)
    }
}

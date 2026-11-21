package app.vinylplayer.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Single source of truth for "what is playing right now" and the transport commands.
 *
 * Fed by [NowPlayingService] (which holds notification-listener access and therefore may call
 * MediaSessionManager.getActiveSessions). All mutable state is confined to the main thread.
 */
object NowPlayingRepo {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    /** True while the system has our NotificationListenerService bound. */
    @Volatile
    var serviceConnected: Boolean = false

    @Volatile
    private var selfPackage: String = ""
    private var appContext: Context? = null

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val registered = LinkedHashMap<MediaSession.Token, MediaController>()
    private var ordered: List<MediaController> = emptyList()
    private var current: MediaController? = null

    // art caches
    private var rawArt: Bitmap? = null
    private var softArt: Bitmap? = null
    private var uriKey: String? = null
    private var uriArt: Bitmap? = null
    private var uriJob: Job? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = select(ordered)
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = select(ordered)
    }

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            selfPackage = context.packageName
        }
    }

    /** Called by the listener service (any thread). */
    fun onSessions(list: List<MediaController>?) {
        main.post { select(list ?: emptyList()) }
    }

    // ---------------------------------------------------------------- commands

    fun play() {
        current?.transportControls?.play()
        optimistic(true)
    }

    fun pause() {
        current?.transportControls?.pause()
        optimistic(false)
    }

    fun toggle() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun next() {
        current?.transportControls?.skipToNext()
    }

    fun previous() {
        current?.transportControls?.skipToPrevious()
    }

    fun seekTo(ms: Long) {
        current?.transportControls?.seekTo(ms)
        _state.update { it.copy(positionMs = ms, positionAtElapsedMs = SystemClock.elapsedRealtime()) }
    }

    /** Update UI state immediately; the player's own callback will confirm or correct it. */
    private fun optimistic(playing: Boolean) {
        _state.update { s ->
            val now = SystemClock.elapsedRealtime()
            s.copy(
                positionMs = s.positionNow(now),
                positionAtElapsedMs = now,
                isPlaying = playing,
                speed = if (playing) 1f else 0f,
            )
        }
    }

    // ---------------------------------------------------------------- session tracking (main thread)

    private fun select(list: List<MediaController>) {
        val fresh = list.filter { it.packageName != selfPackage }
        val tokens = fresh.map { it.sessionToken }.toSet()

        for (t in registered.keys.filter { it !in tokens }) {
            registered.remove(t)?.let { runCatching { it.unregisterCallback(callback) } }
        }
        for (c in fresh) {
            if (c.sessionToken !in registered) {
                registered[c.sessionToken] = c
                c.registerCallback(callback, main)
            }
        }
        ordered = fresh.mapNotNull { registered[it.sessionToken] }

        // Prefer something that is playing, then stay on the current one, then the first.
        current = ordered.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: ordered.firstOrNull { it.sessionToken == current?.sessionToken }
            ?: ordered.firstOrNull()
        publish()
    }

    private fun publish() {
        val c = current
        if (c == null) {
            _state.value = NowPlaying()
            return
        }
        val md = c.metadata
        val ps = c.playbackState

        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val st = ps?.state ?: PlaybackState.STATE_NONE
        val playing = st == PlaybackState.STATE_PLAYING ||
            st == PlaybackState.STATE_BUFFERING ||
            st == PlaybackState.STATE_SKIPPING_TO_NEXT ||
            st == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
            st == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM
        val pos = ps?.position?.takeIf { it >= 0 } ?: 0L
        val at = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
        val speed = if (st == PlaybackState.STATE_PLAYING) (ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f) else 0f
        val canSeek = ps != null && (ps.actions and PlaybackState.ACTION_SEEK_TO) != 0L

        _state.value = NowPlaying(
            hasSession = true,
            sourcePackage = c.packageName ?: "",
            title = title,
            artist = artist,
            album = album,
            art = md?.let { resolveArt(it) },
            durationMs = duration,
            positionMs = pos,
            positionAtElapsedMs = at,
            speed = speed,
            isPlaying = playing,
            canSeek = canSeek,
        )
    }

    // ---------------------------------------------------------------- cover art

    private fun resolveArt(md: MediaMetadata): Bitmap? {
        val raw = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (raw != null) return softAndSmall(raw)

        val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        if (uri.isNullOrBlank()) return null
        if (uri == uriKey) return uriArt
        loadUri(uri)
        return null
    }

    /** Software copy, max 800px on the long side, cached by identity of the raw bitmap. */
    private fun softAndSmall(raw: Bitmap): Bitmap {
        if (raw === rawArt) softArt?.let { return it }
        var bmp = raw
        if (bmp.config == Bitmap.Config.HARDWARE) {
            bmp = bmp.copy(Bitmap.Config.ARGB_8888, false) ?: return raw
        }
        val m = max(bmp.width, bmp.height)
        if (m > MAX_ART) {
            val s = MAX_ART.toFloat() / m
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * s).roundToInt().coerceAtLeast(1),
                (bmp.height * s).roundToInt().coerceAtLeast(1),
                true,
            )
        }
        rawArt = raw
        softArt = bmp
        return bmp
    }

    private fun loadUri(uri: String) {
        uriJob?.cancel()
        uriKey = uri
        uriArt = null
        uriJob = scope.launch {
            val bmp = runCatching { decodeUri(uri) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (uriKey == uri && bmp != null) {
                    uriArt = bmp
                    publish()
                }
            }
        }
    }

    private fun open(uri: String): InputStream? =
        if (uri.startsWith("http", ignoreCase = true)) {
            URL(uri).openConnection().apply {
                connectTimeout = 6000
                readTimeout = 6000
            }.getInputStream()
        } else {
            appContext?.contentResolver?.openInputStream(Uri.parse(uri))
        }

    private fun decodeUri(uri: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_ART * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return open(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private const val MAX_ART = 800
}

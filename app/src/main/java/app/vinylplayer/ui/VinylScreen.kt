package app.vinylplayer.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vinylplayer.AppSettings
import app.vinylplayer.R
import app.vinylplayer.media.NowPlaying
import app.vinylplayer.media.NowPlayingRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Colours of the screen and settings panel for the light / dark theme. */
class VinylPalette(
    val bg: Color, val title: Color, val subtitle: Color,
    val panel: Color, val track: Color, val accent: Color, val onAccent: Color,
    val grain: Int,
)

private val LightPalette = VinylPalette(
    Color(0xFFEFEDE8), Color(0xFF2F2F2F), Color(0xFF8E8C88),
    Color(0xFFF8F6F1), Color(0xFFDAD6CE), Color(0xFF2F2F2F), Color.White,
    android.graphics.Color.rgb(90, 80, 70),
)
private val DarkPalette = VinylPalette(
    Color(0xFF1B1A19), Color(0xFFEDEBE6), Color(0xFF9A9791),
    Color(0xFF2A2927), Color(0xFF45433F), Color(0xFFEDEBE6), Color(0xFF1B1A19),
    android.graphics.Color.rgb(200, 190, 180),
)

/** What is currently drawn: pre-rendered record + the text that goes with it. */
private class ShownDisc(
    val key: String,
    val bitmap: ImageBitmap,
    val title: String,
    val artist: String,
    val hasSession: Boolean,
)

/** Jobs that must outlive recompositions. */
private class Motion {
    var slideOut: Job? = null
    var slideIn: Job? = null
    var fallback: Job? = null
}

@Composable
fun VinylScreen(hasAccess: Boolean, dark: Boolean, onOpenSettings: () -> Unit) {
    val np by NowPlayingRepo.state.collectAsState()
    val pal = if (dark) DarkPalette else LightPalette
    BoxWithConstraints(Modifier.fillMaxSize().background(pal.bg)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val geo = remember(wPx, hPx) { Geo(wPx, hPx) }
        Scene(np, geo, hasAccess, pal, onOpenSettings)
    }
}

@Composable
private fun Scene(np: NowPlaying, geo: Geo, hasAccess: Boolean, pal: VinylPalette, onOpenSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latestNp by rememberUpdatedState(np)
    val motion = remember { Motion() }

    // ------------------------------------------------------------------ state
    var shown by remember { mutableStateOf<ShownDisc?>(null) }
    var swipeX by remember { mutableFloatStateOf(0f) }     // px offset of record + text
    var pendingDir by remember { mutableIntStateOf(0) }    // -1 swiped left (next), +1 right (previous)
    var swipeLock by remember { mutableStateOf(false) }    // ignore touches while a swipe is in flight

    var dragging by remember { mutableStateOf(false) }     // user holds the tonearm head
    var dragAngle by remember { mutableFloatStateOf(0f) }
    val armAngle = remember { Animatable(0f) }
    val armLift = remember { Animatable(1f) }

    var showSettings by remember { mutableStateOf(false) }
    val settingsOpen by rememberUpdatedState(showSettings)
    val speedFactor by rememberUpdatedState(AppSettings.spinSpeed)

    var discAngle by remember { mutableFloatStateOf(0f) }
    val spin = remember { Animatable(0f) }

    // ------------------------------------------------------------------ render the record for a track
    LaunchedEffect(np.trackKey, np.art != null, geo) {
        val snap = latestNp
        val bmp = withContext(Dispatchers.Default) {
            val spec = VinylDesigner.design(snap.art, snap.trackKey, snap.hasSession)
            VinylRenderer.render(geo.discPx, spec, snap.art, snap.title).asImageBitmap()
        }
        // if this change came from our own swipe, let the slide-out finish first
        if (pendingDir != 0) motion.slideOut?.join()

        val prev = shown
        shown = ShownDisc(snap.trackKey, bmp, snap.title, snap.artist, snap.hasSession)

        if (prev != null && prev.key != snap.trackKey) {
            motion.fallback?.cancel()
            val dir = pendingDir
            pendingDir = 0
            val start = if (dir > 0) geo.offLeft else geo.offRight
            swipeX = start
            motion.slideIn?.cancel()
            motion.slideIn = scope.launch {
                animate(start, 0f, animationSpec = spring(0.82f, Spring.StiffnessLow)) { v, _ -> swipeX = v }
                swipeLock = false
            }
        }
    }

    // ------------------------------------------------------------------ record rotation (33 1/3 rpm)
    val spinning = np.isPlaying && !dragging
    LaunchedEffect(spinning) {
        spin.animateTo(
            if (spinning) 1f else 0f,
            tween(if (spinning) 1000 else 1800, easing = LinearOutSlowInEasing),
        )
    }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) {
                    discAngle = (discAngle + spin.value * 200f * speedFactor * ((t - last) / 1_000_000_000f)) % 360f
                }
                last = t
            }
        }
    }

    // ------------------------------------------------------------------ tonearm follows playback
    LaunchedEffect(np.isPlaying, np.trackKey, dragging, geo) {
        if (dragging) return@LaunchedEffect
        if (latestNp.isPlaying && latestNp.hasSession) {
            armLift.animateTo(1f, tween(180))
            armAngle.animateTo(geo.angleForProgress(latestNp.progressNow()), tween(650, easing = FastOutSlowInEasing))
            armLift.animateTo(0f, tween(220))
            while (true) {
                val target = withFrameNanos { geo.angleForProgress(latestNp.progressNow()) }
                armAngle.snapTo(target)
            }
        } else {
            armLift.animateTo(1f, tween(220))
            armAngle.animateTo(0f, tween(650, easing = FastOutSlowInEasing))
        }
    }

    // ------------------------------------------------------------------ gesture actions
    fun beginArm() {
        dragAngle = armAngle.value
        if (latestNp.isPlaying) NowPlayingRepo.pause()   // lifting the head = pause
        dragging = true
        scope.launch { armLift.animateTo(1f, tween(120)) }
    }

    fun updateArm(p: Offset) {
        dragAngle = geo.angleAt(p).coerceIn(0f, geo.thetaInner)
    }

    fun endArm() {
        val a = dragAngle
        val onRecord = a >= geo.thetaOuter - 1.5f
        scope.launch {
            armAngle.snapTo(a)
            if (onRecord) {                                // putting the head down = play (from there)
                val cur = latestNp
                if (cur.durationMs > 0) {
                    val prog = ((a - geo.thetaOuter) / (geo.thetaInner - geo.thetaOuter)).coerceIn(0f, 1f)
                    NowPlayingRepo.seekTo((prog * cur.durationMs).toLong())
                }
                NowPlayingRepo.play()
            }
            dragging = false
        }
    }

    fun springBack() {
        motion.slideIn?.cancel()
        motion.slideIn = scope.launch {
            animate(swipeX, 0f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) { v, _ -> swipeX = v }
            swipeLock = false
        }
    }

    fun endSwipe(vx: Float) {
        val threshold = geo.w * 0.2f
        val dir = when {
            swipeX < -threshold || vx < -1100f -> -1
            swipeX > threshold || vx > 1100f -> 1
            else -> 0
        }
        if (dir == 0) {
            springBack()
            return
        }
        swipeLock = true
        pendingDir = dir
        if (dir < 0) NowPlayingRepo.next() else NowPlayingRepo.previous()
        motion.slideOut = scope.launch {
            val target = if (dir < 0) geo.offLeft else geo.offRight
            animate(swipeX, target, animationSpec = tween(220, easing = FastOutLinearInEasing)) { v, _ -> swipeX = v }
        }
        // the player may refuse (no next track): bring the old record back
        motion.fallback = scope.launch {
            delay(1600)
            if (pendingDir != 0) {
                pendingDir = 0
                motion.slideOut?.join()
                springBack()
            }
        }
    }

    // ------------------------------------------------------------------ drawing helpers
    val noisePaint = remember(pal) {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val rnd = java.util.Random(7)
        val r = android.graphics.Color.red(pal.grain)
        val g = android.graphics.Color.green(pal.grain)
        val b = android.graphics.Color.blue(pal.grain)
        for (y in 0 until 128) for (x in 0 until 128) {
            bmp.setPixel(x, y, android.graphics.Color.argb(rnd.nextInt(16), r, g, b))
        }
        Paint().apply { shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }
    val discShadow = remember(geo) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            alpha = 70
            maskFilter = BlurMaskFilter(geo.r * 0.035f, BlurMaskFilter.Blur.NORMAL)
        }
    }
    val sheen = remember(geo) {
        val shader = SweepGradient(
            geo.r, geo.r,
            intArrayOf(0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.07f, 0.14f, 0.5f, 0.57f, 0.64f, 1f),
        )
        val m = Matrix()
        m.setRotate(-35f, geo.r, geo.r)
        shader.setLocalMatrix(m)
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    }
    val arm = remember(geo, pal) { TonearmPainter(geo, dark = pal === DarkPalette) }

    // ------------------------------------------------------------------ UI
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(geo) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (settingsOpen) {
                        // the panel owns the input: swallow the rest of this gesture and return its
                        // reaction on the next one after the panel closes
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    // tap on the tonearm pivot = settings (works even with no session)
                    if ((down.position - geo.pivot).getDistance() < geo.w * 0.09f) {
                        val up = waitForUpOrCancellation()
                        if (up != null) showSettings = true
                        return@awaitEachGesture
                    }
                    if (swipeLock || !latestNp.hasSession) return@awaitEachGesture

                    val slop = viewConfiguration.touchSlop
                    val headAngle = if (dragging) dragAngle else armAngle.value
                    val onHead = (down.position - geo.stylus(headAngle)).getDistance() < geo.w * 0.13f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var active = false   // arm drag or swipe in progress

                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            // finger up
                            if (!active && !onHead &&
                                (ch.position - down.position).getDistance() < slop &&
                                (down.position - Offset(geo.cx + swipeX, geo.cy)).getDistance() <= geo.labelR
                            ) {
                                NowPlayingRepo.toggle()          // tap on the record's centre
                            }
                            break
                        }
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        val total = ch.position - down.position

                        if (onHead) {
                            if (!active && total.getDistance() > slop) {
                                active = true
                                beginArm()
                            }
                            if (active) {
                                updateArm(ch.position)
                                ch.consume()
                            }
                        } else {
                            if (!active && abs(total.x) > slop && abs(total.x) > abs(total.y)) {
                                active = true
                                motion.slideIn?.cancel()
                                swipeX += total.x
                                ch.consume()
                            } else if (active) {
                                swipeX += ch.position.x - ch.previousPosition.x
                                ch.consume()
                            }
                        }
                    }

                    if (active) {
                        if (onHead) endArm() else endSwipe(tracker.calculateVelocity().x)
                    }
                }
            },
    ) {
        // paper grain
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, noisePaint) }
        }

        // record: shadow + rotating bitmap + fixed sheen, all sliding together on swipe.
        // Drawn on a full-screen canvas at absolute geo coordinates: the record is wider than the
        // screen (it bleeds off the left edge) and a laid-out child that big gets clamped/re-centred
        // by the parent, which would put the bitmap somewhere other than geo.cx/cy.
        Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = swipeX }) {
            drawIntoCanvas {
                it.nativeCanvas.drawCircle(geo.cx, geo.cy + geo.r * 0.02f, geo.r, discShadow)
            }
            shown?.let { sd ->
                rotate(discAngle, Offset(geo.cx, geo.cy)) {
                    drawImage(
                        image = sd.bitmap,
                        dstOffset = IntOffset((geo.cx - geo.r).roundToInt(), (geo.cy - geo.r).roundToInt()),
                        dstSize = IntSize(geo.discPx, geo.discPx),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
            drawIntoCanvas {
                it.nativeCanvas.drawCircle(geo.cx, geo.cy, geo.r, sheen)
            }
        }

        // tonearm
        Canvas(Modifier.fillMaxSize()) {
            val a = if (dragging) dragAngle else armAngle.value
            val lift = armLift.value
            drawIntoCanvas { arm.draw(it.nativeCanvas, a, lift) }
        }

        // title / artist
        val sd = shown
        val title: String
        val subtitle: String
        if (sd == null) {
            title = ""
            subtitle = ""
        } else if (sd.hasSession) {
            title = sd.title.ifBlank { "—" }
            subtitle = sd.artist
        } else if (!hasAccess) {
            title = stringResource(R.string.need_access_title)
            subtitle = stringResource(R.string.need_access_hint)
        } else {
            title = stringResource(R.string.nothing_playing)
            subtitle = stringResource(R.string.start_music)
        }
        Column(
            Modifier
                .offset { IntOffset(0, (geo.h * 0.73f).roundToInt()) }
                .fillMaxWidth()
                .padding(horizontal = with(density) { (geo.w * 0.078f).toDp() })
                .graphicsLayer {
                    translationX = swipeX
                    alpha = 1f - (abs(swipeX) / (geo.w * 0.8f)).coerceIn(0f, 1f)
                },
        ) {
            BasicText(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = pal.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = with(density) { (geo.w * 0.05f).toSp() },
                ),
            )
            Spacer(Modifier.height(with(density) { (geo.w * 0.02f).toDp() }))
            BasicText(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = pal.subtitle,
                    fontWeight = FontWeight.Light,
                    fontSize = with(density) { (geo.w * 0.039f).toSp() },
                ),
            )
        }

        // the only button in the app: shown until notification access is granted
        if (!hasAccess) {
            Box(
                Modifier
                    .offset { IntOffset((geo.w * 0.078f).roundToInt(), (geo.h * 0.86f).roundToInt()) }
                    .clip(RoundedCornerShape(50))
                    .background(pal.title)
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                BasicText(
                    text = stringResource(R.string.grant_access),
                    style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
            }
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsPanel(pal) { showSettings = false }
        }
    }
}

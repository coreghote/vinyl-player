package app.vinylplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vinylplayer.AppSettings
import app.vinylplayer.R
import app.vinylplayer.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsPanel(p: VinylPalette, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(p.panel)
                .pointerInput(Unit) { detectTapGestures { } }   // swallow taps
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.settings), p.title, 20, FontWeight.Bold, Modifier.weight(1f))
                Label(
                    stringResource(R.string.close), p.subtitle, 15, FontWeight.Medium,
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(8.dp),
                )
            }

            // --- spin speed
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Label(stringResource(R.string.spin_speed), p.title, 16, FontWeight.Medium, Modifier.weight(1f))
                    Label("${(AppSettings.spinSpeed * 100).roundToInt()}%", p.subtitle, 16, FontWeight.Medium)
                }
                SpeedSlider(p)
            }

            // --- status bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.hide_status_bar), p.title, 16, FontWeight.Medium, Modifier.weight(1f))
                Toggle(AppSettings.hideStatusBar, p) { AppSettings.updateHideStatusBar(it) }
            }

            // --- keep screen on
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.keep_screen_on), p.title, 16, FontWeight.Medium, Modifier.weight(1f))
                Toggle(AppSettings.keepScreenOn, p) { AppSettings.updateKeepScreenOn(it) }
            }

            // --- theme
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(stringResource(R.string.theme), p.title, 16, FontWeight.Medium)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(p.track),
                ) {
                    val opts = listOf(
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                        ThemeMode.SYSTEM to R.string.theme_system,
                    )
                    for ((mode, res) in opts) {
                        val sel = AppSettings.theme == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) p.accent else Color.Transparent)
                                .clickable { AppSettings.updateTheme(mode) }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Label(stringResource(res), if (sel) p.onAccent else p.subtitle, 14, FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, color: Color, sp: Int, weight: FontWeight, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = sp.sp, fontWeight = weight))
}

@Composable
private fun Toggle(on: Boolean, p: VinylPalette, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) p.accent else p.track)
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(if (on) p.onAccent else p.subtitle))
    }
}

@Composable
private fun SpeedSlider(p: VinylPalette) {
    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val min = AppSettings.SPEED_MIN
    val max = AppSettings.SPEED_MAX
    fun valueAt(x: Float): Float {
        val k = with(density) { 12.dp.toPx() }
        val f = ((x - k) / (widthPx - 2 * k).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val raw = min + f * (max - min)
        val snapped = (raw / 0.05f).roundToInt() * 0.05f
        return if (kotlin.math.abs(snapped - 1f) < 0.04f) 1f else snapped   // sticky 100 %
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) { detectTapGestures { AppSettings.updateSpinSpeed(valueAt(it.x)) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { AppSettings.updateSpinSpeed(valueAt(it.x)) },
                    onDrag = { c, _ -> c.consume(); AppSettings.updateSpinSpeed(valueAt(c.position.x)) },
                )
            },
    ) {
        val th = with(density) { 4.dp.toPx() }
        val knob = with(density) { 12.dp.toPx() }
        val usable = size.width - 2 * knob
        val frac = (AppSettings.spinSpeed - min) / (max - min)
        val x = knob + usable * frac
        val cy = size.height / 2f
        drawRoundRect(p.track, Offset(knob, cy - th / 2), Size(usable, th), CornerRadius(th))
        drawRoundRect(p.accent, Offset(knob, cy - th / 2), Size(x - knob, th), CornerRadius(th))
        val tick = knob + usable * ((1f - min) / (max - min))
        drawCircle(p.subtitle, th / 2, Offset(tick, cy + knob * 1.25f))
        drawCircle(p.accent, knob, Offset(x, cy))
    }
}

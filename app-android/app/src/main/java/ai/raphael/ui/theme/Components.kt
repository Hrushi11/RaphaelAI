package ai.raphael.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------- surfaces

/** Card: one surface step above the page with a hairline. `highlighted` = the selected one. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    radius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(if (highlighted) Rc.Raised else Rc.Card)
            .border(1.dp, if (highlighted) Rc.HairlineStrong else Rc.Hairline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Small raised control holding a row (top bar device name). */
@Composable
fun Pill(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(shape)
            .background(Rc.Raised)
            .border(1.dp, Rc.Hairline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Secondary round button (stop, pause, settings, back). `ring` marks it as switched on. */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    ring: Color? = null,
    tint: Color = Rc.Text2,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Rc.Raised)
            .border(1.dp, ring ?: Rc.Hairline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (ring != null) Rc.Text else tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun BackButton(onClick: () -> Unit) =
    RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick, size = 48.dp)

/**
 * The main round button (ask / finish / stop / resume / retry): off-white with a dark icon.
 * `live` = Raphael is hearing you right now; it gets a thin accent ring (static, no pulsing).
 */
@Composable
fun MainButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, live: Boolean = false, size: Dp = 80.dp) {
    Box(
        Modifier
            .size(size + 12.dp)
            .then(if (live) Modifier.border(2.dp, Rc.Accent, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Rc.Text)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = Rc.Page, modifier = Modifier.size(32.dp))
        }
    }
}

/** Primary action button ("Get started", "Allow", "Continue", "Done"): off-white fill, dark text. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(Rc.Text)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Rc.Page)
    }
}

/**
 * Status row under the diamond. The dot is Raphael's signature "on-air" light:
 * green = can hear you, accent = Raphael's turn, amber = can't hear you, muted = connecting.
 */
@Composable
fun StatusChip(text: String, color: Color, hollow: Boolean = false) {
    Pill {
        StatusDot(color, hollow)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Rc.Text)
    }
}

@Composable
fun StatusDot(color: Color, hollow: Boolean = false) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .then(if (hollow) Modifier.border(1.5.dp, color, CircleShape) else Modifier.background(color))
    )
}

/** Line icon in a fixed-width box so row labels line up. */
@Composable
fun RowIcon(icon: ImageVector, tint: Color = Rc.Text2) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun RaphaelSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) = Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    colors = SwitchDefaults.colors(
        checkedThumbColor = Rc.Text,
        checkedTrackColor = Rc.Violet,
        checkedBorderColor = Rc.Violet,
        uncheckedThumbColor = Rc.Muted,
        uncheckedTrackColor = Rc.Raised,
        uncheckedBorderColor = Rc.HairlineStrong,
    ),
)

/** Onboarding screen title. */
@Composable
fun Title(text: String) {
    Text(
        text, style = MaterialTheme.typography.displayMedium, color = Rc.Text,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- diamond

enum class DiamondMood { IDLE, LISTENING, THINKING, SPEAKING, PAUSED, ERROR }

@DrawableRes
private fun DiamondMood.image(): Int = when (this) {
    DiamondMood.IDLE -> ai.raphael.R.drawable.diamond_idle
    DiamondMood.LISTENING -> ai.raphael.R.drawable.diamond_listening
    DiamondMood.THINKING -> ai.raphael.R.drawable.diamond_thinking
    DiamondMood.SPEAKING -> ai.raphael.R.drawable.diamond_speaking
    DiamondMood.PAUSED -> ai.raphael.R.drawable.diamond_paused
    DiamondMood.ERROR -> ai.raphael.R.drawable.diamond_error
}

/**
 * The glass diamond: the one bright element in the app. Artwork from the mocks (design/tools/extract_diamonds.py).
 * Motion only when it means something: it swells with the voice while listening/speaking and shifts slowly while
 * thinking. Idle, paused and error are still.
 */
@Composable
fun Diamond(mood: DiamondMood, level: () -> Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "diamond")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "phase")
    var smooth by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mood) {
        while (true) withFrameNanos { smooth += (level() - smooth) * 0.25f }
    }
    val scale = when (mood) {
        DiamondMood.LISTENING, DiamondMood.SPEAKING -> 1f + 0.10f * smooth
        DiamondMood.THINKING -> 1f + 0.03f * sin(phase * 3)
        else -> 1f
    }
    val alpha = if (mood == DiamondMood.PAUSED) 0.85f else 1f
    Crossfade(mood, modifier, animationSpec = tween(400), label = "mood") { m ->
        Image(
            painterResource(m.image()),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
        )
    }
}

@Composable
fun SmallDiamond(modifier: Modifier = Modifier) {
    Image(
        painterResource(ai.raphael.R.drawable.diamond_small), null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

// ---------------------------------------------------------------- waveform

enum class WaveStyle { DOTS, BARS_VOICE, SINE, FLAT_DASHED }

/** Horizontal waveform under the card, in one colour. Bars follow the live audio level. */
@Composable
fun Waveform(style: WaveStyle, level: () -> Float, modifier: Modifier = Modifier, color: Color = Rc.Text2) {
    var time by remember { mutableFloatStateOf(0f) }
    var smooth by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(style) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) time += (now - last) / 1e9f
            last = now
            smooth += (level() - smooth) * 0.3f
        }
    }
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        val mid = size.height / 2
        when (style) {
            WaveStyle.DOTS -> {
                val n = 40
                for (i in 0 until n) {
                    val x = size.width * (i + 0.5f) / n
                    drawCircle(Rc.Muted, radius = 1.5.dp.toPx(), center = Offset(x, mid), alpha = 0.5f)
                }
            }
            WaveStyle.FLAT_DASHED -> drawLine(
                Rc.Muted, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)), alpha = 0.6f,
            )
            WaveStyle.SINE -> {
                val path = androidx.compose.ui.graphics.Path()
                val steps = 120
                for (i in 0..steps) {
                    val x = size.width * i / steps
                    val env = sin(PI.toFloat() * i / steps)
                    val y = mid + env * 12.dp.toPx() * sin(i / 7f - time * 3f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
            WaveStyle.BARS_VOICE -> {
                val n = 48
                val barW = size.width / n * 0.4f
                val amp = 0.12f + 0.88f * smooth
                for (i in 0 until n) {
                    val u = i / (n - 1f) // 0..1
                    val env = 1f - abs(u - 0.5f) * 2f // tallest in the middle, tapering to the edges
                    val jitter = 0.55f + 0.45f * abs(sin(i * 1.7f + time * 9f) * sin(i * 0.63f - time * 5f))
                    val h = (4.dp.toPx() + env * env * amp * jitter * size.height * 0.9f)
                    val x = size.width * (i + 0.5f) / n
                    drawRoundRect(
                        color, topLeft = Offset(x - barW / 2, mid - h / 2), size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
            }
        }
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Centered(content: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { content() }

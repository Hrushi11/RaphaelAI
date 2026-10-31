package ai.raphael.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** Frosted glass card (6 % white, thin violet border, 24 dp radius). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dashed: Boolean = false,
    radius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val border = if (highlighted) Brush.linearGradient(listOf(Rc.Magenta, Rc.Blue, Rc.Cyan))
    else Brush.linearGradient(listOf(Color(0x557C4DFF), Color(0x403D7BFF)))
    Box(
        modifier
            .then(if (highlighted) Modifier.shadow(24.dp, shape, ambientColor = Rc.Violet, spotColor = Rc.Violet) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x1A8C78FF), Color(0x0F3D7BFF))))
            .then(
                if (dashed) Modifier.drawBehind {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(Rc.Magenta, Rc.Cyan)),
                        cornerRadius = CornerRadius(radius.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                } else Modifier.border(if (highlighted) 1.5.dp else 1.dp, border, shape)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Rounded pill (top bar "Raphael · Nord Buds 2", chips). */
@Composable
fun GlassPill(modifier: Modifier = Modifier, borderColor: Color = Color(0x337C4DFF), onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color(0x1A8C78FF))
            .border(1.dp, borderColor, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Small dark round button (✕, ‖, gear, back). */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    ring: Color? = null,
    tint: Color = Rc.Text,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x1F8C78FF))
            .border(if (ring != null) 2.dp else 1.dp, ring ?: Color(0x267C4DFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun BackButton(onClick: () -> Unit) =
    RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick, size = 48.dp)

/** Big glowing center button (mic / stop / play / retry) with optional ripple rings. */
@Composable
fun GlowButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    color: Color = Rc.Blue,
    ripples: Boolean = false,
    size: Dp = 88.dp,
) {
    val t = rememberInfiniteTransition(label = "glow")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse")
    Box(Modifier.size(size * 1.9f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.toPx() / 2
            // soft halo
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), radius = r * 1.9f), radius = r * 1.9f)
            if (ripples) for (k in 0..1) {
                val p = (pulse + k * 0.5f) % 1f
                drawCircle(color.copy(alpha = 0.45f * (1 - p)), radius = r * (1.05f + 0.8f * p), style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size(size)
                .shadow(28.dp, CircleShape, ambientColor = color, spotColor = color)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.55f), Color(0xFF101850))))
                .border(3.dp, Brush.linearGradient(listOf(Color(0xFFE6F0FF), color, Color(0xFFB9D4FF))), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.42f))
        }
    }
}

/** Wide violet→blue gradient pill button with glow ("Get started", "Allow", "Continue", "Done"). */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = CircleShape
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(26.dp, shape, ambientColor = Rc.Violet, spotColor = Rc.Blue)
            .clip(shape)
            .background(Rc.Primary)
            .border(1.dp, Color(0x55FFFFFF), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

/** Status chip under the diamond: coloured dot + text. */
@Composable
fun StatusChip(text: String, color: Color, hollow: Boolean = false, textColor: Color = color) {
    GlassPill(borderColor = color.copy(alpha = 0.35f)) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .then(if (hollow) Modifier.border(1.5.dp, color, CircleShape) else Modifier.background(color))
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}

/** Round violet tile holding a line icon (permission rows, settings rows). */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 56.dp, tint: Color = Rc.Lavender) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0x447C4DFF), Color(0x1A7C4DFF))))
            .border(1.dp, Color(0x447C4DFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun RaphaelSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) = Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = Rc.Violet,
        checkedBorderColor = Rc.Violet,
        uncheckedThumbColor = Rc.TextDim,
        uncheckedTrackColor = Color(0x22FFFFFF),
        uncheckedBorderColor = Color(0x44FFFFFF),
    ),
)

/** Two-line onboarding title, second line in lavender ("Raphael needs / a few things"). */
@Composable
fun TwoToneTitle(first: String, second: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(first, style = MaterialTheme.typography.displayMedium, color = Rc.Text, textAlign = TextAlign.Center)
        if (second != null) Text(second, style = MaterialTheme.typography.displayMedium, color = Rc.Lavender, textAlign = TextAlign.Center)
    }
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
 * The glowing glass diamond. Artwork comes from the mocks (design/tools/extract_diamonds.py);
 * motion is code: breathing when idle, swelling with the voice level when listening/speaking.
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
        DiamondMood.IDLE -> 1f + 0.02f * sin(phase)
        DiamondMood.LISTENING, DiamondMood.SPEAKING -> 1f + 0.10f * smooth + 0.015f * sin(phase * 2)
        DiamondMood.THINKING -> 1f + 0.03f * sin(phase * 3)
        DiamondMood.PAUSED, DiamondMood.ERROR -> 1f
    }
    val glow = when (mood) {
        DiamondMood.PAUSED -> 0.85f
        DiamondMood.IDLE -> 0.88f + 0.12f * sin(phase)
        else -> 1f
    }
    Crossfade(mood, modifier, animationSpec = tween(450), label = "mood") { m ->
        Image(
            painterResource(m.image()),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = glow },
        )
    }
}

@Composable
fun SmallDiamond(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "small")
    val s by t.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "s")
    Image(
        painterResource(ai.raphael.R.drawable.diamond_small), null,
        modifier = modifier.graphicsLayer { scaleX = s; scaleY = s },
        contentScale = ContentScale.Fit,
    )
}

// ---------------------------------------------------------------- waveform

enum class WaveStyle { DOTS, BARS_VOICE, SINE, FLAT_DASHED }

/** Horizontal waveform under the card. Bars follow the live audio level. */
@Composable
fun Waveform(style: WaveStyle, level: () -> Float, modifier: Modifier = Modifier) {
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
    val colors = listOf(Rc.Magenta, Rc.Violet, Rc.Blue, Rc.Cyan, Rc.Teal)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val mid = size.height / 2
        val brush = Brush.horizontalGradient(colors)
        when (style) {
            WaveStyle.DOTS -> {
                val n = 48
                for (i in 0 until n) {
                    val x = size.width * (i + 0.5f) / n
                    drawCircle(brush, radius = 2.4.dp.toPx(), center = Offset(x, mid))
                }
            }
            WaveStyle.FLAT_DASHED -> drawLine(
                brush, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)), alpha = 0.7f,
            )
            WaveStyle.SINE -> {
                val path = androidx.compose.ui.graphics.Path()
                val steps = 120
                for (i in 0..steps) {
                    val x = size.width * i / steps
                    val env = sin(PI.toFloat() * i / steps)
                    val y = mid + env * 14.dp.toPx() * sin(i / 7f - time * 3f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Brush.horizontalGradient(listOf(Rc.Magenta, Rc.Violet, Rc.Blue)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            WaveStyle.BARS_VOICE -> {
                val n = 64
                val barW = size.width / n * 0.42f
                val amp = 0.12f + 0.88f * smooth
                for (i in 0 until n) {
                    val u = i / (n - 1f) // 0..1
                    val env = 1f - abs(u - 0.5f) * 2f // tallest in the middle, tapering to the edges
                    val jitter = 0.55f + 0.45f * abs(sin(i * 1.7f + time * 9f) * sin(i * 0.63f - time * 5f))
                    val h = (4.dp.toPx() + env * env * amp * jitter * size.height * 0.95f)
                    val x = size.width * (i + 0.5f) / n
                    drawRoundRect(
                        brush, topLeft = Offset(x - barW / 2, mid - h / 2), size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
            }
        }
    }
}

/** Remembered no-op ripple source for plain clickable text. */
@Composable
fun noRipple() = remember { MutableInteractionSource() }

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Centered(content: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { content() }

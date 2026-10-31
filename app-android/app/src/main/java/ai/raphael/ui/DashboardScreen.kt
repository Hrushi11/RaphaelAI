package ai.raphael.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ai.raphael.service.Link
import ai.raphael.service.UiState
import ai.raphael.ui.theme.GlassCard
import ai.raphael.ui.theme.RaphaelBackground
import ai.raphael.ui.theme.Rc
import ai.raphael.ui.theme.RoundButton
import ai.raphael.ui.theme.SmallDiamond
import ai.raphael.ui.theme.fs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Tools the dashboard knows how to show; `server` = MCP server name from session.ready. */
private data class ToolChip(val server: String, val label: String, val icon: ImageVector)

private val TOOL_CHIPS = listOf(
    ToolChip("phone", "Phone", Icons.Rounded.Smartphone),
    ToolChip("web", "Web search", Icons.Rounded.Search),
    ToolChip("gmail", "Gmail", Icons.Rounded.Email),
    ToolChip("calendar", "Calendar", Icons.Rounded.CalendarMonth),
)

/** App home (mock 13): status, earbuds, "Talk to Raphael", connected tools. */
@Composable
fun DashboardScreen(
    ui: UiState,
    name: String,
    earbudsLabel: String,
    onSettings: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onEarbuds: () -> Unit,
    onTalk: () -> Unit,
    onConnectTool: (String) -> Unit,
) {
    // Clock that ticks every minute for the greeting and date line.
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    RaphaelBackground {
        WaveLines()
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            // Greeting + settings
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (name.isBlank()) greeting else "$greeting,",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Normal),
                        color = Rc.Text.copy(alpha = 0.9f),
                    )
                    if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.displayMedium.copy(fontSize = fs(46)), color = Rc.Text)
                    Text(
                        SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(now),
                        style = MaterialTheme.typography.bodyLarge, color = Rc.Lavender, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                RoundButton(Icons.Rounded.Settings, "Settings", onSettings, size = 52.dp)
            }
            Spacer(Modifier.height(26.dp))

            StatusPill(ui, onPause, onResume, onRetry)
            Spacer(Modifier.height(12.dp))

            // Earbuds row
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(Color(0x148C78FF))
                    .border(1.dp, Color(0x267C4DFF), CircleShape).clickable(onClick = onEarbuds)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Headphones, null, tint = Rc.Lavender, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Text(earbudsLabel, style = MaterialTheme.typography.bodyLarge, color = Rc.Text, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Rc.Lavender)
            }
            Spacer(Modifier.height(20.dp))

            // Hero: Talk to Raphael
            GlassCard(Modifier.fillMaxWidth(), highlighted = true, radius = 32.dp, onClick = onTalk) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SmallDiamond(Modifier.fillMaxWidth(0.82f).height(190.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Talk to Raphael", style = MaterialTheme.typography.headlineLarge, color = Rc.Text)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildAnnotatedString {
                            append("Say ")
                            withStyle(SpanStyle(brush = Rc.TextGradient, fontWeight = FontWeight.SemiBold)) {
                                append("“${titleCase(ui.wakePhrase.ifBlank { "hey raphael" })}”")
                            }
                            append(" or tap here")
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        color = Rc.Lavender, textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            // Connected tools
            Text(
                "CONNECTED TOOLS", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9B6BFF),
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
            )
            TOOL_CHIPS.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { chip ->
                        ToolPill(chip, connected = chip.server in ui.tools, Modifier.weight(1f)) { onConnectTool(chip.label) }
                    }
                }
            }

            // Privacy note
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = Rc.TextDim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Silence never leaves your phone.", style = MaterialTheme.typography.bodyLarge, color = Rc.TextDim)
            }
        }
    }
}

@Composable
private fun StatusPill(ui: UiState, onPause: () -> Unit, onResume: () -> Unit, onRetry: () -> Unit) {
    val mode = homeMode(ui)
    val phrase = titleCase(ui.wakePhrase.ifBlank { "hey raphael" })
    val (text, color) = when (mode) {
        HomeMode.PAUSED -> "Paused — Raphael can’t hear you" to Rc.Lavender
        HomeMode.ERROR -> "Can’t reach Raphael’s server" to Rc.Amber
        HomeMode.CONNECTING -> "Connecting…" to Rc.Cyan
        HomeMode.IDLE -> (if (ui.handsFree) "Listening for “$phrase”" else "Ready — tap to talk") to Rc.Green
        else -> "Raphael is busy…" to Rc.Cyan
    }
    val (icon, action) = when (mode) {
        HomeMode.PAUSED -> Icons.Rounded.PlayArrow to onResume
        HomeMode.ERROR -> Icons.Rounded.Sync to onRetry
        else -> Icons.Rounded.Pause to onPause
    }
    Row(
        Modifier.fillMaxWidth().clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color(0x332E7D5B).takeIf { mode == HomeMode.IDLE } ?: Color(0x1A8C78FF), Color(0x1A8C78FF))))
            .border(1.dp, Brush.horizontalGradient(listOf(color.copy(alpha = 0.45f), Color(0x557C4DFF))), CircleShape)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal), color = Rc.Text, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0x297C4DFF))
                .border(1.5.dp, Color(0x997C4DFF), CircleShape).clickable(onClick = action),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Rc.Text, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun ToolPill(chip: ToolChip, connected: Boolean, modifier: Modifier, onConnect: () -> Unit) {
    val shape = RoundedCornerShape(40.dp)
    Row(
        modifier.clip(shape)
            .background(if (connected) Color(0x1A2EE6D6) else Color(0x128C78FF))
            .border(1.dp, if (connected) Rc.Teal.copy(alpha = 0.6f) else Color(0x337C4DFF), shape)
            .then(if (connected) Modifier else Modifier.clickable(onClick = onConnect))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(chip.icon, null, tint = Rc.Text, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(chip.label, style = MaterialTheme.typography.titleMedium, color = Rc.Text, modifier = Modifier.weight(1f), maxLines = 1)
        if (connected) Icon(Icons.Rounded.Check, "Connected", tint = Rc.Green, modifier = Modifier.size(24.dp))
        else Text("+ Connect", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4DA3FF))
    }
}

/** Faint flowing lines across the background, as in mock 13. */
@Composable
private fun WaveLines() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun wave(y0: Float, amp: Float, alpha: Float, color: Color) {
            val path = Path().apply {
                moveTo(-20f, y0)
                cubicTo(w * 0.3f, y0 - amp, w * 0.6f, y0 + amp, w + 20f, y0 - amp * 0.6f)
            }
            drawPath(path, color.copy(alpha = alpha), style = Stroke(1.5.dp.toPx()))
        }
        wave(h * 0.52f, h * 0.06f, 0.35f, Rc.Blue)
        wave(h * 0.56f, h * 0.05f, 0.18f, Rc.Violet)
        wave(h * 0.90f, h * 0.05f, 0.40f, Rc.Blue)
        wave(h * 0.93f, h * 0.04f, 0.20f, Rc.Cyan)
        drawCircle(
            Brush.radialGradient(listOf(Color(0x333D7BFF), Color.Transparent), center = Offset(w, h * 0.2f), radius = w * 0.6f),
            radius = w * 0.6f, center = Offset(w, h * 0.2f),
        )
    }
}

package ai.raphael.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ai.raphael.service.UiState
import ai.raphael.ui.theme.Panel
import ai.raphael.ui.theme.RaphaelBackground
import ai.raphael.ui.theme.Rc
import ai.raphael.ui.theme.RoundButton
import ai.raphael.ui.theme.RowIcon
import ai.raphael.ui.theme.SmallDiamond
import ai.raphael.ui.theme.StatusDot
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Tools the dashboard shows; `server` = MCP server name from session.ready. */
private data class ToolRow(val server: String, val label: String, val icon: ImageVector)

private val TOOLS = listOf(
    ToolRow("phone", "Phone", Icons.Rounded.Smartphone),
    ToolRow("web", "Web search", Icons.Rounded.Search),
)

/** App home (mock 13, unslopped): status, earbuds, "Talk to Raphael", connected tools. */
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
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            // Title + settings
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (name.isBlank()) greeting else "$greeting, $name",
                        style = MaterialTheme.typography.headlineLarge, color = Rc.Text,
                    )
                    Text(
                        SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(now),
                        style = MaterialTheme.typography.bodyLarge, color = Rc.Text2, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                RoundButton(Icons.Rounded.Settings, "Settings", onSettings, size = 48.dp)
            }
            Spacer(Modifier.height(24.dp))

            StatusRow(ui, onPause, onResume, onRetry)
            Spacer(Modifier.height(8.dp))

            // Earbuds
            Panel(Modifier.fillMaxWidth(), radius = 12.dp, onClick = onEarbuds) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowIcon(Icons.Rounded.Headphones)
                    Spacer(Modifier.width(12.dp))
                    Text(earbudsLabel, style = MaterialTheme.typography.bodyLarge, color = Rc.Text, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Rc.Muted)
                }
            }
            Spacer(Modifier.height(24.dp))

            // Talk to Raphael: the one bright element on this screen is the diamond
            Panel(Modifier.fillMaxWidth(), onClick = onTalk) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SmallDiamond(Modifier.fillMaxWidth(0.8f).height(184.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Talk to Raphael", style = MaterialTheme.typography.headlineSmall, color = Rc.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildAnnotatedString {
                            append("Say ")
                            withStyle(SpanStyle(color = Rc.Text, fontWeight = FontWeight.Medium)) {
                                append("“${titleCase(ui.wakePhrase.ifBlank { "hey raphael" })}”")
                            }
                            append(" or tap here")
                        },
                        style = MaterialTheme.typography.bodyLarge, color = Rc.Text2, textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))

            // Connected tools
            Text(
                "Connected tools", style = MaterialTheme.typography.labelMedium, color = Rc.Text2,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    TOOLS.forEachIndexed { i, tool ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Rc.Hairline))
                        ToolLine(tool, connected = tool.server in ui.tools)
                    }
                }
            }

            // Privacy note
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = Rc.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Silence never leaves your phone", style = MaterialTheme.typography.bodyMedium, color = Rc.Muted)
            }
        }
    }
}

@Composable
private fun StatusRow(ui: UiState, onPause: () -> Unit, onResume: () -> Unit, onRetry: () -> Unit) {
    val mode = homeMode(ui)
    val phrase = titleCase(ui.wakePhrase.ifBlank { "hey raphael" })
    val (text, color) = when (mode) {
        HomeMode.PAUSED -> "Paused — Raphael can’t hear you" to Rc.Amber
        HomeMode.ERROR -> "Can’t reach Raphael’s server" to Rc.Amber
        HomeMode.CONNECTING -> "Connecting…" to Rc.Muted
        HomeMode.IDLE -> (if (ui.handsFree) "Listening for “$phrase”" else "Ready — tap to talk") to Rc.Green
        else -> "Answering" to Rc.Accent
    }
    val (icon, action, label) = when (mode) {
        HomeMode.PAUSED -> Triple(Icons.Rounded.PlayArrow, onResume, "Resume listening")
        HomeMode.ERROR -> Triple(Icons.Rounded.Sync, onRetry, "Retry")
        else -> Triple(Icons.Rounded.Pause, onPause, "Pause listening")
    }
    Panel(Modifier.fillMaxWidth(), radius = 12.dp) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, hollow = mode == HomeMode.PAUSED)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Rc.Text, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Rc.Raised).clickable(onClick = action),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, label, tint = Rc.Text2, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ToolLine(tool: ToolRow, connected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(tool.icon)
        Spacer(Modifier.width(12.dp))
        Text(tool.label, style = MaterialTheme.typography.bodyLarge, color = Rc.Text, modifier = Modifier.weight(1f), maxLines = 1)
        if (connected) {
            Icon(Icons.Rounded.Check, null, tint = Rc.Green, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Connected", style = MaterialTheme.typography.bodyMedium, color = Rc.Text2)
        } else {
            Text("Not connected", style = MaterialTheme.typography.bodyMedium, color = Rc.Muted)
        }
    }
}

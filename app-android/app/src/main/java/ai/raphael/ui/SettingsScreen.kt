package ai.raphael.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.raphael.service.Link
import ai.raphael.ui.theme.GlassCard
import ai.raphael.ui.theme.IconTile
import ai.raphael.ui.theme.RaphaelBackground
import ai.raphael.ui.theme.RaphaelSwitch
import ai.raphael.ui.theme.Rc

/** Settings (mock 11). */
@Composable
fun SettingsScreen(
    serverUrl: String,
    link: Link,
    headsetLabel: String,
    listenWith: String,
    wakePhrase: String,
    handsFree: Boolean,
    keepRunning: Boolean,
    version: String,
    onBack: () -> Unit,
    onServerUrl: (String) -> Unit,
    onEarbuds: () -> Unit,
    onWakeWord: () -> Unit,
    onHandsFree: (Boolean) -> Unit,
    onKeepRunning: () -> Unit,
    onAudioTest: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    RaphaelBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            Row(Modifier.padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Rc.Text,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onBack),
                )
                Spacer(Modifier.width(28.dp))
                Text("Settings", style = MaterialTheme.typography.headlineLarge, color = Rc.Text)
            }

            Section("Connection")
            GlassCard(Modifier.fillMaxWidth(), onClick = { editing = true }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Dns, size = 56.dp, tint = Rc.Text)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Server", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = Rc.Text)
                        Text(
                            serverUrl.removeSuffix("/v1/session"), style = MaterialTheme.typography.bodyMedium, color = Rc.Lavender,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    val (statusText, statusColor) = when (link) {
                        Link.READY -> "Connected" to Rc.Green
                        Link.CONNECTING -> "Connecting" to Rc.Cyan
                        Link.DISCONNECTED -> "Offline" to Rc.Amber
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(8.dp))
                    Text(statusText, style = MaterialTheme.typography.titleMedium, color = statusColor)
                    Chevron()
                }
            }

            Section("Earbuds")
            Row1(Icons.Rounded.Headphones, headsetLabel, "Listen with: $listenWith", onEarbuds)

            Section("Wake word")
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    RowContent(Icons.Rounded.GraphicEq, "Wake word", wakePhrase, onWakeWord) { Chevron() }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Color(0x267C4DFF)))
                    RowContent(Icons.Rounded.Bolt, "Hands-free", null, { onHandsFree(!handsFree) }) { RaphaelSwitch(handsFree, onHandsFree) }
                }
            }

            Section("Background")
            GlassCard(Modifier.fillMaxWidth()) {
                RowContent(Icons.Rounded.PlayCircleOutline, "Keep Raphael running", null, onKeepRunning) {
                    RaphaelSwitch(keepRunning) { onKeepRunning() }
                }
            }

            Section("Tools")
            Row1(Icons.Rounded.Hearing, "Headset audio test", null, onAudioTest)

            Text(
                "Raphael v$version", style = MaterialTheme.typography.bodyLarge, color = Rc.Lavender,
                modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (editing) ServerDialog(serverUrl, onDismiss = { editing = false }, onSave = { onServerUrl(it); editing = false })
}

@Composable
private fun Section(title: String) = Text(
    title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    color = Color(0xFF9B6BFF), modifier = Modifier.padding(start = 18.dp, top = 22.dp, bottom = 12.dp),
)

@Composable
private fun Chevron() {
    Spacer(Modifier.width(10.dp))
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Rc.TextDim, modifier = Modifier.size(28.dp))
}

@Composable
private fun Row1(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) { RowContent(icon, title, subtitle, onClick) { Chevron() } }
}

@Composable
private fun RowContent(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, size = 56.dp, tint = Rc.Text)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = Rc.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Rc.Lavender)
        }
        trailing()
    }
}

@Composable
private fun ServerDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14163A),
        title = { Text("Server", color = Rc.Text) },
        text = {
            Column {
                Text(
                    "Your computer's address on this Wi-Fi. Find it with: ipconfig getifaddr en0",
                    style = MaterialTheme.typography.bodySmall, color = Rc.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("ws://<ip>:8000/v1/session") })
            }
        },
        confirmButton = { TextButton({ onSave(value.trim()) }) { Text("Save & reconnect", color = Rc.Lavender) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Rc.TextDim) } },
    )
}

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
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.raphael.service.Link
import ai.raphael.ui.theme.Panel
import ai.raphael.ui.theme.RowIcon
import ai.raphael.ui.theme.RaphaelBackground
import ai.raphael.ui.theme.RaphaelSwitch
import ai.raphael.ui.theme.Rc

/** Settings (mock 11). */
@Composable
fun SettingsScreen(
    name: String,
    onName: (String) -> Unit,
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
    var editingName by remember { mutableStateOf(false) }
    RaphaelBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            Row(Modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Rc.Text,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onBack),
                )
                Spacer(Modifier.width(16.dp))
                Text("Settings", style = MaterialTheme.typography.headlineLarge, color = Rc.Text)
            }

            Section("You")
            Row1(Icons.Rounded.Person, "Name", name.ifBlank { "Add your name for the greeting" }) { editingName = true }

            Section("Connection")
            Panel(Modifier.fillMaxWidth(), onClick = { editing = true }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowIcon(Icons.Rounded.Dns)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Server", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = Rc.Text)
                        Text(
                            serverUrl.removeSuffix("/v1/session"), style = MaterialTheme.typography.bodyMedium, color = Rc.Text2,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    val (statusText, statusColor) = when (link) {
                        Link.READY -> "Connected" to Rc.Green
                        Link.CONNECTING -> "Connecting" to Rc.Muted
                        Link.DISCONNECTED -> "Offline" to Rc.Amber
                    }
                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(8.dp))
                    Text(statusText, style = MaterialTheme.typography.bodyMedium, color = Rc.Text2)
                    Chevron()
                }
            }

            Section("Earbuds")
            Row1(Icons.Rounded.Headphones, headsetLabel, "Listen with: $listenWith", onEarbuds)

            Section("Wake word")
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    RowContent(Icons.Rounded.GraphicEq, "Wake word", wakePhrase, onWakeWord) { Chevron() }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Rc.Hairline))
                    RowContent(Icons.Rounded.Bolt, "Hands-free", null, { onHandsFree(!handsFree) }) { RaphaelSwitch(handsFree, onHandsFree) }
                }
            }

            Section("Background")
            Panel(Modifier.fillMaxWidth()) {
                RowContent(Icons.Rounded.PlayCircleOutline, "Keep Raphael running", null, onKeepRunning) {
                    RaphaelSwitch(keepRunning) { onKeepRunning() }
                }
            }

            Section("Tools")
            Row1(Icons.Rounded.Hearing, "Headset audio test", null, onAudioTest)

            Text(
                "Raphael v$version", style = MaterialTheme.typography.bodyMedium, color = Rc.Muted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (editing) ServerDialog(serverUrl, onDismiss = { editing = false }, onSave = { onServerUrl(it); editing = false })
    if (editingName) NameDialog(name, onDismiss = { editingName = false }, onSave = { onName(it); editingName = false })
}

@Composable
private fun Section(title: String) = Text(
    title, style = MaterialTheme.typography.labelMedium,
    color = Rc.Text2, modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
)

@Composable
private fun Chevron() {
    Spacer(Modifier.width(8.dp))
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Rc.Muted, modifier = Modifier.size(24.dp))
}

@Composable
private fun Row1(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) { RowContent(icon, title, subtitle, onClick) { Chevron() } }
}

@Composable
private fun RowContent(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = Rc.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Rc.Text2)
        }
        trailing()
    }
}

@Composable
private fun ServerDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rc.Raised,
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
        confirmButton = { TextButton({ onSave(value.trim()) }) { Text("Save & reconnect", color = Rc.Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Rc.TextDim) } },
    )
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rc.Raised,
        title = { Text("Your name", color = Rc.Text) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Shown in the greeting") }) },
        confirmButton = { TextButton({ onSave(value.trim()) }) { Text("Save", color = Rc.Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Rc.TextDim) } },
    )
}

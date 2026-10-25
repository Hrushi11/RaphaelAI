package ai.raphael.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.raphael.audio.label
import ai.raphael.service.Link
import ai.raphael.service.MicMode
import ai.raphael.service.TurnState
import ai.raphael.service.UiState

@Composable
fun HomeScreen(
    ui: UiState,
    url: String,
    onUrl: (String) -> Unit,
    mode: MicMode,
    onMode: (MicMode) -> Unit,
    headsets: List<AudioDeviceInfo>,
    selectedHeadset: Int?,
    onHeadset: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTap: () -> Unit,
    onOpenTest: () -> Unit,
    wakeCard: @Composable () -> Unit,
    batteryRestricted: Boolean,
    onAllowBackground: () -> Unit,
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Raphael", style = MaterialTheme.typography.headlineMedium)

                if (ui.link == Link.DISCONNECTED) {
                    OutlinedTextField(
                        value = url, onValueChange = onUrl, singleLine = true,
                        label = { Text("Backend URL") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Mic source", style = MaterialTheme.typography.titleMedium)
                    Option("Headset mic (call link)", mode == MicMode.HEADSET) { onMode(MicMode.HEADSET) }
                    Option("Phone mic, reply on buds", mode == MicMode.PHONE) { onMode(MicMode.PHONE) }
                    if (mode == MicMode.HEADSET) {
                        if (headsets.isEmpty()) Text("No Bluetooth headset connected.")
                        headsets.forEach { d -> Option(d.label(), selectedHeadset == d.id) { onHeadset(d.id) } }
                    }
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Connect") }
                } else {
                    val status = when (ui.link) {
                        Link.CONNECTING -> "Connecting…"
                        else -> "Connected · ${ui.route}"
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    Text("Keeps listening with the screen off. Tap your earbud to ask.", style = MaterialTheme.typography.bodySmall)
                    if (ui.link == Link.READY && ui.handsFree && ui.turn == TurnState.IDLE) {
                        Text("Say your wake phrase, or tap the button.", style = MaterialTheme.typography.bodyMedium)
                    }

                    val (label, color) = when (ui.turn) {
                        TurnState.IDLE -> "Tap to ask" to MaterialTheme.colorScheme.primary
                        TurnState.CHECKING -> "Hearing speech… (checking for wake phrase)" to MaterialTheme.colorScheme.primary
                        TurnState.LISTENING -> "Listening… tap to finish" to MaterialTheme.colorScheme.tertiary
                        TurnState.THINKING -> "Thinking… tap to stop" to MaterialTheme.colorScheme.secondary
                        TurnState.SPEAKING -> "Speaking… tap to stop" to MaterialTheme.colorScheme.secondary
                    }
                    Button(
                        onClick = onTap,
                        enabled = ui.link == Link.READY,
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    ) { Text(label, style = MaterialTheme.typography.titleLarge) }

                    if (ui.heard.isNotEmpty()) {
                        Text("You", style = MaterialTheme.typography.labelLarge)
                        Text(ui.heard, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (ui.answer.isNotEmpty()) {
                        Text("Raphael", style = MaterialTheme.typography.labelLarge)
                        Text(ui.answer, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (ui.timings.isNotEmpty()) Text(ui.timings, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                }

                wakeCard()

                if (batteryRestricted) {
                    OutlinedButton(onClick = onAllowBackground, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow Raphael to run in the background")
                    }
                }

                if (ui.error.isNotEmpty()) {
                    Text(ui.error, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onOpenTest) { Text("Headset audio test") }
            }
        }
    }
}

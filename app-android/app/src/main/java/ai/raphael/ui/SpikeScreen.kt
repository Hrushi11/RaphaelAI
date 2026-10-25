package ai.raphael.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ai.raphael.audio.label

enum class MicSource(val title: String) {
    HEADSET("Headset mic (call link)"),
    PHONE("Phone mic, reply on buds"),
}

class SpikeState(
    val headsets: SnapshotStateList<AudioDeviceInfo>,
    val log: SnapshotStateList<String>,
    val selectedId: MutableState<Int?>,
    val source: MutableState<MicSource>,
    val busy: MutableState<Boolean>,
    val level: MutableState<Float>,
    val status: MutableState<String>,
    val hasPermission: MutableState<Boolean>,
)

@Composable
fun SpikeScreen(
    state: SpikeState,
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
    onRun: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Raphael — headset audio test", style = MaterialTheme.typography.titleLarge)
                if (onBack != null) OutlinedButton(onClick = onBack, enabled = !state.busy.value) { Text("← Back") }

                if (!state.hasPermission.value) {
                    Text("Raphael needs the microphone and Bluetooth (nearby devices) permissions.")
                    Button(onClick = onGrant) { Text("Grant permissions") }
                    return@Column
                }

                Text("Headset", style = MaterialTheme.typography.titleMedium)
                if (state.headsets.isEmpty()) {
                    Text("No Bluetooth headset found. Connect the buds in Android Settings → Bluetooth.")
                }
                state.headsets.forEach { d ->
                    Option(d.label(), state.selectedId.value == d.id) { state.selectedId.value = d.id }
                }
                OutlinedButton(onClick = onRefresh, enabled = !state.busy.value) { Text("Refresh") }

                Text("Mic source", style = MaterialTheme.typography.titleMedium)
                MicSource.entries.forEach { s ->
                    Option(s.title, state.source.value == s) { state.source.value = s }
                }

                Button(onClick = onRun, enabled = !state.busy.value, modifier = Modifier.fillMaxWidth()) {
                    Text("Record 5 s and play back")
                }
                LinearProgressIndicator(progress = { state.level.value }, modifier = Modifier.fillMaxWidth())
                Text(state.status.value, style = MaterialTheme.typography.bodyLarge)

                Text("Log", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.log) { line ->
                        Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun Option(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text)
    }
}

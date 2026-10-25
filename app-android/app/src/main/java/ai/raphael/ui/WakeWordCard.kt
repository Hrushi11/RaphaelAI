package ai.raphael.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Wake word settings (spec §3.6): hands-free toggle, phrase, and a test log. */
@Composable
fun WakeWordCard(
    handsFree: Boolean,
    onHandsFree: (Boolean) -> Unit,
    phrase: String,
    onPhrase: (String) -> Unit,
    activePhrase: String,
    onSave: () -> Unit,
    wakes: List<String>,
    ignoredBursts: Int,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hands-free (wake word)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = handsFree, onCheckedChange = onHandsFree)
            }
            Text(
                "When you speak, that speech goes to the backend to check for the wake phrase. " +
                    "Speech without it is discarded; silence never leaves the phone.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = phrase, onValueChange = onPhrase, singleLine = true,
                label = { Text("Wake phrase") }, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSave, enabled = phrase.isNotBlank() && phrase.trim().lowercase() != activePhrase) {
                Text("Save wake phrase")
            }
            Text("Active: “$activePhrase” · other speech ignored: $ignoredBursts", style = MaterialTheme.typography.bodySmall)
            if (wakes.isNotEmpty()) {
                Text("Recent wake detections", style = MaterialTheme.typography.labelLarge)
                wakes.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

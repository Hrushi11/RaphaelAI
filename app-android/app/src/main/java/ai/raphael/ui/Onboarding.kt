package ai.raphael.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.raphael.R
import ai.raphael.service.MicMode
import ai.raphael.ui.theme.BackButton
import ai.raphael.ui.theme.Diamond
import ai.raphael.ui.theme.DiamondMood
import ai.raphael.ui.theme.GlassCard
import ai.raphael.ui.theme.GradientButton
import ai.raphael.ui.theme.IconTile
import ai.raphael.ui.theme.RaphaelBackground
import ai.raphael.ui.theme.RaphaelSwitch
import ai.raphael.ui.theme.Rc
import ai.raphael.ui.theme.SmallDiamond
import ai.raphael.ui.theme.TwoToneTitle
import ai.raphael.ui.theme.WaveStyle
import ai.raphael.ui.theme.Waveform

/** Shared frame for onboarding screens: optional back button, small diamond, scrollable body. */
@Composable
private fun OnboardingFrame(onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    RaphaelBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(64.dp).padding(top = 12.dp)) {
                if (onBack != null) BackButton(onBack)
            }
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- 1. Welcome

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    RaphaelBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Diamond(DiamondMood.IDLE, { 0f }, Modifier.fillMaxWidth(0.82f))
            }
            Text("Raphael", style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp, lineHeight = 70.sp), color = Rc.Text)
            Text("Your advisor, in your ear.", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal, fontSize = 26.sp), color = Rc.Lavender)
            Spacer(Modifier.height(72.dp))
            GradientButton("Get started", onStart)
            Spacer(Modifier.height(20.dp))
            Text("Works with any Bluetooth earbuds", style = MaterialTheme.typography.bodyMedium, color = Rc.TextDim)
            Spacer(Modifier.height(36.dp))
        }
    }
}

// ---------------------------------------------------------------- 2. Permissions

@Composable
fun PermissionsScreen(onAllow: () -> Unit, message: String) {
    OnboardingFrame(onBack = null) {
        SmallDiamond(Modifier.fillMaxWidth(0.62f).height(170.dp))
        Spacer(Modifier.height(16.dp))
        TwoToneTitle("Raphael needs", "a few things")
        Spacer(Modifier.height(28.dp))
        PermissionRow(Icons.Rounded.Mic, "Microphone", "to hear you")
        PermissionRow(Icons.Rounded.Headphones, "Nearby devices", "to use your earbuds")
        PermissionRow(Icons.Rounded.Notifications, "Notifications", "so you always know when Raphael can hear you")
        Spacer(Modifier.height(24.dp))
        GradientButton("Allow", onAllow)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = Rc.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Silence never leaves your phone.", style = MaterialTheme.typography.bodyMedium, color = Rc.TextDim)
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = Rc.Amber, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PermissionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, size = 64.dp)
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = Rc.Text)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Rc.Lavender)
            }
        }
    }
}

// ---------------------------------------------------------------- 3. Choose headset

/** A headset row: connected ones can be used now; paired-but-off ones are shown as "Paired". */
data class HeadsetOption(val id: Int?, val name: String, val connected: Boolean)

@Composable
fun ChooseHeadsetScreen(
    options: List<HeadsetOption>,
    selected: String?,
    onSelect: (HeadsetOption) -> Unit,
    mode: MicMode,
    onMode: (MicMode) -> Unit,
    onPairNew: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    message: String,
) {
    OnboardingFrame(onBack = onBack) {
        SmallDiamond(Modifier.fillMaxWidth(0.62f).height(170.dp))
        Spacer(Modifier.height(16.dp))
        TwoToneTitle("Choose your", "earbuds")
        Spacer(Modifier.height(24.dp))
        if (options.isEmpty()) {
            Text("No Bluetooth earbuds found. Pair them first, then come back.", style = MaterialTheme.typography.bodyMedium, color = Rc.TextDim, textAlign = TextAlign.Center)
        }
        options.forEach { o ->
            val isSel = o.name == selected
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 7.dp), highlighted = isSel, onClick = { onSelect(o) }) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).border(2.dp, if (isSel) Color(0xFFD45BFF) else Rc.TextDim, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (isSel) Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFFD45BFF))) }
                    Spacer(Modifier.width(16.dp))
                    IconTile(Icons.Rounded.Headphones, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(o.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = Rc.Text)
                        Spacer(Modifier.height(6.dp))
                        Tag(if (o.connected) "Connected" else "Paired", if (o.connected) Rc.Green else Rc.Lavender)
                    }
                }
            }
        }
        Row(
            Modifier.padding(vertical = 18.dp).clickable(onClick = onPairNew),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Pair new earbuds in Bluetooth settings",
                style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.Underline),
                color = Color(0xFF4DA3FF),
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFF4DA3FF))
        }
        Text("Where should Raphael listen?", style = MaterialTheme.typography.headlineSmall, color = Rc.Text)
        Spacer(Modifier.height(14.dp))
        Segmented(mode, onMode)
        Spacer(Modifier.height(12.dp))
        Text(
            if (mode == MicMode.HEADSET) "Earbuds mic: phone can stay in your pocket." else "Phone mic: best audio, keep the phone near you.",
            style = MaterialTheme.typography.bodyMedium, color = Rc.TextDim,
        )
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = Rc.Amber, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(22.dp))
        GradientButton("Continue", onContinue)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun Segmented(mode: MicMode, onMode: (MicMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(CircleShape).background(Color(0x1A8C78FF)).border(1.dp, Color(0x337C4DFF), CircleShape),
    ) {
        listOf(MicMode.HEADSET to ("Earbuds mic" to Icons.Rounded.Headphones), MicMode.PHONE to ("Phone mic" to Icons.Rounded.Smartphone))
            .forEach { (m, labelIcon) ->
                val on = m == mode
                Row(
                    Modifier.weight(1f).fillMaxSize().clip(CircleShape)
                        .then(if (on) Modifier.background(Rc.Primary) else Modifier)
                        .clickable { onMode(m) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(labelIcon.second, null, tint = if (on) Color.White else Rc.Lavender, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(labelIcon.first, style = MaterialTheme.typography.titleMedium, color = if (on) Color.White else Rc.Lavender)
                }
            }
    }
}

// ---------------------------------------------------------------- 4. Wake word

/** Try-it state on the wake word screen. */
enum class TryState { CONNECTING, WAITING, HEARD }

@Composable
fun WakeWordScreen(
    phrase: String,
    onPhrase: (String) -> Unit,
    tryState: TryState,
    handsFree: Boolean,
    onHandsFree: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String = "Done",
) {
    OnboardingFrame(onBack = onBack) {
        SmallDiamond(Modifier.fillMaxWidth(0.66f).height(190.dp))
        Spacer(Modifier.height(16.dp))
        TwoToneTitle("Your wake word", null)
        Spacer(Modifier.height(24.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = phrase, onValueChange = onPhrase, singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = Rc.Text, fontWeight = FontWeight.Normal),
                    cursorBrush = SolidColor(Rc.Violet),
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.Edit, "Edit", tint = Rc.Text, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        GlassCard(Modifier.fillMaxWidth(), dashed = true) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(110.dp).height(90.dp), contentAlignment = Alignment.Center) {
                    Waveform(WaveStyle.BARS_VOICE, { if (tryState == TryState.HEARD) 0.15f else 0.35f }, Modifier.height(70.dp))
                    Image(painterResource(R.drawable.diamond_small), null, contentScale = ContentScale.Fit, modifier = Modifier.size(80.dp))
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(1.dp).height(70.dp).background(Color(0x337C4DFF)))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Try it — say “${titleCase(phrase.ifBlank { "hey raphael" })}”", style = MaterialTheme.typography.titleMedium, color = Rc.Lavender)
                    Spacer(Modifier.height(8.dp))
                    when (tryState) {
                        TryState.HEARD -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = Rc.Green, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Heard you", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = Rc.Green)
                        }
                        TryState.WAITING -> Text("Listening…", style = MaterialTheme.typography.titleMedium, color = Rc.Cyan)
                        TryState.CONNECTING -> Text("Connecting to Raphael…", style = MaterialTheme.typography.bodyMedium, color = Rc.TextDim)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Headphones, size = 56.dp, tint = Rc.Text)
                Spacer(Modifier.width(18.dp))
                Text("Hands-free", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = Rc.Text, modifier = Modifier.weight(1f))
                RaphaelSwitch(handsFree, onHandsFree)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Shield, null, tint = Rc.TextDim, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                "Raphael checks speech for your wake word and discards everything else.",
                style = MaterialTheme.typography.bodyLarge, color = Rc.TextDim,
            )
        }
        Spacer(Modifier.height(28.dp))
        GradientButton(doneLabel, onDone, enabled = phrase.isNotBlank())
    }
}

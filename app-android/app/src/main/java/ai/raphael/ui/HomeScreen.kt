package ai.raphael.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ai.raphael.service.Link
import ai.raphael.service.TurnState
import ai.raphael.service.UiState
import ai.raphael.ui.theme.Diamond
import ai.raphael.ui.theme.DiamondMood
import ai.raphael.ui.theme.GlassCard
import ai.raphael.ui.theme.GlassPill
import ai.raphael.ui.theme.GlowButton
import ai.raphael.ui.theme.Rc
import ai.raphael.ui.theme.RaphaelBackground
import ai.raphael.ui.theme.RoundButton
import ai.raphael.ui.theme.StatusChip
import ai.raphael.ui.theme.WaveStyle
import ai.raphael.ui.theme.Waveform

/** What the Home screen shows; derived from the controller state (mocks 5–9, 12). */
enum class HomeMode { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, PAUSED, ERROR }

fun homeMode(ui: UiState): HomeMode = when {
    ui.paused -> HomeMode.PAUSED
    ui.link == Link.DISCONNECTED && ui.error.isNotBlank() -> HomeMode.ERROR
    ui.link != Link.READY -> HomeMode.CONNECTING
    ui.turn == TurnState.LISTENING -> HomeMode.LISTENING
    ui.turn == TurnState.THINKING -> HomeMode.THINKING
    ui.turn == TurnState.SPEAKING -> HomeMode.SPEAKING
    else -> HomeMode.IDLE // includes CHECKING: nothing visible until the wake phrase is confirmed
}

fun titleCase(phrase: String) = phrase.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

@Composable
fun HomeScreen(
    ui: UiState,
    micLevel: () -> Float,
    playLevel: () -> Float,
    onPill: () -> Unit,
    onSettings: () -> Unit,
    onAsk: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
) {
    val mode = homeMode(ui)
    val phrase = titleCase(ui.wakePhrase.ifBlank { "hey raphael" })
    val level: () -> Float = when (mode) {
        HomeMode.LISTENING -> micLevel
        HomeMode.SPEAKING -> playLevel
        else -> { { 0f } }
    }

    RaphaelBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPill(onClick = onPill) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF8A6BFF), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Raphael · ${ui.deviceName.ifBlank { "Earbuds" }}", style = MaterialTheme.typography.titleMedium, color = Rc.Text)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Rc.Text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                RoundButton(Icons.Rounded.Settings, "Settings", onSettings, size = 52.dp)
            }

            // Diamond
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Diamond(
                    mood = when (mode) {
                        HomeMode.LISTENING -> DiamondMood.LISTENING
                        HomeMode.THINKING -> DiamondMood.THINKING
                        HomeMode.SPEAKING -> DiamondMood.SPEAKING
                        HomeMode.PAUSED -> DiamondMood.PAUSED
                        HomeMode.ERROR -> DiamondMood.ERROR
                        else -> DiamondMood.IDLE
                    },
                    level = level,
                    modifier = Modifier.fillMaxWidth(if (mode == HomeMode.SPEAKING || mode == HomeMode.LISTENING) 1f else 0.86f),
                )
            }

            // Status chip
            when (mode) {
                HomeMode.IDLE -> StatusChip(
                    if (ui.handsFree) "Listening for “$phrase”" else "Ready — tap to ask",
                    Rc.Green, textColor = Rc.Text,
                )
                HomeMode.CONNECTING -> StatusChip("Connecting…", Rc.Cyan, textColor = Rc.Text)
                HomeMode.LISTENING -> StatusChip("Listening…", Rc.Cyan)
                HomeMode.THINKING -> StatusChip("Thinking…", Color(0xFFD45BFF), textColor = Rc.Lavender)
                HomeMode.SPEAKING -> StatusChip("Speaking", Color(0xFFD45BFF), textColor = Rc.Lavender)
                HomeMode.PAUSED -> StatusChip("Paused — Raphael can’t hear you", Rc.Lavender, hollow = true, textColor = Rc.Text)
                HomeMode.ERROR -> StatusChip(
                    if (ui.error.startsWith("Couldn't open the headset")) "Can’t open your earbuds’ mic" else "Can’t reach Raphael’s server",
                    Rc.Amber,
                )
            }
            Spacer(Modifier.height(18.dp))

            // Card
            AnimatedContent(mode, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "card") { m ->
                when (m) {
                    HomeMode.IDLE, HomeMode.CONNECTING -> HintCard(
                        icon = Icons.Rounded.GraphicEq,
                        text = if (ui.handsFree) buildAnnotatedString {
                            append("Say “"); withStyle(SpanStyle(brush = Rc.TextGradient, fontWeight = FontWeight.Medium)) { append(phrase) }
                            append("”\nor tap your earbud")
                        } else AnnotatedString("Tap the mic or your earbud\nto ask Raphael"),
                        footnote = ui.error.takeIf { m == HomeMode.IDLE && it.isNotBlank() },
                    )
                    HomeMode.LISTENING -> SpeakerCard(you = true) { LiveText(ui.heard.ifBlank { "…" }) }
                    HomeMode.THINKING -> SpeakerCard(you = true) {
                        Text(ui.heard, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal), color = Rc.Text)
                        ThinkingDots()
                    }
                    HomeMode.SPEAKING -> SpeakerCard(you = false, answeredMs = ui.answeredMs) { AnswerText(ui.answer) }
                    HomeMode.PAUSED -> HintCard(
                        icon = Icons.Rounded.MicOff,
                        text = AnnotatedString("Your mic is off. Tap ▶ to resume."),
                        dim = true,
                    )
                    HomeMode.ERROR -> HintCard(
                        icon = Icons.Rounded.WifiOff,
                        iconTint = Rc.Amber,
                        text = AnnotatedString(
                            if (ui.error.startsWith("Couldn't open the headset")) "Your earbuds’ mic didn’t open. Reconnect them, or switch to Phone mic in settings."
                            else "Check that your phone and computer are on the same Wi-Fi, then try again."
                        ),
                        link = "Server settings" to onSettings,
                    )
                }
            }

            // Waveform
            Waveform(
                style = when (mode) {
                    HomeMode.LISTENING, HomeMode.SPEAKING -> WaveStyle.BARS_VOICE
                    HomeMode.THINKING -> WaveStyle.SINE
                    HomeMode.PAUSED, HomeMode.ERROR -> WaveStyle.FLAT_DASHED
                    else -> WaveStyle.DOTS
                },
                level = level,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // Controls
            Controls(mode, onAsk, onStop, onPause, onResume, onRetry)
        }
    }
}

@Composable
private fun Controls(mode: HomeMode, onAsk: () -> Unit, onStop: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.TopCenter) {
        when (mode) {
            HomeMode.IDLE, HomeMode.CONNECTING -> SideControls(
                left = { RoundButton(Icons.Rounded.Close, "Stop Raphael", onStop) },
                center = { GlowButton(Icons.Rounded.Mic, "Ask", onAsk) },
                right = { RoundButton(Icons.Rounded.Pause, "Pause listening", onPause) },
            )
            HomeMode.LISTENING -> Captioned("Tap to finish") { GlowButton(Icons.Rounded.Mic, "Finish question", onAsk, ripples = true) }
            HomeMode.THINKING -> Captioned("Tap to cancel") { GlowButton(Icons.Rounded.Stop, "Cancel", onAsk, color = Color(0xFF5B4BFF)) }
            HomeMode.SPEAKING -> Captioned("Tap to stop") { GlowButton(Icons.Rounded.Stop, "Stop answer", onAsk, color = Color(0xFF5B4BFF)) }
            HomeMode.PAUSED -> SideControls(
                left = { RoundButton(Icons.Rounded.Close, "Stop Raphael", onStop) },
                center = { Captioned("Resume") { GlowButton(Icons.Rounded.PlayArrow, "Resume", onResume) } },
                right = { RoundButton(Icons.Rounded.Pause, "Paused", onResume, ring = Color(0xFF9B4DFF)) },
            )
            HomeMode.ERROR -> Captioned("Retry") { GlowButton(Icons.Rounded.Sync, "Retry", onRetry, color = Rc.Amber) }
        }
    }
}

@Composable
private fun SideControls(left: @Composable () -> Unit, center: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f).padding(top = 58.dp), contentAlignment = Alignment.Center) { left() }
        center()
        Box(Modifier.weight(1f).padding(top = 58.dp), contentAlignment = Alignment.Center) { right() }
    }
}

@Composable
private fun Captioned(caption: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Text(caption, style = MaterialTheme.typography.titleMedium, color = Rc.Lavender, modifier = Modifier.offset(y = (-26).dp))
    }
}

// ---------------------------------------------------------------- cards

@Composable
private fun HintCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: AnnotatedString,
    iconTint: Color = Color(0xFF8A6BFF),
    dim: Boolean = false,
    footnote: String? = null,
    link: Pair<String, () -> Unit>? = null,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(20.dp))
            Box(Modifier.width(1.dp).height(56.dp).background(Color(0x337C4DFF)))
            Spacer(Modifier.width(20.dp))
            Column {
                Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize), color = if (dim) Rc.TextDim else Rc.Text)
                if (link != null) Text(
                    link.first,
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.Underline),
                    color = Color(0xFF4DA3FF),
                    modifier = Modifier.padding(top = 6.dp).clickable { link.second() },
                )
                if (footnote != null) Text(footnote, style = MaterialTheme.typography.bodySmall, color = Rc.Amber, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SpeakerCard(you: Boolean, answeredMs: Int? = null, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), highlighted = !you) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (you) Icons.Rounded.Person else Icons.Rounded.AutoAwesome, null,
                    tint = if (you) Rc.Lavender else Color(0xFF8A6BFF), modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(if (you) "You" else "Raphael", style = MaterialTheme.typography.titleMedium, color = Rc.Lavender)
            }
            Spacer(Modifier.height(8.dp))
            content()
            if (answeredMs != null) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    GlassPill {
                        Icon(Icons.Rounded.Bolt, null, tint = Rc.Cyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("answered in ${"%.1f".format(answeredMs / 1000f)} s", style = MaterialTheme.typography.bodySmall, color = Rc.TextDim)
                    }
                }
            }
        }
    }
}

/** Live transcript: the last word fades in, like the mock ("lakh…" dimmed). */
@Composable
private fun LiveText(text: String) {
    val cut = text.trimEnd().lastIndexOf(' ')
    val annotated = buildAnnotatedString {
        if (cut > 0) {
            append(text.substring(0, cut + 1))
            withStyle(SpanStyle(color = Rc.TextDim)) { append(text.substring(cut + 1)); append("…") }
        } else append(text)
    }
    Text(annotated, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal), color = Rc.Text)
}

/** Answer text; the part after the last " is " in the first sentence gets the gradient underline (mock 8). */
@Composable
private fun AnswerText(answer: String) {
    val text = answer.trim()
    val firstEnd = text.indexOfFirst { it == '.' || it == '!' || it == '?' }.let { if (it < 0) text.length else it }
    val split = text.substring(0, firstEnd).lastIndexOf(" is ")
    val annotated = buildAnnotatedString {
        if (split > 0 && firstEnd - split < 60) {
            append(text.substring(0, split + 4))
            withStyle(SpanStyle(brush = Rc.TextGradient, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                append(text.substring(split + 4, firstEnd))
            }
            append(text.substring(firstEnd))
        } else append(text)
    }
    Text(annotated, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal), color = Rc.Text)
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val p by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "p")
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
        repeat(3) { i ->
            val on = (p.toInt() == i)
            Box(
                Modifier.padding(horizontal = 9.dp).size(12.dp).alpha(if (on) 1f else 0.4f).clip(CircleShape)
                    .background(Color(0xFFC05BFF))
            )
        }
    }
}

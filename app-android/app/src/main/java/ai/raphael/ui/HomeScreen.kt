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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ai.raphael.service.Link
import ai.raphael.service.TurnState
import ai.raphael.service.UiState
import ai.raphael.ui.theme.Diamond
import ai.raphael.ui.theme.DiamondMood
import ai.raphael.ui.theme.MainButton
import ai.raphael.ui.theme.Panel
import ai.raphael.ui.theme.Pill
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
                Pill(onClick = onPill) {
                    Icon(Icons.Rounded.Headphones, null, tint = Rc.Text2, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(ui.deviceName.ifBlank { "Earbuds" }, style = MaterialTheme.typography.titleMedium, color = Rc.Text)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Rc.Muted, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                RoundButton(Icons.Rounded.Settings, "Settings", onSettings, size = 48.dp)
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
                HomeMode.IDLE -> StatusChip(if (ui.handsFree) "Listening for “$phrase”" else "Ready — tap to ask", Rc.Green)
                HomeMode.CONNECTING -> StatusChip("Connecting…", Rc.Muted)
                HomeMode.LISTENING -> StatusChip("Listening…", Rc.Green)
                HomeMode.THINKING -> StatusChip("Thinking…", Rc.Accent)
                HomeMode.SPEAKING -> StatusChip("Speaking", Rc.Accent)
                HomeMode.PAUSED -> StatusChip("Paused — Raphael can’t hear you", Rc.Amber, hollow = true)
                HomeMode.ERROR -> StatusChip(
                    if (ui.error.startsWith("Couldn't open the headset")) "Can’t open your earbuds’ mic" else "Can’t reach Raphael’s server",
                    Rc.Amber,
                )
            }
            Spacer(Modifier.height(16.dp))

            // Card
            AnimatedContent(mode, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "card") { m ->
                when (m) {
                    HomeMode.IDLE, HomeMode.CONNECTING -> HintCard(
                        icon = Icons.Rounded.GraphicEq,
                        text = if (ui.handsFree) buildAnnotatedString {
                            append("Say “"); withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(phrase) }
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
                        text = AnnotatedString("Your mic is off. Tap Resume to listen again."),
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
                color = if (mode == HomeMode.SPEAKING || mode == HomeMode.THINKING) Rc.Accent else Rc.Text2,
            )

            // Controls
            Controls(mode, onAsk, onStop, onPause, onResume, onRetry)
        }
    }
}

@Composable
private fun Controls(mode: HomeMode, onAsk: () -> Unit, onStop: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(148.dp), contentAlignment = Alignment.TopCenter) {
        when (mode) {
            HomeMode.IDLE, HomeMode.CONNECTING -> SideControls(
                left = { RoundButton(Icons.Rounded.Close, "Stop Raphael", onStop) },
                center = { Captioned("Ask") { MainButton(Icons.Rounded.Mic, "Ask", onAsk) } },
                right = { RoundButton(Icons.Rounded.Pause, "Pause listening", onPause) },
            )
            HomeMode.LISTENING -> Captioned("Tap to finish") { MainButton(Icons.Rounded.Mic, "Finish question", onAsk, live = true) }
            HomeMode.THINKING -> Captioned("Tap to cancel") { MainButton(Icons.Rounded.Stop, "Cancel", onAsk) }
            HomeMode.SPEAKING -> Captioned("Tap to stop") { MainButton(Icons.Rounded.Stop, "Stop answer", onAsk) }
            HomeMode.PAUSED -> SideControls(
                left = { RoundButton(Icons.Rounded.Close, "Stop Raphael", onStop) },
                center = { Captioned("Resume") { MainButton(Icons.Rounded.PlayArrow, "Resume", onResume) } },
                right = { RoundButton(Icons.Rounded.Pause, "Paused", onResume, ring = Rc.Amber) },
            )
            HomeMode.ERROR -> SideControls(
                left = { RoundButton(Icons.Rounded.Close, "Stop Raphael", onStop) },
                center = { Captioned("Retry") { MainButton(Icons.Rounded.Sync, "Retry", onRetry) } },
                right = { },
            )
        }
    }
}

@Composable
private fun SideControls(left: @Composable () -> Unit, center: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f).padding(top = 18.dp), contentAlignment = Alignment.Center) { left() }
        center()
        Box(Modifier.weight(1f).padding(top = 18.dp), contentAlignment = Alignment.Center) { right() }
    }
}

@Composable
private fun Captioned(caption: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Spacer(Modifier.height(8.dp))
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = Rc.Text2)
    }
}

// ---------------------------------------------------------------- cards

@Composable
private fun HintCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: AnnotatedString,
    iconTint: Color = Rc.Text2,
    dim: Boolean = false,
    footnote: String? = null,
    link: Pair<String, () -> Unit>? = null,
) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Box(Modifier.width(1.dp).height(48.dp).background(Rc.Hairline))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize), color = if (dim) Rc.TextDim else Rc.Text)
                if (link != null) Text(
                    link.first,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Rc.Accent,
                    modifier = Modifier.padding(top = 8.dp).clickable { link.second() },
                )
                if (footnote != null) Text(footnote, style = MaterialTheme.typography.bodySmall, color = Rc.Amber, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SpeakerCard(you: Boolean, answeredMs: Int? = null, content: @Composable () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(if (you) "You" else "Raphael", style = MaterialTheme.typography.titleMedium, color = if (you) Rc.Text2 else Rc.Accent)
            Spacer(Modifier.height(8.dp))
            content()
            if (answeredMs != null) {
                Text(
                    "Answered in ${"%.1f".format(answeredMs / 1000f)} s",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = Rc.Muted,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.End,
                )
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

/** Answer text; the part after the last " is " in the first sentence (the figure that matters) gets the accent. */
@Composable
private fun AnswerText(answer: String) {
    val text = answer.trim()
    val firstEnd = text.indexOfFirst { it == '.' || it == '!' || it == '?' }.let { if (it < 0) text.length else it }
    val split = text.substring(0, firstEnd).lastIndexOf(" is ")
    val annotated = buildAnnotatedString {
        if (split > 0 && firstEnd - split < 60) {
            append(text.substring(0, split + 4))
            withStyle(SpanStyle(color = Rc.Accent, fontWeight = FontWeight.Medium)) {
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
                Modifier.padding(horizontal = 8.dp).size(8.dp).alpha(if (on) 1f else 0.3f).clip(CircleShape)
                    .background(Rc.Accent)
            )
        }
    }
}

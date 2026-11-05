package ai.raphael.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ai.raphael.R

/**
 * Design tokens (unslop pass, Nov 2026): quiet "ink" neutrals for every surface, one solid violet accent
 * taken from the diamond, which stays the only colourful thing on screen. Semantic colours only carry meaning.
 */
object Rc {
    // Neutral ramp (dark, ink-tinted)
    val Deep = Color(0xFF07080D)
    val Page = Color(0xFF0B0D14)
    val Card = Color(0xFF11141C)
    val Raised = Color(0xFF1A1E28)
    val Hairline = Color(0x14EEF0F5)        // primary text at 8 %
    val HairlineStrong = Color(0x29EEF0F5)  // selected / focused outline

    // Three text roles
    val Text = Color(0xFFEEF0F5)
    val Text2 = Color(0xFFA3A9B8)
    val Muted = Color(0xFF7A8296)

    // One accent: the diamond's violet. Fill for controls; lighter shade for text and icons (≥ 4.5:1 on Page).
    val Violet = Color(0xFF7C4DFF)
    val Accent = Color(0xFFA68BFF)

    // Semantic
    val Green = Color(0xFF4CC38A)   // Raphael can hear you
    val Amber = Color(0xFFE5A54B)   // Raphael can't hear you / problem

    // Older names, mapped onto the roles above
    val TextDim = Text2
    val Lavender = Text2
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

/**
 * ONE knob for all text sizes in the app. 1.0 = the original sizes; 0.85 = 15 % smaller.
 * Every font size below (and the few hard-coded ones on other screens, via [fs]) is multiplied by this.
 * Note: Android's own "Font size" setting (Settings → Display) still scales on top of this.
 */
const val FontScale = 0.70f

/** Scaled font size: use `fs(20)` instead of `20.sp`. */
fun fs(size: Number) = (size.toFloat() * FontScale).sp

private val typography = Typography(
    displayMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(40), lineHeight = fs(46)),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(34), lineHeight = fs(40)),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(22), lineHeight = fs(28)),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(20), lineHeight = fs(26)),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(17), lineHeight = fs(22)),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = fs(18), lineHeight = fs(25)),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = fs(15), lineHeight = fs(21)),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = fs(13), lineHeight = fs(18)),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(15)),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(14), lineHeight = fs(20)),
)

@Composable
fun RaphaelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Rc.Accent,
            secondary = Rc.Text2,
            tertiary = Rc.Text2,
            background = Rc.Page,
            surface = Rc.Card,
            surfaceContainerHigh = Rc.Raised,
            onPrimary = Rc.Page,
            onBackground = Rc.Text,
            onSurface = Rc.Text,
            onSurfaceVariant = Rc.Text2,
            error = Rc.Amber,
            outline = Rc.HairlineStrong,
        ),
        typography = typography,
        content = content,
    )
}

/** Screen background: one flat neutral. Depth comes from surface steps and hairlines, not glows. */
@Composable
fun RaphaelBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Rc.Page), content = content)
}

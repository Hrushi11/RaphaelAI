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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ai.raphael.R

/** Design tokens from design/ui-prompts.md and the mocks in design/ui-designs-mocks. */
object Rc {
    val BgTop = Color(0xFF05060F)
    val BgBottom = Color(0xFF0B0D2A)
    val Violet = Color(0xFF7C4DFF)
    val Magenta = Color(0xFFB04DFF)
    val Blue = Color(0xFF3D7BFF)
    val Cyan = Color(0xFF22C8FF)
    val Teal = Color(0xFF2EE6D6)
    val Amber = Color(0xFFFFB547)
    val Green = Color(0xFF3DDC84)
    val Text = Color(0xFFE8E9F5)
    val TextDim = Color(0xFF9AA0C3)
    val Lavender = Color(0xFFB9A8FF)
    val GlassFill = Color(0x14FFFFFF)
    val GlassFillStrong = Color(0x1F8C78FF)
    val GlassBorder = Color(0x407C4DFF)

    /** Violet → blue, used for primary buttons and highlighted words. */
    val Primary = Brush.horizontalGradient(listOf(Magenta, Color(0xFF5B4BFF), Color(0xFF1FA8FF)))
    val TextGradient = Brush.horizontalGradient(listOf(Color(0xFFC04DFF), Color(0xFF6E8BFF), Cyan))
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
const val FontScale = 0.85f

/** Scaled font size: use `fs(20)` instead of `20.sp`. */
fun fs(size: Number) = (size.toFloat() * FontScale).sp

private val typography = Typography(
    displayMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = fs(40), lineHeight = fs(46), letterSpacing = fs(-0.5)),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = fs(34), lineHeight = fs(40), letterSpacing = fs(-0.4)),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = fs(22), lineHeight = fs(28)),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = fs(20), lineHeight = fs(26)),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(17), lineHeight = fs(22)),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = fs(18), lineHeight = fs(25)),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = fs(15), lineHeight = fs(21)),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = fs(13), lineHeight = fs(18)),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = fs(15)),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = fs(13), letterSpacing = fs(1.2)),
)

@Composable
fun RaphaelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Rc.Violet,
            secondary = Rc.Blue,
            tertiary = Rc.Teal,
            background = Rc.BgTop,
            surface = Rc.BgBottom,
            onPrimary = Color.White,
            onBackground = Rc.Text,
            onSurface = Rc.Text,
            onSurfaceVariant = Rc.TextDim,
            error = Rc.Amber,
            outline = Rc.GlassBorder,
        ),
        typography = typography,
        content = content,
    )
}

/** Screen background: navy gradient with soft violet / blue glows in the bottom corners (mocks 5–12). */
@Composable
fun RaphaelBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Rc.BgTop, Color(0xFF070820), Rc.BgBottom)))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x557C4DFF), Color.Transparent), center = Offset(0f, size.height), radius = size.width * 0.75f),
                    radius = size.width * 0.75f, center = Offset(0f, size.height),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x553D7BFF), Color.Transparent), center = Offset(size.width, size.height), radius = size.width * 0.7f),
                    radius = size.width * 0.7f, center = Offset(size.width, size.height),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x223D7BFF), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.8f),
                    radius = size.width * 0.8f, center = Offset(size.width, 0f),
                )
            },
        content = content,
    )
}

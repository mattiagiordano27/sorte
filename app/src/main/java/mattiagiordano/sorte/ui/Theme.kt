package mattiagiordano.sorte.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mattiagiordano.sorte.R

/** Stile: nero/bianco e grigi, nessun colore d'accento; titoli a puntini, etichette mono. */

@Immutable
data class Palette(
    val bg: Color, val surface: Color, val text: Color, val muted: Color, val line: Color,
    val ledOff: Color, val ledOn: Color, val dark: Boolean,
)

private val Dark = Palette(
    bg = Color(0xFF000000), surface = Color(0xFF111111), text = Color(0xFFF2F2F2),
    muted = Color(0xFF8A8A8A), line = Color(0xFF2A2A2A),
    ledOff = Color(0xFF1C1C1C), ledOn = Color(0xFFFFFFFF), dark = true,
)

private val Light = Palette(
    bg = Color(0xFFF2F2F2), surface = Color(0xFFFFFFFF), text = Color(0xFF0A0A0A),
    muted = Color(0xFF6E6E6E), line = Color(0xFFD8D8D8),
    // la matrice resta "accesa su nero" anche in tema chiaro, come sul retro del telefono
    ledOff = Color(0xFF1C1C1C), ledOn = Color(0xFFFFFFFF), dark = false,
)

val LocalPalette = staticCompositionLocalOf { Dark }

@OptIn(ExperimentalTextApi::class)
val Dot = FontFamily(
    Font(
        R.font.doto,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.Setting("ROND", 100f)),
    ),
)

object T {
    val display = TextStyle(fontFamily = Dot, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 48.sp)
    val chapter = TextStyle(fontFamily = Dot, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
    val numeral = TextStyle(fontFamily = Dot, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val heading = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val example = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.Serif)
    val label = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.5.sp)
    val answer = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun OracleTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (isSystemInDarkTheme()) Dark else Light, content = content)
}

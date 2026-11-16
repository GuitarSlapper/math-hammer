package ru.mathhammer.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import ru.mathhammer.R

val Background = Color(0xFF111820)
val Panel = Color(0xFF1B2530)
val Border = Color(0xFF34404C)
val Gold = Color(0xFFC6A76B)
val Danger = Color(0xFFB84E4B)
val Ink = Color(0xFFF1EEE7)
val Muted = Color(0xFFA7AFB7)
@OptIn(ExperimentalTextApi::class)
private fun exo(weight: Int) = Font(R.font.exo2, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
val Exo2 = FontFamily(exo(400), exo(600), exo(700), exo(800))

@Composable
fun MathHammerTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val type = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Exo2), displayMedium = base.displayMedium.copy(fontFamily = Exo2),
        displaySmall = base.displaySmall.copy(fontFamily = Exo2), headlineLarge = base.headlineLarge.copy(fontFamily = Exo2),
        headlineMedium = base.headlineMedium.copy(fontFamily = Exo2), headlineSmall = base.headlineSmall.copy(fontFamily = Exo2),
        titleLarge = base.titleLarge.copy(fontFamily = Exo2), titleMedium = base.titleMedium.copy(fontFamily = Exo2),
        titleSmall = base.titleSmall.copy(fontFamily = Exo2), bodyLarge = base.bodyLarge.copy(fontFamily = Exo2),
        bodyMedium = base.bodyMedium.copy(fontFamily = Exo2), bodySmall = base.bodySmall.copy(fontFamily = Exo2),
        labelLarge = base.labelLarge.copy(fontFamily = Exo2), labelMedium = base.labelMedium.copy(fontFamily = Exo2),
        labelSmall = base.labelSmall.copy(fontFamily = Exo2))
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Gold, onPrimary = Background, primaryContainer = Panel, onPrimaryContainer = Ink,
            secondary = Gold, background = Background, surface = Panel, surfaceVariant = Panel,
            onSurface = Ink, onBackground = Ink, onSurfaceVariant = Muted, outline = Border,
            error = Color(0xFFFF9690), onError = Background), typography = type, content = content)
}

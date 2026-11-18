package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AuraThemeMode {
    CYBER_OBSIDIAN,
    NEURAL_VIOLET,
    QUANTUM_EMERALD,
    SOLAR_LIGHT
}

private val CyberObsidianScheme = darkColorScheme(
    primary = CyanElectric,
    onPrimary = ObsidianDeep,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanSoft,
    secondary = VioletNeural,
    onSecondary = Color.White,
    secondaryContainer = VioletContainer,
    onSecondaryContainer = VioletSoft,
    tertiary = EmeraldActive,
    onTertiary = ObsidianDeep,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = Color(0xFF6EE7B7),
    background = ObsidianDeep,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineBright,
    error = CrimsonStop,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val NeuralVioletScheme = CyberObsidianScheme.copy(
    primary = VioletNeural,
    primaryContainer = VioletContainer,
    onPrimaryContainer = VioletSoft,
    secondary = CyanElectric,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanSoft
)

private val QuantumEmeraldScheme = CyberObsidianScheme.copy(
    primary = EmeraldActive,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondary = CyanElectric,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanSoft
)

private val SolarLightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = EmeraldActive,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = CrimsonStop,
    onError = Color.White
)

val AuraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AuraTheme(
    themeMode: AuraThemeMode = AuraThemeMode.CYBER_OBSIDIAN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AuraThemeMode.CYBER_OBSIDIAN -> CyberObsidianScheme
        AuraThemeMode.NEURAL_VIOLET -> NeuralVioletScheme
        AuraThemeMode.QUANTUM_EMERALD -> QuantumEmeraldScheme
        AuraThemeMode.SOLAR_LIGHT -> SolarLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AuraShapes,
        content = content
    )
}

package com.nutra.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.nutra.app.domain.model.ThemePreference

private val LightMaterialScheme = lightColorScheme(
    primary = LightCalorieAccent,
    onPrimary = LightCardSurface,
    primaryContainer = LightCalorieContainer,
    onPrimaryContainer = LightTextPrimary,
    secondary = LightWaterAccent,
    secondaryContainer = LightWaterContainer,
    tertiary = LightFatAccent,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightCardSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightElevatedSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightHairlineBorder,
    outlineVariant = LightHairlineBorder,
)

private val DarkMaterialScheme = darkColorScheme(
    primary = DarkCalorieAccent,
    onPrimary = DarkBackground,
    primaryContainer = DarkCalorieContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkWaterAccent,
    secondaryContainer = DarkWaterContainer,
    tertiary = DarkFatAccent,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCardSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkElevatedSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkHairlineBorder,
    outlineVariant = DarkHairlineBorder,
)

@Composable
fun NutraTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val context = LocalContext.current
    val baseSemantic = if (isDark) DarkNutraSemanticColors else LightNutraSemanticColors

    val colorScheme = when {
        useDynamicColor -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkMaterialScheme
        else -> LightMaterialScheme
    }

    val effectiveSemantic = if (useDynamicColor) {
        baseSemantic.copy(
            calorieAccent = colorScheme.primary,
            calorieContainer = colorScheme.primaryContainer,
        )
    } else {
        baseSemantic
    }

    CompositionLocalProvider(
        LocalNutraColors provides effectiveSemantic,
        LocalNutraTypography provides DefaultNutraCustomTypography,
        LocalNutraSpacing provides NutraSpacing(),
        LocalNutraShapes provides NutraShapes(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Material3Typography,
            content = content,
        )
    }
}

object NutraTheme {
    val colors: NutraSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNutraColors.current

    val typography: NutraCustomTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalNutraTypography.current

    val spacing: NutraSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalNutraSpacing.current

    val shapes: NutraShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalNutraShapes.current
}

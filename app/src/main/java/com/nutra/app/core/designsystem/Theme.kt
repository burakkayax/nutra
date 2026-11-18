package com.nutra.app.core.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import com.nutra.app.domain.model.ThemePreference

private fun NutraColors.toMaterialScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = accentSoft,
        onPrimaryContainer = textPrimary,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = surfaceMuted,
        onSecondaryContainer = textPrimary,
        tertiary = water,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceElevated,
        surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceMuted,
        surfaceTint = surface,
        outline = divider,
        outlineVariant = divider,
        error = danger,
        onError = onInk,
        errorContainer = dangerSoft,
        scrim = scrim,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val DefaultNutraSpacing = NutraSpacing()
private val DefaultNutraShapes = NutraShapes()

@Composable
fun ThemePreference.resolveIsDark(): Boolean = when (this) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

@Composable
fun NutraTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = themePreference.resolveIsDark()

    val context = LocalContext.current
    val baseColors = if (isDark) DarkNutraColors else LightNutraColors

    // Dynamic color only re-tints the brand accent; neutrals stay calm.
    val colors = remember(isDark, useDynamicColor, context) {
        if (useDynamicColor) {
            val dynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            baseColors.copy(
                accent = dynamic.primary,
                accentSoft = dynamic.primaryContainer,
                onAccent = dynamic.onPrimary,
            )
        } else {
            baseColors
        }
    }
    val materialScheme = remember(colors) { colors.toMaterialScheme() }

    val view = LocalView.current
    val backgroundArgb = colors.background.toArgb()
    if (!view.isInEditMode) {
        DisposableEffect(view, backgroundArgb) {
            val window = view.context.findActivity()?.window
            val currentBg = (window?.decorView?.background as? android.graphics.drawable.ColorDrawable)?.color
            if (currentBg != backgroundArgb) {
                window?.setBackgroundDrawable(backgroundArgb.toDrawable())
            }
            onDispose {}
        }
    }

    CompositionLocalProvider(
        LocalNutraColors provides colors,
        LocalNutraTypography provides DefaultNutraTypography,
        LocalNutraSpacing provides DefaultNutraSpacing,
        LocalNutraShapes provides DefaultNutraShapes,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Material3Typography,
            content = content,
        )
    }
}

object NutraTheme {
    val colors: NutraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNutraColors.current

    val typography: NutraTypography
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

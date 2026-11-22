package com.nutra.app.core.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nutra.app.domain.model.ThemePreference

val LocalReducedMotion = staticCompositionLocalOf { false }

private fun readReducedMotionSetting(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

private fun NutraColors.toMaterialScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = accentSoft,
        onPrimaryContainer = onAccentSoft,
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
        outline = controlBorder,
        outlineVariant = divider,
        error = danger,
        onError = onInk,
        errorContainer = dangerSoft,
        onErrorContainer = dangerText,
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
    val dynamicConfigKey = if (useDynamicColor) LocalConfiguration.current else null
    val baseColors = if (isDark) DarkNutraColors else LightNutraColors

    var reducedMotion by remember(context) { mutableStateOf(readReducedMotionSetting(context)) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            reducedMotion = readReducedMotionSetting(context)
        }
    }

    // Dynamic color only re-tints the brand accent; neutrals stay calm.
    val colors = remember(isDark, useDynamicColor, context, dynamicConfigKey) {
        if (useDynamicColor) {
            val dynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            baseColors.copy(
                accent = dynamic.primary,
                accentSoft = dynamic.primaryContainer,
                onAccent = dynamic.onPrimary,
                onAccentSoft = dynamic.onPrimaryContainer,
                accentOnInk = dynamic.inversePrimary,
                controlBorderFocused = dynamic.primary,
            )
        } else {
            baseColors
        }
    }
    val materialScheme = remember(colors) { colors.toMaterialScheme() }

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
        LocalReducedMotion provides reducedMotion,
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

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedMotion.current
}

package com.nutra.app

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutra.app.core.datastore.ProfileStorageState
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.InlinePredictiveBackHandler
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraPredictiveBackOverlayHost
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.PredictiveBackTransform
import com.nutra.app.core.designsystem.predictiveBackBackground
import com.nutra.app.core.designsystem.predictiveBackForeground
import com.nutra.app.core.designsystem.rememberDisplayCornerRadiusPx
import com.nutra.app.core.designsystem.rememberHoldHighlightAlpha
import com.nutra.app.core.designsystem.rememberInlinePredictiveBackState
import com.nutra.app.core.designsystem.rememberPredictiveBackOverlayState
import com.nutra.app.core.designsystem.resolveIsDark
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.feature.analytics.AnalyticsScreen
import com.nutra.app.feature.analytics.AnalyticsViewModel
import com.nutra.app.feature.analytics.WeightHistoryScreen
import com.nutra.app.feature.dashboard.DashboardScreen
import com.nutra.app.feature.dashboard.DashboardViewModel
import com.nutra.app.feature.intelligence.IntelligenceViewModel
import com.nutra.app.feature.intelligence.NutraIntelligenceScreen
import com.nutra.app.feature.metabolism.MetabolismDetailLoadingScreen
import com.nutra.app.feature.metabolism.MetabolismDetailScreen
import com.nutra.app.feature.onboarding.OnboardingScreen
import com.nutra.app.feature.onboarding.OnboardingViewModel
import com.nutra.app.feature.profile.DailyGoalsScreen
import com.nutra.app.feature.profile.DietaryPreferencesScreen
import com.nutra.app.feature.profile.EditBodyProfileScreen
import com.nutra.app.feature.profile.ProfileScreen
import com.nutra.app.feature.profile.ProfileViewModel
import com.nutra.app.feature.profile.WaterReminderScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PrimaryDestination(val title: String) {
    DASHBOARD("Bugün"),
    ANALYTICS("İlerleme"),
    PROFILE("Profil");

    @Composable
    fun iconVector(selected: Boolean): ImageVector = when (this) {
        DASHBOARD -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
        ANALYTICS -> if (selected) NutraIcons.InsightsRounded else NutraIcons.InsightsOutlined
        PROFILE -> if (selected) Icons.Rounded.Person else Icons.Outlined.Person
    }
}

@Immutable
private data class RootAppConfig(
    val onboardingCompleted: Boolean,
    val themePreference: ThemePreference,
    val useDynamicColor: Boolean,
)

/** Height reserved at the bottom of every tab so content can scroll above the floating nav bar. */
private val BottomBarReservedHeight = 96.dp
private val NavPillSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 420f)
private val NavIconScaleSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 500f)

private const val CONTENT_READY_PREDRAW_TIMEOUT_MS = 350L
private const val CONFIG_EMERGENCY_PREDRAW_TIMEOUT_MS = 1200L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    private var hasRootConfig = false
    private var isAppReady = false
    private var lastAppliedIsDark: Boolean? = null
    private val showSkeletonFallbackState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cachedProfile = preferencesDataStore.currentCachedProfile()
        val initialDark = when (cachedProfile?.themePreference) {
            ThemePreference.DARK -> true
            ThemePreference.LIGHT -> false
            ThemePreference.SYSTEM, null ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        applyEdgeToEdgeForTheme(initialDark)

        val contentView: View = findViewById(android.R.id.content)
        val preDrawStartMs = SystemClock.uptimeMillis()
        contentView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val elapsed = SystemClock.uptimeMillis() - preDrawStartMs
                    val timedOutWithConfig = hasRootConfig && elapsed >= CONTENT_READY_PREDRAW_TIMEOUT_MS
                    val allowDraw = isAppReady || timedOutWithConfig || elapsed >= CONFIG_EMERGENCY_PREDRAW_TIMEOUT_MS
                    return if (allowDraw) {
                        if (!isAppReady && timedOutWithConfig) {
                            showSkeletonFallbackState.value = true
                        }
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            },
        )

        setContent {
            val storageState by preferencesDataStore.profileStorageState.collectAsStateWithLifecycle()
            val retryScope = rememberCoroutineScope()
            var isRetryingRead by remember { mutableStateOf(false) }

            val resolvedProfile = when (val state = storageState) {
                is ProfileStorageState.Loaded -> state.profile
                is ProfileStorageState.Loading -> cachedProfile
                is ProfileStorageState.ReadError -> null
            }

            if (storageState is ProfileStorageState.ReadError) {
                val errMsg = (storageState as ProfileStorageState.ReadError).message
                SideEffect {
                    hasRootConfig = true
                    isAppReady = true
                }
                NutraTheme(themePreference = ThemePreference.SYSTEM, useDynamicColor = false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NutraTheme.colors.background)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        NutraEmptyState(
                            icon = Icons.Rounded.Warning,
                            title = "Veriler okunamadı",
                            message = errMsg,
                            action = {
                                NutraButton(
                                    text = "Tekrar dene",
                                    loading = isRetryingRead,
                                    onClick = {
                                        if (!isRetryingRead) {
                                            isRetryingRead = true
                                            retryScope.launch {
                                                preferencesDataStore.retryReadProfile()
                                                isRetryingRead = false
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
                return@setContent
            }

            val config = remember(
                resolvedProfile?.onboardingCompleted,
                resolvedProfile?.themePreference,
                resolvedProfile?.useDynamicColor,
            ) {
                resolvedProfile?.let { profile ->
                    RootAppConfig(
                        onboardingCompleted = profile.onboardingCompleted,
                        themePreference = profile.themePreference,
                        useDynamicColor = profile.useDynamicColor,
                    )
                }
            }
            if (config == null) {
                SideEffect {
                    hasRootConfig = false
                    isAppReady = false
                }
                return@setContent
            }

            SideEffect { hasRootConfig = true }

            LaunchedEffect(config.themePreference) {
                withFrameNanos { }
                preferencesDataStore.applySystemNightMode(config.themePreference)
            }

            val isDark = config.themePreference.resolveIsDark()
            SideEffect {
                applyEdgeToEdgeForTheme(isDark)
            }

            NutraTheme(
                themePreference = config.themePreference,
                useDynamicColor = config.useDynamicColor,
            ) {
                NutraAppRoot(
                    onboardingCompleted = config.onboardingCompleted,
                    showSkeletonFallback = showSkeletonFallbackState.value,
                    onReadyChanged = { ready -> isAppReady = ready },
                )
            }
        }
    }

    private fun applyEdgeToEdgeForTheme(isDark: Boolean) {
        if (lastAppliedIsDark == isDark) return
        lastAppliedIsDark = isDark
        val barStyle = if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            )
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle,
        )
    }
}

private enum class OverlayDestination {
    INTELLIGENCE_STUDIO,
    METABOLISM_DETAIL,
    WEIGHT_HISTORY,
    EDIT_BODY_PROFILE,
    DAILY_GOALS,
    WATER_REMINDER,
    DIETARY_PREFERENCES,
}

private class TabLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

@Composable
fun NutraAppRoot(
    onboardingCompleted: Boolean,
    showSkeletonFallback: Boolean = false,
    onReadyChanged: (Boolean) -> Unit = {},
) {
    var activeTab by rememberSaveable { mutableStateOf(PrimaryDestination.DASHBOARD) }
    var activeOverlay by rememberSaveable { mutableStateOf<OverlayDestination?>(null) }

    Crossfade(
        targetState = onboardingCompleted,
        animationSpec = tween(240),
        label = "RootOnboardingTransition",
    ) { isCompleted ->
        if (!isCompleted) {
            SideEffect { onReadyChanged(true) }
            ReportDrawnWhen { true }
            val onboardingViewModel: OnboardingViewModel = viewModel()
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onOnboardingFinished = {
                    activeTab = PrimaryDestination.DASHBOARD
                    activeOverlay = null
                },
            )
        } else {
            val dashboardViewModel: DashboardViewModel = viewModel()
            val isDashboardReady by dashboardViewModel.isReady.collectAsStateWithLifecycle()
            // Read isDashboardReady directly in composition so NutraAppRoot recomposes the instant
            // Room emits and releases the pre-draw splash gate without waiting for the fallback timeout.
            val readyNow = isDashboardReady
            SideEffect { onReadyChanged(readyNow) }
            ReportDrawnWhen { isDashboardReady }

            if (!readyNow && !showSkeletonFallback) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NutraTheme.colors.background),
                )
                return@Crossfade
            }

            // Mount Dashboard immediately for first frame, and keep at most one secondary tab
            // (the active or most recently visited secondary tab) alive in composition so peak
            // memory stays bounded while SaveableStateProvider preserves scroll state across swaps.
            var lastSecondaryTab by rememberSaveable { mutableStateOf<PrimaryDestination?>(null) }

            LaunchedEffect(activeTab) {
                if (activeTab == PrimaryDestination.ANALYTICS || activeTab == PrimaryDestination.PROFILE) {
                    lastSecondaryTab = activeTab
                }
            }

            val mountAnalytics = activeTab == PrimaryDestination.ANALYTICS || lastSecondaryTab == PrimaryDestination.ANALYTICS
            val mountProfile = activeTab == PrimaryDestination.PROFILE || lastSecondaryTab == PrimaryDestination.PROFILE
            val saveableStateHolder = rememberSaveableStateHolder()
            var dashboardScrollTopTick by remember { mutableIntStateOf(0) }
            var analyticsScrollTopTick by remember { mutableIntStateOf(0) }
            var profileScrollTopTick by remember { mutableIntStateOf(0) }

            val intelligenceViewModel: IntelligenceViewModel = viewModel()
            val densityObj = LocalDensity.current
            val density = densityObj.density
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val colors = NutraTheme.colors
            val isDarkTheme = colors.isDark
            val reducedMotion = NutraTheme.reducedMotion
            val deviceCornerRadiusPx = rememberDisplayCornerRadiusPx()

            val overlayBackState = rememberPredictiveBackOverlayState(activeOverlay)
            val tabBackState = rememberInlinePredictiveBackState()
            var skipNextTabCrossfade by remember { mutableStateOf(false) }

            val hasActiveOverlay = activeOverlay != null
            val hasRenderedOverlay = overlayBackState.hasRenderedOverlay
            val isOverlayBackgroundRevealed = overlayBackState.isBackgroundRevealed
            val overlayFullyCoversBase = overlayBackState.overlayFullyCoversBase
            val isBaseInteractive = !hasActiveOverlay && !overlayBackState.isCommittingExit
            val isTabBackActive = tabBackState.isActive && !hasRenderedOverlay && activeTab != PrimaryDestination.DASHBOARD

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                intelligenceViewModel.refreshToday()
            }

            // Side-effect-only date sync so NutraAppRoot never recomposes on Dashboard date changes.
            LaunchedEffect(dashboardViewModel, intelligenceViewModel) {
                dashboardViewModel.uiState
                    .map { it.selectedDate }
                    .distinctUntilChanged()
                    .collect { selectedDate ->
                        intelligenceViewModel.syncDate(selectedDate)
                    }
            }

            // Secondary primary tabs (Analytics / Profile) return to Dashboard with Cross-Activity Predictive Back.
            // On root Dashboard (no overlay), no handler is enabled so Android's native Back-to-Home runs.
            InlinePredictiveBackHandler(
                enabled = !hasActiveOverlay && !hasRenderedOverlay && activeTab != PrimaryDestination.DASHBOARD,
                state = tabBackState,
                onCommit = {
                    skipNextTabCrossfade = true
                    activeTab = PrimaryDestination.DASHBOARD
                },
            )

            LaunchedEffect(activeTab) {
                if (skipNextTabCrossfade) {
                    withFrameNanos { }
                    skipNextTabCrossfade = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
                    .onSizeChanged { size ->
                        if (size.width > 0) {
                            tabBackState.containerWidthPx = size.width.toFloat()
                        }
                    },
            ) {
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                var measuredBottomBarHeight by remember { mutableStateOf(BottomBarReservedHeight + navBarBottom) }
                val effectiveBottomClearance = maxOf(BottomBarReservedHeight + navBarBottom, measuredBottomBarHeight)
                val contentPadding = remember(statusBarTop, effectiveBottomClearance) {
                    PaddingValues(
                        top = statusBarTop,
                        bottom = effectiveBottomClearance,
                    )
                }

                val horizontalContentInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))

                // Layer 1: Unified Base Screen (primary tabs + floating NutraBottomBar) so the
                // entire previous screen reveals cleanly behind shrinking predictive-back overlays.
                val latestBaseInteractive by rememberUpdatedState(isBaseInteractive)
                val baseLayerModifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .predictiveBackBackground(
                        transformProvider = {
                            if (isOverlayBackgroundRevealed) {
                                overlayBackState.currentTransform(
                                    density = density,
                                    deviceCornerRadiusPx = deviceCornerRadiusPx,
                                    isDarkTheme = isDarkTheme,
                                    reducedMotion = reducedMotion,
                                    isRtl = isRtl,
                                )
                            } else {
                                PredictiveBackTransform.Identity
                            }
                        },
                    )
                    .focusProperties { canFocus = isBaseInteractive }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (!latestBaseInteractive) {
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }

                Box(
                    modifier = if (isBaseInteractive) {
                        baseLayerModifier
                    } else {
                        baseLayerModifier.clearAndSetSemantics {}
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(horizontalContentInsets),
                    ) {
                        val isDashboardActive = activeTab == PrimaryDestination.DASHBOARD
                        val isDashboardRevealedForOverlay = isDashboardActive && isOverlayBackgroundRevealed
                        val isDashboardRevealedForTabBack = isTabBackActive

                        TabSlotBox(
                            isSelected = isDashboardActive && isBaseInteractive,
                            modifier = if (isDashboardRevealedForTabBack) {
                                Modifier.predictiveBackBackground(
                                    transformProvider = {
                                        tabBackState.currentTransform(
                                            density = density,
                                            deviceCornerRadiusPx = deviceCornerRadiusPx,
                                            isDarkTheme = isDarkTheme,
                                            reducedMotion = reducedMotion,
                                            isRtl = isRtl,
                                        )
                                    },
                                )
                            } else {
                                Modifier
                            },
                            isRevealedInBackground = isDashboardRevealedForOverlay || isDashboardRevealedForTabBack,
                            instantTransition = skipNextTabCrossfade,
                            zIndexOverride = if (isDashboardRevealedForTabBack) 0.5f else null,
                        ) {
                            saveableStateHolder.SaveableStateProvider(PrimaryDestination.DASHBOARD) {
                                DashboardScreen(
                                    viewModel = dashboardViewModel,
                                    contentPadding = contentPadding,
                                    scrollToTopTrigger = dashboardScrollTopTick,
                                    intelligenceCardStateFlow = intelligenceViewModel.dashboardCardState,
                                    onOpenIntelligenceStudio = { mealType, tab ->
                                        intelligenceViewModel.openForMeal(mealType, tab)
                                        activeOverlay = OverlayDestination.INTELLIGENCE_STUDIO
                                    },
                                )
                            }
                        }

                        if (mountAnalytics) {
                            val isAnalyticsActive = activeTab == PrimaryDestination.ANALYTICS
                            val isAnalyticsGesturingBack = isAnalyticsActive && isTabBackActive
                            TabSlotBox(
                                isSelected = isAnalyticsActive && isBaseInteractive,
                                modifier = if (isAnalyticsGesturingBack) {
                                    Modifier
                                        .predictiveBackForeground(
                                            transformProvider = {
                                                tabBackState.currentTransform(
                                                    density = density,
                                                    deviceCornerRadiusPx = deviceCornerRadiusPx,
                                                    isDarkTheme = isDarkTheme,
                                                    reducedMotion = reducedMotion,
                                                    isRtl = isRtl,
                                                )
                                            },
                                            borderColor = colors.textPrimary,
                                            ambientShadowColor = colors.ink,
                                        )
                                        .background(colors.background)
                                } else {
                                    Modifier
                                },
                                isRevealedInBackground = isAnalyticsActive && isOverlayBackgroundRevealed,
                                instantTransition = skipNextTabCrossfade,
                                zIndexOverride = if (isAnalyticsGesturingBack) 1.5f else null,
                            ) {
                                saveableStateHolder.SaveableStateProvider(PrimaryDestination.ANALYTICS) {
                                    val analyticsViewModel: AnalyticsViewModel = viewModel()
                                    AnalyticsScreen(
                                        viewModel = analyticsViewModel,
                                        contentPadding = contentPadding,
                                        scrollToTopTrigger = analyticsScrollTopTick,
                                        onOpenMetabolismDetail = { activeOverlay = OverlayDestination.METABOLISM_DETAIL },
                                        onOpenWeightHistory = { activeOverlay = OverlayDestination.WEIGHT_HISTORY },
                                    )
                                }
                            }
                        }

                        if (mountProfile) {
                            val isProfileActive = activeTab == PrimaryDestination.PROFILE
                            val isProfileGesturingBack = isProfileActive && isTabBackActive
                            TabSlotBox(
                                isSelected = isProfileActive && isBaseInteractive,
                                modifier = if (isProfileGesturingBack) {
                                    Modifier
                                        .predictiveBackForeground(
                                            transformProvider = {
                                                tabBackState.currentTransform(
                                                    density = density,
                                                    deviceCornerRadiusPx = deviceCornerRadiusPx,
                                                    isDarkTheme = isDarkTheme,
                                                    reducedMotion = reducedMotion,
                                                    isRtl = isRtl,
                                                )
                                            },
                                            borderColor = colors.textPrimary,
                                            ambientShadowColor = colors.ink,
                                        )
                                        .background(colors.background)
                                } else {
                                    Modifier
                                },
                                isRevealedInBackground = isProfileActive && isOverlayBackgroundRevealed,
                                instantTransition = skipNextTabCrossfade,
                                zIndexOverride = if (isProfileGesturingBack) 1.5f else null,
                            ) {
                                saveableStateHolder.SaveableStateProvider(PrimaryDestination.PROFILE) {
                                    val profileViewModel: ProfileViewModel = viewModel()
                                    ProfileScreen(
                                        viewModel = profileViewModel,
                                        contentPadding = contentPadding,
                                        scrollToTopTrigger = profileScrollTopTick,
                                        onEditBodyProfile = { activeOverlay = OverlayDestination.EDIT_BODY_PROFILE },
                                        onOpenDailyGoals = { activeOverlay = OverlayDestination.DAILY_GOALS },
                                        onOpenWaterReminder = { activeOverlay = OverlayDestination.WATER_REMINDER },
                                        onOpenDietaryPreferences = { activeOverlay = OverlayDestination.DIETARY_PREFERENCES },
                                    )
                                }
                            }
                        }
                    }

                    if (!overlayFullyCoversBase) {
                        NutraBottomBar(
                            selectedTab = activeTab,
                            onSelectTab = { dest ->
                                tabBackState.resetImmediately()
                                skipNextTabCrossfade = false
                                activeOverlay = null
                                if (dest == activeTab) {
                                    when (dest) {
                                        PrimaryDestination.DASHBOARD -> dashboardScrollTopTick++
                                        PrimaryDestination.ANALYTICS -> analyticsScrollTopTick++
                                        PrimaryDestination.PROFILE -> profileScrollTopTick++
                                    }
                                } else {
                                    activeTab = dest
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(2f)
                                .onSizeChanged { size ->
                                    if (size.height > 0) {
                                        measuredBottomBarHeight = with(densityObj) { size.height.toDp() }
                                    }
                                },
                        )
                    }
                }

                // Layer 2: Unified Cross-Activity Predictive Back Host for all full-screen sub-screens
                NutraPredictiveBackOverlayHost(
                    currentDestination = activeOverlay,
                    state = overlayBackState,
                    onDismiss = { activeOverlay = null },
                    deviceCornerRadiusPx = deviceCornerRadiusPx,
                    modifier = Modifier.zIndex(3f),
                ) { destination ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background)
                            .windowInsetsPadding(horizontalContentInsets),
                    ) {
                        when (destination) {
                            OverlayDestination.INTELLIGENCE_STUDIO -> {
                                NutraIntelligenceScreen(
                                    viewModel = intelligenceViewModel,
                                    onNavigateBack = { activeOverlay = null },
                                    onLoggedMeal = { mealType, msg ->
                                        dashboardViewModel.onIntelligenceMealLogged(mealType, msg)
                                    },
                                )
                            }

                            OverlayDestination.METABOLISM_DETAIL -> {
                                val analyticsViewModel: AnalyticsViewModel = viewModel()
                                val analysis by analyticsViewModel.metabolismAnalysis.collectAsStateWithLifecycle()
                                val metabolismFeedback by analyticsViewModel.metabolismFeedback.collectAsStateWithLifecycle()
                                val currentAnalysis = analysis
                                if (currentAnalysis != null) {
                                    MetabolismDetailScreen(
                                        analysis = currentAnalysis,
                                        onNavigateBack = { activeOverlay = null },
                                        onApproveProposal = analyticsViewModel::approveCalorieProposal,
                                        onDismissProposal = analyticsViewModel::dismissCalorieProposal,
                                        feedbackMessage = metabolismFeedback.first,
                                        feedbackEventId = metabolismFeedback.second,
                                        onClearFeedback = analyticsViewModel::clearFeedback,
                                    )
                                } else {
                                    MetabolismDetailLoadingScreen(
                                        onNavigateBack = { activeOverlay = null },
                                    )
                                }
                            }

                            OverlayDestination.WEIGHT_HISTORY -> {
                                val analyticsViewModel: AnalyticsViewModel = viewModel()
                                val analyticsState by analyticsViewModel.uiState.collectAsStateWithLifecycle()
                                val allEntries by analyticsViewModel.allWeightEntries.collectAsStateWithLifecycle()
                                val weightFeedback by analyticsViewModel.metabolismFeedback.collectAsStateWithLifecycle()
                                val immutableEntries = remember(allEntries) { allEntries.toImmutableList() }
                                val latestWeight = analyticsState.latestRecordedWeightKg
                                    ?: analyticsState.profile.currentWeightKg
                                WeightHistoryScreen(
                                    entries = immutableEntries,
                                    todayIso = analyticsState.todayIso,
                                    targetWeightKg = analyticsState.profile.targetWeightKg,
                                    latestWeightKg = latestWeight,
                                    goalType = analyticsState.profile.goalType,
                                    onNavigateBack = { activeOverlay = null },
                                    onAddWeight = analyticsViewModel::logNewWeight,
                                    onEditWeightEntry = analyticsViewModel::updateWeightEntry,
                                    onDeleteWeightEntry = analyticsViewModel::deleteWeightEntry,
                                    feedbackMessage = weightFeedback.first,
                                    feedbackEventId = weightFeedback.second,
                                    onClearFeedback = analyticsViewModel::clearFeedback,
                                )
                            }

                            OverlayDestination.EDIT_BODY_PROFILE -> {
                                val profileViewModel: ProfileViewModel = viewModel()
                                val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                                if (profileState.isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colors.background),
                                    )
                                } else {
                                    EditBodyProfileScreen(
                                        profile = profileState.profile,
                                        onNavigateBack = { activeOverlay = null },
                                        onProfileChange = { sex, age, heightCm, currentWeight, targetWeight, activity, goalType, recalculate ->
                                            profileViewModel.updateBodyProfile(
                                                sex = sex,
                                                age = age,
                                                heightCm = heightCm,
                                                currentWeightKg = currentWeight,
                                                targetWeightKg = targetWeight,
                                                activityLevel = activity,
                                                goalType = goalType,
                                                recalculateGoals = recalculate,
                                            )
                                        },
                                    )
                                }
                            }

                            OverlayDestination.DAILY_GOALS -> {
                                val profileViewModel: ProfileViewModel = viewModel()
                                val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                                if (profileState.isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colors.background),
                                    )
                                } else {
                                    val analyticsViewModel: AnalyticsViewModel = viewModel()
                                    val metabolismAnalysis by analyticsViewModel.metabolismAnalysis.collectAsStateWithLifecycle()
                                    DailyGoalsScreen(
                                        profile = profileState.profile,
                                        onNavigateBack = { activeOverlay = null },
                                        onGoalsChange = { updatedGoals, source ->
                                            profileViewModel.updateDailyGoals(updatedGoals, source)
                                        },
                                        metabolismAnalysis = metabolismAnalysis,
                                        onRecalculateMifflin = profileViewModel::recalculateWithMifflinStJeor,
                                        onUndoRecalculate = profileViewModel::undoRecalculateGoals,
                                        feedback = profileState.feedback,
                                        onClearFeedback = profileViewModel::clearFeedback,
                                    )
                                }
                            }

                            OverlayDestination.DIETARY_PREFERENCES -> {
                                val profileViewModel: ProfileViewModel = viewModel()
                                val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                                if (profileState.isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colors.background),
                                    )
                                } else {
                                    val immutableDislikedFoods = remember(profileState.dislikedFoods) {
                                        profileState.dislikedFoods.toImmutableList()
                                    }
                                    DietaryPreferencesScreen(
                                        profile = profileState.profile,
                                        dislikedFoods = immutableDislikedFoods,
                                        onNavigateBack = { activeOverlay = null },
                                        onDietaryProfileChange = profileViewModel::updateDietaryProfile,
                                        onAdaptiveMetabolismEnabledChange = profileViewModel::updateAdaptiveMetabolismEnabled,
                                        onRestoreDislikedFood = profileViewModel::restoreDislikedFood,
                                        feedback = profileState.feedback,
                                        onClearFeedback = profileViewModel::clearFeedback,
                                    )
                                }
                            }

                            OverlayDestination.WATER_REMINDER -> {
                                val profileViewModel: ProfileViewModel = viewModel()
                                val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                                if (profileState.isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colors.background),
                                    )
                                } else {
                                    WaterReminderScreen(
                                        initialSettings = profileState.profile.waterReminder,
                                        waterGoalMl = profileState.profile.goals.waterMl,
                                        onNavigateBack = { activeOverlay = null },
                                        onSettingsChange = { updatedReminder ->
                                            profileViewModel.updateWaterReminder(updatedReminder)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Keeps visited tab UI state warm off-screen while pausing its Lifecycle to CREATED (stopping
 * collectAsStateWithLifecycle subscriptions & background recompositions) and skipping measure/layout,
 * focus, pointer input, and accessibility when hidden. When [isRevealedInBackground] is true (during
 * predictive back gestures or overlay transitions), the tab renders immediately behind the active surface.
 */
@Composable
private fun TabSlotBox(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isRevealedInBackground: Boolean = false,
    instantTransition: Boolean = false,
    zIndexOverride: Float? = null,
    content: @Composable () -> Unit,
) {
    val parentLifecycleOwner = LocalLifecycleOwner.current
    val isVisible = isSelected || isRevealedInBackground
    // Start active/prewarmed tabs at STARTED so initial Room/DataStore hydration completes once,
    // then pause hidden tabs down to CREATED after fade-out/hydration settles.
    var allowHiddenCollection by remember { mutableStateOf(!isVisible) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            allowHiddenCollection = false
        } else {
            allowHiddenCollection = true
            delay(320L)
            allowHiddenCollection = false
        }
    }

    val desiredMaxState = when {
        isSelected -> Lifecycle.State.RESUMED
        isRevealedInBackground || allowHiddenCollection -> Lifecycle.State.STARTED
        else -> Lifecycle.State.CREATED
    }
    val tabLifecycleOwner = remember(parentLifecycleOwner) {
        TabLifecycleOwner().apply {
            val parentState = parentLifecycleOwner.lifecycle.currentState
            val initial = if (parentState.isAtLeast(desiredMaxState)) desiredMaxState else parentState
            if (initial != Lifecycle.State.INITIALIZED) {
                registry.currentState = initial
            }
        }
    }
    val latestIsSelected = rememberUpdatedState(isSelected)
    val latestIsRevealed = rememberUpdatedState(isRevealedInBackground)
    val latestDesiredMaxState = rememberUpdatedState(desiredMaxState)

    fun syncTabLifecycle(parentState: Lifecycle.State, maxState: Lifecycle.State) {
        val current = tabLifecycleOwner.registry.currentState
        if (current == Lifecycle.State.DESTROYED) return
        val target = if (parentState.isAtLeast(maxState)) maxState else parentState
        if (target == Lifecycle.State.INITIALIZED || current == target) return
        if (current == Lifecycle.State.INITIALIZED && target == Lifecycle.State.DESTROYED) return
        tabLifecycleOwner.registry.currentState = target
    }

    DisposableEffect(parentLifecycleOwner) {
        val observer = LifecycleEventObserver { source, _ ->
            syncTabLifecycle(source.lifecycle.currentState, latestDesiredMaxState.value)
        }
        syncTabLifecycle(parentLifecycleOwner.lifecycle.currentState, latestDesiredMaxState.value)
        parentLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            parentLifecycleOwner.lifecycle.removeObserver(observer)
            if (tabLifecycleOwner.registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                tabLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }

    SideEffect {
        syncTabLifecycle(parentLifecycleOwner.lifecycle.currentState, desiredMaxState)
    }

    val alphaAnim = remember { Animatable(if (isVisible) 1f else 0f) }
    LaunchedEffect(isSelected, isRevealedInBackground, instantTransition) {
        val target = if (isSelected || isRevealedInBackground) 1f else 0f
        if (isRevealedInBackground || instantTransition) {
            alphaAnim.snapTo(target)
        } else if (alphaAnim.value != target) {
            alphaAnim.animateTo(target, tween(durationMillis = 150))
        }
    }

    // Only invalidate layout when crossing visibility threshold, keeping per-frame fade GPU-only.
    val shouldLayoutState = remember {
        derivedStateOf { latestIsSelected.value || latestIsRevealed.value || alphaAnim.value > 0.01f }
    }

    val effectiveZIndex = zIndexOverride ?: if (isSelected) 1f else 0f

    val baseModifier = Modifier
        .fillMaxSize()
        .zIndex(effectiveZIndex)
        .focusProperties { canFocus = isSelected }
        .then(modifier)
        .graphicsLayer {
            alpha = if (latestIsRevealed.value) 1f else alphaAnim.value
        }
        .layout { measurable, constraints ->
            if (!shouldLayoutState.value) {
                layout(0, 0) {}
            } else {
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (!latestIsSelected.value) {
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }

    CompositionLocalProvider(LocalLifecycleOwner provides tabLifecycleOwner) {
        Box(
            modifier = if (isSelected) {
                baseModifier
            } else {
                baseModifier.clearAndSetSemantics {}
            },
        ) {
            content()
        }
    }
}

/**
 * Centered floating pill bottom bar over a soft vertical fade scrim with a sliding active pill indicator.
 */
@Composable
private fun NutraBottomBar(
    selectedTab: PrimaryDestination,
    onSelectTab: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NutraTheme.colors
    val typography = NutraTheme.typography
    val layoutDirection = LocalLayoutDirection.current
    val shadowElevation = if (colors.isDark) 0.dp else 2.dp
    val shadowTint = colors.ink.copy(alpha = 0.28f)
    val borderStroke = remember(colors.isDark, colors.segmentThumb, colors.divider) {
        if (colors.isDark) {
            BorderStroke(1.dp, colors.segmentThumb.copy(alpha = 0.65f))
        } else {
            BorderStroke(1.dp, colors.divider.copy(alpha = 0.85f))
        }
    }
    val scrimBrush = remember(colors.background) {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.38f to colors.background.copy(alpha = 0.35f),
            0.75f to colors.background.copy(alpha = 0.82f),
            1f to colors.background.copy(alpha = 0.96f),
        )
    }
    val labelStyle = remember(typography.overline, typography.caption) {
        typography.overline.copy(letterSpacing = typography.caption.letterSpacing)
    }
    val activePillColor = if (colors.isDark) colors.segmentThumb.copy(alpha = 0.65f) else colors.surfaceMuted
    val holdHighlightBase = colors.ink.copy(alpha = if (colors.isDark) 0.12f else 0.07f)
    val animatedIndexState = animateFloatAsState(
        targetValue = selectedTab.ordinal.toFloat(),
        animationSpec = NavPillSpring,
        label = "navPillIndex",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scrimBrush)
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val pillShape = NutraTheme.shapes.pill
        Surface(
            modifier = Modifier
                .widthIn(max = 328.dp)
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .graphicsLayer {
                    alpha = 0.97f
                    if (shadowElevation > 0.dp) {
                        this.shadowElevation = shadowElevation.toPx()
                        this.shape = pillShape
                        this.ambientShadowColor = shadowTint
                        this.spotShadowColor = shadowTint
                    }
                },
            shape = pillShape,
            color = if (colors.isDark) colors.surfaceElevated else colors.surface,
            shadowElevation = 0.dp,
            border = borderStroke,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .drawBehind {
                        val count = PrimaryDestination.entries.size
                        val slotWidth = size.width / count.toFloat()
                        val visualIndex = if (layoutDirection == LayoutDirection.Rtl) {
                            (count - 1) - animatedIndexState.value
                        } else {
                            animatedIndexState.value
                        }
                        val corner = CornerRadius(size.height / 2f, size.height / 2f)
                        drawRoundRect(
                            color = activePillColor,
                            topLeft = Offset(visualIndex * slotWidth, 0f),
                            size = Size(slotWidth, size.height),
                            cornerRadius = corner,
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryDestination.entries.forEach { dest ->
                    NutraBottomBarItem(
                        destination = dest,
                        selected = dest == selectedTab,
                        selectedColor = colors.textPrimary,
                        unselectedColor = colors.textTertiary,
                        holdHighlightBase = holdHighlightBase,
                        labelStyle = labelStyle,
                        onSelect = onSelectTab,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NutraBottomBarItem(
    destination: PrimaryDestination,
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    holdHighlightBase: Color,
    labelStyle: TextStyle,
    onSelect: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val holdAlphaState = rememberHoldHighlightAlpha(
        interactionSource = interactionSource,
        label = "navHoldAlpha",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(180),
        label = "navTint",
    )
    val iconScaleState = animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = NavIconScaleSpring,
        label = "navIconScale",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 52.dp)
            .clip(NutraTheme.shapes.pill)
            .drawBehind {
                val a = holdAlphaState.value
                if (a > 0.005f) {
                    drawRect(holdHighlightBase.copy(alpha = holdHighlightBase.alpha * a))
                }
            }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect(destination)
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.iconVector(selected),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val s = iconScaleState.value
                    scaleX = s
                    scaleY = s
                },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = destination.title,
            style = labelStyle,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

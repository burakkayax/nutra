package com.nutra.app

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberHoldHighlightAlpha
import com.nutra.app.core.designsystem.resolveIsDark
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.feature.analytics.AnalyticsScreen
import com.nutra.app.feature.analytics.AnalyticsViewModel
import com.nutra.app.feature.dashboard.DashboardScreen
import com.nutra.app.feature.dashboard.DashboardViewModel
import com.nutra.app.feature.onboarding.OnboardingScreen
import com.nutra.app.feature.onboarding.OnboardingViewModel
import com.nutra.app.feature.profile.EditBodyProfileScreen
import com.nutra.app.feature.profile.ProfileScreen
import com.nutra.app.feature.profile.ProfileViewModel
import com.nutra.app.feature.profile.WaterReminderScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
private val NavPressScaleSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 700f)

private const val CONTENT_READY_PREDRAW_TIMEOUT_MS = 350L
private const val CONFIG_EMERGENCY_PREDRAW_TIMEOUT_MS = 1200L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    private var hasRootConfig = false
    private var isAppReady = false
    private var lastAppliedIsDark: Boolean? = null

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
                    val allowDraw = isAppReady ||
                        (hasRootConfig && elapsed >= CONTENT_READY_PREDRAW_TIMEOUT_MS) ||
                        elapsed >= CONFIG_EMERGENCY_PREDRAW_TIMEOUT_MS
                    return if (allowDraw) {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            },
        )

        val rootConfigFlow = preferencesDataStore.userProfileFlow
            .map { profile ->
                RootAppConfig(
                    onboardingCompleted = profile.onboardingCompleted,
                    themePreference = profile.themePreference,
                    useDynamicColor = profile.useDynamicColor,
                )
            }
            .distinctUntilChanged()

        setContent {
            val initialConfig = remember {
                cachedProfile?.let { profile ->
                    RootAppConfig(
                        onboardingCompleted = profile.onboardingCompleted,
                        themePreference = profile.themePreference,
                        useDynamicColor = profile.useDynamicColor,
                    )
                }
            }
            val rootConfig by rootConfigFlow.collectAsStateWithLifecycle(initialValue = initialConfig)

            val config = rootConfig
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

private enum class ProfileSubScreen {
    NONE,
    EDIT_BODY_PROFILE,
    WATER_REMINDER,
}

private class TabLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

@Composable
fun NutraAppRoot(
    onboardingCompleted: Boolean,
    onReadyChanged: (Boolean) -> Unit = {},
) {
    var activeTab by rememberSaveable { mutableStateOf(PrimaryDestination.DASHBOARD) }
    var profileSubScreen by rememberSaveable { mutableStateOf(ProfileSubScreen.NONE) }

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
                    profileSubScreen = ProfileSubScreen.NONE
                },
            )
        } else {
            val dashboardViewModel: DashboardViewModel = viewModel()
            val isDashboardReady by dashboardViewModel.isReady.collectAsStateWithLifecycle()
            SideEffect { onReadyChanged(isDashboardReady) }
            ReportDrawnWhen { isDashboardReady }

            // Lazy-mount secondary tabs on first visit, then keep visited tabs warm with paused hidden lifecycles.
            var hasVisitedAnalytics by rememberSaveable { mutableStateOf(false) }
            var hasVisitedProfile by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(activeTab) {
                if (activeTab == PrimaryDestination.ANALYTICS && !hasVisitedAnalytics) {
                    hasVisitedAnalytics = true
                }
                if (activeTab == PrimaryDestination.PROFILE && !hasVisitedProfile) {
                    hasVisitedProfile = true
                }
            }

            val mountAnalytics = hasVisitedAnalytics || activeTab == PrimaryDestination.ANALYTICS
            val mountProfile = hasVisitedProfile || activeTab == PrimaryDestination.PROFILE
            val saveableStateHolder = rememberSaveableStateHolder()
            val showEditBodyProfile = activeTab == PrimaryDestination.PROFILE && profileSubScreen == ProfileSubScreen.EDIT_BODY_PROFILE
            val showWaterReminder = activeTab == PrimaryDestination.PROFILE && profileSubScreen == ProfileSubScreen.WATER_REMINDER
            val hasProfileSubScreen = showEditBodyProfile || showWaterReminder
            var dashboardScrollTopTick by remember { mutableIntStateOf(0) }
            var analyticsScrollTopTick by remember { mutableIntStateOf(0) }
            var profileScrollTopTick by remember { mutableIntStateOf(0) }

            BackHandler(enabled = hasProfileSubScreen) {
                profileSubScreen = ProfileSubScreen.NONE
            }

            BackHandler(enabled = !hasProfileSubScreen && activeTab != PrimaryDestination.DASHBOARD) {
                activeTab = PrimaryDestination.DASHBOARD
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NutraTheme.colors.background),
            ) {
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val contentPadding = remember(navBarBottom) {
                    PaddingValues(
                        top = 0.dp,
                        bottom = BottomBarReservedHeight + navBarBottom,
                    )
                }

                val contentInsets = WindowInsets.statusBars
                    .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(contentInsets),
                ) {
                    TabSlotBox(isSelected = activeTab == PrimaryDestination.DASHBOARD && !hasProfileSubScreen) {
                        saveableStateHolder.SaveableStateProvider(PrimaryDestination.DASHBOARD) {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                contentPadding = contentPadding,
                                scrollToTopTrigger = dashboardScrollTopTick,
                            )
                        }
                    }

                    if (mountAnalytics) {
                        TabSlotBox(isSelected = activeTab == PrimaryDestination.ANALYTICS && !hasProfileSubScreen) {
                            saveableStateHolder.SaveableStateProvider(PrimaryDestination.ANALYTICS) {
                                val analyticsViewModel: AnalyticsViewModel = viewModel()
                                AnalyticsScreen(
                                    viewModel = analyticsViewModel,
                                    contentPadding = contentPadding,
                                    scrollToTopTrigger = analyticsScrollTopTick,
                                )
                            }
                        }
                    }

                    if (mountProfile) {
                        TabSlotBox(isSelected = activeTab == PrimaryDestination.PROFILE && !hasProfileSubScreen) {
                            saveableStateHolder.SaveableStateProvider(PrimaryDestination.PROFILE) {
                                val profileViewModel: ProfileViewModel = viewModel()
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    contentPadding = contentPadding,
                                    scrollToTopTrigger = profileScrollTopTick,
                                    onEditBodyProfile = { profileSubScreen = ProfileSubScreen.EDIT_BODY_PROFILE },
                                    onOpenWaterReminder = { profileSubScreen = ProfileSubScreen.WATER_REMINDER },
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !hasProfileSubScreen,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f),
                ) {
                    NutraBottomBar(
                        selectedTab = activeTab,
                        onSelectTab = { dest ->
                            profileSubScreen = ProfileSubScreen.NONE
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
                    )
                }

                AnimatedVisibility(
                    visible = showEditBodyProfile,
                    enter = slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180)),
                    exit = slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(140)),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                        .background(NutraTheme.colors.background)
                        .windowInsetsPadding(contentInsets),
                ) {
                    val profileViewModel: ProfileViewModel = viewModel()
                    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                    if (profileState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NutraTheme.colors.background),
                        )
                    } else {
                        EditBodyProfileScreen(
                            profile = profileState.profile,
                            onNavigateBack = { profileSubScreen = ProfileSubScreen.NONE },
                            onSave = { sex, age, heightCm, currentWeight, targetWeight, activity, goalType, recalculate ->
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
                                profileSubScreen = ProfileSubScreen.NONE
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showWaterReminder,
                    enter = slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180)),
                    exit = slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(140)),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                        .background(NutraTheme.colors.background)
                        .windowInsetsPadding(contentInsets),
                ) {
                    val profileViewModel: ProfileViewModel = viewModel()
                    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                    if (profileState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NutraTheme.colors.background),
                        )
                    } else {
                        WaterReminderScreen(
                            initialSettings = profileState.profile.waterReminder,
                            waterGoalMl = profileState.profile.goals.waterMl,
                            onNavigateBack = { profileSubScreen = ProfileSubScreen.NONE },
                            onSave = { updatedReminder ->
                                profileViewModel.updateWaterReminder(updatedReminder)
                                profileSubScreen = ProfileSubScreen.NONE
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keeps visited tab UI state warm off-screen while pausing its Lifecycle to CREATED (stopping
 * collectAsStateWithLifecycle subscriptions & background recompositions) and skipping measure/layout,
 * focus, pointer input, and accessibility when hidden.
 */
@Composable
private fun TabSlotBox(
    isSelected: Boolean,
    content: @Composable () -> Unit,
) {
    val parentLifecycleOwner = LocalLifecycleOwner.current
    val tabLifecycleOwner = remember(parentLifecycleOwner) {
        TabLifecycleOwner().apply {
            val parentState = parentLifecycleOwner.lifecycle.currentState
            val maxState = if (isSelected) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
            val initial = if (parentState.isAtLeast(maxState)) maxState else parentState
            if (initial != Lifecycle.State.INITIALIZED) {
                registry.currentState = initial
            }
        }
    }
    val latestIsSelected = rememberUpdatedState(isSelected)

    fun syncTabLifecycle(parentState: Lifecycle.State, selected: Boolean) {
        val current = tabLifecycleOwner.registry.currentState
        if (current == Lifecycle.State.DESTROYED) return
        val maxState = if (selected) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        val target = if (parentState.isAtLeast(maxState)) maxState else parentState
        if (target == Lifecycle.State.INITIALIZED || current == target) return
        if (current == Lifecycle.State.INITIALIZED && target == Lifecycle.State.DESTROYED) return
        tabLifecycleOwner.registry.currentState = target
    }

    DisposableEffect(parentLifecycleOwner) {
        val observer = LifecycleEventObserver { source, _ ->
            syncTabLifecycle(source.lifecycle.currentState, latestIsSelected.value)
        }
        syncTabLifecycle(parentLifecycleOwner.lifecycle.currentState, latestIsSelected.value)
        parentLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            parentLifecycleOwner.lifecycle.removeObserver(observer)
            if (tabLifecycleOwner.registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                tabLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }

    SideEffect {
        syncTabLifecycle(parentLifecycleOwner.lifecycle.currentState, isSelected)
    }

    val alphaState = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "tabAlpha",
    )
    // Only invalidate layout when crossing visibility threshold, keeping per-frame fade GPU-only.
    val shouldLayoutState = remember {
        derivedStateOf { latestIsSelected.value || alphaState.value > 0.01f }
    }

    val baseModifier = Modifier
        .fillMaxSize()
        .zIndex(if (isSelected) 1f else 0f)
        .focusProperties { canFocus = isSelected }
        .graphicsLayer { alpha = alphaState.value }
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
        Surface(
            modifier = Modifier
                .widthIn(max = 328.dp)
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(
                    if (shadowElevation > 0.dp) {
                        Modifier.shadow(
                            elevation = shadowElevation,
                            shape = NutraTheme.shapes.pill,
                            ambientColor = shadowTint,
                            spotColor = shadowTint,
                        )
                    } else {
                        Modifier
                    },
                ),
            shape = NutraTheme.shapes.pill,
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
    val isPressed by interactionSource.collectIsPressedAsState()
    val holdAlphaState = rememberHoldHighlightAlpha(
        interactionSource = interactionSource,
        label = "navHoldAlpha",
    )
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = NavPressScaleSpring,
        label = "navPressScale",
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
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
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
        )
    }
}

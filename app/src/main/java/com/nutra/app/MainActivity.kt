package com.nutra.app

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraTheme
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    private var isAppReady = false
    private var lastAppliedIsDark: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val contentView: View = findViewById(android.R.id.content)
        val preDrawStartMs = SystemClock.uptimeMillis()
        contentView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val timedOut = SystemClock.uptimeMillis() - preDrawStartMs > 1500L
                    return if (isAppReady || timedOut) {
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
            val rootConfig by rootConfigFlow.collectAsStateWithLifecycle(initialValue = null)

            val config = rootConfig
            if (config == null) {
                SideEffect { isAppReady = false }
                return@setContent
            }

            LaunchedEffect(config.themePreference) {
                preferencesDataStore.applySystemNightMode(config.themePreference)
            }

            val isDark = config.themePreference.resolveIsDark()
            SideEffect {
                if (lastAppliedIsDark != isDark) {
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
}

@Composable
fun NutraAppRoot(
    onboardingCompleted: Boolean,
    onReadyChanged: (Boolean) -> Unit = {},
) {
    var activeTab by rememberSaveable { mutableStateOf(PrimaryDestination.DASHBOARD) }
    var isEditingBodyProfile by rememberSaveable { mutableStateOf(false) }

    Crossfade(
        targetState = onboardingCompleted,
        animationSpec = tween(240),
        label = "RootOnboardingTransition",
    ) { isCompleted ->
        if (!isCompleted) {
            SideEffect { onReadyChanged(true) }
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onOnboardingFinished = {
                    activeTab = PrimaryDestination.DASHBOARD
                    isEditingBodyProfile = false
                },
            )
        } else {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val isDashboardReady by dashboardViewModel.isReady.collectAsStateWithLifecycle()
            SideEffect { onReadyChanged(isDashboardReady) }
            if (!isDashboardReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NutraTheme.colors.background),
                )
                return@Crossfade
            }

            // Mount Dashboard immediately for first frame, then stagger-warm secondary tabs off-screen.
            var hasVisitedAnalytics by rememberSaveable { mutableStateOf(false) }
            var hasVisitedProfile by rememberSaveable { mutableStateOf(false) }
            var prewarmAnalytics by remember { mutableStateOf(false) }
            var prewarmProfile by remember { mutableStateOf(false) }

            SideEffect {
                if (activeTab == PrimaryDestination.ANALYTICS && !hasVisitedAnalytics) {
                    hasVisitedAnalytics = true
                }
                if (activeTab == PrimaryDestination.PROFILE && !hasVisitedProfile) {
                    hasVisitedProfile = true
                }
            }

            LaunchedEffect(Unit) {
                withFrameNanos { }
                prewarmAnalytics = true
                withFrameNanos { }
                prewarmProfile = true
            }

            val mountAnalytics = hasVisitedAnalytics || prewarmAnalytics || activeTab == PrimaryDestination.ANALYTICS
            val mountProfile = hasVisitedProfile || prewarmProfile || activeTab == PrimaryDestination.PROFILE
            val saveableStateHolder = rememberSaveableStateHolder()
            val showEditBodyProfile = activeTab == PrimaryDestination.PROFILE && isEditingBodyProfile

            BackHandler(enabled = showEditBodyProfile) {
                isEditingBodyProfile = false
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
                    TabSlotBox(isSelected = activeTab == PrimaryDestination.DASHBOARD && !showEditBodyProfile) {
                        saveableStateHolder.SaveableStateProvider(PrimaryDestination.DASHBOARD) {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                contentPadding = contentPadding,
                            )
                        }
                    }

                    if (mountAnalytics) {
                        TabSlotBox(isSelected = activeTab == PrimaryDestination.ANALYTICS && !showEditBodyProfile) {
                            saveableStateHolder.SaveableStateProvider(PrimaryDestination.ANALYTICS) {
                                val analyticsViewModel: AnalyticsViewModel = hiltViewModel()
                                AnalyticsScreen(
                                    viewModel = analyticsViewModel,
                                    contentPadding = contentPadding,
                                )
                            }
                        }
                    }

                    if (mountProfile) {
                        TabSlotBox(isSelected = activeTab == PrimaryDestination.PROFILE && !showEditBodyProfile) {
                            saveableStateHolder.SaveableStateProvider(PrimaryDestination.PROFILE) {
                                val profileViewModel: ProfileViewModel = hiltViewModel()
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    contentPadding = contentPadding,
                                    onEditBodyProfile = { isEditingBodyProfile = true },
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showEditBodyProfile,
                        enter = slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180)),
                        exit = slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(140)),
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f),
                    ) {
                        val profileViewModel: ProfileViewModel = hiltViewModel()
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
                                onNavigateBack = { isEditingBodyProfile = false },
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
                                    isEditingBodyProfile = false
                                },
                            )
                        }
                    }
                }

                // Solid status bar backdrop so scrolled content never collides with system icons
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(NutraTheme.colors.background)
                        .align(Alignment.TopCenter),
                )

                AnimatedVisibility(
                    visible = !showEditBodyProfile,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    NutraBottomBar(
                        selectedTab = activeTab,
                        onSelectTab = {
                            isEditingBodyProfile = false
                            activeTab = it
                        },
                    )
                }
            }
        }
    }
}

/**
 * Keeps mounted tab state alive off-screen while skipping placement and accessibility when unselected.
 */
@Composable
private fun TabSlotBox(
    isSelected: Boolean,
    content: @Composable () -> Unit,
) {
    val alphaState = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "tabAlpha",
    )

    val baseModifier = Modifier
        .fillMaxSize()
        .zIndex(if (isSelected) 1f else 0f)
        .graphicsLayer { alpha = alphaState.value }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                if (isSelected || alphaState.value > 0.01f) {
                    placeable.place(0, 0)
                }
            }
        }
        .pointerInput(isSelected) {
            if (!isSelected) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }

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

/**
 * Centered floating pill bottom bar over a soft vertical fade scrim.
 */
@Composable
private fun NutraBottomBar(
    selectedTab: PrimaryDestination,
    onSelectTab: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colors = NutraTheme.colors
    val shadowElevation = if (colors.isDark) 0.dp else 12.dp
    val scrimBrush = remember(colors.background) {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.45f to colors.background.copy(alpha = 0.92f),
            1f to colors.background,
        )
    }

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
                .height(64.dp)
                .shadow(
                    elevation = shadowElevation,
                    shape = NutraTheme.shapes.pill,
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor = Color.Black.copy(alpha = 0.10f),
                ),
            shape = NutraTheme.shapes.pill,
            color = if (colors.isDark) colors.surfaceElevated else colors.surface,
            border = if (colors.isDark) BorderStroke(1.dp, colors.segmentThumb.copy(alpha = 0.65f)) else null,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryDestination.entries.forEach { dest ->
                    val selected = dest == selectedTab
                    val tint by animateColorAsState(
                        targetValue = if (selected) colors.textPrimary else colors.textTertiary,
                        animationSpec = tween(180),
                        label = "navTint",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                            .clip(NutraTheme.shapes.pill)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) {
                                if (!selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectTab(dest)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = dest.iconVector(selected),
                            contentDescription = dest.title,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = dest.title,
                            style = NutraTheme.typography.overline.copy(letterSpacing = NutraTheme.typography.caption.letterSpacing),
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

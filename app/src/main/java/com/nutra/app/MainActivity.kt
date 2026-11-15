package com.nutra.app

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.resolveIsDark
import com.nutra.app.feature.analytics.AnalyticsScreen
import com.nutra.app.feature.analytics.AnalyticsViewModel
import com.nutra.app.feature.dashboard.DashboardScreen
import com.nutra.app.feature.dashboard.DashboardViewModel
import com.nutra.app.feature.onboarding.OnboardingScreen
import com.nutra.app.feature.onboarding.OnboardingViewModel
import com.nutra.app.feature.profile.ProfileScreen
import com.nutra.app.feature.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

enum class PrimaryDestination(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    DASHBOARD("Bugün", Icons.Outlined.Home, Icons.Rounded.Home),
    ANALYTICS("İlerleme", Icons.Outlined.Insights, Icons.Rounded.Insights),
    PROFILE("Profil", Icons.Outlined.Person, Icons.Rounded.Person),
}

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
        contentView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (isAppReady) {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            },
        )

        setContent {
            val profile by preferencesDataStore.userProfileFlow.collectAsStateWithLifecycle(
                initialValue = null,
            )

            val currentProfile = profile
            if (currentProfile == null) {
                SideEffect { isAppReady = false }
                return@setContent
            }

            LaunchedEffect(currentProfile.themePreference) {
                preferencesDataStore.applySystemNightMode(currentProfile.themePreference)
            }

            val isDark = currentProfile.themePreference.resolveIsDark()
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
                themePreference = currentProfile.themePreference,
                useDynamicColor = currentProfile.useDynamicColor,
            ) {
                NutraAppRoot(
                    onboardingCompleted = currentProfile.onboardingCompleted,
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

    Crossfade(
        targetState = onboardingCompleted,
        animationSpec = tween(300),
        label = "RootOnboardingTransition",
    ) { isCompleted ->
        if (!isCompleted) {
            SideEffect { onReadyChanged(true) }
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onOnboardingFinished = { activeTab = PrimaryDestination.DASHBOARD },
            )
        } else {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
            SideEffect { onReadyChanged(!dashboardState.isLoading) }
            if (dashboardState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NutraTheme.colors.background),
                )
                return@Crossfade
            }
            val analyticsViewModel: AnalyticsViewModel = hiltViewModel()
            val profileViewModel: ProfileViewModel = hiltViewModel()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NutraTheme.colors.background),
            ) {
                val contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = BottomBarReservedHeight,
                )

                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "MainTabTransition",
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) { destination ->
                    when (destination) {
                        PrimaryDestination.DASHBOARD -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            contentPadding = contentPadding,
                        )
                        PrimaryDestination.ANALYTICS -> AnalyticsScreen(
                            viewModel = analyticsViewModel,
                            contentPadding = contentPadding,
                        )
                        PrimaryDestination.PROFILE -> ProfileScreen(
                            viewModel = profileViewModel,
                            contentPadding = contentPadding,
                        )
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

                NutraBottomBar(
                    selectedTab = activeTab,
                    onSelectTab = { activeTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to colors.background.copy(alpha = 0.92f),
                    1f to colors.background,
                ),
            )
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
                            imageVector = if (selected) dest.selectedIcon else dest.icon,
                            contentDescription = dest.title,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(3.dp))
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

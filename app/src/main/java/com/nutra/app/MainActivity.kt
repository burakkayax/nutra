package com.nutra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.UserProfile
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
) {
    DASHBOARD("Günlük", Icons.Rounded.SpaceDashboard),
    ANALYTICS("Analiz", Icons.Rounded.BarChart),
    PROFILE("Profil", Icons.Rounded.PersonOutline),
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val profile by preferencesDataStore.userProfileFlow.collectAsStateWithLifecycle(
                initialValue = UserProfile(onboardingCompleted = true),
            )

            NutraTheme(
                themePreference = profile.themePreference,
                useDynamicColor = profile.useDynamicColor,
            ) {
                NutraAppRoot(onboardingCompleted = profile.onboardingCompleted)
            }
        }
    }
}

@Composable
fun NutraAppRoot(onboardingCompleted: Boolean) {
    var activeTab by rememberSaveable { mutableStateOf(PrimaryDestination.DASHBOARD) }

    Crossfade(
        targetState = onboardingCompleted,
        label = "RootOnboardingTransition",
    ) { isCompleted ->
        if (!isCompleted) {
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onOnboardingFinished = { activeTab = PrimaryDestination.DASHBOARD },
            )
        } else {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val analyticsViewModel: AnalyticsViewModel = hiltViewModel()
            val profileViewModel: ProfileViewModel = hiltViewModel()

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = NutraTheme.colors.background,
                bottomBar = {
                    NutraBottomNavigationBar(
                        selectedTab = activeTab,
                        onSelectTab = { activeTab = it },
                    )
                },
            ) { innerPadding ->
                Crossfade(
                    targetState = activeTab,
                    label = "MainTabCrossfade",
                ) { destination ->
                    when (destination) {
                        PrimaryDestination.DASHBOARD -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            contentPadding = innerPadding,
                        )
                        PrimaryDestination.ANALYTICS -> AnalyticsScreen(
                            viewModel = analyticsViewModel,
                            contentPadding = innerPadding,
                        )
                        PrimaryDestination.PROFILE -> ProfileScreen(
                            viewModel = profileViewModel,
                            contentPadding = innerPadding,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NutraBottomNavigationBar(
    selectedTab: PrimaryDestination,
    onSelectTab: (PrimaryDestination) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NutraTheme.colors.background.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .padding(horizontal = NutraTheme.spacing.screenHorizontal, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(NutraTheme.shapes.standardCard)
                .background(NutraTheme.colors.cardSurface)
                .border(1.dp, NutraTheme.colors.hairlineBorder, NutraTheme.shapes.standardCard)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryDestination.entries.forEach { dest ->
                val isSelected = dest == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(NutraTheme.shapes.innerSubCard)
                        .background(
                            if (isSelected) {
                                NutraTheme.colors.calorieAccent.copy(
                                    alpha = if (NutraTheme.colors.isDark) 0.18f else 0.12f,
                                )
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .clickable {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectTab(dest)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.title,
                            tint = if (isSelected) NutraTheme.colors.calorieAccent else NutraTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = dest.title,
                            style = NutraTheme.typography.macroValue,
                            color = if (isSelected) NutraTheme.colors.calorieAccent else NutraTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

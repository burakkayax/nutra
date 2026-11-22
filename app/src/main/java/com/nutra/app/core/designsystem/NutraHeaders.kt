package com.nutra.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Default expanded vertical space reserved at the top of a LazyColumn when using [NutraStickyScreenHeader].
 */
val NutraStickyHeaderTopReservedPadding: Dp = 108.dp

/**
 * Compact expanded vertical space reserved at the top of sub-screens with a back button.
 */
val NutraStickySubScreenHeaderTopReservedPadding: Dp = 85.dp

private val HeaderCollapseSpring = spring<Float>(dampingRatio = 0.92f, stiffness = 520f)

/**
 * Tracks a smooth, spring-animated 0f..1f header collapse progress from [listState]
 * over the first [collapseRangeDp] of scroll.
 */
@Composable
fun rememberHeaderCollapseFraction(
    listState: LazyListState,
    collapseRangeDp: Dp = 56.dp,
): State<Float> {
    val density = LocalDensity.current
    val rangePx = remember(density, collapseRangeDp) {
        with(density) { collapseRangeDp.toPx().coerceAtLeast(1f) }
    }
    val rawFraction by remember(listState, rangePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / rangePx).coerceIn(0f, 1f)
            }
        }
    }
    return animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = HeaderCollapseSpring,
        label = "headerCollapseFraction",
    )
}

/**
 * Tracks a smooth, spring-animated 0f..1f header collapse progress from [scrollState]
 * over the first [collapseRangeDp] of scroll.
 */
@Composable
fun rememberHeaderCollapseFraction(
    scrollState: ScrollState,
    collapseRangeDp: Dp = 56.dp,
): State<Float> {
    val density = LocalDensity.current
    val rangePx = remember(density, collapseRangeDp) {
        with(density) { collapseRangeDp.toPx().coerceAtLeast(1f) }
    }
    val rawFraction by remember(scrollState, rangePx) {
        derivedStateOf {
            (scrollState.value.toFloat() / rangePx).coerceIn(0f, 1f)
        }
    }
    return animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = HeaderCollapseSpring,
        label = "headerCollapseFractionScroll",
    )
}

private fun Modifier.headerScaleCollapse(
    scaleProvider: () -> Float,
    originX: Float = 0f,
    scaleWidth: Boolean = false,
): Modifier = this
    .layout { measurable, constraints ->
        val scale = scaleProvider().coerceIn(0.5f, 1f)
        val placeable = measurable.measure(constraints)
        if (scale >= 0.999f) {
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        } else {
            val scaledWidth = if (scaleWidth) {
                (placeable.width * scale).roundToInt().coerceAtLeast(0)
            } else {
                placeable.width
            }
            val scaledHeight = (placeable.height * scale).roundToInt().coerceAtLeast(0)
            layout(scaledWidth, scaledHeight) {
                val xOffset = if (scaleWidth && originX > 0.5f) {
                    scaledWidth - placeable.width
                } else {
                    0
                }
                val yOffset = ((scaledHeight - placeable.height) / 2f).roundToInt()
                placeable.placeRelative(xOffset, yOffset)
            }
        }
    }
    .graphicsLayer {
        val scale = scaleProvider().coerceIn(0.5f, 1f)
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(originX, 0.5f)
    }

/**
 * Sticky, smoothly collapsing top screen header bar with a unified 97% opaque backdrop
 * covering both the device status bar and the header without any bottom hairline.
 * Defers [collapseFractionState] reads into layout/draw phases so scrolling causes zero recompositions.
 */
@Composable
fun NutraStickyScreenHeader(
    title: String,
    collapseFractionState: State<Float>,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    eyebrowColor: Color = NutraTheme.colors.textSecondary,
    onNavigateBack: (() -> Unit)? = null,
    horizontalPadding: PaddingValues = PaddingValues(horizontal = NutraTheme.spacing.screen),
    onHeaderHeightMeasured: ((Dp) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = NutraTheme.colors
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val isSubScreen = onNavigateBack != null
    val latestOnMeasured by rememberUpdatedState(onHeaderHeightMeasured)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onHeaderHeightMeasured != null) {
                    Modifier.onSizeChanged { size ->
                        // Report expanded height (when not collapsed) or current height for dynamic top clearance
                        if (collapseFractionState.value < 0.05f && size.height > 0) {
                            latestOnMeasured?.invoke(with(density) { size.height.toDp() })
                        }
                    }
                } else {
                    Modifier
                },
            )
            .drawBehind {
                // Unified 97% opaque backdrop covering both the device status bar and the top bar,
                // with no bottom hairline or dark fade strip.
                drawRect(color = colors.background.copy(alpha = 0.97f))
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = horizontalPadding.calculateStartPadding(layoutDirection),
                    end = horizontalPadding.calculateEndPadding(layoutDirection),
                )
                .layout { measurable, constraints ->
                    val fraction = if (isSubScreen) 0f else collapseFractionState.value.coerceIn(0f, 1f)
                    val topPadPx = (if (isSubScreen) 14.dp else (24f - 12f * fraction).dp).roundToPx()
                    val bottomPadPx = (if (isSubScreen) 12.dp else (14f - 6f * fraction).dp).roundToPx()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height + topPadPx + bottomPadPx) {
                        placeable.placeRelative(0, topPadPx)
                    }
                },
        ) {
            NutraScreenHeader(
                title = title,
                eyebrow = eyebrow,
                eyebrowColor = eyebrowColor,
                onNavigateBack = onNavigateBack,
                collapseFractionState = if (isSubScreen) null else collapseFractionState,
                actions = actions,
            )
            if (bottomContent != null) {
                bottomContent()
            }
        }
    }
}

/**
 * Large screen title with optional eyebrow (small line above), optional back button, and trailing actions.
 */
@Composable
fun NutraScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    eyebrowColor: Color = NutraTheme.colors.textSecondary,
    onNavigateBack: (() -> Unit)? = null,
    collapseFraction: Float = 0f,
    collapseFractionState: State<Float>? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isSubScreen = onNavigateBack != null
    val fractionProvider: () -> Float = {
        if (isSubScreen) 0f else (collapseFractionState?.value ?: collapseFraction).coerceIn(0f, 1f)
    }
    val baseEyebrowStyle = remember(isSubScreen) {
        if (isSubScreen) {
            DefaultNutraTypography.caption.copy(fontSize = 12.8.sp, lineHeight = 16.sp)
        } else {
            DefaultNutraTypography.body
        }
    }
    val baseTitleStyle = remember(isSubScreen) {
        if (isSubScreen) {
            DefaultNutraTypography.titleLarge.copy(fontSize = 21.4.sp, lineHeight = 25.5.sp, letterSpacing = (-0.45).sp)
        } else {
            DefaultNutraTypography.titleLarge.copy(fontSize = 30.sp, lineHeight = 36.sp)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateBack != null) {
            Box(
                modifier = Modifier.padding(end = 10.dp),
            ) {
                NutraIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Geri",
                    onClick = onNavigateBack,
                    size = 44.dp,
                    iconSize = 20.dp,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (isSubScreen) {
                Text(
                    text = title,
                    style = baseTitleStyle,
                    color = NutraTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.headerScaleCollapse(scaleProvider = { 1f - 0.16f * fractionProvider() }, originX = 0f),
                )
                if (eyebrow != null) {
                    Text(
                        text = eyebrow,
                        style = baseEyebrowStyle,
                        color = eyebrowColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.headerScaleCollapse(scaleProvider = { 1f - 0.10f * fractionProvider() }, originX = 0f),
                    )
                }
            } else {
                if (eyebrow != null) {
                    Text(
                        text = eyebrow,
                        style = baseEyebrowStyle,
                        color = eyebrowColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.headerScaleCollapse(scaleProvider = { 1f - 0.10f * fractionProvider() }, originX = 0f),
                    )
                }
                Text(
                    text = title,
                    style = baseTitleStyle,
                    color = NutraTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.headerScaleCollapse(scaleProvider = { 1f - 0.16f * fractionProvider() }, originX = 0f),
                )
            }
        }
        Row(
            modifier = Modifier.headerScaleCollapse(scaleProvider = { 1f - 0.08f * fractionProvider() }, originX = 1f),
            horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Section title with optional pill action button on the right. */
@Composable
fun NutraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionContainerColor: Color = NutraTheme.colors.surface,
    actionContentColor: Color = NutraTheme.colors.textPrimary,
    actionHaptic: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = NutraTheme.typography.titleMedium,
                color = NutraTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = NutraTheme.typography.bodySmall,
                    color = NutraTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .heightIn(min = 44.dp)
                    .clip(NutraTheme.shapes.pill)
                    .background(actionContainerColor)
                    .clickable(role = Role.Button) {
                        if (actionHaptic != null) haptic.performHapticFeedback(actionHaptic)
                        onAction()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        tint = actionContentColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = actionLabel,
                    style = NutraTheme.typography.labelSmall,
                    color = actionContentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

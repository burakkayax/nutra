package com.nutra.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Maximum content width for adaptive centering on landscape phones and tablets. */
val NutraContentMaxWidth: Dp = 640.dp

/** Animation spec used for all progress indicators. */
private val ProgressSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
internal val PressScaleSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 700f)
private val RatioSpring = spring<Float>(dampingRatio = 0.88f, stiffness = 360f)
internal val SegmentThumbSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)

internal fun Modifier.nutraRoundedStroke(
    color: () -> Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.5.dp,
): Modifier = drawWithContent {
    drawContent()
    val strokeColor = color()
    if (strokeColor.alpha > 0f) {
        val strokePx = strokeWidth.toPx()
        val half = strokePx / 2f
        val innerRadius = (cornerRadius.toPx() - half).coerceAtLeast(0f)
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(half, half),
            size = Size(size.width - strokePx, size.height - strokePx),
            cornerRadius = CornerRadius(innerRadius, innerRadius),
            style = Stroke(width = strokePx),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Containers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Borderless surface card. Use sparingly: group related content, don't box every row.
 */
@Composable
fun NutraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = NutraTheme.colors.surface,
    shape: Shape = NutraTheme.shapes.lg,
    contentPadding: PaddingValues = PaddingValues(NutraTheme.spacing.card),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    } else {
        Column(
            modifier = modifier
                .clip(shape)
                .background(color)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun NutraDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp,
    color: Color = NutraTheme.colors.divider,
) {
    Box(
        modifier = modifier
            .padding(start = startIndent, end = endIndent)
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NutraLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = NutraTheme.colors.accent,
    trackColor: Color = NutraTheme.colors.surfaceMuted,
    height: Dp = 6.dp,
    overflowProgress: Float = 0f,
    overflowColor: Color = NutraTheme.colors.warning,
    contentDescription: String? = null,
) {
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val safeOverflow = if (overflowProgress.isFinite()) overflowProgress.coerceIn(0f, 1f) else 0f
    val animatedState = animateFloatAsState(safeProgress, ProgressSpring, label = "bar")
    val animatedOverflowState = animateFloatAsState(safeOverflow, ProgressSpring, label = "barOverflow")
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
            .drawBehind {
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                drawRoundRect(color = trackColor, cornerRadius = corner)
                val fraction = animatedState.value
                if (fraction > 0.005f && size.width > 0f) {
                    val fillWidth = (size.width * fraction).coerceAtLeast(size.height.coerceAtMost(size.width))
                    drawRoundRect(
                        color = color,
                        size = Size(fillWidth, size.height),
                        cornerRadius = corner,
                    )
                }
                val overflowFraction = animatedOverflowState.value
                if (overflowFraction > 0.005f && size.width > 0f) {
                    val overflowWidth = (size.width * (0.14f + overflowFraction * 0.22f))
                        .coerceIn(size.height.coerceAtMost(size.width), size.width * 0.36f)
                    drawRoundRect(
                        color = overflowColor,
                        topLeft = Offset(size.width - overflowWidth, 0f),
                        size = Size(overflowWidth, size.height),
                        cornerRadius = corner,
                    )
                }
            },
    )
}

/** Compact macro statistic: label, "consumed / target", thin bar. */
@Composable
fun NutraMacroStat(
    label: String,
    consumed: Float,
    target: Int,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String = "g",
) {
    val safeTarget = target.coerceAtLeast(1)
    val roundedConsumed = consumed.roundToInt()
    val ratio = consumed / safeTarget.toFloat()
    val isOver = roundedConsumed > safeTarget
    val valueColor by animateColorAsState(
        targetValue = if (isOver) NutraTheme.colors.warningText else NutraTheme.colors.textPrimary,
        animationSpec = tween(180),
        label = "macroValueColor",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = NutraTheme.typography.caption,
            color = NutraTheme.colors.textSecondary,
            maxLines = 1,
        )
        Row {
            Text(
                text = NutraFormatters.formatInt(roundedConsumed),
                style = NutraTheme.typography.numberSmall,
                color = valueColor,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = " / ${NutraFormatters.formatInt(safeTarget)} $unit",
                style = NutraTheme.typography.caption,
                color = NutraTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline(),
            )
        }
        NutraLinearProgress(
            progress = ratio,
            color = color,
            height = 4.dp,
            overflowProgress = if (isOver) (ratio - 1f).coerceAtLeast(0.05f) else 0f,
            overflowColor = NutraTheme.colors.warning,
            contentDescription = "$label $roundedConsumed / $safeTarget $unit",
        )
    }
}

/** Spring-animated 3-segment macro energy share bar shared across Analytics and Onboarding. */
@Composable
fun NutraStackedRatioBar(
    proteinRatio: Float,
    carbsRatio: Float,
    fatRatio: Float,
    hasData: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    contentDescription: String? = null,
) {
    val colors = NutraTheme.colors
    val pColor = colors.protein.copy(alpha = alpha)
    val cColor = colors.carbs.copy(alpha = alpha)
    val fColor = colors.fat.copy(alpha = alpha)
    val trackColor = colors.surfaceMuted

    val animPState = animateFloatAsState(if (hasData) proteinRatio.coerceAtLeast(0f) else 0f, RatioSpring, label = "macroRatioP")
    val animCState = animateFloatAsState(if (hasData) carbsRatio.coerceAtLeast(0f) else 0f, RatioSpring, label = "macroRatioC")
    val animFState = animateFloatAsState(if (hasData) fatRatio.coerceAtLeast(0f) else 0f, RatioSpring, label = "macroRatioF")

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(NutraTheme.shapes.pill)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val pRaw = animPState.value
                val cRaw = animCState.value
                val fRaw = animFState.value
                if (!hasData && pRaw + cRaw + fRaw < 0.01f) {
                    drawRect(color = trackColor)
                    return@drawBehind
                }
                val pVal = if (pRaw > 0.005f) pRaw.coerceAtLeast(0.01f) else 0f
                val cVal = if (cRaw > 0.005f) cRaw.coerceAtLeast(0.01f) else 0f
                val fVal = if (fRaw > 0.005f) fRaw.coerceAtLeast(0.01f) else 0f
                val total = pVal + cVal + fVal
                if (total <= 0.001f) {
                    drawRect(color = trackColor)
                    return@drawBehind
                }

                val gapPx = 2.dp.toPx()
                val activeCount = (if (pVal > 0f) 1 else 0) + (if (cVal > 0f) 1 else 0) + (if (fVal > 0f) 1 else 0)
                val usableWidth = (size.width - gapPx * (activeCount - 1).coerceAtLeast(0)).coerceAtLeast(0f)
                var x = 0f

                if (pVal > 0f) {
                    val w = usableWidth * (pVal / total)
                    drawRect(color = pColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    x += w + gapPx
                }
                if (cVal > 0f) {
                    val w = usableWidth * (cVal / total)
                    drawRect(color = cColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    x += w + gapPx
                }
                if (fVal > 0f) {
                    val w = (size.width - x).coerceAtLeast(0f)
                    drawRect(color = fColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                }
            },
    )
}

/** Small colored dot used as a legend marker. */
@Composable
fun NutraDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

// ─────────────────────────────────────────────────────────────────────────────
// Selection controls
// ─────────────────────────────────────────────────────────────────────────────

/** Rounded square with a tinted background holding an icon. */
@Composable
fun NutraIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = NutraTheme.colors.textPrimary,
    container: Color = NutraTheme.colors.surfaceMuted,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = NutraTheme.shapes.sm,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Generic list row: [leading] · title/subtitle · [trailing]. */
@Composable
fun NutraListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = NutraTheme.colors.textPrimary,
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NutraTheme.typography.titleSmall, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = NutraTheme.typography.bodySmall, color = NutraTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** Small pill label. */
@Composable
fun NutraBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NutraTheme.colors.textSecondary,
    container: Color = NutraTheme.colors.surfaceMuted,
) {
    Text(
        text = text,
        style = NutraTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(NutraTheme.shapes.pill)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun NutraEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NutraIconBadge(icon = icon, size = 56.dp, iconSize = 26.dp, shape = CircleShape, tint = NutraTheme.colors.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(title, style = NutraTheme.typography.titleSmall, color = NutraTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = NutraTheme.typography.bodySmall, color = NutraTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feedback
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns `true` when the active window width is narrow (< [compactWidthDp]) or accessibility font
 * scaling is enlarged (>= [largeFontScale]). Uses [LocalWindowInfo] instead of `Configuration.screenWidthDp`
 * so multi-window and foldable layouts respond accurately.
 */
@Composable
fun isCompactWidthOrLargeFont(
    compactWidthDp: Dp = 360.dp,
    largeFontScale: Float = 1.25f,
): Boolean {
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val windowWidthDp = with(density) { windowWidthPx.toDp() }
    return density.fontScale >= largeFontScale || (windowWidthPx > 0 && windowWidthDp < compactWidthDp)
}

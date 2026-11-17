package com.example.ui.theme

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class UiDensityTier {
    COMPACT,
    STANDARD,
    SPACIOUS
}

@Immutable
data class AdaptiveLayoutSpec(
    val windowSizeClass: WindowSizeClass,
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val densityTier: UiDensityTier,
    val isCompactWidth: Boolean,
    val isMediumWidth: Boolean,
    val isExpandedWidth: Boolean,
    val useNavigationRail: Boolean,
    val screenHorizontalPadding: Dp,
    val cardHorizontalPadding: Dp,
    val cardInnerPadding: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val chipHorizontalPadding: Dp,
    val chipVerticalPadding: Dp,
    val maxContentWidth: Dp,
    val gridColumns: Int,
    val chartHeight: Dp,
    val radarHeight: Dp
) {
    companion object {
        @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
        fun default(): AdaptiveLayoutSpec = fromWindowSizeClass(
            WindowSizeClass.calculateFromSize(DpSize(390.dp, 844.dp))
        )

        fun fromWindowSizeClass(
            windowSizeClass: WindowSizeClass,
            fontScale: Float = 1.0f,
            screenDensity: Float = 2.0f
        ): AdaptiveLayoutSpec {
            val widthClass = windowSizeClass.widthSizeClass
            val heightClass = windowSizeClass.heightSizeClass
            val isCompact = widthClass == WindowWidthSizeClass.Compact
            val isMedium = widthClass == WindowWidthSizeClass.Medium
            val isExpanded = widthClass == WindowWidthSizeClass.Expanded

            val densityTier = when {
                isCompact && (fontScale >= 1.15f || screenDensity >= 3.0f || heightClass == WindowHeightSizeClass.Compact) ->
                    UiDensityTier.COMPACT
                isExpanded && fontScale < 1.15f ->
                    UiDensityTier.SPACIOUS
                isMedium && fontScale < 1.15f ->
                    UiDensityTier.SPACIOUS
                else ->
                    UiDensityTier.STANDARD
            }

            val chartH = when {
                heightClass == WindowHeightSizeClass.Compact -> 158.dp
                densityTier == UiDensityTier.COMPACT -> 176.dp
                isCompact -> 190.dp
                isMedium -> 225.dp
                else -> 250.dp
            }
            val radarH = when {
                heightClass == WindowHeightSizeClass.Compact -> 190.dp
                densityTier == UiDensityTier.COMPACT -> 212.dp
                isCompact -> 228.dp
                isMedium -> 255.dp
                else -> 275.dp
            }

            return AdaptiveLayoutSpec(
                windowSizeClass = windowSizeClass,
                widthSizeClass = widthClass,
                heightSizeClass = heightClass,
                densityTier = densityTier,
                isCompactWidth = isCompact,
                isMediumWidth = isMedium,
                isExpandedWidth = isExpanded,
                useNavigationRail = !isCompact,
                screenHorizontalPadding = when {
                    densityTier == UiDensityTier.COMPACT -> 12.dp
                    isCompact -> 14.dp
                    isMedium -> 20.dp
                    else -> 24.dp
                },
                cardHorizontalPadding = when {
                    densityTier == UiDensityTier.COMPACT -> 10.dp
                    isCompact -> 12.dp
                    isMedium -> 18.dp
                    else -> 22.dp
                },
                cardInnerPadding = when {
                    densityTier == UiDensityTier.COMPACT -> 10.dp
                    isCompact -> 12.dp
                    else -> 16.dp
                },
                sectionSpacing = when (densityTier) {
                    UiDensityTier.COMPACT -> 8.dp
                    UiDensityTier.STANDARD -> 10.dp
                    UiDensityTier.SPACIOUS -> 14.dp
                },
                itemSpacing = when (densityTier) {
                    UiDensityTier.COMPACT -> 6.dp
                    UiDensityTier.STANDARD -> 8.dp
                    UiDensityTier.SPACIOUS -> 10.dp
                },
                chipHorizontalPadding = when (densityTier) {
                    UiDensityTier.COMPACT -> 6.dp
                    UiDensityTier.STANDARD -> 8.dp
                    UiDensityTier.SPACIOUS -> 10.dp
                },
                chipVerticalPadding = when (densityTier) {
                    UiDensityTier.COMPACT -> 4.dp
                    UiDensityTier.STANDARD -> 5.dp
                    UiDensityTier.SPACIOUS -> 6.dp
                },
                maxContentWidth = when {
                    isCompact -> Dp.Unspecified
                    isMedium -> 840.dp
                    else -> 1140.dp
                },
                gridColumns = if (isCompact) 1 else 2,
                chartHeight = chartH,
                radarHeight = radarH
            )
        }
    }
}

val LocalAdaptiveLayout = staticCompositionLocalOf { AdaptiveLayoutSpec.default() }

@Composable
fun rememberAdaptiveLayoutSpec(windowSizeClass: WindowSizeClass): AdaptiveLayoutSpec {
    val density = LocalDensity.current
    return remember(windowSizeClass, density.fontScale, density.density) {
        AdaptiveLayoutSpec.fromWindowSizeClass(
            windowSizeClass = windowSizeClass,
            fontScale = density.fontScale,
            screenDensity = density.density
        )
    }
}

fun isNarrowPortrait(availableWidth: Dp, fontScale: Float): Boolean {
    return availableWidth < 360.dp || (availableWidth < 405.dp && fontScale >= 1.15f)
}

fun isUltraNarrowPortrait(availableWidth: Dp, fontScale: Float): Boolean {
    return availableWidth < 340.dp || (availableWidth < 380.dp && fontScale >= 1.25f)
}

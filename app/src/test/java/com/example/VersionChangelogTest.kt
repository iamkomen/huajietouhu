package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.data.model.AppChangelogCatalog
import com.example.data.model.ChangeCategory
import com.example.data.model.StockQuote
import com.example.ui.components.StockHeaderBar
import com.example.ui.components.VersionChangelogDialog
import com.example.ui.theme.AdaptiveLayoutSpec
import com.example.ui.theme.LocalAdaptiveLayout
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h900dp-hdpi")
class VersionChangelogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `changelog catalog matches BuildConfig version and contains valid factual entries`() {
        val releases = AppChangelogCatalog.releases
        assertTrue("Changelog must have multiple historical releases", releases.size >= 5)

        val currentReleases = releases.filter { it.isCurrent }
        assertEquals("Exactly one release should be marked current", 1, currentReleases.size)
        assertEquals(BuildConfig.VERSION_NAME, currentReleases.first().versionName)
        assertEquals(BuildConfig.VERSION_CODE, currentReleases.first().versionCode)

        val uniqueVersions = releases.map { it.versionName }.toSet()
        assertEquals("Version names must be unique", releases.size, uniqueVersions.size)

        releases.forEach { release ->
            assertTrue("${release.versionName} must have non-blank title", release.title.isNotBlank())
            assertTrue("${release.versionName} must have non-blank summary", release.summary.isNotBlank())
            assertTrue("${release.versionName} must contain at least one change", release.changes.isNotEmpty())
            release.changes.forEach { change ->
                assertTrue("${release.versionName} change title blank", change.title.isNotBlank())
                assertTrue("${release.versionName} change detail blank", change.detail.isNotBlank())
                assertTrue("Item category cannot be ALL", change.category != ChangeCategory.ALL)
            }
        }
    }

    @Test
    fun `header version pill opens changelog dialog and filters categories cleanly on 320dp portrait`() {
        val sampleQuote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "贵州茅台",
            price = 1480.0,
            yesterdayClose = 1470.0,
            open = 1475.0,
            change = 10.0,
            changePercent = 0.68,
            high = 1488.0,
            low = 1468.0,
            volumeLots = 30000,
            turnoverTenThousand = 440000.0,
            peTtm = 20.5,
            pb = 6.5,
            totalMarketCapHundredMillion = 18590.0,
            amplitude = 1.36
        )
        val compactSizeClass = WindowSizeClass.calculateFromSize(DpSize(320.dp, 900.dp))
        val compactSpec = AdaptiveLayoutSpec.fromWindowSizeClass(compactSizeClass, fontScale = 1.3f, screenDensity = 1.5f)

        composeRule.setContent {
            var showDialog by remember { mutableStateOf(false) }
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.5f, fontScale = 1.3f),
                LocalAdaptiveLayout provides compactSpec
            ) {
                Theme {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .background(TerminalBg)
                            .testTag("changelog_test_root")
                    ) {
                        StockHeaderBar(
                            quote = sampleQuote,
                            isWatchlisted = false,
                            onToggleWatchlist = {},
                            onSearchClick = {},
                            onRefreshClick = {},
                            onVersionClick = { showDialog = true }
                        )
                        if (showDialog) {
                            VersionChangelogDialog(onDismiss = { showDialog = false })
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onNodeWithTag("changelog_test_root").fetchSemanticsNode().boundsInRoot
        val versionBtn = composeRule.onNodeWithTag("header_version_changelog_button")
        versionBtn.assertIsDisplayed()
        val btnBounds = versionBtn.fetchSemanticsNode().boundsInRoot
        assertTrue("Version button left clipped", btnBounds.left >= rootBounds.left - 2f)
        assertTrue("Version button right overflowed", btnBounds.right <= rootBounds.right + 2f)

        // Tap version pill to open dialog
        versionBtn.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("version_changelog_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("changelog_release_${BuildConfig.VERSION_NAME}").assertIsDisplayed()

        // Filter by FIX -> releases with fixes (current 1.4.1 and 1.1) appear, feature-only 1.4/1.3/1.2 are filtered out
        composeRule.onNodeWithTag("changelog_filter_fix").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("changelog_release_${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeRule.onNodeWithTag("changelog_release_1.4").assertDoesNotExist()
        composeRule.onNodeWithTag("changelog_release_1.3").assertDoesNotExist()
        composeRule.onNodeWithTag("changelog_release_1.2").assertDoesNotExist()

        // Switch back to ALL -> current release (1.4.1) and 1.4 both appear
        composeRule.onNodeWithTag("changelog_filter_all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("changelog_release_${BuildConfig.VERSION_NAME}").assertIsDisplayed()

        // Close dialog
        composeRule.onNodeWithTag("changelog_close_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("version_changelog_dialog").assertDoesNotExist()
    }
}

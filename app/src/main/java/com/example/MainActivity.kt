package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.StockTerminalViewModel

enum class NavigationTab(val title: String, val icon: ImageVectorAlias, val tag: String) {
    TERMINAL("行情中枢", Icons.AutoMirrored.Filled.ShowChart, "tab_terminal"),
    WATCHLIST("自选标的", Icons.Default.Bookmarks, "tab_watchlist"),
    SCREENER("策略选股", Icons.Default.FilterList, "tab_screener"),
    RISK_MONITOR("风险监控", Icons.Default.Shield, "tab_risk"),
    STORAGE("因子沉淀", Icons.Default.Layers, "tab_storage")
}

private typealias ImageVectorAlias = androidx.compose.ui.graphics.vector.ImageVector

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            Theme {
                MainTerminalApp(windowSizeClass = windowSizeClass)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainTerminalApp(
    windowSizeClass: WindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(390.dp, 844.dp)),
    viewModel: StockTerminalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(NavigationTab.TERMINAL) }
    val density = LocalDensity.current
    val adaptiveSpec = remember(windowSizeClass, density.fontScale, density.density) {
        AdaptiveLayoutSpec.fromWindowSizeClass(
            windowSizeClass = windowSizeClass,
            fontScale = density.fontScale,
            screenDensity = density.density
        )
    }

    // Intercept back button to return to Terminal tab if on another screen
    BackHandler(enabled = currentTab != NavigationTab.TERMINAL) {
        currentTab = NavigationTab.TERMINAL
    }

    CompositionLocalProvider(LocalAdaptiveLayout provides adaptiveSpec) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalBg),
            containerColor = TerminalBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!adaptiveSpec.useNavigationRail) {
                    NavigationBar(
                        containerColor = TerminalSurface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("main_bottom_nav_bar")
                    ) {
                        NavigationTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (selected) WallStreetGold else TextMuted
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        color = if (selected) WallStreetGold else TextMuted,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = WallStreetGoldSoft
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (adaptiveSpec.useNavigationRail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = TerminalSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("main_nav_rail"),
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "投壶",
                                    color = WallStreetGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "QUANT",
                                    color = QuantCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))
                            NavigationTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (selected) WallStreetGold else TextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            color = if (selected) WallStreetGold else TextMuted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    colors = NavigationRailItemDefaults.colors(
                                        indicatorColor = WallStreetGoldSoft
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        MainScreenContent(
                            currentTab = currentTab,
                            windowSizeClass = windowSizeClass,
                            viewModel = viewModel,
                            uiState = uiState,
                            onNavigateToTerminal = { currentTab = NavigationTab.TERMINAL },
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = adaptiveSpec.maxContentWidth)
                                .fillMaxWidth()
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    MainScreenContent(
                        currentTab = currentTab,
                        windowSizeClass = windowSizeClass,
                        viewModel = viewModel,
                        uiState = uiState,
                        onNavigateToTerminal = { currentTab = NavigationTab.TERMINAL },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreenContent(
    currentTab: NavigationTab,
    windowSizeClass: WindowSizeClass,
    viewModel: StockTerminalViewModel,
    uiState: com.example.ui.viewmodel.TerminalUiState,
    onNavigateToTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (currentTab) {
            NavigationTab.TERMINAL -> {
                StockTerminalScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    windowSizeClass = windowSizeClass
                )
            }
            NavigationTab.WATCHLIST -> {
                WatchlistScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToTerminal = onNavigateToTerminal,
                    windowSizeClass = windowSizeClass
                )
            }
            NavigationTab.SCREENER -> {
                ScreenerScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToTerminal = onNavigateToTerminal,
                    windowSizeClass = windowSizeClass
                )
            }
            NavigationTab.RISK_MONITOR -> {
                RiskMonitorScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    windowSizeClass = windowSizeClass
                )
            }
            NavigationTab.STORAGE -> {
                QuantFactorStorageScreen(
                    uiState = uiState,
                    windowSizeClass = windowSizeClass
                )
            }
        }
    }
}

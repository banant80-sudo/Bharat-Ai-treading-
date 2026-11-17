package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.TerminalTab
import com.example.ui.components.BrokerConnectionSheet
import com.example.ui.components.TodaysProfitSummaryBar
import com.example.ui.components.TopTelemetryBar
import com.example.ui.screens.LogsAnalyticsScreen
import com.example.ui.screens.PositionsScreen
import com.example.ui.screens.RiskConfigScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.CyanTerminal
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.QuantPulseTheme
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalDivider
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TerminalVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.TradingUiState
import com.example.ui.viewmodel.TradingViewModel
import com.example.util.Formatters
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            QuantPulseTheme {
                QuantPulseTerminalApp()
            }
        }
    }
}

private data class NavDestination(
    val tab: TerminalTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TerminalDestinations = listOf(
    NavDestination(
        tab = TerminalTab.SCANNER,
        label = "Scanner",
        selectedIcon = Icons.Filled.CandlestickChart,
        unselectedIcon = Icons.Outlined.CandlestickChart
    ),
    NavDestination(
        tab = TerminalTab.POSITIONS,
        label = "Positions",
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet
    ),
    NavDestination(
        tab = TerminalTab.RISK,
        label = "Risk Rules",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune
    ),
    NavDestination(
        tab = TerminalTab.LOGS,
        label = "Logs",
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong
    )
)

@Composable
fun QuantPulseTerminalApp(
    viewModel: TradingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = uiState.selectedTab != TerminalTab.SCANNER) {
        viewModel.selectTab(TerminalTab.SCANNER)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalVoid)
    ) {
        val useRail = maxWidth >= 600.dp
        val isExpandedDesktopPane = maxWidth >= 900.dp

        Scaffold(
            containerColor = TerminalVoid,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = TerminalSurfaceElevated,
                        contentColor = TextPrimary,
                        actionColor = EmeraldBright
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    Column {
                        HorizontalDivider(color = TerminalDivider, thickness = 1.dp)
                        NavigationBar(
                            containerColor = TerminalSurface,
                            tonalElevation = 0.dp
                        ) {
                            TerminalDestinations.forEach { dest ->
                                val selected = uiState.selectedTab == dest.tab
                                val badgeCount = if (dest.tab == TerminalTab.POSITIONS) {
                                    uiState.activePositions.size
                                } else {
                                    0
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(dest.tab) },
                                    icon = {
                                        NavIconWithBadge(
                                            icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            label = dest.label,
                                            badgeCount = badgeCount
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldBright,
                                        selectedTextColor = EmeraldBright,
                                        indicatorColor = EmeraldContainer,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_${dest.tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = TerminalSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .navigationBarsPadding()
                    ) {
                        TerminalDestinations.forEach { dest ->
                            val selected = uiState.selectedTab == dest.tab
                            val badgeCount = if (dest.tab == TerminalTab.POSITIONS) {
                                uiState.activePositions.size
                            } else {
                                0
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    NavIconWithBadge(
                                        icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        label = dest.label,
                                        badgeCount = badgeCount
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldBright,
                                    selectedTextColor = EmeraldBright,
                                    indicatorColor = EmeraldContainer,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("rail_${dest.tab.name.lowercase()}")
                            )
                        }
                    }
                    VerticalDivider(color = TerminalDivider, thickness = 1.dp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1280.dp)
                    ) {
                        TopTelemetryBar(
                            connectionState = uiState.connectionState,
                            isPaperMode = uiState.riskConfig.isPaperMode,
                            marketWindowStatus = uiState.marketWindowStatus,
                            accountSummary = uiState.accountSummary,
                            onOpenBrokerModal = { viewModel.setBrokerSheetOpen(true) }
                        )
                        HorizontalDivider(color = TerminalBorder, thickness = 1.dp)

                        if (uiState.isCircuitBreakerTriggered) {
                            CircuitBreakerAlertBanner(
                                dailyMaxLossLimitInr = uiState.riskConfig.dailyMaxLossLimitInr,
                                isPaperMode = uiState.riskConfig.isPaperMode,
                                onResetPaper = viewModel::resetPaperAccount
                            )
                        }

                        if (uiState.selectedTab == TerminalTab.SCANNER || uiState.selectedTab == TerminalTab.LOGS) {
                            TodaysProfitSummaryBar(
                                accountSummary = uiState.accountSummary,
                                riskConfig = uiState.riskConfig,
                                onNavigateToTransactions = { viewModel.selectTab(TerminalTab.LOGS) }
                            )
                        }

                        if (isExpandedDesktopPane && uiState.selectedTab == TerminalTab.SCANNER) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                ScannerContent(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1.3f)
                                )
                                VerticalDivider(color = TerminalDivider, thickness = 1.dp)
                                PositionsScreen(
                                    positions = uiState.activePositions,
                                    accountSummary = uiState.accountSummary,
                                    riskConfig = uiState.riskConfig,
                                    autoTradingEnabled = uiState.autoTradingEnabled,
                                    buyBasketCooldownRemainingSec = uiState.buyBasketCooldownRemainingSec,
                                    sellBasketCooldownRemainingSec = uiState.sellBasketCooldownRemainingSec,
                                    buyBasketHighWater = uiState.buyBasketHighWater,
                                    sellBasketHighWater = uiState.sellBasketHighWater,
                                    onBookBasketProfitNow = viewModel::bookBasketProfitNow,
                                    onBookAllCombinedProfitNow = viewModel::bookAllCombinedProfitNow,
                                    onSquareOffPosition = viewModel::squareOffPosition,
                                    onSquareOffAll = viewModel::squareOffAll,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            when (uiState.selectedTab) {
                                TerminalTab.SCANNER -> ScannerContent(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                                TerminalTab.POSITIONS -> PositionsScreen(
                                    positions = uiState.activePositions,
                                    accountSummary = uiState.accountSummary,
                                    riskConfig = uiState.riskConfig,
                                    autoTradingEnabled = uiState.autoTradingEnabled,
                                    buyBasketCooldownRemainingSec = uiState.buyBasketCooldownRemainingSec,
                                    sellBasketCooldownRemainingSec = uiState.sellBasketCooldownRemainingSec,
                                    buyBasketHighWater = uiState.buyBasketHighWater,
                                    sellBasketHighWater = uiState.sellBasketHighWater,
                                    onBookBasketProfitNow = viewModel::bookBasketProfitNow,
                                    onBookAllCombinedProfitNow = viewModel::bookAllCombinedProfitNow,
                                    onSquareOffPosition = viewModel::squareOffPosition,
                                    onSquareOffAll = viewModel::squareOffAll,
                                    modifier = Modifier.fillMaxSize()
                                )
                                TerminalTab.RISK -> RiskConfigScreen(
                                    riskConfig = uiState.riskConfig,
                                    instruments = uiState.instruments,
                                    marketWindowStatus = uiState.marketWindowStatus,
                                    onUpdateRiskConfig = viewModel::updateRiskConfig,
                                    onResetPaperAccount = viewModel::resetPaperAccount,
                                    modifier = Modifier.fillMaxSize()
                                )
                                TerminalTab.LOGS -> LogsAnalyticsScreen(
                                    accountSummary = uiState.accountSummary,
                                    transactions = uiState.transactions,
                                    tradeLogs = uiState.tradeLogs,
                                    performanceDiagnostic = uiState.performanceDiagnostic,
                                    onRunAiOptimization = viewModel::runAiPerformanceOptimization,
                                    onToggleApiQuotaLimit = viewModel::toggleApiQuotaLimit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isBrokerSheetOpen) {
        BrokerConnectionSheet(
            credentials = uiState.credentials,
            connectionState = uiState.connectionState,
            isPaperMode = uiState.riskConfig.isPaperMode,
            onCredentialsChange = viewModel::updateCredentials,
            onPaperModeToggle = { paperEnabled ->
                viewModel.updateRiskConfig(uiState.riskConfig.copy(isPaperMode = paperEnabled))
            },
            onConnect = viewModel::connectBroker,
            onDisconnect = viewModel::disconnectBroker,
            onDismiss = { viewModel.setBrokerSheetOpen(false) }
        )
    }
}

@Composable
private fun ScannerContent(
    uiState: TradingUiState,
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    ScannerScreen(
        instruments = uiState.instruments,
        activePositions = uiState.activePositions,
        accountSummary = uiState.accountSummary,
        selectedExchange = uiState.selectedExchange,
        signalFilter = uiState.signalFilter,
        searchQuery = uiState.searchQuery,
        autoTradingEnabled = uiState.autoTradingEnabled,
        isCircuitBreakerTriggered = uiState.isCircuitBreakerTriggered,
        riskConfig = uiState.riskConfig,
        marketWindowStatus = uiState.marketWindowStatus,
        buyBasketCooldownRemainingSec = uiState.buyBasketCooldownRemainingSec,
        sellBasketCooldownRemainingSec = uiState.sellBasketCooldownRemainingSec,
        buyBasketHighWater = uiState.buyBasketHighWater,
        sellBasketHighWater = uiState.sellBasketHighWater,
        screenerUiState = uiState.screenerUiState,
        onRunGeminiDeepScan = viewModel::runGeminiDeepScan,
        onBookBasketProfitNow = viewModel::bookBasketProfitNow,
        onBookAllCombinedProfitNow = viewModel::bookAllCombinedProfitNow,
        onSelectExchange = viewModel::selectExchange,
        onSelectSignalFilter = viewModel::selectSignalFilter,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onToggleAutoTrading = viewModel::toggleAutoTrading,
        onToggleAllowSellAutoTrade = viewModel::toggleAllowSellAutoTrade,
        onManualOrder = viewModel::placeManualOrder,
        modifier = modifier
    )
}

@Composable
private fun CircuitBreakerAlertBanner(
    dailyMaxLossLimitInr: Double,
    isPaperMode: Boolean,
    onResetPaper: () -> Unit
) {
    Surface(
        color = CrimsonContainer,
        border = BorderStroke(1.dp, CrimsonLoss),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("circuit_breaker_alert_banner")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = "Daily Circuit Breaker Triggered",
                tint = CrimsonBright,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DAILY CIRCUIT BREAKER TRIGGERED (-${Formatters.formatInr(dailyMaxLossLimitInr)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonBright
                )
                Text(
                    text = "All open trades squared off & Auto-Trading locked for today to protect capital.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
            if (isPaperMode) {
                OutlinedButton(
                    onClick = onResetPaper,
                    border = BorderStroke(1.dp, CrimsonBright),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBright),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("circuit_breaker_reset_paper")
                ) {
                    Text("Reset Paper")
                }
            }
        }
    }
}

@Composable
private fun NavIconWithBadge(
    icon: ImageVector,
    label: String,
    badgeCount: Int
) {
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = CyanTerminal,
                    contentColor = TerminalVoid
                ) {
                    Text(badgeCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = label)
    }
}

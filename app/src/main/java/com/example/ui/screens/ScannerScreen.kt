package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.GeminiScreenerUiState
import com.example.domain.model.AccountSummary
import com.example.domain.model.ActivePosition
import com.example.domain.model.AiTrendSignal
import com.example.domain.model.BasketHighWaterState
import com.example.domain.model.Exchange
import com.example.domain.model.MarketInstrument
import com.example.domain.model.RiskConfig
import com.example.domain.model.RsiStatus
import com.example.domain.model.StopLossMode
import com.example.domain.model.TargetProfitMode
import com.example.domain.model.TradeSide
import com.example.ui.components.BasketProfitProgressCard
import com.example.ui.components.MiniSparkline
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTerminal
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TerminalSurfaceHighlight
import com.example.ui.theme.TerminalVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SignalFilter
import com.example.util.Formatters
import com.example.util.MarketWindowStatus
import java.util.Locale
import kotlin.math.floor

@Composable
fun ScannerScreen(
    instruments: List<MarketInstrument>,
    activePositions: List<ActivePosition>,
    accountSummary: AccountSummary,
    selectedExchange: Exchange?,
    signalFilter: SignalFilter,
    searchQuery: String,
    autoTradingEnabled: Boolean,
    isCircuitBreakerTriggered: Boolean,
    riskConfig: RiskConfig,
    marketWindowStatus: MarketWindowStatus,
    buyBasketCooldownRemainingSec: Int,
    sellBasketCooldownRemainingSec: Int,
    buyBasketHighWater: BasketHighWaterState = BasketHighWaterState(),
    sellBasketHighWater: BasketHighWaterState = BasketHighWaterState(),
    screenerUiState: GeminiScreenerUiState = GeminiScreenerUiState.Idle,
    onRunGeminiDeepScan: () -> Unit = {},
    onBookBasketProfitNow: (TradeSide) -> Unit = {},
    onBookAllCombinedProfitNow: () -> Unit = {},
    onSelectExchange: (Exchange?) -> Unit,
    onSelectSignalFilter: (SignalFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleAutoTrading: (Boolean) -> Unit,
    onToggleAllowSellAutoTrade: (Boolean) -> Unit = {},
    onActivateMorningEveningSchedule: (Boolean) -> Unit = {},
    onManualOrder: (MarketInstrument, TradeSide) -> Unit,
    modifier: Modifier = Modifier
) {
    val budgetPerTrade = riskConfig.effectiveCapitalPerTrade

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "basket_progress_scanner") {
            BasketProfitProgressCard(
                positions = activePositions,
                accountSummary = accountSummary,
                riskConfig = riskConfig,
                autoTradingEnabled = autoTradingEnabled,
                buyBasketCooldownRemainingSec = buyBasketCooldownRemainingSec,
                sellBasketCooldownRemainingSec = sellBasketCooldownRemainingSec,
                buyBasketHighWater = buyBasketHighWater,
                sellBasketHighWater = sellBasketHighWater,
                onBookBasketProfitNow = onBookBasketProfitNow,
                onBookAllCombinedProfitNow = onBookAllCombinedProfitNow
            )
        }

        item(key = "auto_trading_card") {
            AutoTradingControlCard(
                enabled = autoTradingEnabled,
                isCircuitBreakerTriggered = isCircuitBreakerTriggered,
                riskConfig = riskConfig,
                marketWindowStatus = marketWindowStatus,
                eligibleCount = instruments.count {
                    floor(budgetPerTrade / it.ltp) >= 1.0
                },
                totalCount = instruments.size,
                onToggle = onToggleAutoTrading,
                onToggleAllowSellAutoTrade = onToggleAllowSellAutoTrade,
                dailyProfitFloorInr = accountSummary.dailyProfitFloorInr,
                onActivateMorningEveningSchedule = onActivateMorningEveningSchedule
            )
        }

        item(key = "gemini_screener_card") {
            GeminiScreenerBannerCard(
                screenerUiState = screenerUiState,
                passingCount = instruments.count { it.allScreenerChecksPassed && it.aiScreenerScore >= riskConfig.minAiConfidence },
                totalCount = instruments.size,
                minAiConfidence = riskConfig.minAiConfidence,
                onRunDeepScan = onRunGeminiDeepScan
            )
        }

        item(key = "scanner_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search NSE, BSE, MCX symbols…", color = TextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldProfit,
                        unfocusedBorderColor = TerminalBorder,
                        focusedContainerColor = TerminalSurface,
                        unfocusedContainerColor = TerminalSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanner_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldBright,
                        containerColor = TerminalSurfaceElevated,
                        labelColor = TextSecondary
                    )

                    FilterChip(
                        selected = selectedExchange == null,
                        onClick = { onSelectExchange(null) },
                        label = { Text("ALL") },
                        colors = chipColors,
                        modifier = Modifier.testTag("filter_exchange_all")
                    )
                    Exchange.entries.forEach { ex ->
                        FilterChip(
                            selected = selectedExchange == ex,
                            onClick = { onSelectExchange(ex) },
                            label = { Text(ex.label) },
                            colors = chipColors,
                            modifier = Modifier.testTag("filter_exchange_${ex.label.lowercase()}")
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    SignalFilter.entries.forEach { sf ->
                        if (sf != SignalFilter.ALL) {
                            val activeColor = if (sf == SignalFilter.STRONG_BUY) EmeraldContainer else CrimsonContainer
                            val activeText = if (sf == SignalFilter.STRONG_BUY) EmeraldBright else CrimsonBright
                            FilterChip(
                                selected = signalFilter == sf,
                                onClick = {
                                    onSelectSignalFilter(if (signalFilter == sf) SignalFilter.ALL else sf)
                                },
                                label = { Text(sf.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = activeColor,
                                    selectedLabelColor = activeText,
                                    containerColor = TerminalSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("filter_signal_${sf.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        if (instruments.isEmpty()) {
            item(key = "empty_scanner") {
                Surface(
                    color = TerminalSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TerminalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No matching instruments",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Clear the search query or switch exchange filters to view NSE, BSE & MCX feeds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(instruments, key = { it.symbol }) { instrument ->
                WatchlistInstrumentCard(
                    instrument = instrument,
                    perTradeBudget = budgetPerTrade,
                    onBuy = { onManualOrder(instrument, TradeSide.LONG) },
                    onSell = { onManualOrder(instrument, TradeSide.SHORT) }
                )
            }
        }

        item(key = "scanner_bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AutoTradingControlCard(
    enabled: Boolean,
    isCircuitBreakerTriggered: Boolean,
    riskConfig: RiskConfig,
    marketWindowStatus: MarketWindowStatus,
    eligibleCount: Int,
    totalCount: Int,
    onToggle: (Boolean) -> Unit,
    onToggleAllowSellAutoTrade: (Boolean) -> Unit = {},
    dailyProfitFloorInr: Double? = null,
    onActivateMorningEveningSchedule: (Boolean) -> Unit = {}
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isCircuitBreakerTriggered -> CrimsonLoss
            enabled -> EmeraldProfit
            else -> TerminalBorder
        },
        label = "auto_card_border"
    )

    val tpSummary = "Zero-Wait +₹${riskConfig.singleTradeInstantBookInr.toInt()}/+₹${riskConfig.basketInstantBookInr.toInt()}"
    val slSummary = if (riskConfig.stopLossMode == StopLossMode.FIXED_INR) {
        "-${Formatters.formatInr(riskConfig.stopLossInr)} SL"
    } else {
        "-${riskConfig.stopLossPct}% SL"
    }
    val allocSummary = if (riskConfig.smallAmountAutoSplitEnabled) {
        "Auto-Split ${Formatters.formatInr(riskConfig.smallAmountPerTrade)}/trade"
    } else {
        "Cap ${Formatters.formatInr(riskConfig.maxCapitalPerTrade)}/trade"
    }

    Surface(
        color = TerminalSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = if (enabled || isCircuitBreakerTriggered) 0.75f else 1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = when {
                            isCircuitBreakerTriggered -> CrimsonContainer
                            enabled -> EmeraldContainer
                            else -> TerminalSurfaceHighlight
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = when {
                                    isCircuitBreakerTriggered -> CrimsonBright
                                    enabled -> EmeraldBright
                                    else -> TextSecondary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = when {
                                isCircuitBreakerTriggered && !enabled -> "Circuit Breaker (Tap to Re-Arm)"
                                riskConfig.singleDirectionOnly -> "Single-Direction AI Auto-Scalper"
                                else -> "Synchronized Pair Auto-Scalper"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCircuitBreakerTriggered && !enabled) CrimsonBright else TextPrimary
                        )
                        val dirHeader = if (riskConfig.singleDirectionOnly) "1-DIR (BUY or SELL)" else "1 BUY + 1 SELL PAIR"
                        Text(
                            text = "$dirHeader • $allocSummary • $tpSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) EmeraldBright else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    enabled = true,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("auto_trading_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TerminalVoid,
                        checkedTrackColor = EmeraldProfit,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = TerminalSurfaceHighlight
                    )
                )
            }

            // Dedicated One-Tap Allow SELL Auto-Trading (OFF / ON) Switch Row
            HorizontalDivider(color = TerminalBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (riskConfig.allowSellAutoTrade) CrimsonContainer else AmberContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (riskConfig.allowSellAutoTrade) "SELL: ACTIVE (ON)" else "SELL: HOLD (OFF)",
                            style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = if (riskConfig.allowSellAutoTrade) CrimsonBright else AmberAlert,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("sell_auto_trade_status_badge")
                        )
                    }
                    Column {
                        Text(
                            text = "Allow SELL Auto-Invest",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            text = if (riskConfig.allowSellAutoTrade) {
                                "Active: Bot enters confirmed SELL setups"
                            } else {
                                "HOLD: 100% BUY-Only setups (Zero SELL entries)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Switch(
                    checked = riskConfig.allowSellAutoTrade,
                    onCheckedChange = onToggleAllowSellAutoTrade,
                    modifier = Modifier.testTag("allow_sell_auto_trade_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TerminalVoid,
                        checkedTrackColor = CrimsonBright,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = TerminalSurfaceHighlight
                    )
                )
            }

            // Strategy Confirmation + Opening Volatility Guard + Market Window Status Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CyanContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$eligibleCount/$totalCount Eligible (≤${Formatters.formatInr(riskConfig.effectiveCapitalPerTrade)})",
                        style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = CyanTerminal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = EmeraldContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "VWAP + 9/21 EMA + RSI",
                        style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = EmeraldBright,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (riskConfig.enforceMarketHours && riskConfig.skipOpeningVolatilityEnabled && marketWindowStatus.isOpeningVolatilityWindow) {
                    Surface(
                        color = AmberContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Opening Vol Guard (09:15–09:30)",
                            style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            color = AmberAlert,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(
                    color = CyanContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (riskConfig.singleDirectionOnly) "1-Direction AI Lock • BE +₹3" else "Sync Pair (1B+1S) • BE +₹3",
                        style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = CyanTerminal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                val isMorningEveningOn = riskConfig.enforceMarketHours &&
                    riskConfig.autoStartAtMarketOpen &&
                    !riskConfig.mcxExtendedHoursEnabled
                FilterChip(
                    selected = isMorningEveningOn,
                    onClick = { onActivateMorningEveningSchedule(!isMorningEveningOn) },
                    label = {
                        Text(
                            text = if (isMorningEveningOn) "09:15–15:15 IST Auto: ON" else "09:15–15:15 Auto: OFF (24/7 Demo)",
                            style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldBright,
                        containerColor = TerminalSurface,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("scanner_morning_evening_chip")
                )
                if (dailyProfitFloorInr != null) {
                    Surface(
                        color = EmeraldContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Profit Shield ≥+₹${dailyProfitFloorInr.toInt()}",
                            style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = EmeraldBright,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = AmberContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberAlert.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Risk management warning",
                            tint = AmberAlert,
                            modifier = Modifier.size(18.dp)
                        )
                        val dirNote = if (riskConfig.singleDirectionOnly) {
                            "1-Direction Only (highest AI side BUY or SELL)"
                        } else {
                            "Synchronized Pair (1 BUY + 1 SELL together, waits until both exit)"
                        }
                        val cdNote = if (riskConfig.reentryCooldownSec == 0) {
                            "0s Instant Re-Entry"
                        } else {
                            "${riskConfig.reentryCooldownSec}s cooldown"
                        }
                        Text(
                            text = if (riskConfig.isPaperMode) {
                                "Zero-Wait Shield Armed (Paper): $dirNote • Max ${riskConfig.maxOpenPositions} trades at ≥${riskConfig.minAiConfidence}% Local AI • +₹${riskConfig.singleTradeInstantBookInr.toInt()} Trade / +₹${riskConfig.basketInstantBookInr.toInt()} Basket • BE +₹3 & $cdNote."
                            } else {
                                "LIVE ZERO-WAIT SHIELD ARMED: $dirNote • Real NSE/BSE/MCX trades (max ${riskConfig.maxOpenPositions}) at ≥${riskConfig.minAiConfidence}% Local AI • +₹${riskConfig.singleTradeInstantBookInr.toInt()} Trade / +₹${riskConfig.basketInstantBookInr.toInt()} Basket & mandatory $slSummary."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiScreenerBannerCard(
    screenerUiState: GeminiScreenerUiState,
    passingCount: Int,
    totalCount: Int,
    minAiConfidence: Int,
    onRunDeepScan: () -> Unit
) {
    val isLoading = screenerUiState is GeminiScreenerUiState.Loading
    val borderColor = when (screenerUiState) {
        is GeminiScreenerUiState.Error -> AmberAlert.copy(alpha = 0.7f)
        is GeminiScreenerUiState.Success -> EmeraldProfit.copy(alpha = 0.65f)
        else -> CyanTerminal.copy(alpha = 0.55f)
    }

    Surface(
        color = TerminalSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gemini_screener_banner")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanTerminal,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "$minAiConfidence% Quantitative AI Screener",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$passingCount/$totalCount Instant PASS (≥$minAiConfidence% Gate • <1ms/tick)",
                            style = MonoTabularStyle.copy(fontSize = 11.sp),
                            color = if (passingCount > 0) EmeraldBright else AmberAlert
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRunDeepScan,
                    enabled = !isLoading,
                    border = BorderStroke(1.dp, CyanTerminal.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanTerminal),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("run_gemini_scan_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = CyanTerminal,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scanning", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("Scan Now", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when (screenerUiState) {
                is GeminiScreenerUiState.Idle -> {
                    Text(
                        text = "Scores every stock (0–100) locally every tick in <1ms on Trend/EMA/VWAP, Volume Surge & Fee-Clearance. Enters immediately at ≥$minAiConfidence%.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                is GeminiScreenerUiState.Loading -> {
                    Text(
                        text = "Scanning live NSE, BSE & MCX snapshots locally…",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanTerminal
                    )
                }
                is GeminiScreenerUiState.Success -> {
                    val picksText = if (screenerUiState.topPickSymbols.isNotEmpty()) {
                        "Instant ≥$minAiConfidence% Picks: ${screenerUiState.topPickSymbols.joinToString(" • ")}"
                    } else {
                        "0 ≥$minAiConfidence% setups right now — holding cash until 3-factor lock"
                    }
                    Text(
                        text = "${screenerUiState.lastScannedAt} • ${screenerUiState.marketSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = picksText,
                        style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = if (screenerUiState.topPickSymbols.isNotEmpty()) EmeraldBright else AmberAlert
                    )
                }
                is GeminiScreenerUiState.Error -> {
                    Text(
                        text = "Scan notice: ${screenerUiState.message} (Local 3-factor ≥$minAiConfidence% gate remains active).",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberAlert
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchlistInstrumentCard(
    instrument: MarketInstrument,
    perTradeBudget: Double,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    val isPositive = instrument.changePct >= 0.0
    val priceColor = if (isPositive) EmeraldProfit else CrimsonLoss
    val affordableQty = floor(perTradeBudget / instrument.ltp)
    val isAffordable = affordableQty >= 1.0

    val (signalBg, signalFg) = when (instrument.aiSignal) {
        AiTrendSignal.STRONG_BUY -> EmeraldContainer to EmeraldBright
        AiTrendSignal.BUY -> EmeraldContainer.copy(alpha = 0.55f) to EmeraldProfit
        AiTrendSignal.NEUTRAL -> TerminalSurfaceHighlight to TextSecondary
        AiTrendSignal.SELL -> CrimsonContainer.copy(alpha = 0.55f) to CrimsonLoss
        AiTrendSignal.STRONG_SELL -> CrimsonContainer to CrimsonBright
    }

    val (rsiBg, rsiFg) = when (instrument.rsiStatus) {
        RsiStatus.OVERSOLD -> EmeraldContainer.copy(alpha = 0.5f) to EmeraldBright
        RsiStatus.OVERBOUGHT -> CrimsonContainer.copy(alpha = 0.5f) to CrimsonBright
        RsiStatus.NEUTRAL -> TerminalSurfaceHighlight to TextSecondary
    }

    Surface(
        color = TerminalSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isAffordable) TerminalBorder else TerminalBorder.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("instrument_card_${instrument.symbol}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.25f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = instrument.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isAffordable) TextPrimary else TextSecondary
                        )
                        Surface(
                            color = when (instrument.exchange) {
                                Exchange.NSE -> CyanContainer.copy(alpha = 0.6f)
                                Exchange.BSE -> TerminalSurfaceHighlight
                                Exchange.MCX -> AmberContainer.copy(alpha = 0.65f)
                            },
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = instrument.exchange.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (instrument.exchange) {
                                    Exchange.NSE -> CyanTerminal
                                    Exchange.BSE -> TextSecondary
                                    Exchange.MCX -> AmberAlert
                                },
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    val qtyLabel = Formatters.formatQuantity(affordableQty, instrument.exchange)
                    Text(
                        text = if (isAffordable) {
                            "${instrument.displayName} • $qtyLabel qty (${Formatters.formatInr(affordableQty * instrument.ltp)})"
                        } else {
                            "${instrument.displayName} • Skipped (> ${Formatters.formatInr(perTradeBudget)} limit)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAffordable) TextSecondary else AmberAlert.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                MiniSparkline(
                    prices = instrument.priceHistory,
                    isPositive = isPositive,
                    modifier = Modifier
                        .width(64.dp)
                        .height(30.dp)
                        .padding(horizontal = 4.dp)
                )

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.95f)
                ) {
                    Text(
                        text = Formatters.formatPrice(instrument.ltp),
                        style = MonoTabularStyle.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = priceColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = Formatters.formatPct(instrument.changePct),
                            style = MonoTabularStyle.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = priceColor
                        )
                    }
                }
            }

            // Gemini AI 4 Hydrogen Screener + 3-Factor Check Row
            val screenerPass = instrument.aiScreenerVerdict == "PASS" && instrument.allScreenerChecksPassed
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (screenerPass) EmeraldContainer else AmberContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("ai_screener_badge_${instrument.symbol}")
                    ) {
                        Text(
                            text = "AI ${instrument.aiScreenerScore}% • ${instrument.aiScreenerVerdict}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (screenerPass) EmeraldBright else AmberAlert,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = TerminalSurfaceHighlight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (instrument.trendMomentumPass) "Trend ✓" else "Trend ✕",
                            style = MonoTabularStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (instrument.trendMomentumPass) EmeraldProfit else AmberAlert,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = TerminalSurfaceHighlight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Vol ${String.format(Locale.US, "%.1fx", instrument.volumeSurgeRatio)} ${if (instrument.volumeLiquidityPass) "✓" else "✕"}",
                            style = MonoTabularStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (instrument.volumeLiquidityPass) EmeraldProfit else AmberAlert,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = TerminalSurfaceHighlight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (instrument.feeClearancePass) "FeeClr ✓" else "FeeClr ✕",
                            style = MonoTabularStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (instrument.feeClearancePass) EmeraldProfit else CrimsonLoss,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = if (instrument.strategyPassed) EmeraldContainer.copy(alpha = 0.5f) else TerminalSurfaceHighlight,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("strategy_badge_${instrument.symbol}")
                    ) {
                        Text(
                            text = if (instrument.isPriceAboveVwap) "VWAP ▲" else "VWAP ▼",
                            style = MonoTabularStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (instrument.isPriceAboveVwap) EmeraldProfit else CrimsonLoss,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = TerminalSurfaceHighlight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (instrument.isEmaBullish) "EMA 9>21" else "EMA 9<21",
                            style = MonoTabularStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (instrument.isEmaBullish) EmeraldProfit else CrimsonLoss,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = instrument.aiScreenerReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (screenerPass) EmeraldBright else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 1-Minute Candlestick Pattern + 99% Gate Live Status Pill
                Surface(
                    color = if (instrument.isOneMinCandleConfirmed && instrument.aiScreenerScore >= 99) {
                        EmeraldContainer.copy(alpha = 0.85f)
                    } else {
                        TerminalSurfaceHighlight
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (instrument.isOneMinCandleConfirmed && instrument.aiScreenerScore >= 99) EmeraldProfit.copy(alpha = 0.7f) else TerminalBorder.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("one_min_candle_pill_${instrument.symbol}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CandlestickChart,
                                contentDescription = null,
                                tint = if (instrument.isOneMinCandleConfirmed && instrument.aiScreenerScore >= 99) EmeraldBright else CyanTerminal,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = instrument.oneMinCandlePattern,
                                style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = if (instrument.isOneMinCandleConfirmed && instrument.aiScreenerScore >= 99) EmeraldBright else TextPrimary
                            )
                        }
                        Text(
                            text = if (instrument.isOneMinCandleConfirmed && instrument.aiScreenerScore >= 99) {
                                "99% PASS • 0s ENTRY"
                            } else {
                                instrument.oneMinCandleStatusPill
                            },
                            style = MonoTabularStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = if (instrument.isOneMinCandleConfirmed && instrument.aiScreenerScore >= 99) EmeraldBright else TextSecondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(color = rsiBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "RSI ${String.format(Locale.US, "%.1f", instrument.rsi14)}",
                            style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            color = rsiFg,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }

                    Surface(color = signalBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "${instrument.aiSignal.label} (${instrument.aiConfidence}%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = signalFg,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }

                    if (instrument.cooldownRemainingSec > 0) {
                        Surface(
                            color = AmberContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = AmberAlert,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${instrument.cooldownRemainingSec}s",
                                    style = MonoTabularStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = AmberAlert
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onBuy,
                        enabled = isAffordable,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldContainer,
                            contentColor = EmeraldBright,
                            disabledContainerColor = TerminalSurfaceHighlight,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("buy_button_${instrument.symbol}")
                    ) {
                        Text("BUY", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = onSell,
                        enabled = isAffordable,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonContainer,
                            contentColor = CrimsonBright,
                            disabledContainerColor = TerminalSurfaceHighlight,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("sell_button_${instrument.symbol}")
                    ) {
                        Text("SELL", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

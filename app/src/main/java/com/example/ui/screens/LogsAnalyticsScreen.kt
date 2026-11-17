package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AccountSummary
import com.example.domain.model.GeminiPerformanceDiagnostic
import com.example.domain.model.GrowthDiagnosisStatus
import com.example.domain.model.LogCategory
import com.example.domain.model.TradeLogEntry
import com.example.domain.model.TradeSide
import com.example.domain.model.TradeTransaction
import com.example.domain.model.TransactionStatus
import com.example.ui.components.EquityCurveChart
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.AngelOneChargeCalculator
import com.example.util.Formatters
import java.util.Locale

private enum class TransactionFilter(val label: String) {
    ALL_TRADES("ALL TRADES"),
    PROFIT_ONLY("PROFIT ONLY"),
    OPEN("OPEN"),
    CLOSED("CLOSED")
}

private enum class LogFilter(val label: String) {
    ALL("All Logs"),
    AUTO_EXITS("Auto-Drops & SL"),
    ORDERS("Orders & AI"),
    SCHEDULER("Scheduler & API")
}

@Composable
fun LogsAnalyticsScreen(
    accountSummary: AccountSummary,
    transactions: List<TradeTransaction>,
    tradeLogs: List<TradeLogEntry>,
    performanceDiagnostic: GeminiPerformanceDiagnostic = GeminiPerformanceDiagnostic(),
    onRunAiOptimization: () -> Unit = {},
    onToggleApiQuotaLimit: (Boolean) -> Unit = {},
    onWithdrawProfit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTxFilter by remember { mutableStateOf(TransactionFilter.ALL_TRADES) }
    var txSearchQuery by remember { mutableStateOf("") }
    var selectedLogFilter by remember { mutableStateOf(LogFilter.ALL) }

    val filteredTransactions = remember(transactions, selectedTxFilter, txSearchQuery) {
        val q = txSearchQuery.trim()
        transactions.filter { tx ->
            val matchFilter = when (selectedTxFilter) {
                TransactionFilter.ALL_TRADES -> true
                TransactionFilter.PROFIT_ONLY -> tx.netPnlInr > 0.0
                TransactionFilter.OPEN -> tx.status == TransactionStatus.OPEN
                TransactionFilter.CLOSED -> tx.status == TransactionStatus.CLOSED
            }
            val matchQuery = q.isEmpty() ||
                tx.symbol.contains(q, ignoreCase = true) ||
                tx.displayName.contains(q, ignoreCase = true) ||
                tx.exchange.label.contains(q, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    val filteredLogs = remember(tradeLogs, selectedLogFilter) {
        tradeLogs.filter { entry ->
            when (selectedLogFilter) {
                LogFilter.ALL -> true
                LogFilter.AUTO_EXITS -> entry.category == LogCategory.AUTO_PROFIT_EXIT ||
                    entry.category == LogCategory.BREAK_EVEN_EXIT ||
                    entry.category == LogCategory.STOP_LOSS_EXIT ||
                    entry.category == LogCategory.CIRCUIT_BREAKER ||
                    entry.category == LogCategory.MANUAL_EXIT
                LogFilter.ORDERS -> entry.category == LogCategory.AI_SIGNAL ||
                    entry.category == LogCategory.AI_SCREENER ||
                    entry.category == LogCategory.AI_OPTIMIZER ||
                    entry.category == LogCategory.ORDER_EXECUTION
                LogFilter.SCHEDULER -> entry.category == LogCategory.MARKET_SCHEDULER ||
                    entry.category == LogCategory.BROKER_API ||
                    entry.category == LogCategory.RISK_GUARD ||
                    entry.category == LogCategory.CIRCUIT_BREAKER
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "analytics_card") {
            Surface(
                color = TerminalSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Performance Analytics",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Net Realized P&L (after est. ₹${String.format(Locale.US, "%.2f", accountSummary.totalEstimatedCharges)} charges)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val realizedPositive = accountSummary.realizedDayPnl >= 0.0
                        Text(
                            text = Formatters.formatInr(accountSummary.realizedDayPnl, showSign = true),
                            style = MonoTabularStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            color = if (realizedPositive) EmeraldProfit else CrimsonLoss,
                            maxLines = 1
                        )
                    }

                    Surface(
                        color = TerminalSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TerminalBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        EquityCurveChart(
                            points = accountSummary.equityCurve,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiChip(
                            label = "WIN RATE",
                            value = "${String.format(Locale.US, "%.0f", accountSummary.winRatePct)}%",
                            sub = "${accountSummary.winCount}W / ${accountSummary.lossCount}L",
                            valueColor = if (accountSummary.winRatePct >= 50.0) EmeraldBright else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiChip(
                            label = "PROFIT FACTOR",
                            value = String.format(Locale.US, "%.2f", accountSummary.profitFactor),
                            sub = "Gross R:R",
                            valueColor = CyanTerminal,
                            modifier = Modifier.weight(1f)
                        )
                        KpiChip(
                            label = "BASKET DROPS",
                            value = "${accountSummary.basketDropsCompleted}",
                            sub = "${Formatters.formatInr(accountSummary.basketBookedProfitInr)} Booked",
                            valueColor = EmeraldBright,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TerminalSurfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Withdrawable Profit: ${Formatters.formatInr(accountSummary.withdrawableProfitInr)}",
                                style = MonoTabularStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                color = if (accountSummary.withdrawableProfitInr > 0.0) EmeraldBright else TextSecondary
                            )
                            Text(
                                text = "Withdrawn: ${Formatters.formatInr(accountSummary.withdrawnProfitInr)} • Base ₹5,00,000 safe",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        OutlinedButton(
                            onClick = onWithdrawProfit,
                            enabled = accountSummary.withdrawableProfitInr > 0.0,
                            border = BorderStroke(
                                1.dp,
                                if (accountSummary.withdrawableProfitInr > 0.0) EmeraldBright else TerminalBorder
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = EmeraldBright,
                                disabledContentColor = TextMuted
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("logs_withdraw_profit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(5.dp))
                            Text("Withdraw Profit", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item(key = "gemini_ai_4h_coach_card") {
            GeminiAiPerformanceCoachCard(
                accountSummary = accountSummary,
                diagnostic = performanceDiagnostic,
                onRunAiOptimization = onRunAiOptimization,
                onToggleApiQuotaLimit = onToggleApiQuotaLimit
            )
        }

        item(key = "transactions_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction History (${filteredTransactions.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Inv ${Formatters.formatInr(accountSummary.totalCapitalInvestedToday)}",
                        style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = CyanTerminal
                    )
                }

                OutlinedTextField(
                    value = txSearchQuery,
                    onValueChange = { txSearchQuery = it },
                    placeholder = { Text("Search transactions (RELIANCE, INFY, MCX…)", color = TextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (txSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { txSearchQuery = "" }) {
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
                        .testTag("tx_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransactionFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedTxFilter == filter,
                            onClick = { selectedTxFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldBright,
                                containerColor = TerminalSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("tx_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredTransactions.isEmpty()) {
            item(key = "empty_transactions") {
                Surface(
                    color = TerminalSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TerminalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = CyanTerminal,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (transactions.isEmpty()) "No Transactions Executed Yet" else "No Matching Transactions",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = if (transactions.isEmpty()) {
                                "Enable Synchronized Pair AI Auto-Scalper or tap BUY / SELL in Scanner to record live per-trade investment & P&L breakdowns."
                            } else {
                                "Try switching back to ALL TRADES or clearing the search box."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(filteredTransactions, key = { "tx_${it.id}" }) { tx ->
                TransactionDetailCard(transaction = tx)
            }
        }

        item(key = "logs_header") {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AI & Engine Logs",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LogFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedLogFilter == filter,
                            onClick = { selectedLogFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldBright,
                                containerColor = TerminalSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("log_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        items(filteredLogs, key = { it.id }) { log ->
            TradeLogCard(entry = log)
        }

        item(key = "logs_bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TransactionDetailCard(transaction: TradeTransaction) {
    val isProfit = transaction.netPnlInr >= 0.0
    val pnlColor = if (isProfit) EmeraldProfit else CrimsonLoss
    val sideBg = if (transaction.side == TradeSide.LONG) EmeraldContainer else CrimsonContainer
    val sideFg = if (transaction.side == TradeSide.LONG) EmeraldBright else CrimsonBright
    val statusBg = if (transaction.status == TransactionStatus.OPEN) CyanContainer.copy(alpha = 0.6f) else TerminalSurfaceHighlight
    val statusFg = if (transaction.status == TransactionStatus.OPEN) CyanTerminal else TextSecondary
    val qtyText = Formatters.formatQuantity(transaction.quantity, transaction.exchange)

    Surface(
        color = TerminalSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, pnlColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_card_${transaction.symbol}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${transaction.symbol} • ${transaction.exchange.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(color = sideBg, shape = RoundedCornerShape(5.dp)) {
                            Text(
                                text = transaction.side.smartApiTransactionType,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sideFg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(color = statusBg, shape = RoundedCornerShape(5.dp)) {
                            Text(
                                text = transaction.status.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusFg,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val timeRange = if (transaction.exitTimeFormatted != null) {
                        "${transaction.entryTimeFormatted} → ${transaction.exitTimeFormatted}"
                    } else {
                        "Entered ${transaction.entryTimeFormatted} • LIVE"
                    }
                    Text(
                        text = "$timeRange • ${transaction.brokerOrderId}",
                        style = MonoTabularStyle.copy(fontSize = 11.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (transaction.exitReason != null) {
                        Text(
                            text = transaction.exitReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isProfit) EmeraldBright else AmberAlert,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatInr(transaction.netPnlInr, showSign = true),
                        style = MonoTabularStyle.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = pnlColor
                    )
                    Text(
                        text = "Net (${Formatters.formatPct(transaction.pnlPct)})",
                        style = MonoTabularStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = pnlColor
                    )
                    Text(
                        text = "Gross ${Formatters.formatInr(transaction.grossPnlInr, showSign = true)}",
                        style = MonoTabularStyle.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                    Text(
                        text = "Chg ₹${String.format(Locale.US, "%.2f", transaction.estimatedChargesInr)}",
                        style = MonoTabularStyle.copy(fontSize = 10.sp),
                        color = TextMuted
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TxStatCell(
                    label = "INVESTED",
                    value = Formatters.formatInr(transaction.investedAmountInr)
                )
                TxStatCell(
                    label = "QTY",
                    value = qtyText
                )
                TxStatCell(
                    label = if (transaction.status == TransactionStatus.OPEN) "ENTRY → LTP" else "ENTRY → EXIT",
                    value = "${Formatters.formatPrice(transaction.entryPrice)} → ${Formatters.formatPrice(transaction.exitOrCurrentPrice)}"
                )
            }
        }
    }
}

@Composable
private fun TxStatCell(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = value,
            style = MonoTabularStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
    }
}

@Composable
private fun KpiChip(
    label: String,
    value: String,
    sub: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = TerminalSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, TerminalBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MonoTabularStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TradeLogCard(entry: TradeLogEntry) {
    val (badgeBg, badgeFg) = when (entry.category) {
        LogCategory.AUTO_PROFIT_EXIT -> EmeraldContainer to EmeraldBright
        LogCategory.BREAK_EVEN_EXIT -> EmeraldContainer.copy(alpha = 0.7f) to EmeraldBright
        LogCategory.STOP_LOSS_EXIT -> CrimsonContainer to CrimsonBright
        LogCategory.AI_SIGNAL -> CyanContainer to CyanTerminal
        LogCategory.AI_SCREENER -> CyanContainer.copy(alpha = 0.75f) to CyanTerminal
        LogCategory.AI_OPTIMIZER -> EmeraldContainer to CyanTerminal
        LogCategory.ORDER_EXECUTION -> EmeraldContainer.copy(alpha = 0.6f) to EmeraldProfit
        LogCategory.MANUAL_EXIT -> TerminalSurfaceHighlight to TextPrimary
        LogCategory.MARKET_SCHEDULER -> AmberContainer to AmberAlert
        LogCategory.BROKER_API -> TerminalSurfaceHighlight to CyanTerminal
        LogCategory.RISK_GUARD -> AmberContainer to AmberAlert
        LogCategory.CIRCUIT_BREAKER -> CrimsonContainer to CrimsonBright
    }

    Surface(
        color = TerminalSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, TerminalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(color = badgeBg, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            text = entry.category.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (entry.exchange != null) {
                        Text(
                            text = "${entry.symbol} • ${entry.exchange.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
                Text(
                    text = entry.timestampFormatted,
                    style = MonoTabularStyle.copy(fontSize = 11.sp),
                    color = TextMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (entry.pnl != null) {
                    val pnlColor = if (entry.pnl >= 0.0) EmeraldProfit else CrimsonLoss
                    Text(
                        text = Formatters.formatInr(entry.pnl, showSign = true),
                        style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = pnlColor
                    )
                }
            }

            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun GeminiAiPerformanceCoachCard(
    accountSummary: AccountSummary,
    diagnostic: GeminiPerformanceDiagnostic,
    onRunAiOptimization: () -> Unit,
    onToggleApiQuotaLimit: (Boolean) -> Unit
) {
    val (statusBadgeBg, statusBadgeFg, statusBorder) = when (diagnostic.status) {
        GrowthDiagnosisStatus.GROWING_CONTINUE -> Triple(EmeraldContainer, EmeraldBright, EmeraldProfit.copy(alpha = 0.6f))
        GrowthDiagnosisStatus.STAGNANT_SELF_MODIFIED -> Triple(CyanContainer, CyanTerminal, CyanTerminal.copy(alpha = 0.6f))
        GrowthDiagnosisStatus.API_LIMIT_HOLD -> Triple(AmberContainer, AmberAlert, AmberAlert.copy(alpha = 0.8f))
    }

    Surface(
        color = TerminalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, statusBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gemini_ai_performance_coach_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        contentDescription = "Gemini AI 4 Hydrogen",
                        tint = CyanTerminal,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Gemini AI 4 Hydrogen • Growth Coach",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Self-Modifying 99% Success Rate Optimizer • Synced ${diagnostic.lastEvaluatedAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    color = statusBadgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = diagnostic.status.badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusBadgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Headline Banner
            Surface(
                color = TerminalSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = diagnostic.headline,
                        style = MaterialTheme.typography.titleSmall,
                        color = statusBadgeFg
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiChip(
                            label = "KITNA INVEST HUA",
                            value = Formatters.formatInr(accountSummary.totalCapitalInvestedToday),
                            sub = "${accountSummary.totalTradesExecutedToday} Trades Today",
                            valueColor = CyanTerminal,
                            modifier = Modifier.weight(1f)
                        )
                        val totalNetPnl = accountSummary.totalDayPnl
                        KpiChip(
                            label = "NET PROFIT / LOSS",
                            value = Formatters.formatInr(totalNetPnl, showSign = true),
                            sub = "Realized ${Formatters.formatInr(accountSummary.realizedDayPnl, showSign = true)}",
                            valueColor = if (totalNetPnl >= 0.0) EmeraldBright else CrimsonBright,
                            modifier = Modifier.weight(1f)
                        )
                        KpiChip(
                            label = "CHARGES PAID",
                            value = Formatters.formatInr(accountSummary.totalEstimatedCharges),
                            sub = "Target ${diagnostic.targetSuccessRatePct}% Win",
                            valueColor = AmberAlert,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Diagnosis: Why Loss / Slow Growth Occurred
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "KYUN GROWTH NAHI HUA / LOSS DIAGNOSIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanTerminal
                )
                Text(
                    text = diagnostic.whyLossOrSlowGrowthDiagnosis,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }

            // How Gemini AI 4 Hydrogen Improves Profit & Self-Modifies
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PROFIT KAISE IMPROVE KAREGA (KHUD MODIFY KAR KE INVEST • 99% TARGET)",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldBright
                )
                Text(
                    text = diagnostic.profitImprovementPlan,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
                Surface(
                    color = TerminalSurfaceHighlight,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diagnostic.autoModifiedSummary,
                        style = MonoTabularStyle.copy(fontSize = 11.sp),
                        color = CyanTerminal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // 3-Segment Charge Matrix (Buy ₹5,000 -> Sell ₹5,100)
            val intraDetail = remember { AngelOneChargeCalculator.calculateEquityIntradayCharges(5000.0, 5100.0) }
            val delDetail = remember { AngelOneChargeCalculator.calculateEquityDeliveryCharges(5000.0, 5100.0) }
            val optDetail = remember { AngelOneChargeCalculator.calculateOptionsCharges(5000.0, 5100.0) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "BROKERAGE & TAX MATRIX (BUY ₹5,000 → SELL ₹5,100)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiChip(
                        label = "INTRADAY (ACTIVE)",
                        value = "₹${intraDetail.totalChargesFormatted}",
                        sub = "Net +₹${String.format(Locale.US, "%.2f", intraDetail.netProfitAfterCharges)}",
                        valueColor = EmeraldBright,
                        modifier = Modifier.weight(1f)
                    )
                    KpiChip(
                        label = "DELIVERY (+₹20 DP)",
                        value = "₹${delDetail.totalChargesFormatted}",
                        sub = "Net +₹${String.format(Locale.US, "%.2f", delDetail.netProfitAfterCharges)}",
                        valueColor = CyanTerminal,
                        modifier = Modifier.weight(1f)
                    )
                    KpiChip(
                        label = "OPTIONS (₹40 BROK)",
                        value = "₹${optDetail.totalChargesFormatted}",
                        sub = "Net +₹${String.format(Locale.US, "%.2f", optDetail.netProfitAfterCharges)}",
                        valueColor = AmberAlert,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = TerminalBorder, thickness = 1.dp)

            // API Key Access Limit Guard Row ("api key acess limit katham ho ga to book nahi karega")
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
                        imageVector = Icons.Default.Shield,
                        contentDescription = "API Limit Booking Guard",
                        tint = if (diagnostic.isApiQuotaExhausted) AmberAlert else EmeraldBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = if (diagnostic.isApiQuotaExhausted) {
                                "API Key Access Limit Exhausted • Auto-Book Paused"
                            } else {
                                "API Key Access Active • +₹25 Auto-Book Armed"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (diagnostic.isApiQuotaExhausted) AmberAlert else EmeraldBright
                        )
                        Text(
                            text = "If API key access limit ends, positions hold without auto-booking until restored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = diagnostic.isApiQuotaExhausted,
                    onCheckedChange = onToggleApiQuotaLimit,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AmberAlert,
                        checkedTrackColor = AmberContainer,
                        uncheckedThumbColor = EmeraldBright,
                        uncheckedTrackColor = EmeraldContainer
                    ),
                    modifier = Modifier.testTag("api_quota_guard_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRunAiOptimization,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanTerminal,
                        contentColor = TerminalSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ai_self_optimize_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Run AI 4H Self-Modify",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "AI 4H Self-Modify & Optimize (99%)",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (diagnostic.isApiQuotaExhausted) {
                    OutlinedButton(
                        onClick = { onToggleApiQuotaLimit(false) },
                        border = BorderStroke(1.dp, EmeraldBright),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldBright),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("restore_api_quota_button")
                    ) {
                        Text("Restore API")
                    }
                }
            }
        }
    }
}


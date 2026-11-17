package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AccountSummary
import com.example.domain.model.RiskConfig
import com.example.domain.model.TargetProfitMode
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.CyanTerminal
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.TerminalDivider
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.Formatters
import java.util.Locale

@Composable
fun TodaysProfitSummaryBar(
    accountSummary: AccountSummary,
    riskConfig: RiskConfig,
    onNavigateToTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val netProfit = accountSummary.totalDayPnl
    val isProfit = netProfit >= 0.0
    val profitColor = if (isProfit) EmeraldProfit else CrimsonLoss
    val accentBorder = if (isProfit) EmeraldProfit.copy(alpha = 0.45f) else CrimsonLoss.copy(alpha = 0.45f)
    val bgTint = if (isProfit) EmeraldContainer.copy(alpha = 0.16f) else CrimsonContainer.copy(alpha = 0.16f)

    val cycleWord = if (accountSummary.basketDropsCompleted == 1) "Cycle" else "Cycles"
    val bookedStr = Formatters.formatInr(accountSummary.basketBookedProfitInr)

    Surface(
        onClick = onNavigateToTransactions,
        color = TerminalSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("todays_profit_summary_bar")
    ) {
        Surface(
            color = bgTint,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Today's Net Profit",
                            tint = profitColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Today's Net Profit",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = Formatters.formatInr(netProfit, showSign = true),
                            style = MonoTabularStyle.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = profitColor,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Transaction History",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = TerminalDivider.copy(alpha = 0.65f), thickness = 1.dp)

                val basketDropsLabel = if (riskConfig.targetProfitMode == TargetProfitMode.PERCENTAGE) {
                    val pctText = if ((riskConfig.targetProfitPct * 100).toInt() % 10 == 0) {
                        String.format(Locale.US, "%.1f", riskConfig.targetProfitPct)
                    } else {
                        String.format(Locale.US, "%.2f", riskConfig.targetProfitPct)
                    }
                    "$pctText% BASKET DROPS"
                } else {
                    "₹${riskConfig.targetProfitInr.toInt()} BASKET DROPS"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    SummaryStatPill(
                        label = basketDropsLabel,
                        value = "${accountSummary.basketDropsCompleted} $cycleWord",
                        subValue = "$bookedStr Booked",
                        valueColor = if (accountSummary.basketDropsCompleted > 0) EmeraldBright else TextSecondary,
                        modifier = Modifier.weight(1.15f)
                    )
                    SummaryStatPill(
                        label = "TRADES TODAY",
                        value = "${accountSummary.totalTradesExecutedToday} Execs",
                        subValue = "${accountSummary.totalClosedTrades} Closed",
                        valueColor = CyanTerminal,
                        modifier = Modifier.weight(0.85f)
                    )
                    SummaryStatPill(
                        label = "INVESTED TODAY",
                        value = Formatters.formatInr(accountSummary.totalCapitalInvestedToday),
                        subValue = "Total Volume",
                        valueColor = TextPrimary,
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    subValue: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
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
            style = MonoTabularStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subValue,
            style = MonoTabularStyle.copy(fontSize = 10.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

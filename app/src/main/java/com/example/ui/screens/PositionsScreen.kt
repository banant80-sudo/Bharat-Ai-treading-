package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AccountSummary
import com.example.domain.model.ActivePosition
import com.example.domain.model.BasketExitScope
import com.example.domain.model.BasketHighWaterState
import com.example.domain.model.RiskConfig
import com.example.domain.model.StopLossMode
import com.example.domain.model.TargetProfitMode
import com.example.domain.model.TradeSide
import com.example.ui.components.BasketProfitProgressCard
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
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
import com.example.util.Formatters
import java.util.Locale

@Composable
fun PositionsScreen(
    positions: List<ActivePosition>,
    accountSummary: AccountSummary,
    riskConfig: RiskConfig,
    autoTradingEnabled: Boolean,
    buyBasketCooldownRemainingSec: Int,
    sellBasketCooldownRemainingSec: Int,
    buyBasketHighWater: BasketHighWaterState = BasketHighWaterState(),
    sellBasketHighWater: BasketHighWaterState = BasketHighWaterState(),
    onBookBasketProfitNow: (TradeSide) -> Unit = {},
    onBookAllCombinedProfitNow: () -> Unit = {},
    onSquareOffPosition: (String) -> Unit,
    onSquareOffAll: () -> Unit,
    onWithdrawProfit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "basket_progress_positions") {
            BasketProfitProgressCard(
                positions = positions,
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

        item(key = "profit_withdraw_wallet_card") {
            ProfitOnlyWalletCard(
                accountSummary = accountSummary,
                onWithdrawProfit = onWithdrawProfit
            )
        }

        item(key = "positions_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active Positions (${positions.size}/${riskConfig.maxOpenPositions})",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    val targetSummary = "Zero-Wait +₹${riskConfig.singleTradeInstantBookInr.toInt()} Trade / +₹${riskConfig.basketInstantBookInr.toInt()} Basket"
                    Text(
                        text = if (riskConfig.basketExitScope == BasketExitScope.COMBINED_BASKET) {
                            "$targetSummary • BE +₹3 charge shield"
                        } else {
                            "Per-stock Zero-Wait +₹${riskConfig.singleTradeInstantBookInr.toInt()} & SL monitor"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (positions.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onSquareOffAll,
                        border = BorderStroke(1.dp, CrimsonLoss),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBright),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("square_off_all_button")
                    ) {
                        Text("Square-Off All", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (positions.isEmpty()) {
            item(key = "empty_positions") {
                Surface(
                    color = TerminalSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TerminalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = TerminalSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LayersClear,
                                    contentDescription = null,
                                    tint = CyanTerminal,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = "No Open Positions",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Arm the Synchronized Pair AI Auto-Scalper in Scanner or tap BUY / SELL on any affordable NSE, BSE, or MCX ticker to track live zero-wait auto-books.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(positions, key = { it.id }) { pos ->
                ActivePositionCard(
                    position = pos,
                    riskConfig = riskConfig,
                    onSquareOff = { onSquareOffPosition(pos.id) }
                )
            }
        }

        item(key = "positions_bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActivePositionCard(
    position: ActivePosition,
    riskConfig: RiskConfig,
    onSquareOff: () -> Unit
) {
    val netPnl = position.netUnrealizedPnl
    val isProfit = netPnl >= 0.0
    val pnlColor = if (isProfit) EmeraldProfit else CrimsonLoss
    val sideBg = if (position.side == TradeSide.LONG) EmeraldContainer else CrimsonContainer
    val sideFg = if (position.side == TradeSide.LONG) EmeraldBright else CrimsonBright

    Surface(
        color = TerminalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, pnlColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("position_card_${position.symbol}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            text = "${position.symbol} • ${position.exchange.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Surface(color = sideBg, shape = RoundedCornerShape(5.dp)) {
                            Text(
                                text = position.side.smartApiTransactionType,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sideFg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (position.lockedProfitFloorInr != null) {
                            Surface(color = EmeraldContainer, shape = RoundedCornerShape(5.dp)) {
                                Text(
                                    text = "LOCKED +₹${position.lockedProfitFloorInr.toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldBright,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        } else if (position.isBreakEvenLocked) {
                            Surface(color = EmeraldContainer, shape = RoundedCornerShape(5.dp)) {
                                Text(
                                    text = "BE +₹3 LOCKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${position.displayName} • ${position.openedAtFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Entry ${Formatters.formatPrice(position.entryPrice)} → LTP ${Formatters.formatPrice(position.currentPrice)}",
                        style = MonoTabularStyle.copy(fontSize = 11.sp),
                        color = CyanTerminal
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatInr(netPnl, showSign = true),
                        style = MonoTabularStyle.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = pnlColor
                    )
                    Text(
                        text = "Net (${Formatters.formatPct(position.unrealizedPnlPct)})",
                        style = MonoTabularStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = pnlColor
                    )
                    Text(
                        text = "Gross ${Formatters.formatInr(position.grossUnrealizedPnl, showSign = true)}",
                        style = MonoTabularStyle.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                    Text(
                        text = "Charges ₹${String.format(Locale.US, "%.2f", position.estimatedChargesInr)}",
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
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val qtyText = Formatters.formatQuantity(position.quantity, position.exchange)
                PositionStatCell("INVESTED AMOUNT", Formatters.formatInr(position.investedCapital))
                PositionStatCell("QUANTITY", qtyText)
                PositionStatCell("ENTRY → LTP", "${Formatters.formatPrice(position.entryPrice)} → ${Formatters.formatPrice(position.currentPrice)}")
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val slLabel = if (position.stopLossMode == StopLossMode.FIXED_INR) {
                    "SL -${Formatters.formatInr(position.stopLossInr)}"
                } else {
                    "SL -${position.stopLossPct}%"
                }
                val tpLabel = if (riskConfig.basketExitScope == BasketExitScope.COMBINED_BASKET) {
                    "Zero-Wait +₹${riskConfig.singleTradeInstantBookInr.toInt()} / Basket +₹${riskConfig.basketInstantBookInr.toInt()}"
                } else if (position.targetProfitMode == TargetProfitMode.FIXED_INR) {
                    "Auto-Drop +${Formatters.formatInr(position.targetProfitInr)} Net"
                } else {
                    "Auto-Drop +${position.targetProfitPct}%"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when {
                            position.lockedProfitFloorInr != null ->
                                "Profit Lock +₹${position.lockedProfitFloorInr.toInt()} (${Formatters.formatPrice(position.stopLossPrice)})"
                            position.isBreakEvenLocked ->
                                "BE +₹3 Net Lock (${Formatters.formatPrice(position.breakEvenPrice)})"
                            else ->
                                "$slLabel (${Formatters.formatPrice(position.stopLossPrice)})"
                        },
                        style = MonoTabularStyle.copy(fontSize = 11.sp),
                        color = if (position.isBreakEvenLocked || position.lockedProfitFloorInr != null) EmeraldBright else CrimsonBright
                    )
                    Text(
                        text = if (riskConfig.basketExitScope == BasketExitScope.COMBINED_BASKET) tpLabel else "$tpLabel (${Formatters.formatPrice(position.targetExitPrice)})",
                        style = MonoTabularStyle.copy(fontSize = 11.sp),
                        color = EmeraldBright
                    )
                }

                val progress = position.normalizedTargetProgress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TerminalSurfaceHighlight)
                ) {
                    val fillFraction = kotlin.math.abs(progress).coerceIn(0.03f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fillFraction)
                            .align(if (progress >= 0f) Alignment.CenterStart else Alignment.CenterEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (progress >= 0f) EmeraldProfit else CrimsonLoss)
                    )
                }
            }

            Button(
                onClick = onSquareOff,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonContainer,
                    contentColor = CrimsonBright
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("square_off_button_${position.symbol}")
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "Instant Square-Off",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ProfitOnlyWalletCard(
    accountSummary: AccountSummary,
    onWithdrawProfit: () -> Unit
) {
    val withdrawable = accountSummary.withdrawableProfitInr
    val canWithdraw = withdrawable > 0.0
    Surface(
        color = TerminalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (canWithdraw) EmeraldProfit.copy(alpha = 0.6f) else TerminalBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profit_only_wallet_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = "Profit-Only Withdrawal Wallet",
                        tint = EmeraldBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Profit-Only Bank Withdrawal",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Base ₹5,00,000 capital stays locked • Only Net Profit withdraws",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                if (accountSummary.dailyProfitFloorInr != null) {
                    Surface(color = EmeraldContainer, shape = RoundedCornerShape(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Daily Profit Shield",
                                tint = EmeraldBright,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Floor +₹${accountSummary.dailyProfitFloorInr.toInt()}",
                                style = MonoTabularStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = EmeraldBright
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PositionStatCell("BASE PRINCIPAL", "₹5,00,000")
                PositionStatCell("WITHDRAWABLE PROFIT", Formatters.formatInr(withdrawable))
                PositionStatCell("TOTAL WITHDRAWN", Formatters.formatInr(accountSummary.withdrawnProfitInr))
            }

            Button(
                onClick = onWithdrawProfit,
                enabled = canWithdraw,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldProfit,
                    contentColor = TerminalSurface,
                    disabledContainerColor = TerminalSurfaceHighlight,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("withdraw_profit_only_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = if (canWithdraw) {
                        "Withdraw Net Profit Only (${Formatters.formatInr(withdrawable)})"
                    } else {
                        "Earn Net Profit > ₹0.00 to Withdraw"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PositionStatCell(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = value,
            style = MonoTabularStyle.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextPrimary
        )
    }
}

package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AccountSummary
import com.example.domain.model.ActivePosition
import com.example.domain.model.BasketExitScope
import com.example.domain.model.BasketHighWaterState
import com.example.domain.model.RiskConfig
import com.example.domain.model.TargetProfitMode
import com.example.domain.model.TradeSide
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
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TerminalSurfaceHighlight
import com.example.ui.theme.TerminalVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.Formatters
import java.util.Locale
import kotlin.math.abs

@Composable
fun BasketProfitProgressCard(
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
    modifier: Modifier = Modifier
) {
    val buyPositions = positions.filter { it.side == TradeSide.LONG }
    val sellPositions = positions.filter { it.side == TradeSide.SHORT }

    val singleDirectionOnly = riskConfig.singleDirectionOnly
    val activeSideLabel = when {
        buyPositions.isNotEmpty() && sellPositions.isEmpty() -> "BUY"
        sellPositions.isNotEmpty() && buyPositions.isEmpty() -> "SELL"
        else -> null
    }

    val pairWaitForBuy = !singleDirectionOnly && buyPositions.isEmpty() && sellPositions.isNotEmpty()
    val pairWaitForSell = !singleDirectionOnly && sellPositions.isEmpty() && buyPositions.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dual_basket_progress_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MasterCombinedDualBasketCard(
            buyPositions = buyPositions,
            sellPositions = sellPositions,
            masterTarget = riskConfig.basketInstantBookInr,
            singleDirectionOnly = singleDirectionOnly,
            minAiConfidence = riskConfig.minAiConfidence,
            onBookAllCombinedProfitNow = onBookAllCombinedProfitNow
        )

        SingleSideBasketCard(
            sideLabel = "BUY Combined Basket",
            sideBadge = "BUY (LONG)",
            isBuySide = true,
            positions = buyPositions,
            riskConfig = riskConfig,
            autoTradingEnabled = autoTradingEnabled,
            cooldownRemainingSec = buyBasketCooldownRemainingSec,
            highWaterState = buyBasketHighWater,
            lockedByOtherSideLabel = when {
                singleDirectionOnly && activeSideLabel == "SELL" -> "SELL"
                pairWaitForBuy -> "Booked +₹25 • Holding SELL for profit"
                else -> null
            },
            onBookProfitNow = { onBookBasketProfitNow(TradeSide.LONG) },
            testTagName = "buy_basket_progress_card"
        )

        SingleSideBasketCard(
            sideLabel = "SELL Combined Basket",
            sideBadge = "SELL (SHORT)",
            isBuySide = false,
            positions = sellPositions,
            riskConfig = riskConfig,
            autoTradingEnabled = autoTradingEnabled,
            cooldownRemainingSec = sellBasketCooldownRemainingSec,
            highWaterState = sellBasketHighWater,
            lockedByOtherSideLabel = when {
                singleDirectionOnly && activeSideLabel == "BUY" -> "BUY"
                pairWaitForSell -> "Booked +₹25 • Holding BUY for profit"
                else -> null
            },
            onBookProfitNow = { onBookBasketProfitNow(TradeSide.SHORT) },
            testTagName = "sell_basket_progress_card"
        )
    }
}

@Composable
private fun MasterCombinedDualBasketCard(
    buyPositions: List<ActivePosition>,
    sellPositions: List<ActivePosition>,
    masterTarget: Double,
    singleDirectionOnly: Boolean,
    minAiConfidence: Int,
    onBookAllCombinedProfitNow: () -> Unit
) {
    val allPositions = buyPositions + sellPositions
    val buyNetPnl = buyPositions.sumOf { it.netUnrealizedPnl }
    val sellNetPnl = sellPositions.sumOf { it.netUnrealizedPnl }
    val combinedNetPnl = buyNetPnl + sellNetPnl
    val rawFraction = if (allPositions.isEmpty()) 0f else (abs(combinedNetPnl) / masterTarget).toFloat().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (allPositions.isEmpty()) 0f else rawFraction.coerceAtLeast(0.03f),
        label = "master_dual_basket_progress"
    )
    val isPositive = combinedNetPnl >= 0.0
    val statusColor = when {
        allPositions.isEmpty() -> TextSecondary
        isPositive -> EmeraldProfit
        else -> CrimsonLoss
    }
    val pctToTarget = if (allPositions.isNotEmpty() && combinedNetPnl > 0.0) {
        ((combinedNetPnl / masterTarget) * 100.0).coerceIn(0.0, 100.0)
    } else {
        0.0
    }

    Surface(
        color = TerminalSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (allPositions.isNotEmpty() && isPositive) 1.5.dp else 1.dp,
            color = when {
                allPositions.isEmpty() -> CyanTerminal.copy(alpha = 0.35f)
                isPositive -> EmeraldProfit.copy(alpha = 0.7f)
                else -> CrimsonLoss.copy(alpha = 0.55f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_dual_basket_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (isPositive && allPositions.isNotEmpty()) EmeraldBright else CyanTerminal,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (singleDirectionOnly) "Active AI Trend Basket" else "Synchronized Pair (BUY+SELL)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(color = CyanContainer, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            text = if (singleDirectionOnly) "1-DIR +₹${masterTarget.toInt()}" else "PAIR +₹${masterTarget.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanTerminal,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(
                    color = if (allPositions.isNotEmpty()) EmeraldContainer.copy(alpha = 0.7f) else TerminalSurfaceHighlight,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${allPositions.size} Open",
                        style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = if (allPositions.isNotEmpty()) EmeraldBright else TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (allPositions.isNotEmpty()) {
                        "${Formatters.formatInr(combinedNetPnl, showSign = true)} / +₹${String.format(Locale.US, "%.2f", masterTarget)} Net"
                    } else {
                        "₹0.00 / +₹${String.format(Locale.US, "%.2f", masterTarget)} Zero-Wait Net"
                    },
                    style = MonoTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val directionStatus = when {
                    singleDirectionOnly && buyPositions.isNotEmpty() && sellPositions.isEmpty() ->
                        "Locked Side: BUY (LONG) Only • SELL blocked until exit"
                    singleDirectionOnly && sellPositions.isNotEmpty() && buyPositions.isEmpty() ->
                        "Locked Side: SELL (SHORT) Only • BUY blocked until exit"
                    singleDirectionOnly ->
                        "Standby • Picks Highest ≥${minAiConfidence}% AI Side (BUY or SELL)"
                    allPositions.isEmpty() ->
                        "Standby • Enters 1 BUY + 1 SELL together at ≥${minAiConfidence}% AI"
                    allPositions.size == 1 && buyPositions.isNotEmpty() ->
                        "SELL Booked • Holding BUY (${Formatters.formatInr(buyNetPnl, showSign = true)}) to recover & book +₹${masterTarget.toInt()}"
                    allPositions.size == 1 && sellPositions.isNotEmpty() ->
                        "BUY Booked • Holding SELL (${Formatters.formatInr(sellNetPnl, showSign = true)}) to recover & book +₹${masterTarget.toInt()}"
                    else ->
                        "BUY Net ${Formatters.formatInr(buyNetPnl, showSign = true)}  •  SELL Net ${Formatters.formatInr(sellNetPnl, showSign = true)}"
                }
                Text(
                    text = directionStatus,
                    style = MonoTabularStyle.copy(fontSize = 11.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalSurfaceHighlight)
            ) {
                if (allPositions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFraction)
                            .align(if (isPositive) Alignment.CenterStart else Alignment.CenterEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor)
                    )
                }
            }

            Text(
                text = when {
                    allPositions.isEmpty() && singleDirectionOnly ->
                        "Single-Direction Zero-Wait: opens ONLY BUY or SELL (+₹${String.format(Locale.US, "%.2f", masterTarget)} Net Auto-Book)"
                    allPositions.isEmpty() ->
                        "Pair Rule: Combined BUY+SELL ≥ +₹${masterTarget.toInt()} books BOTH • Single leg ≥ +₹${masterTarget.toInt()} books & holds other leg for profit"
                    allPositions.size == 1 ->
                        "Holding remaining leg to recover & book +₹${String.format(Locale.US, "%.2f", masterTarget)} Net (next pair locked until 0 Open)"
                    else ->
                        "${String.format(Locale.US, "%.0f", pctToTarget)}% to Combined Pair +₹${String.format(Locale.US, "%.2f", masterTarget)} Auto-Book (or +₹${masterTarget.toInt()} single leg)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (allPositions.isNotEmpty() && combinedNetPnl > 0.0) {
                Button(
                    onClick = onBookAllCombinedProfitNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldProfit,
                        contentColor = TerminalVoid
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("book_all_combined_profit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BOOK ALL NET PROFIT NOW (${Formatters.formatInr(combinedNetPnl, showSign = true)})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleSideBasketCard(
    sideLabel: String,
    sideBadge: String,
    isBuySide: Boolean,
    positions: List<ActivePosition>,
    riskConfig: RiskConfig,
    autoTradingEnabled: Boolean,
    cooldownRemainingSec: Int,
    highWaterState: BasketHighWaterState,
    lockedByOtherSideLabel: String?,
    onBookProfitNow: () -> Unit,
    testTagName: String
) {
    val combinedNetPnl = positions.sumOf { it.netUnrealizedPnl }
    val totalInvested = positions.sumOf { it.investedCapital }
    val totalCharges = positions.sumOf { it.estimatedChargesInr }

    val targetInr = if (positions.isNotEmpty()) {
        riskConfig.computeEffectiveSingleBasketTargetInr(totalInvested)
    } else {
        riskConfig.computeEffectiveSingleBasketTargetInr(riskConfig.effectiveCapitalPerTrade)
    }

    val targetBadgeLabel = if (riskConfig.targetProfitMode == TargetProfitMode.PERCENTAGE && targetInr < riskConfig.basketInstantBookInr) {
        val pctText = if ((riskConfig.targetProfitPct * 100).toInt() % 10 == 0) {
            String.format(Locale.US, "%.1f", riskConfig.targetProfitPct)
        } else {
            String.format(Locale.US, "%.2f", riskConfig.targetProfitPct)
        }
        "+${Formatters.formatInr(targetInr)} ($pctText%)"
    } else {
        "+${Formatters.formatInr(targetInr)}"
    }

    val isPositive = combinedNetPnl >= 0.0
    val denominator = if (isPositive) targetInr.coerceAtLeast(1.0) else riskConfig.basketMaxLossInr.coerceAtLeast(1.0)
    val rawFraction = (abs(combinedNetPnl) / denominator).toFloat().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (positions.isEmpty()) 0f else rawFraction.coerceAtLeast(0.03f),
        label = "basket_progress_$testTagName"
    )
    val barColor = if (isPositive) EmeraldProfit else CrimsonLoss
    val sideBadgeBg = if (isBuySide) EmeraldContainer else CrimsonContainer
    val sideBadgeFg = if (isBuySide) EmeraldBright else CrimsonBright
    val borderColor = when {
        cooldownRemainingSec > 0 -> AmberAlert.copy(alpha = 0.65f)
        positions.isNotEmpty() -> barColor.copy(alpha = 0.55f)
        else -> TerminalBorder
    }

    Surface(
        color = TerminalSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTagName)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isBuySide) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = sideBadgeFg,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = sideLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Surface(color = sideBadgeBg, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            text = sideBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = sideBadgeFg,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                if (cooldownRemainingSec > 0) {
                    Surface(color = AmberContainer, shape = RoundedCornerShape(6.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = AmberAlert,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Re-entry in ${cooldownRemainingSec}s",
                                style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = AmberAlert
                            )
                        }
                    }
                } else {
                    Surface(
                        color = if (positions.isNotEmpty()) sideBadgeBg.copy(alpha = 0.7f) else TerminalSurfaceHighlight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${positions.size} Open",
                            style = MonoTabularStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = if (positions.isNotEmpty()) sideBadgeFg else TextSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        positions.isEmpty() -> "₹0.00 / $targetBadgeLabel • -₹${riskConfig.basketMaxLossInr.toInt()} SL"
                        isPositive -> "${Formatters.formatInr(combinedNetPnl, showSign = true)} / $targetBadgeLabel Net"
                        else -> "${Formatters.formatInr(combinedNetPnl, showSign = true)} / -₹${riskConfig.basketMaxLossInr.toInt()} Max SL"
                    },
                    style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = if (positions.isEmpty()) TextSecondary else barColor
                )
                if (positions.isNotEmpty()) {
                    Text(
                        text = "Inv ${Formatters.formatInr(totalInvested)} • Chg ₹${String.format(Locale.US, "%.2f", totalCharges)}",
                        style = MonoTabularStyle.copy(fontSize = 10.sp),
                        color = TextMuted
                    )
                } else {
                    Text(
                        text = when {
                            cooldownRemainingSec > 0 -> "Auto-drop booked!"
                            lockedByOtherSideLabel != null -> if (
                                lockedByOtherSideLabel.startsWith("Pair Lock") ||
                                lockedByOtherSideLabel.startsWith("Booked")
                            ) {
                                lockedByOtherSideLabel
                            } else {
                                "Locked ($lockedByOtherSideLabel active)"
                            }
                            autoTradingEnabled -> if (isBuySide) "Scanning STRONG BUY…" else "Scanning STRONG SELL…"
                            else -> "Idle"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lockedByOtherSideLabel != null) AmberAlert else TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalSurfaceHighlight)
            ) {
                if (positions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFraction)
                            .align(if (isPositive) Alignment.CenterStart else Alignment.CenterEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
            }

            if (riskConfig.basketExitScope == BasketExitScope.COMBINED_BASKET) {
                val pctComplete = if (positions.isNotEmpty() && combinedNetPnl > 0.0) {
                    ((combinedNetPnl / targetInr.coerceAtLeast(1.0)) * 100.0).coerceIn(0.0, 100.0)
                } else {
                    0.0
                }
                val directionName = if (isBuySide) "BUY" else "SELL"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (highWaterState.isFloorLocked && riskConfig.highProbabilityProfitLockEnabled) {
                            Icons.Default.Lock
                        } else {
                            Icons.Default.Bolt
                        },
                        contentDescription = null,
                        tint = when {
                            cooldownRemainingSec > 0 -> AmberAlert
                            highWaterState.isFloorLocked && riskConfig.highProbabilityProfitLockEnabled -> EmeraldBright
                            else -> CyanTerminal
                        },
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = when {
                            cooldownRemainingSec > 0 ->
                                "$directionName basket exited • re-entry in ${cooldownRemainingSec}s"
                            positions.isNotEmpty() && !isPositive ->
                                "Basket Stop-Loss Guard: cuts $directionName losers at -₹${riskConfig.basketMaxLossInr.toInt()} (winner stays open)"
                            positions.isNotEmpty() && highWaterState.lockedFloorInr != null && riskConfig.highProbabilityProfitLockEnabled ->
                                "PROFIT GUARD ACTIVE: Floor locked at +₹${highWaterState.lockedFloorInr.toInt()} (Peak +₹${String.format(Locale.US, "%.0f", highWaterState.peakNetPnlInr)})"
                            else ->
                                "${String.format(Locale.US, "%.0f", pctComplete)}% to instant $directionName Square-Off (+${Formatters.formatInr(targetInr)} net)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            positions.isNotEmpty() && !isPositive -> CrimsonBright
                            highWaterState.isFloorLocked && riskConfig.highProbabilityProfitLockEnabled && positions.isNotEmpty() -> EmeraldBright
                            else -> TextSecondary
                        }
                    )
                }
            }

            if (positions.isNotEmpty() && combinedNetPnl > 0.0) {
                val buttonTag = if (isBuySide) "book_buy_basket_profit_button" else "book_sell_basket_profit_button"
                Button(
                    onClick = onBookProfitNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldProfit,
                        contentColor = TerminalVoid
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(buttonTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BOOK NET PROFIT NOW (${Formatters.formatInr(combinedNetPnl, showSign = true)})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

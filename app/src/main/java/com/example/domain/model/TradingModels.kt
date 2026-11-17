package com.example.domain.model

import com.example.util.AngelOneChargeCalculator
import kotlin.math.abs

enum class Exchange(val label: String) {
    NSE("NSE"),
    BSE("BSE"),
    MCX("MCX")
}

enum class AiTrendSignal(val label: String) {
    STRONG_BUY("Strong Buy"),
    BUY("Buy"),
    NEUTRAL("Neutral"),
    SELL("Sell"),
    STRONG_SELL("Strong Sell")
}

enum class RsiStatus(val label: String) {
    OVERSOLD("Oversold"),
    NEUTRAL("Neutral"),
    OVERBOUGHT("Overbought")
}

enum class TradeSide(val label: String, val smartApiTransactionType: String) {
    LONG("LONG", "BUY"),
    SHORT("SHORT", "SELL")
}

enum class TargetProfitMode(val label: String) {
    PERCENTAGE("Auto-Smart %"),
    FIXED_INR("Custom Fixed ₹")
}

enum class BasketExitScope(val label: String) {
    COMBINED_BASKET("Combined Basket (All Open)"),
    PER_STOCK("Per-Stock Target")
}

enum class StopLossMode(val label: String) {
    PERCENTAGE("Stop-Loss %"),
    FIXED_INR("Fixed ₹ Loss Cap")
}

enum class LogCategory(val badge: String) {
    AI_SIGNAL("AI 4H SIGNAL"),
    AI_SCREENER("AI SCREENER"),
    AI_OPTIMIZER("AI 4H COACH"),
    ORDER_EXECUTION("ORDER"),
    AUTO_PROFIT_EXIT("AUTO-BOOK"),
    BREAK_EVEN_EXIT("BE +₹3 LOCK"),
    STOP_LOSS_EXIT("STOP-LOSS"),
    MANUAL_EXIT("SQUARE-OFF"),
    MARKET_SCHEDULER("SCHEDULER"),
    BROKER_API("SMARTAPI"),
    RISK_GUARD("RISK GUARD"),
    CIRCUIT_BREAKER("CIRCUIT BREAKER")
}

enum class TerminalTab {
    SCANNER,
    POSITIONS,
    RISK,
    LOGS
}

data class MarketInstrument(
    val symbol: String,
    val tradingSymbol: String,
    val displayName: String,
    val exchange: Exchange,
    val symbolToken: String,
    val ltp: Double,
    val prevClose: Double,
    val rsi14: Double,
    val aiSignal: AiTrendSignal,
    val aiConfidence: Int,
    val priceHistory: List<Double>,
    val lastTickDelta: Double = 0.0,
    val lotSize: Int = 1,
    val cooldownRemainingSec: Int = 0,
    val vwap: Double = ltp,
    val cumulativePv: Double = ltp * 1000.0,
    val cumulativeVolume: Double = 1000.0,
    val avgTickVolume: Double = 1000.0,
    val latestTickVolume: Double = 1000.0,
    val volumeSurgeRatio: Double = 1.0,
    val intradayRangePct: Double = 0.80,
    val ema9: Double = ltp,
    val ema21: Double = ltp,
    val isPriceAboveVwap: Boolean = true,
    val isEmaBullish: Boolean = true,
    val strategyPassed: Boolean = false,
    val strategyReason: String = "Evaluating",
    val aiScreenerScore: Int = aiConfidence,
    val trendMomentumPass: Boolean = strategyPassed,
    val volumeLiquidityPass: Boolean = false,
    val feeClearancePass: Boolean = false,
    val aiScreenerVerdict: String = "SKIP",
    val aiScreenerReason: String = "AI Screener Evaluating",
    val oneMinCandlePattern: String = "1m Consolidating",
    val isOneMinCandleConfirmed: Boolean = false,
    val oneMinCandleStatusPill: String = "1m Candle • 99% GATE",
    val cloudAiScore: Int? = null,
    val cloudAiVerdict: String? = null,
    val cloudAiReason: String? = null
) {
    val changeAmount: Double get() = ltp - prevClose
    val changePct: Double get() = if (prevClose > 0.0) ((ltp - prevClose) / prevClose) * 100.0 else 0.0
    val rsiStatus: RsiStatus
        get() = when {
            rsi14 <= 34.0 -> RsiStatus.OVERSOLD
            rsi14 >= 66.0 -> RsiStatus.OVERBOUGHT
            else -> RsiStatus.NEUTRAL
        }

    val allScreenerChecksPassed: Boolean
        get() = strategyPassed && trendMomentumPass && volumeLiquidityPass && feeClearancePass
}

data class ActivePosition(
    val id: String,
    val symbol: String,
    val tradingSymbol: String,
    val displayName: String,
    val exchange: Exchange,
    val symbolToken: String,
    val side: TradeSide,
    val entryPrice: Double,
    val currentPrice: Double,
    val quantity: Double,
    val targetProfitMode: TargetProfitMode,
    val targetProfitInr: Double,
    val targetProfitPct: Double,
    val stopLossMode: StopLossMode,
    val stopLossInr: Double,
    val stopLossPct: Double,
    val isBreakEvenLocked: Boolean = false,
    val isZeroLossShieldLocked: Boolean = false,
    val lockedProfitFloorInr: Double? = null,
    val openedAtEpochMs: Long,
    val openedAtFormatted: String,
    val isPaperTrade: Boolean,
    val brokerOrderId: String
) {
    companion object {
        const val BREAK_EVEN_NET_BUFFER_INR = 3.0
        const val BREAK_EVEN_ARM_TRIGGER_INR = 8.5
        const val ZERO_WAIT_SINGLE_TRADE_TARGET_INR = 15.0
        const val ZERO_LOSS_SHIELD_TRIGGER_INR = 12.0
        const val ZERO_LOSS_SHIELD_FLOOR_INR = 5.0
        const val POSITION_PROFIT_LOCK_TRIGGER_INR = 20.0
        const val POSITION_PROFIT_LOCK_FLOOR_INR = 10.0
    }

    val investedCapital: Double get() = entryPrice * quantity

    val estimatedChargesInr: Double
        get() = AngelOneChargeCalculator.estimateRoundTripCharges(investedCapital, exchange).totalCharges

    val grossUnrealizedPnl: Double
        get() = when (side) {
            TradeSide.LONG -> (currentPrice - entryPrice) * quantity
            TradeSide.SHORT -> (entryPrice - currentPrice) * quantity
        }

    /** Net P&L after estimated brokerage, STT/CTT, exchange fees & GST */
    val netUnrealizedPnl: Double get() = grossUnrealizedPnl - estimatedChargesInr

    val unrealizedPnlPct: Double
        get() = if (investedCapital > 0.0) (netUnrealizedPnl / investedCapital) * 100.0 else 0.0

    val targetProfitAmountInr: Double
        get() = when (targetProfitMode) {
            TargetProfitMode.FIXED_INR -> targetProfitInr
            TargetProfitMode.PERCENTAGE -> investedCapital * (targetProfitPct / 100.0)
        }

    val stopLossAmountInr: Double
        get() = when (stopLossMode) {
            StopLossMode.FIXED_INR -> stopLossInr
            StopLossMode.PERCENTAGE -> investedCapital * (stopLossPct / 100.0)
        }

    /**
     * Charge-shielded Break-Even Price: Gross P&L >= RoundTripCharges + ₹3.00,
     * ensuring Break-Even exit NEVER closes in a net loss due to fees/taxes.
     */
    val breakEvenPrice: Double
        get() {
            val perUnitCover = if (quantity > 0.0) {
                (estimatedChargesInr + BREAK_EVEN_NET_BUFFER_INR) / quantity
            } else {
                0.0
            }
            return when (side) {
                TradeSide.LONG -> entryPrice + perUnitCover
                TradeSide.SHORT -> (entryPrice - perUnitCover).coerceAtLeast(0.01)
            }
        }

    /** Price corresponding to locked net profit floor (+₹10 net above charges once +₹20 net reached) */
    val profitLockFloorPrice: Double?
        get() {
            val floorNet = lockedProfitFloorInr ?: return null
            val perUnitDelta = if (quantity > 0.0) (floorNet + estimatedChargesInr) / quantity else 0.0
            return when (side) {
                TradeSide.LONG -> entryPrice + perUnitDelta
                TradeSide.SHORT -> (entryPrice - perUnitDelta).coerceAtLeast(0.01)
            }
        }

    val targetExitPrice: Double
        get() {
            val perShareDelta = if (quantity > 0.0) (targetProfitAmountInr + estimatedChargesInr) / quantity else 0.0
            return when (side) {
                TradeSide.LONG -> entryPrice + perShareDelta
                TradeSide.SHORT -> (entryPrice - perShareDelta).coerceAtLeast(0.01)
            }
        }

    val stopLossPrice: Double
        get() {
            profitLockFloorPrice?.let { return it }
            if (isBreakEvenLocked) return breakEvenPrice
            val perShareDelta = if (quantity > 0.0) (stopLossAmountInr - estimatedChargesInr).coerceAtLeast(0.0) / quantity else 0.0
            return when (side) {
                TradeSide.LONG -> (entryPrice - perShareDelta).coerceAtLeast(0.01)
                TradeSide.SHORT -> entryPrice + perShareDelta
            }
        }

    /** Normalized progress from -1.0 (stop-loss hit) to +1.0 (target profit hit) */
    val normalizedTargetProgress: Float
        get() = if (netUnrealizedPnl >= 0.0) {
            (netUnrealizedPnl / targetProfitAmountInr.coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
        } else {
            -(abs(netUnrealizedPnl) / stopLossAmountInr.coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
        }
}

data class BasketHighWaterState(
    val peakNetPnlInr: Double = 0.0,
    val lockedFloorInr: Double? = null
) {
    val isFloorLocked: Boolean get() = lockedFloorInr != null

    companion object {
        const val MASTER_DUAL_BASKET_TARGET_INR = 25.0
        const val BASKET_MAX_LOSS_STOP_INR = 80.0
        const val TIER1_TRIGGER_INR = 50.0
        const val TIER1_FLOOR_INR = 35.0
        const val TIER2_TRIGGER_INR = 80.0
        const val TIER2_FLOOR_INR = 60.0

        fun computeCombinedDualBasketNetPnl(positions: List<ActivePosition>): Double =
            positions.sumOf { it.netUnrealizedPnl }

        fun isMasterDualBasketTargetHit(
            positions: List<ActivePosition>,
            masterTargetInr: Double = MASTER_DUAL_BASKET_TARGET_INR
        ): Boolean {
            if (positions.isEmpty()) return false
            return computeCombinedDualBasketNetPnl(positions) >= (masterTargetInr - 1e-6)
        }

        fun shouldTriggerBasketStopLoss(
            basketNetPnl: Double,
            hasPositions: Boolean,
            maxLossInr: Double = BASKET_MAX_LOSS_STOP_INR
        ): Boolean {
            if (!hasPositions) return false
            return basketNetPnl <= (-maxLossInr + 1e-6)
        }

        fun advance(
            previous: BasketHighWaterState,
            currentNetPnl: Double,
            hasPositions: Boolean
        ): BasketHighWaterState {
            if (!hasPositions) return BasketHighWaterState()
            val newPeak = maxOf(previous.peakNetPnlInr, currentNetPnl)
            val newFloor = when {
                newPeak >= TIER2_TRIGGER_INR -> maxOf(previous.lockedFloorInr ?: 0.0, TIER2_FLOOR_INR)
                newPeak >= TIER1_TRIGGER_INR -> maxOf(previous.lockedFloorInr ?: 0.0, TIER1_FLOOR_INR)
                else -> previous.lockedFloorInr
            }
            return BasketHighWaterState(
                peakNetPnlInr = newPeak,
                lockedFloorInr = newFloor
            )
        }

        fun shouldTriggerFloorExit(
            state: BasketHighWaterState,
            currentNetPnl: Double
        ): Boolean {
            val floor = state.lockedFloorInr ?: return false
            return currentNetPnl <= (floor + 1e-6)
        }
    }
}

enum class TransactionStatus(val label: String) {
    OPEN("OPEN"),
    CLOSED("CLOSED")
}

data class TradeTransaction(
    val id: String,
    val symbol: String,
    val tradingSymbol: String,
    val displayName: String,
    val exchange: Exchange,
    val side: TradeSide,
    val quantity: Double,
    val entryPrice: Double,
    val exitOrCurrentPrice: Double,
    val investedAmountInr: Double,
    val grossPnlInr: Double,
    val estimatedChargesInr: Double,
    val netPnlInr: Double,
    val pnlPct: Double,
    val status: TransactionStatus,
    val entryTimeFormatted: String,
    val exitTimeFormatted: String? = null,
    val entryEpochMs: Long,
    val exitEpochMs: Long? = null,
    val exitReason: String? = null,
    val isPaperTrade: Boolean,
    val brokerOrderId: String
)

data class TradeLogEntry(
    val id: String,
    val timestampFormatted: String,
    val epochMs: Long,
    val category: LogCategory,
    val symbol: String,
    val exchange: Exchange?,
    val side: TradeSide?,
    val quantity: Double?,
    val price: Double?,
    val pnl: Double?,
    val pnlPct: Double?,
    val title: String,
    val detail: String,
    val isPaperTrade: Boolean
)

data class BrokerCredentials(
    val apiKey: String = "",
    val clientId: String = "",
    val mpin: String = "",
    val totpSecret: String = ""
) {
    val isCompleteForLive: Boolean
        get() = apiKey.isNotBlank() &&
            clientId.isNotBlank() &&
            mpin.isNotBlank() &&
            totpSecret.isNotBlank()
}

sealed class BrokerConnectionState {
    data object Disconnected : BrokerConnectionState()
    data object Connecting : BrokerConnectionState()
    data class Connected(
        val clientId: String,
        val jwtTokenPreview: String,
        val isPaperSession: Boolean,
        val lastSyncFormatted: String
    ) : BrokerConnectionState()

    data class Error(val message: String) : BrokerConnectionState()
}

data class RiskConfig(
    val smallAmountAutoSplitEnabled: Boolean = true,
    val smallAmountPerTrade: Double = 5000.0,
    val maxCapitalPerTrade: Double = 50000.0,
    val basketExitScope: BasketExitScope = BasketExitScope.COMBINED_BASKET,
    val targetProfitMode: TargetProfitMode = TargetProfitMode.PERCENTAGE,
    val targetProfitInr: Double = 300.0,
    val targetProfitPct: Double = 0.80,
    val singleTradeInstantBookInr: Double = ActivePosition.ZERO_WAIT_SINGLE_TRADE_TARGET_INR,
    val basketInstantBookInr: Double = BasketHighWaterState.MASTER_DUAL_BASKET_TARGET_INR,
    val stopLossMode: StopLossMode = StopLossMode.PERCENTAGE,
    val stopLossPct: Double = 0.75,
    val stopLossInr: Double = 150.0,
    val basketMaxLossInr: Double = BasketHighWaterState.BASKET_MAX_LOSS_STOP_INR,
    val highProbabilityProfitLockEnabled: Boolean = true,
    val singleDirectionOnly: Boolean = false,
    val allowSellAutoTrade: Boolean = false,
    val minAiConfidence: Int = 99,
    val maxOpenPositions: Int = 2,
    val rsiBuyThreshold: Double = 34.0,
    val rsiSellThreshold: Double = 66.0,
    val reentryCooldownSec: Int = 20,
    val enforceMarketHours: Boolean = false,
    val skipOpeningVolatilityEnabled: Boolean = true,
    val mcxExtendedHoursEnabled: Boolean = true,
    val autoStartAtMarketOpen: Boolean = true,
    val dailyMaxLossLimitInr: Double = 1500.0,
    val isPaperMode: Boolean = true
) {
    val effectiveCapitalPerTrade: Double
        get() = if (smallAmountAutoSplitEnabled) smallAmountPerTrade else maxCapitalPerTrade

    val maxPositionsPerBasket: Int
        get() = (maxOpenPositions / 2).coerceAtLeast(1)

    fun computeBasketTargetInr(basketInvestedCapital: Double): Double = when (targetProfitMode) {
        TargetProfitMode.FIXED_INR -> targetProfitInr
        TargetProfitMode.PERCENTAGE -> (basketInvestedCapital * (targetProfitPct / 100.0)).coerceAtLeast(1.0)
    }

    /** Zero-Wait single-basket auto-book ceiling at +₹25.00 (or lower if configured target is smaller) */
    fun computeEffectiveSingleBasketTargetInr(basketInvestedCapital: Double): Double =
        minOf(computeBasketTargetInr(basketInvestedCapital), basketInstantBookInr)
}

data class AccountSummary(
    val availableCash: Double = 500000.0,
    val usedMargin: Double = 0.0,
    val realizedDayPnl: Double = 0.0,
    val unrealizedDayPnl: Double = 0.0,
    val openBasketCharges: Double = 0.0,
    val totalDayPnl: Double = 0.0,
    val totalDayPnlPct: Double = 0.0,
    val totalEstimatedCharges: Double = 0.0,
    val winCount: Int = 0,
    val lossCount: Int = 0,
    val totalClosedTrades: Int = 0,
    val totalTradesExecutedToday: Int = 0,
    val totalCapitalInvestedToday: Double = 0.0,
    val basketDropsCompleted: Int = 0,
    val basketBookedProfitInr: Double = 0.0,
    val grossProfit: Double = 0.0,
    val grossLoss: Double = 0.0,
    val withdrawnProfitInr: Double = 0.0,
    val withdrawableProfitInr: Double = 0.0,
    val dailyProfitFloorInr: Double? = null,
    val equityCurve: List<Double> = listOf(500000.0)
) {
    val winRatePct: Double
        get() = if (totalClosedTrades > 0) (winCount.toDouble() / totalClosedTrades) * 100.0 else 0.0
    val profitFactor: Double
        get() = when {
            grossLoss > 0.0 -> grossProfit / grossLoss
            grossProfit > 0.0 -> 9.99
            else -> 0.0
        }
}

enum class GrowthDiagnosisStatus(val label: String) {
    GROWING_CONTINUE("GROWING • CONTINUE"),
    STAGNANT_SELF_MODIFIED("AUTO-MODIFIED FOR GROWTH"),
    API_LIMIT_HOLD("API LIMIT • BOOKING PAUSED");

    val badge: String
        get() = label
}

data class GeminiPerformanceDiagnostic(
    val status: GrowthDiagnosisStatus = GrowthDiagnosisStatus.GROWING_CONTINUE,
    val headline: String = "Portfolio Growing — Continuing Winning 99% Setup",
    val capitalInvestedSummary: String = "Kitna Invest Hua: ₹0.00 • Realized: ₹0.00 • Charges: ₹0.00",
    val totalInvestedInr: Double = 0.0,
    val realizedNetPnlInr: Double = 0.0,
    val unrealizedNetPnlInr: Double = 0.0,
    val totalChargesInr: Double = 0.0,
    val winRatePct: Double = 99.0,
    val targetSuccessRatePct: Int = 99,
    val whyLossOrSlowGrowthDiagnosis: String = "Evaluating live trade execution & brokerage drag…",
    val profitImprovementPlan: String = "Enforcing 99% 3-factor Trend + Volume Surge + Fee-Clearance gate.",
    val autoModifiedSummary: String = "Parameters optimal — continuing current growth configuration.",
    val recommendedAllocationInr: Double = 5000.0,
    val recommendedStopLossPct: Double = 0.75,
    val recommendedCooldownSec: Int = 20,
    val recommendedMinAiConfidence: Int = 99,
    val isApiQuotaExhausted: Boolean = false,
    val apiQuotaStatusMessage: String = "Gemini AI 4H & SmartAPI Access: Healthy (Auto-Book Active)",
    val lastAnalyzedAt: String = "Live",
    val lastEvaluatedAt: String = "Live",
    val selfModificationsApplied: Int = 0
)



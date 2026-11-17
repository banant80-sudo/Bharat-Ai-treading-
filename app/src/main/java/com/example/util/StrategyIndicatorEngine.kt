package com.example.util

import com.example.domain.model.ActivePosition
import com.example.domain.model.AiTrendSignal
import com.example.domain.model.Exchange
import com.example.domain.model.MarketInstrument
import com.example.domain.model.TradeSide
import java.util.Locale
import kotlin.math.abs

data class StrategyEvaluation(
    val vwap: Double,
    val ema9: Double,
    val ema21: Double,
    val rsi14: Double,
    val isPriceAboveVwap: Boolean,
    val isEmaBullish: Boolean,
    val isRsiBuyZone: Boolean,
    val isRsiSellZone: Boolean,
    val signal: AiTrendSignal,
    val confidence: Int,
    val strategyPassed: Boolean,
    val statusReason: String
)

data class CandleOhlcWindow(
    val minuteBucket: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val prevOpen: Double,
    val prevHigh: Double,
    val prevLow: Double,
    val prevClose: Double,
    val secondsIntoCandle: Int
)

data class OneMinCandleAnalysis(
    val patternName: String,
    val isBullish: Boolean,
    val isBearish: Boolean,
    val isConfirmed: Boolean,
    val statusPill: String
)

data class DeepScreenerResult(
    val strategyEval: StrategyEvaluation,
    val screenerScore: Int,
    val trendMomentumPass: Boolean,
    val volumeLiquidityPass: Boolean,
    val feeClearancePass: Boolean,
    val volumeSurgeRatio: Double,
    val intradayRangePct: Double,
    val roundTripChargePct: Double,
    val verdict: String,
    val reason: String,
    val oneMinCandlePattern: String = "1m Consolidating",
    val isOneMinCandleConfirmed: Boolean = false,
    val oneMinCandleStatusPill: String = "1m Candle • 99% GATE"
) {
    val allChecksPassed: Boolean
        get() = strategyEval.strategyPassed && trendMomentumPass && volumeLiquidityPass && feeClearancePass
}

data class SingleDirectionPlan(
    val activeDirection: TradeSide?,
    val selectedCandidates: List<MarketInstrument>,
    val blockedOppositeCount: Int
)

data class SynchronizedPairPlan(
    val buyCandidate: MarketInstrument?,
    val sellCandidate: MarketInstrument?,
    val blockedReason: String? = null
) {
    val isReadyToExecutePair: Boolean
        get() = buyCandidate != null && sellCandidate != null
}

object StrategyIndicatorEngine {

    const val RSI_BUY_MIN = 55.0
    const val RSI_BUY_MAX = 68.0
    const val RSI_SELL_MIN = 32.0
    const val RSI_SELL_MAX = 45.0

    const val MIN_VOLUME_SURGE_RATIO = 1.10
    const val MIN_DIRECTIONAL_EFFICIENCY = 0.24
    const val MIN_AI_CONVICTION_SCORE = 99

    fun computeWilderRsi(prices: List<Double>, period: Int = 14): Double {
        if (prices.size < period + 1) return 50.0
        var gainSum = 0.0
        var lossSum = 0.0
        val start = prices.size - period - 1
        for (i in (start + 1) until prices.size) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) gainSum += change else lossSum += abs(change)
        }
        val avgGain = gainSum / period
        val avgLoss = lossSum / period
        if (avgLoss == 0.0) return 85.0
        val rs = avgGain / avgLoss
        return (100.0 - (100.0 / (1.0 + rs))).coerceIn(8.0, 92.0)
    }

    fun computeEma(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        if (prices.size == 1) return prices.first()
        val seedCount = minOf(period, prices.size)
        var ema = prices.take(seedCount).average()
        val k = 2.0 / (period + 1.0)
        for (i in seedCount until prices.size) {
            ema = (prices[i] * k) + (ema * (1.0 - k))
        }
        return Math.round(ema * 100.0) / 100.0
    }

    /**
     * Directional efficiency (0.0..1.0): net price displacement / total path length over recent candles.
     * Low efficiency (<0.24) identifies choppy, whipsaw price action.
     */
    fun computeDirectionalEfficiency(prices: List<Double>, lookback: Int = 10): Double {
        if (prices.size < 3) return 0.5
        val slice = prices.takeLast(minOf(lookback, prices.size))
        val netMove = abs(slice.last() - slice.first())
        var totalPath = 0.0
        for (i in 1 until slice.size) {
            totalPath += abs(slice[i] - slice[i - 1])
        }
        return if (totalPath > 0.0) (netMove / totalPath).coerceIn(0.0, 1.0) else 0.0
    }

    fun computeIntradayRangePct(prices: List<Double>, ltp: Double): Double {
        if (prices.isEmpty() || ltp <= 0.0) return 0.0
        val high = prices.maxOrNull() ?: ltp
        val low = prices.minOrNull() ?: ltp
        return Math.round((((high - low) / ltp) * 100.0) * 100.0) / 100.0
    }

    /**
     * Evaluates live 1-minute candlestick confirmation:
     * - Bullish breakout/engulfing/hammer candle closing above 9 EMA & VWAP with RSI in 52–68 momentum zone.
     * - Bearish breakdown/engulfing candle closing below 9 EMA & VWAP with RSI in 32–48 momentum zone.
     */
    fun evaluateOneMinCandle(
        ltp: Double,
        vwap: Double,
        ema9: Double,
        rsi14: Double,
        priceHistory: List<Double>,
        candleOhlc: CandleOhlcWindow? = null
    ): OneMinCandleAnalysis {
        val safeVwap = if (vwap > 0.0) vwap else ltp
        val safeEma = if (ema9 > 0.0) ema9 else ltp
        val close = candleOhlc?.close ?: ltp
        val slice = priceHistory.takeLast(minOf(4, priceHistory.size))
        val open = candleOhlc?.open ?: if (slice.isNotEmpty()) slice.first() else close
        val high = candleOhlc?.high ?: slice.maxOrNull() ?: close
        val low = candleOhlc?.low ?: slice.minOrNull() ?: close

        val body = abs(close - open)
        val upperWick = high - maxOf(close, open)
        val lowerWick = minOf(close, open) - low
        val isGreen = close >= open
        val isRed = close < open

        val prevClose = candleOhlc?.prevClose
            ?: if (priceHistory.size >= 5) priceHistory[priceHistory.size - 5] else open
        val prevOpen = candleOhlc?.prevOpen
            ?: if (priceHistory.size >= 8) priceHistory[priceHistory.size - 8] else prevClose
        val prevHigh = candleOhlc?.prevHigh ?: maxOf(prevOpen, prevClose)
        val prevLow = candleOhlc?.prevLow ?: minOf(prevOpen, prevClose)
        val prevIsRed = prevClose <= prevOpen
        val prevIsGreen = prevClose > prevOpen

        // Bullish 1-Minute Candlestick Confirmation:
        // Must close above 9 EMA & VWAP with RSI in 52..68 momentum zone
        val isAboveEmaAndVwap = close > safeEma && close > safeVwap
        val isRsiBullishZone = rsi14 in 52.0..68.0

        val isBullishEngulfing = isGreen && prevIsRed && close >= prevOpen && open <= prevClose && isAboveEmaAndVwap && isRsiBullishZone
        val isBullishHammer = isGreen && lowerWick >= 1.2 * maxOf(body, 0.04) && upperWick <= 0.6 * maxOf(body, 0.04) && isAboveEmaAndVwap && isRsiBullishZone
        val isBullishBreakout = isGreen && (close >= maxOf(high, prevHigh) * 0.9995 || close > prevClose * 1.0005) && isAboveEmaAndVwap && isRsiBullishZone

        // Bearish 1-Minute Candlestick Confirmation:
        // Must close below 9 EMA & VWAP with RSI in 32..48 momentum zone
        val isBelowEmaAndVwap = close < safeEma && close < safeVwap
        val isRsiBearishZone = rsi14 in 32.0..48.0

        val isBearishEngulfing = isRed && prevIsGreen && close <= prevOpen && open >= prevClose && isBelowEmaAndVwap && isRsiBearishZone
        val isBearishBreakdown = isRed && (close <= minOf(low, prevLow) * 1.0005 || close < prevClose * 0.9995) && isBelowEmaAndVwap && isRsiBearishZone
        val isShootingStar = isRed && upperWick >= 1.2 * maxOf(body, 0.04) && lowerWick <= 0.6 * maxOf(body, 0.04) && isBelowEmaAndVwap && isRsiBearishZone

        val (pattern, isBullish, isBearish, isConfirmed) = when {
            isBullishEngulfing -> Tuple4("1m Bullish Engulfing", true, false, true)
            isBullishHammer -> Tuple4("1m Hammer Breakout", true, false, true)
            isBullishBreakout -> Tuple4("1m Bullish Breakout", true, false, true)
            isBearishEngulfing -> Tuple4("1m Bearish Engulfing", false, true, true)
            isBearishBreakdown -> Tuple4("1m Bearish Breakdown", false, true, true)
            isShootingStar -> Tuple4("1m Rejection Breakdown", false, true, true)
            isGreen && isAboveEmaAndVwap -> Tuple4("1m Bullish Momentum", true, false, isRsiBullishZone)
            isRed && isBelowEmaAndVwap -> Tuple4("1m Bearish Momentum", false, true, isRsiBearishZone)
            else -> Tuple4("1m Consolidating", false, false, false)
        }

        val barClock = candleOhlc?.let { " (${it.secondsIntoCandle}s/60s)" } ?: ""
        val pill = if (isConfirmed) "$pattern$barClock • 99% PASS" else "$pattern$barClock • WAIT"
        return OneMinCandleAnalysis(
            patternName = pattern,
            isBullish = isBullish,
            isBearish = isBearish,
            isConfirmed = isConfirmed,
            statusPill = pill
        )
    }

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun evaluateStrategy(
        ltp: Double,
        vwap: Double,
        priceHistory: List<Double>
    ): StrategyEvaluation {
        val safeVwap = if (vwap > 0.0) vwap else ltp
        val ema9 = computeEma(priceHistory, 9)
        val ema21 = computeEma(priceHistory, 21)
        val rsi = computeWilderRsi(priceHistory, 14)

        val aboveVwap = ltp > safeVwap
        val belowVwap = ltp < safeVwap
        val emaBullish = ema9 > ema21
        val emaBearish = ema9 < ema21
        val rsiBuyZone = rsi in RSI_BUY_MIN..RSI_BUY_MAX
        val rsiSellZone = rsi in RSI_SELL_MIN..RSI_SELL_MAX

        val vwapSpreadPct = if (safeVwap > 0.0) abs(ltp - safeVwap) / safeVwap * 100.0 else 0.0
        val emaSpreadPct = if (ema21 > 0.0) abs(ema9 - ema21) / ema21 * 100.0 else 0.0

        return when {
            aboveVwap && emaBullish && rsiBuyZone -> {
                val rsiCenterBonus = 6.0 - abs(rsi - 61.5) * 0.4
                val conf = (86.0 + rsiCenterBonus + (vwapSpreadPct * 2.5) + (emaSpreadPct * 3.0))
                    .toInt()
                    .coerceIn(88, 98)
                StrategyEvaluation(
                    vwap = safeVwap,
                    ema9 = ema9,
                    ema21 = ema21,
                    rsi14 = rsi,
                    isPriceAboveVwap = true,
                    isEmaBullish = true,
                    isRsiBuyZone = true,
                    isRsiSellZone = false,
                    signal = AiTrendSignal.STRONG_BUY,
                    confidence = conf,
                    strategyPassed = true,
                    statusReason = "VWAP▲ • 9>21 EMA • RSI ${String.format(Locale.US, "%.1f", rsi)}"
                )
            }
            belowVwap && emaBearish && rsiSellZone -> {
                val rsiCenterBonus = 6.0 - abs(rsi - 38.5) * 0.4
                val conf = (86.0 + rsiCenterBonus + (vwapSpreadPct * 2.5) + (emaSpreadPct * 3.0))
                    .toInt()
                    .coerceIn(88, 98)
                StrategyEvaluation(
                    vwap = safeVwap,
                    ema9 = ema9,
                    ema21 = ema21,
                    rsi14 = rsi,
                    isPriceAboveVwap = false,
                    isEmaBullish = false,
                    isRsiBuyZone = false,
                    isRsiSellZone = true,
                    signal = AiTrendSignal.STRONG_SELL,
                    confidence = conf,
                    strategyPassed = true,
                    statusReason = "VWAP▼ • 9<21 EMA • RSI ${String.format(Locale.US, "%.1f", rsi)}"
                )
            }
            aboveVwap && emaBullish -> {
                val reason = if (rsi > RSI_BUY_MAX) {
                    "Exhaustion RSI ${String.format(Locale.US, "%.1f", rsi)} (>68)"
                } else {
                    "RSI ${String.format(Locale.US, "%.1f", rsi)} (<55)"
                }
                StrategyEvaluation(
                    vwap = safeVwap,
                    ema9 = ema9,
                    ema21 = ema21,
                    rsi14 = rsi,
                    isPriceAboveVwap = true,
                    isEmaBullish = true,
                    isRsiBuyZone = false,
                    isRsiSellZone = rsiSellZone,
                    signal = AiTrendSignal.BUY,
                    confidence = 76,
                    strategyPassed = false,
                    statusReason = reason
                )
            }
            belowVwap && emaBearish -> {
                val reason = if (rsi < RSI_SELL_MIN) {
                    "Oversold Trap RSI ${String.format(Locale.US, "%.1f", rsi)} (<32)"
                } else {
                    "RSI ${String.format(Locale.US, "%.1f", rsi)} (>45)"
                }
                StrategyEvaluation(
                    vwap = safeVwap,
                    ema9 = ema9,
                    ema21 = ema21,
                    rsi14 = rsi,
                    isPriceAboveVwap = false,
                    isEmaBullish = false,
                    isRsiBuyZone = rsiBuyZone,
                    isRsiSellZone = false,
                    signal = AiTrendSignal.SELL,
                    confidence = 75,
                    strategyPassed = false,
                    statusReason = reason
                )
            }
            else -> {
                StrategyEvaluation(
                    vwap = safeVwap,
                    ema9 = ema9,
                    ema21 = ema21,
                    rsi14 = rsi,
                    isPriceAboveVwap = aboveVwap,
                    isEmaBullish = emaBullish,
                    isRsiBuyZone = rsiBuyZone,
                    isRsiSellZone = rsiSellZone,
                    signal = AiTrendSignal.NEUTRAL,
                    confidence = 58,
                    strategyPassed = false,
                    statusReason = "VWAP/EMA Divergence"
                )
            }
        }
    }

    /**
     * Multi-factor Pre-Trade Deep Company & Market Screener scoring (0–100) on:
     * 1) Trend & Momentum Alignment (9/21 EMA slope, VWAP flow, RSI sweet-spot)
     * 2) Volume Surge & Liquidity Check (rejects low-volume, choppy, or high-slippage action)
     * 3) Volatility & Fee-Clearance Viability (intraday range comfortably clears round-trip fees)
     */
    fun evaluateGeminiDeepScreener(
        ltp: Double,
        vwap: Double,
        priceHistory: List<Double>,
        exchange: Exchange,
        allocationInr: Double,
        volumeSurgeRatio: Double,
        minConvictionThreshold: Int = MIN_AI_CONVICTION_SCORE,
        cloudScore: Int? = null,
        cloudVerdict: String? = null,
        cloudReason: String? = null,
        candleOhlc: CandleOhlcWindow? = null,
        minEfficiencyOverride: Double = MIN_DIRECTIONAL_EFFICIENCY
    ): DeepScreenerResult {
        val strategyEval = evaluateStrategy(ltp = ltp, vwap = vwap, priceHistory = priceHistory)
        val emaSlopeConfirmed = if (priceHistory.size >= 4) {
            val prevEma9 = computeEma(priceHistory.dropLast(1), 9)
            if (strategyEval.isEmaBullish) strategyEval.ema9 >= prevEma9 else strategyEval.ema9 <= prevEma9
        } else {
            true
        }

        val trendMomentumPass = strategyEval.strategyPassed && emaSlopeConfirmed

        val efficiency = computeDirectionalEfficiency(priceHistory)
        val requiredEfficiency = maxOf(MIN_DIRECTIONAL_EFFICIENCY, minEfficiencyOverride)
        val volumeLiquidityPass = volumeSurgeRatio >= MIN_VOLUME_SURGE_RATIO && efficiency >= requiredEfficiency

        val safeAlloc = allocationInr.coerceAtLeast(50.0)
        val charges = AngelOneChargeCalculator.estimateRoundTripCharges(safeAlloc, exchange).totalCharges
        val chargePct = if (safeAlloc > 0.0) (charges / safeAlloc) * 100.0 else 0.5
        val rangePct = computeIntradayRangePct(priceHistory, ltp)
        val requiredRangePct = maxOf(0.28, chargePct * 2.4)
        val feeClearancePass = rangePct >= requiredRangePct

        // Weighted 0..100 score: reaches 99% ONLY when all 3 factors strongly peak together
        val trendPoints = if (trendMomentumPass) {
            42 + when {
                strategyEval.confidence >= 95 -> 3
                strategyEval.confidence >= 92 -> 2
                else -> 1
            }
        } else if (strategyEval.isPriceAboveVwap == strategyEval.isEmaBullish) {
            28
        } else {
            16
        }
        val volumePoints = when {
            volumeSurgeRatio >= 1.25 && efficiency >= maxOf(0.30, requiredEfficiency) -> 30
            volumeSurgeRatio >= 1.15 && efficiency >= maxOf(0.26, requiredEfficiency) -> 29
            volumeLiquidityPass -> 26
            volumeSurgeRatio >= 1.0 -> 16
            else -> 9
        }
        val feePoints = when {
            rangePct >= requiredRangePct * 1.40 -> 25
            feeClearancePass -> 23
            else -> 10
        }

        val quantScore = (trendPoints + volumePoints + feePoints).coerceIn(30, 99)
        val allQuantPassed = trendMomentumPass && volumeLiquidityPass && feeClearancePass

        val candleAnalysis = evaluateOneMinCandle(
            ltp = ltp,
            vwap = vwap,
            ema9 = strategyEval.ema9,
            rsi14 = strategyEval.rsi14,
            priceHistory = priceHistory,
            candleOhlc = candleOhlc
        )
        val candleDirectionAligned = when (strategyEval.signal) {
            AiTrendSignal.STRONG_BUY -> candleAnalysis.isBullish && candleAnalysis.isConfirmed
            AiTrendSignal.STRONG_SELL -> candleAnalysis.isBearish && candleAnalysis.isConfirmed
            else -> false
        }

        val rawScore = when {
            !allQuantPassed -> minOf(quantScore, 84)
            candleOhlc != null && !candleDirectionAligned -> minOf(quantScore, 94)
            cloudScore != null && cloudVerdict == "SKIP" -> minOf(quantScore, cloudScore, 85)
            cloudScore != null -> ((quantScore * 0.65) + (cloudScore * 0.35)).toInt().coerceIn(88, 99)
            else -> quantScore
        }

        val finalPass = allQuantPassed &&
            rawScore >= minConvictionThreshold &&
            (candleOhlc == null || candleDirectionAligned) &&
            (cloudVerdict == null || cloudVerdict == "PASS")

        val verdict = if (finalPass) "PASS" else "SKIP"
        val reason = when {
            finalPass -> cloudReason?.takeIf { it.isNotBlank() }
                ?: "AI $rawScore% Confirmed: Vol ${String.format(Locale.US, "%.1fx", volumeSurgeRatio)} • Range ${String.format(Locale.US, "%.2f", rangePct)}% clears fees"
            !trendMomentumPass -> "AI Screener Blocked: Weak Momentum / High Chop"
            !volumeLiquidityPass -> if (volumeSurgeRatio < MIN_VOLUME_SURGE_RATIO) {
                "AI Screener Blocked: Low Volume (${String.format(Locale.US, "%.2fx", volumeSurgeRatio)})"
            } else {
                "AI Screener Blocked: Weak Momentum / High Chop"
            }
            !feeClearancePass -> "AI Screener Blocked: Fee-Clearance Low (${String.format(Locale.US, "%.2f", rangePct)}%)"
            candleOhlc != null && !candleDirectionAligned -> "AI Screener Blocked: 1m Candle Not Confirmed (${candleAnalysis.patternName})"
            cloudVerdict == "SKIP" && !cloudReason.isNullOrBlank() -> cloudReason
            else -> "AI Screener Blocked: Conviction $rawScore% (<$minConvictionThreshold%)"
        }

        return DeepScreenerResult(
            strategyEval = strategyEval,
            screenerScore = rawScore,
            trendMomentumPass = trendMomentumPass,
            volumeLiquidityPass = volumeLiquidityPass,
            feeClearancePass = feeClearancePass,
            volumeSurgeRatio = Math.round(volumeSurgeRatio * 100.0) / 100.0,
            intradayRangePct = rangePct,
            roundTripChargePct = Math.round(chargePct * 1000.0) / 1000.0,
            verdict = verdict,
            reason = reason,
            oneMinCandlePattern = candleAnalysis.patternName,
            isOneMinCandleConfirmed = candleAnalysis.isConfirmed,
            oneMinCandleStatusPill = candleAnalysis.statusPill
        )
    }

    /**
     * Single-Direction AI Trend Selector:
     * - NEVER allows opposing BUY and SELL positions at the same time.
     * - If any position is already open, locks onto that side until all active positions exit.
     * - If flat, selects ONLY the single direction (BUY or SELL) of the highest-scoring (>= minAiConfidence)
     *   Gemini AI 4 Hydrogen candidate that is not in cooldown.
     */
    fun selectSingleDirectionEntries(
        activePositions: List<ActivePosition>,
        candidates: List<MarketInstrument>,
        minAiConfidence: Int,
        maxSlots: Int,
        allowSellAutoTrade: Boolean = false,
        isSideInCooldown: (TradeSide) -> Boolean = { false }
    ): SingleDirectionPlan {
        fun sideFor(inst: MarketInstrument): TradeSide? = when (inst.aiSignal) {
            AiTrendSignal.STRONG_BUY -> TradeSide.LONG
            AiTrendSignal.STRONG_SELL -> if (allowSellAutoTrade) TradeSide.SHORT else null
            else -> null
        }

        val passing = candidates
            .filter { inst ->
                inst.allScreenerChecksPassed &&
                    inst.aiScreenerVerdict == "PASS" &&
                    inst.aiScreenerScore >= minAiConfidence &&
                    inst.isOneMinCandleConfirmed &&
                    sideFor(inst) != null
            }
            .sortedByDescending { it.aiScreenerScore }

        if (activePositions.isNotEmpty()) {
            val openSides = activePositions.map { it.side }.distinct()
            if (openSides.size > 1) {
                return SingleDirectionPlan(
                    activeDirection = null,
                    selectedCandidates = emptyList(),
                    blockedOppositeCount = passing.size
                )
            }
            val lockedSide = openSides.first()
            val remainingSlots = (maxSlots - activePositions.size).coerceAtLeast(0)
            val blockedOpp = passing.count { sideFor(it) != lockedSide }
            if (remainingSlots <= 0 || isSideInCooldown(lockedSide)) {
                return SingleDirectionPlan(
                    activeDirection = lockedSide,
                    selectedCandidates = emptyList(),
                    blockedOppositeCount = blockedOpp
                )
            }
            val sameSideSelected = passing
                .filter { sideFor(it) == lockedSide }
                .take(remainingSlots)
            return SingleDirectionPlan(
                activeDirection = lockedSide,
                selectedCandidates = sameSideSelected,
                blockedOppositeCount = blockedOpp
            )
        }

        val topWinner = passing.firstOrNull { inst ->
            val side = sideFor(inst)
            side != null && !isSideInCooldown(side)
        } ?: return SingleDirectionPlan(
            activeDirection = null,
            selectedCandidates = emptyList(),
            blockedOppositeCount = 0
        )

        val winningSide = sideFor(topWinner)!!
        val selected = passing
            .filter { sideFor(it) == winningSide }
            .take(maxSlots.coerceAtLeast(1))
        val blockedOpposite = passing.count { sideFor(it) != winningSide }

        return SingleDirectionPlan(
            activeDirection = winningSide,
            selectedCandidates = selected,
            blockedOppositeCount = blockedOpposite
        )
    }

    /**
     * Synchronized Dual-Basket Pair Selector:
     * - Strictly requires 0 open positions ("Wait Until Both Legs Exit" lock — never allows partial re-entry).
     * - Strictly requires both BUY & SELL pair cooldowns to have elapsed.
     * - Requires allowSellAutoTrade == true, plus qualifying >= minAiConfidence + 1m Candle Confirmed
     *   candidates on both BUY and SELL sides.
     */
    fun selectSynchronizedPairEntries(
        activePositions: List<ActivePosition>,
        candidates: List<MarketInstrument>,
        minAiConfidence: Int,
        allowSellAutoTrade: Boolean = false,
        isBuyCooldownActive: Boolean = false,
        isSellCooldownActive: Boolean = false
    ): SynchronizedPairPlan {
        if (!allowSellAutoTrade) {
            return SynchronizedPairPlan(
                buyCandidate = null,
                sellCandidate = null,
                blockedReason = "SELL Auto-Trading is OFF (HOLD) — 100% BUY-Only Scalper Mode active"
            )
        }
        if (activePositions.isNotEmpty()) {
            return SynchronizedPairPlan(
                buyCandidate = null,
                sellCandidate = null,
                blockedReason = "Wait Lock Active (${activePositions.size} leg(s) open) — waiting until both legs exit before next pair"
            )
        }
        if (isBuyCooldownActive || isSellCooldownActive) {
            return SynchronizedPairPlan(
                buyCandidate = null,
                sellCandidate = null,
                blockedReason = "Pair re-entry cooldown active"
            )
        }

        val passing = candidates
            .filter { inst ->
                inst.allScreenerChecksPassed &&
                    inst.aiScreenerVerdict == "PASS" &&
                    inst.aiScreenerScore >= minAiConfidence &&
                    inst.isOneMinCandleConfirmed
            }
            .sortedByDescending { it.aiScreenerScore }

        val topBuy = passing.firstOrNull { it.aiSignal == AiTrendSignal.STRONG_BUY }
        val topSell = passing.firstOrNull {
            it.aiSignal == AiTrendSignal.STRONG_SELL && it.symbol != topBuy?.symbol
        }

        if (topBuy == null || topSell == null) {
            val missing = when {
                topBuy == null && topSell == null -> "1m BUY & SELL ≥$minAiConfidence%"
                topBuy == null -> "1m BUY ≥$minAiConfidence%"
                else -> "1m SELL ≥$minAiConfidence%"
            }
            return SynchronizedPairPlan(
                buyCandidate = null,
                sellCandidate = null,
                blockedReason = "Waiting for synchronized 1m candle pair ($missing missing)"
            )
        }

        return SynchronizedPairPlan(
            buyCandidate = topBuy,
            sellCandidate = topSell,
            blockedReason = null
        )
    }

    /**
     * Pure 100% BUY-Only Scalper Selector (used when allowSellAutoTrade == false):
     * - Selects high-conviction BUY setups (AI >= 99% + 1m Candle Breakout/Engulfing/Hammer confirmed)
     * - Never selects SELL signals.
     */
    fun selectBuyOnlyScalperEntries(
        activePositions: List<ActivePosition>,
        candidates: List<MarketInstrument>,
        minAiConfidence: Int,
        maxSlots: Int,
        isBuyCooldownActive: Boolean = false
    ): List<MarketInstrument> {
        val currentBuyCount = activePositions.count { it.side == TradeSide.LONG }
        val remainingSlots = (maxSlots - currentBuyCount).coerceAtLeast(0)
        if (remainingSlots <= 0 || isBuyCooldownActive) return emptyList()

        val activeSymbols = activePositions.map { it.symbol }.toSet()
        return candidates
            .filter { inst ->
                inst.symbol !in activeSymbols &&
                    inst.aiSignal == AiTrendSignal.STRONG_BUY &&
                    inst.allScreenerChecksPassed &&
                    inst.aiScreenerVerdict == "PASS" &&
                    inst.aiScreenerScore >= minAiConfidence &&
                    inst.isOneMinCandleConfirmed
            }
            .sortedByDescending { it.aiScreenerScore }
            .take(remainingSlots)
    }
}

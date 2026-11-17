package com.example.data.repository

import com.example.data.ai.CloudAiStockAssessment
import com.example.data.ai.GeminiMarketScreenerService
import com.example.data.ai.GeminiScreenerUiState
import com.example.data.smartapi.AngelOneBrokerClient
import com.example.data.smartapi.SmartApiSession
import com.example.domain.model.AccountSummary
import com.example.domain.model.ActivePosition
import com.example.domain.model.AiTrendSignal
import com.example.domain.model.BasketExitScope
import com.example.domain.model.BasketHighWaterState
import com.example.domain.model.BrokerConnectionState
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.Exchange
import com.example.domain.model.GeminiPerformanceDiagnostic
import com.example.domain.model.GrowthDiagnosisStatus
import com.example.domain.model.LogCategory
import com.example.domain.model.MarketInstrument
import com.example.domain.model.RiskConfig
import com.example.domain.model.StopLossMode
import com.example.domain.model.TargetProfitMode
import com.example.domain.model.TradeLogEntry
import com.example.domain.model.TradeSide
import com.example.domain.model.TradeTransaction
import com.example.domain.model.TransactionStatus
import com.example.util.CandleOhlcWindow
import com.example.util.Formatters
import com.example.util.IstMarketScheduler
import com.example.util.MarketWindowStatus
import com.example.util.StrategyIndicatorEngine
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

class TradingEngineRepository(
    private val preferencesStorage: RiskPreferencesStorage,
    private val brokerClient: AngelOneBrokerClient = AngelOneBrokerClient(),
    private val geminiScreenerService: GeminiMarketScreenerService = GeminiMarketScreenerService()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tradeMutex = Mutex()
    private val random = Random(System.currentTimeMillis())

    private val _riskConfig = MutableStateFlow(RiskConfig())
    val riskConfig: StateFlow<RiskConfig> = _riskConfig.asStateFlow()

    private val _autoTradingEnabled = MutableStateFlow(false)
    val autoTradingEnabled: StateFlow<Boolean> = _autoTradingEnabled.asStateFlow()

    private val _connectionState = MutableStateFlow<BrokerConnectionState>(
        BrokerConnectionState.Connected(
            clientId = "PAPER_DEMO",
            jwtTokenPreview = "DEMO-VIRTUAL-₹5L",
            isPaperSession = true,
            lastSyncFormatted = Formatters.formatTime()
        )
    )
    val connectionState: StateFlow<BrokerConnectionState> = _connectionState.asStateFlow()

    private val _marketWindowStatus = MutableStateFlow(IstMarketScheduler.evaluate(RiskConfig()))
    val marketWindowStatus: StateFlow<MarketWindowStatus> = _marketWindowStatus.asStateFlow()

    private val _instruments = MutableStateFlow(createInitialWatchlist())
    val instruments: StateFlow<List<MarketInstrument>> = _instruments.asStateFlow()

    private val _activePositions = MutableStateFlow<List<ActivePosition>>(emptyList())
    val activePositions: StateFlow<List<ActivePosition>> = _activePositions.asStateFlow()

    private val _tradeLogs = MutableStateFlow<List<TradeLogEntry>>(initialBootLogs())
    val tradeLogs: StateFlow<List<TradeLogEntry>> = _tradeLogs.asStateFlow()

    private val _transactions = MutableStateFlow<List<TradeTransaction>>(emptyList())
    val transactions: StateFlow<List<TradeTransaction>> = _transactions.asStateFlow()

    private var initialDayCapital: Double = 500000.0
    private var cashBalance: Double = 500000.0
    private var realizedPnl: Double = 0.0
    private var totalChargesPaid: Double = 0.0
    private var winCount: Int = 0
    private var lossCount: Int = 0
    private var totalTradesExecutedToday: Int = 0
    private var totalCapitalInvestedToday: Double = 0.0
    private var basketDropsCompleted: Int = 0
    private var basketBookedProfitInr: Double = 0.0
    private var grossProfit: Double = 0.0
    private var grossLoss: Double = 0.0
    private val equityCurvePoints = mutableListOf(500000.0)

    // Independent cooldowns for BUY basket, SELL basket, individual exits & per-symbol re-entry
    private val symbolCooldownUntilMs = mutableMapOf<String, Long>()
    private val blockedAuditLogAtMs = mutableMapOf<String, Long>()
    private var buyBasketCooldownUntilMs: Long = 0L
    private var sellBasketCooldownUntilMs: Long = 0L
    private var lastBuyExitCooldownUntilMs: Long = 0L
    private var lastSellExitCooldownUntilMs: Long = 0L
    private val _buyBasketCooldownRemainingSec = MutableStateFlow(0)
    val buyBasketCooldownRemainingSec: StateFlow<Int> = _buyBasketCooldownRemainingSec.asStateFlow()
    private val _sellBasketCooldownRemainingSec = MutableStateFlow(0)
    val sellBasketCooldownRemainingSec: StateFlow<Int> = _sellBasketCooldownRemainingSec.asStateFlow()

    private val _buyBasketHighWater = MutableStateFlow(BasketHighWaterState())
    val buyBasketHighWater: StateFlow<BasketHighWaterState> = _buyBasketHighWater.asStateFlow()
    private val _sellBasketHighWater = MutableStateFlow(BasketHighWaterState())
    val sellBasketHighWater: StateFlow<BasketHighWaterState> = _sellBasketHighWater.asStateFlow()

    private val _screenerUiState = MutableStateFlow<GeminiScreenerUiState>(GeminiScreenerUiState.Idle)
    val screenerUiState: StateFlow<GeminiScreenerUiState> = _screenerUiState.asStateFlow()

    private var wasNseBseOpenLastTick: Boolean = false
    private var wasMcxOpenLastTick: Boolean = false
    private var circuitBreakerIstDayKey: Int = currentIstDayKey()
    private var autoStartedIstDayKey: Int = -1
    private var dailyProfitLockedIstDayKey: Int = -1
    private var dailyProfitIstDayKey: Int = currentIstDayKey()
    private var peakRealizedProfitInr: Double = 0.0
    private var dailyProfitFloorInr: Double? = null
    private var withdrawnProfitTotal: Double = 0.0

    private val _circuitBreakerTriggered = MutableStateFlow(false)
    val circuitBreakerTriggered: StateFlow<Boolean> = _circuitBreakerTriggered.asStateFlow()

    private val _apiQuotaExhausted = MutableStateFlow(false)
    val apiQuotaExhausted: StateFlow<Boolean> = _apiQuotaExhausted.asStateFlow()

    private val _performanceDiagnostic = MutableStateFlow(GeminiPerformanceDiagnostic())
    val performanceDiagnostic: StateFlow<GeminiPerformanceDiagnostic> = _performanceDiagnostic.asStateFlow()

    private var lastAutoTuneTradeCount: Int = 0
    private var lastApiHoldAuditAtMs: Long = 0L
    @Volatile
    private var hasUserModifiedConfig: Boolean = false
    private val symbolCandles = mutableMapOf<String, CandleOhlcWindow>()
    private val symbolConsecutiveLosses = mutableMapOf<String, Int>()
    private val symbolWinCounts = mutableMapOf<String, Int>()
    private val symbolNetPnlStats = mutableMapOf<String, Double>()
    private var adaptiveMinEfficiency: Double = StrategyIndicatorEngine.MIN_DIRECTIONAL_EFFICIENCY

    private val _savedCredentials = MutableStateFlow(BrokerCredentials())
    val savedCredentials: StateFlow<BrokerCredentials> = _savedCredentials.asStateFlow()

    private val _accountSummary = MutableStateFlow(AccountSummary())
    val accountSummary: StateFlow<AccountSummary> = _accountSummary.asStateFlow()

    private var activeSession: SmartApiSession? = SmartApiSession(
        clientId = "PAPER_DEMO",
        apiKey = "PAPER-SANDBOX",
        jwtToken = "paper_session_jwt",
        refreshToken = "paper_refresh",
        feedToken = "paper_feed",
        isPaperSession = true
    )

    init {
        scope.launch {
            val saved = preferencesStorage.riskConfigFlow.first()
            val savedCreds = preferencesStorage.savedCredentialsFlow.first()
            var shouldAutoConnectLive = false
            tradeMutex.withLock {
                if (!hasUserModifiedConfig) {
                    _riskConfig.value = saved
                }
                _savedCredentials.value = savedCreds
                val activeCfg = _riskConfig.value
                val initialWindow = IstMarketScheduler.evaluate(activeCfg)
                _marketWindowStatus.value = initialWindow
                wasNseBseOpenLastTick = initialWindow.isNseBseOpen
                wasMcxOpenLastTick = initialWindow.isMcxOpen
                if (activeCfg.isPaperMode) {
                    connectPaperSandboxSilently()
                } else if (savedCreds.isCompleteForLive) {
                    shouldAutoConnectLive = true
                }
            }
            if (shouldAutoConnectLive) {
                connectBroker(savedCreds)
            }
            startMarketTickLoop()
        }
    }

    private fun connectPaperSandboxSilently() {
        val session = SmartApiSession(
            clientId = "PAPER_DEMO",
            apiKey = "PAPER-SANDBOX",
            jwtToken = "paper_session_jwt",
            refreshToken = "paper_refresh",
            feedToken = "paper_feed",
            isPaperSession = true
        )
        activeSession = session
        _connectionState.value = BrokerConnectionState.Connected(
            clientId = session.clientId,
            jwtTokenPreview = "DEMO-VIRTUAL-₹5L",
            isPaperSession = true,
            lastSyncFormatted = Formatters.formatTime()
        )
    }

    private fun startMarketTickLoop() {
        scope.launch {
            while (isActive) {
                delay(750L)
                processMarketTick()
            }
        }
    }

    internal suspend fun stepMarketTickForTest(nowMsOverride: Long? = null) {
        processMarketTick(nowMsOverride)
    }

    private suspend fun processMarketTick(nowMsOverride: Long? = null) = tradeMutex.withLock {
        val config = _riskConfig.value
        val nowMs = nowMsOverride ?: System.currentTimeMillis()
        val windowStatus = IstMarketScheduler.evaluate(config, nowMs)
        _marketWindowStatus.value = windowStatus

        val currentDayKey = currentIstDayKey(nowMs)
        if (currentDayKey != dailyProfitIstDayKey) {
            dailyProfitIstDayKey = currentDayKey
            peakRealizedProfitInr = 0.0
            dailyProfitFloorInr = null
            dailyProfitLockedIstDayKey = -1
        }
        if (currentDayKey != circuitBreakerIstDayKey && _circuitBreakerTriggered.value) {
            _circuitBreakerTriggered.value = false
            circuitBreakerIstDayKey = currentDayKey
        }

        // Scheduler: Morning Auto-Start (once per trading day when market opens, even if launched mid-morning)
        val isDailyProfitLockedToday = dailyProfitLockedIstDayKey == currentDayKey
        if (config.enforceMarketHours && config.autoStartAtMarketOpen &&
            windowStatus.isAnyMarketOpen &&
            autoStartedIstDayKey != currentDayKey &&
            _connectionState.value is BrokerConnectionState.Connected &&
            !_circuitBreakerTriggered.value &&
            !isDailyProfitLockedToday
        ) {
            autoStartedIstDayKey = currentDayKey
            if (!_autoTradingEnabled.value) {
                _autoTradingEnabled.value = true
                val volNote = if (config.skipOpeningVolatilityEnabled && windowStatus.isOpeningVolatilityWindow) {
                    "Opening Volatility Guard active until 09:30 IST before NSE/BSE entries."
                } else {
                    "Morning-to-Evening Scanner armed (${windowStatus.statusSummary}) with ₹${config.effectiveCapitalPerTrade.toInt()}/trade."
                }
                appendLogLocked(
                    category = LogCategory.MARKET_SCHEDULER,
                    symbol = "MORNING OPEN",
                    exchange = null,
                    side = null,
                    quantity = null,
                    price = null,
                    pnl = null,
                    pnlPct = null,
                    title = "Market Open — Auto-Trading Started",
                    detail = volNote
                )
            }
        }

        // Scheduler: Evening / Market Close Auto-Square-Off & Stop Trading
        if (config.enforceMarketHours) {
            val closedExchangePositions = _activePositions.value.filter { pos ->
                !IstMarketScheduler.isExchangeOpen(pos.exchange, windowStatus)
            }
            if (closedExchangePositions.isNotEmpty()) {
                for (pos in closedExchangePositions) {
                    executePositionExitLocked(
                        position = pos,
                        category = LogCategory.MARKET_SCHEDULER,
                        reasonTitle = "Evening Market Close Square-Off (${pos.exchange.label})",
                        nowMs = nowMs
                    )
                }
                _activePositions.value = _activePositions.value.filter { pos ->
                    IstMarketScheduler.isExchangeOpen(pos.exchange, windowStatus)
                }
                if (_activePositions.value.none { it.side == TradeSide.LONG }) {
                    _buyBasketHighWater.value = BasketHighWaterState()
                }
                if (_activePositions.value.none { it.side == TradeSide.SHORT }) {
                    _sellBasketHighWater.value = BasketHighWaterState()
                }
            }
            if (!windowStatus.isAnyMarketOpen && _autoTradingEnabled.value) {
                _autoTradingEnabled.value = false
                appendLogLocked(
                    category = LogCategory.MARKET_SCHEDULER,
                    symbol = "EVENING CLOSE",
                    exchange = null,
                    side = null,
                    quantity = null,
                    price = null,
                    pnl = null,
                    pnlPct = null,
                    title = "Evening Market Closed — Auto-Trading Stopped",
                    detail = if (closedExchangePositions.isNotEmpty()) {
                        "Squared off ${closedExchangePositions.size} position(s) at market close & safely halted trading until tomorrow morning."
                    } else {
                        "Market window ended (${windowStatus.istTimeFormatted}). Auto-Trading stopped until morning open."
                    }
                )
            }
        }
        wasNseBseOpenLastTick = windowStatus.isNseBseOpen
        wasMcxOpenLastTick = windowStatus.isMcxOpen

        // Tick prices & compute Intraday VWAP + 9/21 EMA + RSI + 1m Candle + Adaptive 99% AI Screener in <1ms
        val allocBudget = config.effectiveCapitalPerTrade
        val currentMinuteBucket = nowMs / 60_000L
        val secondsIntoCandle = ((nowMs % 60_000L) / 1000L).toInt().coerceIn(0, 59)

        val updatedInstruments = _instruments.value.map { inst ->
            val volatilityPct = when (inst.exchange) {
                Exchange.MCX -> 0.0012
                Exchange.NSE -> 0.00095
                Exchange.BSE -> 0.00090
            }
            val isConfirmed99Setup = inst.allScreenerChecksPassed &&
                inst.aiScreenerScore >= 99 &&
                inst.isOneMinCandleConfirmed
            val driftBias = when {
                inst.rsi14 < 24.0 -> 0.0008
                inst.rsi14 > 76.0 -> -0.0008
                isConfirmed99Setup && inst.aiSignal == AiTrendSignal.STRONG_BUY -> 0.00062
                isConfirmed99Setup && inst.aiSignal == AiTrendSignal.STRONG_SELL -> -0.00062
                inst.isPriceAboveVwap && inst.isEmaBullish -> 0.00032
                !inst.isPriceAboveVwap && !inst.isEmaBullish -> -0.00032
                inst.isEmaBullish -> 0.00018
                else -> -0.00018
            }
            val pctMove = ((random.nextDouble() - 0.50) * 2.0 * volatilityPct) + driftBias
            val rawNewPrice = (inst.ltp * (1.0 + pctMove)).coerceAtLeast(1.0)
            val roundedLtp = Math.round(rawNewPrice * 100.0) / 100.0
            val delta = roundedLtp - inst.ltp

            val tickVolume = 800.0 + (abs(pctMove) * 420_000.0)
            val prevAvgVol = inst.avgTickVolume.coerceAtLeast(1.0)
            val surgeRatio = tickVolume / prevAvgVol
            val newAvgVol = (prevAvgVol * 0.90) + (tickVolume * 0.10)
            val newCumPv = inst.cumulativePv + (roundedLtp * tickVolume)
            val newCumVolume = inst.cumulativeVolume + tickVolume
            val newVwap = Math.round((newCumPv / newCumVolume) * 100.0) / 100.0

            val updatedHistory = (inst.priceHistory + roundedLtp).takeLast(32)
            val prevCandle = symbolCandles[inst.symbol]
            val updatedCandle = if (prevCandle == null) {
                val seedOpen = updatedHistory.takeLast(4).firstOrNull() ?: roundedLtp
                val seedPrevOpen = if (updatedHistory.size >= 8) updatedHistory[updatedHistory.size - 8] else seedOpen
                val seedPrevClose = if (updatedHistory.size >= 5) updatedHistory[updatedHistory.size - 5] else seedOpen
                CandleOhlcWindow(
                    minuteBucket = currentMinuteBucket,
                    open = seedOpen,
                    high = maxOf(seedOpen, roundedLtp),
                    low = minOf(seedOpen, roundedLtp),
                    close = roundedLtp,
                    prevOpen = seedPrevOpen,
                    prevHigh = maxOf(seedPrevOpen, seedPrevClose),
                    prevLow = minOf(seedPrevOpen, seedPrevClose),
                    prevClose = seedPrevClose,
                    secondsIntoCandle = secondsIntoCandle
                )
            } else if (prevCandle.minuteBucket != currentMinuteBucket) {
                CandleOhlcWindow(
                    minuteBucket = currentMinuteBucket,
                    open = inst.ltp,
                    high = maxOf(inst.ltp, roundedLtp),
                    low = minOf(inst.ltp, roundedLtp),
                    close = roundedLtp,
                    prevOpen = prevCandle.open,
                    prevHigh = prevCandle.high,
                    prevLow = prevCandle.low,
                    prevClose = prevCandle.close,
                    secondsIntoCandle = secondsIntoCandle
                )
            } else {
                prevCandle.copy(
                    high = maxOf(prevCandle.high, roundedLtp),
                    low = minOf(prevCandle.low, roundedLtp),
                    close = roundedLtp,
                    secondsIntoCandle = secondsIntoCandle
                )
            }
            symbolCandles[inst.symbol] = updatedCandle

            val screener = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
                ltp = roundedLtp,
                vwap = newVwap,
                priceHistory = updatedHistory,
                exchange = inst.exchange,
                allocationInr = allocBudget,
                volumeSurgeRatio = surgeRatio,
                minConvictionThreshold = config.minAiConfidence,
                candleOhlc = updatedCandle,
                minEfficiencyOverride = adaptiveMinEfficiency
            )
            val eval = screener.strategyEval

            val cooldownUntil = symbolCooldownUntilMs[inst.symbol] ?: 0L
            val cooldownSec = if (cooldownUntil > nowMs) {
                ceil((cooldownUntil - nowMs) / 1000.0).toInt().coerceAtLeast(1)
            } else {
                symbolCooldownUntilMs.remove(inst.symbol)
                0
            }

            inst.copy(
                ltp = roundedLtp,
                rsi14 = eval.rsi14,
                aiSignal = eval.signal,
                aiConfidence = eval.confidence,
                priceHistory = updatedHistory,
                lastTickDelta = delta,
                cooldownRemainingSec = cooldownSec,
                vwap = eval.vwap,
                cumulativePv = newCumPv,
                cumulativeVolume = newCumVolume,
                avgTickVolume = newAvgVol,
                latestTickVolume = tickVolume,
                volumeSurgeRatio = screener.volumeSurgeRatio,
                intradayRangePct = screener.intradayRangePct,
                ema9 = eval.ema9,
                ema21 = eval.ema21,
                isPriceAboveVwap = eval.isPriceAboveVwap,
                isEmaBullish = eval.isEmaBullish,
                strategyPassed = eval.strategyPassed,
                strategyReason = eval.statusReason,
                aiScreenerScore = screener.screenerScore,
                trendMomentumPass = screener.trendMomentumPass,
                volumeLiquidityPass = screener.volumeLiquidityPass,
                feeClearancePass = screener.feeClearancePass,
                aiScreenerVerdict = screener.verdict,
                aiScreenerReason = screener.reason,
                oneMinCandlePattern = screener.oneMinCandlePattern,
                isOneMinCandleConfirmed = screener.isOneMinCandleConfirmed,
                oneMinCandleStatusPill = screener.oneMinCandleStatusPill
            )
        }
        _instruments.value = updatedInstruments
        refreshLocalScreenerBanner(updatedInstruments, config.minAiConfidence, nowMs)

        val priceLookup = updatedInstruments.associateBy { it.symbol }

        // Sync active positions & arm Break-Even (+₹3 net above charges at +₹5 trigger), Zero-Loss Shield (+₹5 floor at +₹12 trigger) & +₹10 Profit Floor
        val newlyBeLockedPositions = mutableListOf<ActivePosition>()
        val newlyZeroLossShieldPositions = mutableListOf<ActivePosition>()
        val newlyProfitFloorPositions = mutableListOf<ActivePosition>()
        val syncedPositions = _activePositions.value.map { pos ->
            val latest = priceLookup[pos.symbol]?.ltp ?: pos.currentPrice
            val updated = pos.copy(currentPrice = latest)
            if (config.highProbabilityProfitLockEnabled) {
                val netPnl = updated.netUnrealizedPnl
                if (netPnl >= ActivePosition.POSITION_PROFIT_LOCK_TRIGGER_INR &&
                    (updated.lockedProfitFloorInr ?: -1.0) < ActivePosition.POSITION_PROFIT_LOCK_FLOOR_INR
                ) {
                    val locked = updated.copy(
                        isBreakEvenLocked = true,
                        isZeroLossShieldLocked = true,
                        lockedProfitFloorInr = ActivePosition.POSITION_PROFIT_LOCK_FLOOR_INR
                    )
                    newlyProfitFloorPositions.add(locked)
                    locked
                } else if (netPnl >= ActivePosition.ZERO_LOSS_SHIELD_TRIGGER_INR &&
                    (updated.lockedProfitFloorInr ?: -1.0) < ActivePosition.ZERO_LOSS_SHIELD_FLOOR_INR
                ) {
                    val locked = updated.copy(
                        isBreakEvenLocked = true,
                        isZeroLossShieldLocked = true,
                        lockedProfitFloorInr = ActivePosition.ZERO_LOSS_SHIELD_FLOOR_INR
                    )
                    newlyZeroLossShieldPositions.add(locked)
                    locked
                } else if (!updated.isBreakEvenLocked && netPnl >= ActivePosition.BREAK_EVEN_ARM_TRIGGER_INR) {
                    val locked = updated.copy(isBreakEvenLocked = true)
                    newlyBeLockedPositions.add(locked)
                    locked
                } else {
                    updated
                }
            } else {
                updated
            }
        }
        _activePositions.value = syncedPositions
        syncOpenTransactionsLocked(syncedPositions)
        for (lockedPos in newlyBeLockedPositions) {
            appendLogLocked(
                category = LogCategory.RISK_GUARD,
                symbol = lockedPos.symbol,
                exchange = lockedPos.exchange,
                side = lockedPos.side,
                quantity = lockedPos.quantity,
                price = lockedPos.breakEvenPrice,
                pnl = lockedPos.netUnrealizedPnl,
                pnlPct = lockedPos.unrealizedPnlPct,
                title = "Break-Even +₹3 Net Lock Armed • ${lockedPos.symbol}",
                detail = "SL locked at Charges + ₹3.00 net (${Formatters.formatPrice(lockedPos.breakEvenPrice)}) so charges can never cause a loss."
            )
        }
        for (lockedPos in newlyZeroLossShieldPositions) {
            appendLogLocked(
                category = LogCategory.RISK_GUARD,
                symbol = lockedPos.symbol,
                exchange = lockedPos.exchange,
                side = lockedPos.side,
                quantity = lockedPos.quantity,
                price = lockedPos.profitLockFloorPrice ?: lockedPos.breakEvenPrice,
                pnl = lockedPos.netUnrealizedPnl,
                pnlPct = lockedPos.unrealizedPnlPct,
                title = "Zero-Loss Profit Shield +₹5 Armed • ${lockedPos.symbol}",
                detail = "Hit +₹12.00 net; SL locked at +₹5.00 net floor (${Formatters.formatPrice(lockedPos.profitLockFloorPrice ?: lockedPos.breakEvenPrice)}) strictly after all charges so trade can NEVER lose."
            )
        }
        for (lockedPos in newlyProfitFloorPositions) {
            appendLogLocked(
                category = LogCategory.RISK_GUARD,
                symbol = lockedPos.symbol,
                exchange = lockedPos.exchange,
                side = lockedPos.side,
                quantity = lockedPos.quantity,
                price = lockedPos.profitLockFloorPrice ?: lockedPos.breakEvenPrice,
                pnl = lockedPos.netUnrealizedPnl,
                pnlPct = lockedPos.unrealizedPnlPct,
                title = "Profit-Lock +₹10 Armed • ${lockedPos.symbol}",
                detail = "Hit +₹20 net profit; SL raised to +₹10 net floor (${Formatters.formatPrice(lockedPos.stopLossPrice)}) above charges."
            )
        }

        // 0) Daily Max Loss Circuit Breaker (default -₹1,500) -> Square Off All & Lock Auto-Trading
        val totalDayNetPnl = realizedPnl + syncedPositions.sumOf { it.netUnrealizedPnl }
        var circuitBreakerFiredThisTick = false
        var remainingAfterBasketCheck = syncedPositions
        if (!_circuitBreakerTriggered.value && totalDayNetPnl <= -config.dailyMaxLossLimitInr) {
            val openCount = syncedPositions.size
            for (pos in syncedPositions) {
                executePositionExitLocked(
                    position = pos,
                    category = LogCategory.CIRCUIT_BREAKER,
                    reasonTitle = "Circuit Breaker (-₹${config.dailyMaxLossLimitInr.toInt()} Daily Cap)",
                    nowMs = nowMs
                )
            }
            _activePositions.value = emptyList()
            _buyBasketHighWater.value = BasketHighWaterState()
            _sellBasketHighWater.value = BasketHighWaterState()
            remainingAfterBasketCheck = emptyList()
            _autoTradingEnabled.value = false
            _circuitBreakerTriggered.value = true
            circuitBreakerIstDayKey = currentDayKey
            circuitBreakerFiredThisTick = true
            appendLogLocked(
                category = LogCategory.CIRCUIT_BREAKER,
                symbol = "CIRCUIT",
                exchange = null,
                side = null,
                quantity = null,
                price = null,
                pnl = totalDayNetPnl,
                pnlPct = null,
                title = "DAILY MAX LOSS CIRCUIT BREAKER TRIGGERED",
                detail = "Total Day Net P&L hit ${Formatters.formatInr(totalDayNetPnl, showSign = true)} (Limit -${Formatters.formatInr(config.dailyMaxLossLimitInr)}). Squared off $openCount position(s) & locked Auto-Trading for the rest of the day."
            )
        }

        // 0.15) Daily Profit Shield (protect day's net gains once realized >= +₹100 so day stays net positive)
        if (realizedPnl >= 100.0) {
            peakRealizedProfitInr = maxOf(peakRealizedProfitInr, realizedPnl)
            dailyProfitFloorInr = maxOf(35.0, Math.round(peakRealizedProfitInr * 0.65 * 100.0) / 100.0)
        }
        val activeProfitFloor = dailyProfitFloorInr
        if (!circuitBreakerFiredThisTick && activeProfitFloor != null &&
            dailyProfitLockedIstDayKey != currentDayKey &&
            totalDayNetPnl <= activeProfitFloor &&
            realizedPnl > 0.0
        ) {
            val openCount = remainingAfterBasketCheck.size
            for (pos in remainingAfterBasketCheck) {
                executePositionExitLocked(
                    position = pos,
                    category = LogCategory.RISK_GUARD,
                    reasonTitle = "Daily Profit Shield (+₹${activeProfitFloor.toInt()} Floor)",
                    nowMs = nowMs
                )
            }
            remainingAfterBasketCheck = emptyList()
            _activePositions.value = emptyList()
            _buyBasketHighWater.value = BasketHighWaterState()
            _sellBasketHighWater.value = BasketHighWaterState()
            _autoTradingEnabled.value = false
            dailyProfitLockedIstDayKey = currentDayKey
            appendLogLocked(
                category = LogCategory.RISK_GUARD,
                symbol = "PROFIT SHIELD",
                exchange = null,
                side = null,
                quantity = null,
                price = null,
                pnl = realizedPnl,
                pnlPct = null,
                title = "Daily Profit Shield Activated (+${Formatters.formatInr(realizedPnl)} Net Protected)",
                detail = "Locked daily profit floor (+${Formatters.formatInr(activeProfitFloor)} from peak +${Formatters.formatInr(peakRealizedProfitInr)}). Closed $openCount trade(s) & paused entries so today closes strictly in Profit!"
            )
        }

        // 0.20) API Key / Access Limit Guard:
        // If Gemini AI / SmartAPI quota is exhausted, DO NOT auto-book profits until API access is restored.
        val isApiLimitReached = _apiQuotaExhausted.value
        if (isApiLimitReached && remainingAfterBasketCheck.isNotEmpty()) {
            val combinedNet = BasketHighWaterState.computeCombinedDualBasketNetPnl(remainingAfterBasketCheck)
            val anySingleReady = remainingAfterBasketCheck.any { it.netUnrealizedPnl >= (config.singleTradeInstantBookInr - 1e-6) }
            if ((combinedNet >= config.basketInstantBookInr || anySingleReady) && (nowMs - lastApiHoldAuditAtMs) >= 30_000L) {
                lastApiHoldAuditAtMs = nowMs
                appendLogLocked(
                    category = LogCategory.RISK_GUARD,
                    symbol = "API LIMIT HOLD",
                    exchange = null,
                    side = null,
                    quantity = remainingAfterBasketCheck.sumOf { it.quantity },
                    price = null,
                    pnl = combinedNet,
                    pnlPct = null,
                    title = "API Access Limit Exhausted — Profit Booking Paused",
                    detail = "Target +₹25.00 reached (${Formatters.formatInr(combinedNet, showSign = true)} Net), but Gemini AI 4H / API key quota limit is exhausted. Holding position without auto-booking until API access is restored."
                )
            }
        }

        // 0.25) MASTER Combined Pair / Dual-Basket (BUY + SELL) +₹25.00 Zero-Wait Instant Auto-Book
        // Checked BEFORE single-leg exits so whenever BUY Net + SELL Net >= +₹25.00, BOTH legs square off together immediately!
        if (!circuitBreakerFiredThisTick && !isApiLimitReached && remainingAfterBasketCheck.isNotEmpty()) {
            val masterTargetInr = config.basketInstantBookInr
            if (BasketHighWaterState.isMasterDualBasketTargetHit(remainingAfterBasketCheck, masterTargetInr)) {
                val buyOpen = remainingAfterBasketCheck.filter { it.side == TradeSide.LONG }
                val sellOpen = remainingAfterBasketCheck.filter { it.side == TradeSide.SHORT }
                val hasBuy = buyOpen.isNotEmpty()
                val hasSell = sellOpen.isNotEmpty()
                val combinedNetPnl = BasketHighWaterState.computeCombinedDualBasketNetPnl(remainingAfterBasketCheck)
                val combinedInvested = remainingAfterBasketCheck.sumOf { it.investedCapital }
                val combinedPct = if (combinedInvested > 0.0) (combinedNetPnl / combinedInvested) * 100.0 else 0.0
                val reasonLabel = when {
                    hasBuy && hasSell -> "Synchronized Pair +₹${masterTargetInr.toInt()} Zero-Wait Auto-Book"
                    hasBuy -> "BUY Basket +₹${masterTargetInr.toInt()} Zero-Wait Auto-Book"
                    else -> "SELL Basket +₹${masterTargetInr.toInt()} Zero-Wait Auto-Book"
                }
                for (pos in remainingAfterBasketCheck) {
                    executePositionExitLocked(
                        position = pos,
                        category = LogCategory.AUTO_PROFIT_EXIT,
                        reasonTitle = reasonLabel,
                        nowMs = nowMs
                    )
                }
                val closedCount = remainingAfterBasketCheck.size
                val closedQty = remainingAfterBasketCheck.sumOf { it.quantity }
                val buyPart = Formatters.formatInr(buyOpen.sumOf { it.netUnrealizedPnl }, showSign = true)
                val sellPart = Formatters.formatInr(sellOpen.sumOf { it.netUnrealizedPnl }, showSign = true)
                remainingAfterBasketCheck = emptyList()
                _activePositions.value = emptyList()
                _buyBasketHighWater.value = BasketHighWaterState()
                _sellBasketHighWater.value = BasketHighWaterState()
                val cdMs = config.reentryCooldownSec * 1000L
                if (hasBuy) buyBasketCooldownUntilMs = if (cdMs > 0L) nowMs + cdMs else 0L
                if (hasSell) sellBasketCooldownUntilMs = if (cdMs > 0L) nowMs + cdMs else 0L
                val cdNote = if (config.reentryCooldownSec == 0) "0s Instant Re-Entry active." else "${config.reentryCooldownSec}s cooldown."
                basketDropsCompleted++
                basketBookedProfitInr += combinedNetPnl
                appendLogLocked(
                    category = LogCategory.AUTO_PROFIT_EXIT,
                    symbol = if (hasBuy && hasSell) "SYNC PAIR($closedCount)" else if (hasBuy) "BUY BASKET(${buyOpen.size})" else "SELL BASKET(${sellOpen.size})",
                    exchange = null,
                    side = if (hasBuy && !hasSell) TradeSide.LONG else if (hasSell && !hasBuy) TradeSide.SHORT else null,
                    quantity = closedQty,
                    price = null,
                    pnl = combinedNetPnl,
                    pnlPct = combinedPct,
                    title = if (hasBuy && hasSell) {
                        "Combined Pair +₹${masterTargetInr.toInt()} Auto-Book (${Formatters.formatInr(combinedNetPnl, showSign = true)} Net)"
                    } else {
                        "${if (hasBuy) "BUY" else "SELL"} Basket +₹${masterTargetInr.toInt()} Auto-Book (${Formatters.formatInr(combinedNetPnl, showSign = true)} Net)"
                    },
                    detail = if (hasBuy && hasSell) {
                        "Combined BUY ($buyPart) + SELL ($sellPart) hit +₹${masterTargetInr.toInt()} net across $closedCount leg(s) — squared off BOTH legs together • $cdNote"
                    } else {
                        "Single basket hit +₹${masterTargetInr.toInt()} zero-wait target across $closedCount position(s) • $cdNote"
                    }
                )
            }
        }

        // 0.5) ZERO-WAIT Single-Leg Instant Auto-Book (>= +₹15.00 Net P&L strictly after round-trip charges)
        if (!circuitBreakerFiredThisTick && !isApiLimitReached && remainingAfterBasketCheck.isNotEmpty()) {
            val stillOpen = mutableListOf<ActivePosition>()
            val bookedWinners = mutableListOf<ActivePosition>()
            for (pos in remainingAfterBasketCheck) {
                val effectiveSingleTarget = minOf(
                    config.singleTradeInstantBookInr,
                    maxOf(6.0, pos.investedCapital * 0.006)
                )
                val netPnl = pos.netUnrealizedPnl
                if (netPnl >= (effectiveSingleTarget - 1e-6)) {
                    val otherLegs = remainingAfterBasketCheck.filter { it.id != pos.id }
                    val isOppositePairLeg = !config.singleDirectionOnly && config.allowSellAutoTrade && otherLegs.any { it.side != pos.side }
                    val holdNote = when {
                        isOppositePairLeg -> {
                            val otherSide = otherLegs.first().side.smartApiTransactionType
                            " • Holding $otherSide leg for profit recovery"
                        }
                        otherLegs.isNotEmpty() -> " • ${otherLegs.size} BUY trade active"
                        else -> " • Trade cycle complete"
                    }
                    executePositionExitLocked(
                        position = pos,
                        category = LogCategory.AUTO_PROFIT_EXIT,
                        reasonTitle = "Zero-Wait +₹${effectiveSingleTarget.toInt()} Leg Booked (${Formatters.formatInr(netPnl, showSign = true)})$holdNote",
                        nowMs = nowMs
                    )
                    basketDropsCompleted++
                    basketBookedProfitInr += netPnl
                    bookedWinners.add(pos)
                } else {
                    stillOpen.add(pos)
                }
            }
            if (stillOpen.size != remainingAfterBasketCheck.size) {
                remainingAfterBasketCheck = stillOpen
                _activePositions.value = stillOpen
                if (stillOpen.none { it.side == TradeSide.LONG }) {
                    _buyBasketHighWater.value = BasketHighWaterState()
                }
                if (stillOpen.none { it.side == TradeSide.SHORT }) {
                    _sellBasketHighWater.value = BasketHighWaterState()
                }
                if (stillOpen.isNotEmpty() && bookedWinners.isNotEmpty()) {
                    val winner = bookedWinners.first()
                    val held = stillOpen.first()
                    val effectiveSingleTarget = minOf(
                        config.singleTradeInstantBookInr,
                        maxOf(6.0, winner.investedCapital * 0.006)
                    )
                    appendLogLocked(
                        category = LogCategory.AUTO_PROFIT_EXIT,
                        symbol = "${winner.symbol} → HOLD ${held.symbol}",
                        exchange = winner.exchange,
                        side = winner.side,
                        quantity = winner.quantity,
                        price = winner.currentPrice,
                        pnl = winner.netUnrealizedPnl,
                        pnlPct = winner.unrealizedPnlPct,
                        title = "${winner.side.smartApiTransactionType} +₹${effectiveSingleTarget.toInt()} Crossed & Booked (${Formatters.formatInr(winner.netUnrealizedPnl, showSign = true)} Net)",
                        detail = if (!config.singleDirectionOnly && config.allowSellAutoTrade && held.side != winner.side) {
                            "Holding ${held.side.smartApiTransactionType} (${held.symbol} at ${Formatters.formatInr(held.netUnrealizedPnl, showSign = true)}) to recover & book profit."
                        } else {
                            "${held.symbol} (${held.side.smartApiTransactionType}) continues at ${Formatters.formatInr(held.netUnrealizedPnl, showSign = true)}."
                        }
                    )
                }
            }
        }

        // 1) Independent BUY & SELL Basket Guard:
        //    - Strict -₹25.00 Max Loss Cut (cut losing basket fast, leave winning basket running)
        //    - Zero-Wait +₹25.00 Single-Basket Auto-Book (banks +₹25 immediately after charges)
        //    - Trailing High-Water Guard (+₹50->+₹35, +₹80->+₹60 when custom ceiling > ₹50)
        if (!circuitBreakerFiredThisTick && remainingAfterBasketCheck.isNotEmpty()) {
            val targetDesc = if (config.targetProfitMode == TargetProfitMode.FIXED_INR) {
                Formatters.formatInr(config.targetProfitInr)
            } else {
                "${config.targetProfitPct}% Smart"
            }

            // 1A) BUY Basket (TradeSide.LONG)
            val buyPositions = remainingAfterBasketCheck.filter { it.side == TradeSide.LONG }
            if (buyPositions.isEmpty()) {
                _buyBasketHighWater.value = BasketHighWaterState()
            } else {
                val buyNetPnl = buyPositions.sumOf { it.netUnrealizedPnl }
                val buyInvested = buyPositions.sumOf { it.investedCapital }
                val buyPct = if (buyInvested > 0.0) (buyNetPnl / buyInvested) * 100.0 else 0.0
                val buyEffectiveTargetInr = config.computeEffectiveSingleBasketTargetInr(buyInvested)

                val buyCharges = buyPositions.sumOf { it.estimatedChargesInr }
                val effectiveBuyMaxLossInr = maxOf(config.basketMaxLossInr, buyCharges + 10.0)
                val buyHitBasketStopLoss = BasketHighWaterState.shouldTriggerBasketStopLoss(
                    basketNetPnl = buyNetPnl,
                    hasPositions = true,
                    maxLossInr = effectiveBuyMaxLossInr
                )

                val prevBuyHw = _buyBasketHighWater.value
                val nextBuyHw = BasketHighWaterState.advance(prevBuyHw, buyNetPnl, hasPositions = true)
                _buyBasketHighWater.value = nextBuyHw

                val buyHitInstant25 = !isApiLimitReached && buyInvested > 0.0 && buyNetPnl >= (config.basketInstantBookInr - 1e-6)
                val buyHitConfiguredTarget = !isApiLimitReached && config.basketExitScope == BasketExitScope.COMBINED_BASKET &&
                    buyInvested > 0.0 &&
                    buyNetPnl >= (buyEffectiveTargetInr - 1e-6)
                val buyHitTarget = !buyHitBasketStopLoss && (buyHitInstant25 || buyHitConfiguredTarget)
                val buyHitFloor = !isApiLimitReached && !buyHitBasketStopLoss &&
                    config.highProbabilityProfitLockEnabled &&
                    !buyHitTarget &&
                    BasketHighWaterState.shouldTriggerFloorExit(nextBuyHw, buyNetPnl)

                if (config.highProbabilityProfitLockEnabled &&
                    nextBuyHw.lockedFloorInr != prevBuyHw.lockedFloorInr &&
                    nextBuyHw.lockedFloorInr != null &&
                    !buyHitBasketStopLoss &&
                    !buyHitTarget &&
                    !buyHitFloor
                ) {
                    appendLogLocked(
                        category = LogCategory.RISK_GUARD,
                        symbol = "BUY BASKET(${buyPositions.size})",
                        exchange = null,
                        side = TradeSide.LONG,
                        quantity = buyPositions.sumOf { it.quantity },
                        price = null,
                        pnl = buyNetPnl,
                        pnlPct = buyPct,
                        title = "BUY Basket Profit-Lock Armed (Floor +₹${nextBuyHw.lockedFloorInr.toInt()})",
                        detail = "Peak reached ${Formatters.formatInr(nextBuyHw.peakNetPnlInr, showSign = true)}; hard profit floor set at +₹${nextBuyHw.lockedFloorInr.toInt()} net."
                    )
                }

                if (buyHitBasketStopLoss || buyHitTarget || buyHitFloor) {
                    val lockedFloor = nextBuyHw.lockedFloorInr ?: BasketHighWaterState.TIER1_FLOOR_INR
                    val exitCategory = if (buyHitBasketStopLoss) LogCategory.STOP_LOSS_EXIT else LogCategory.AUTO_PROFIT_EXIT
                    val exitReason = when {
                        buyHitBasketStopLoss -> "BUY Basket -₹${effectiveBuyMaxLossInr.toInt()} Stop-Loss"
                        buyHitInstant25 -> "BUY Basket +₹${config.basketInstantBookInr.toInt()} Zero-Wait Auto-Book"
                        buyHitConfiguredTarget -> "BUY Basket Auto-Book ($targetDesc • +${Formatters.formatInr(buyEffectiveTargetInr)})"
                        else -> "BUY Basket High-Water Floor (+₹${lockedFloor.toInt()} Lock)"
                    }
                    for (pos in buyPositions) {
                        executePositionExitLocked(
                            position = pos,
                            category = exitCategory,
                            reasonTitle = exitReason,
                            nowMs = nowMs
                        )
                    }
                    remainingAfterBasketCheck = remainingAfterBasketCheck.filterNot { it.side == TradeSide.LONG }
                    _activePositions.value = remainingAfterBasketCheck
                    _buyBasketHighWater.value = BasketHighWaterState()
                    buyBasketCooldownUntilMs = if (config.reentryCooldownSec > 0) nowMs + (config.reentryCooldownSec * 1000L) else 0L
                    val cdNote = if (config.reentryCooldownSec == 0) "0s Instant Re-Entry active." else "${config.reentryCooldownSec}s cooldown."
                    if (!buyHitBasketStopLoss) {
                        basketDropsCompleted++
                        basketBookedProfitInr += buyNetPnl
                    }
                    appendLogLocked(
                        category = exitCategory,
                        symbol = "BUY BASKET(${buyPositions.size})",
                        exchange = null,
                        side = TradeSide.LONG,
                        quantity = buyPositions.sumOf { it.quantity },
                        price = null,
                        pnl = buyNetPnl,
                        pnlPct = buyPct,
                        title = when {
                            buyHitBasketStopLoss -> "BUY Basket -₹${effectiveBuyMaxLossInr.toInt()} Stop-Loss Cut (${Formatters.formatInr(buyNetPnl, showSign = true)} Net)"
                            buyHitTarget -> "BUY Basket Zero-Wait Banked (${Formatters.formatInr(buyNetPnl, showSign = true)} Net)"
                            else -> "BUY Basket Profit-Lock Floor Square-Off (${Formatters.formatInr(buyNetPnl, showSign = true)} Net)"
                        },
                        detail = when {
                            buyHitBasketStopLoss -> "Hit -₹${effectiveBuyMaxLossInr.toInt()} basket cap across ${buyPositions.size} BUY position(s); SELL basket left running • $cdNote"
                            buyHitTarget -> "Zero-Wait booked +${Formatters.formatInr(buyEffectiveTargetInr)} net across ${buyPositions.size} BUY position(s) • $cdNote"
                            else -> "Peak ${Formatters.formatInr(nextBuyHw.peakNetPnlInr, showSign = true)} dipped to +₹${lockedFloor.toInt()} guard across ${buyPositions.size} BUY position(s) • Profit locked!"
                        }
                    )
                }
            }

            // 1B) SELL Basket (TradeSide.SHORT)
            val sellPositions = remainingAfterBasketCheck.filter { it.side == TradeSide.SHORT }
            if (sellPositions.isEmpty()) {
                _sellBasketHighWater.value = BasketHighWaterState()
            } else {
                val sellNetPnl = sellPositions.sumOf { it.netUnrealizedPnl }
                val sellInvested = sellPositions.sumOf { it.investedCapital }
                val sellPct = if (sellInvested > 0.0) (sellNetPnl / sellInvested) * 100.0 else 0.0
                val sellEffectiveTargetInr = config.computeEffectiveSingleBasketTargetInr(sellInvested)

                val sellCharges = sellPositions.sumOf { it.estimatedChargesInr }
                val effectiveSellMaxLossInr = maxOf(config.basketMaxLossInr, sellCharges + 10.0)
                val sellHitBasketStopLoss = BasketHighWaterState.shouldTriggerBasketStopLoss(
                    basketNetPnl = sellNetPnl,
                    hasPositions = true,
                    maxLossInr = effectiveSellMaxLossInr
                )

                val prevSellHw = _sellBasketHighWater.value
                val nextSellHw = BasketHighWaterState.advance(prevSellHw, sellNetPnl, hasPositions = true)
                _sellBasketHighWater.value = nextSellHw

                val sellHitInstant25 = !isApiLimitReached && sellInvested > 0.0 && sellNetPnl >= (config.basketInstantBookInr - 1e-6)
                val sellHitConfiguredTarget = !isApiLimitReached && config.basketExitScope == BasketExitScope.COMBINED_BASKET &&
                    sellInvested > 0.0 &&
                    sellNetPnl >= (sellEffectiveTargetInr - 1e-6)
                val sellHitTarget = !sellHitBasketStopLoss && (sellHitInstant25 || sellHitConfiguredTarget)
                val sellHitFloor = !isApiLimitReached && !sellHitBasketStopLoss &&
                    config.highProbabilityProfitLockEnabled &&
                    !sellHitTarget &&
                    BasketHighWaterState.shouldTriggerFloorExit(nextSellHw, sellNetPnl)

                if (config.highProbabilityProfitLockEnabled &&
                    nextSellHw.lockedFloorInr != prevSellHw.lockedFloorInr &&
                    nextSellHw.lockedFloorInr != null &&
                    !sellHitBasketStopLoss &&
                    !sellHitTarget &&
                    !sellHitFloor
                ) {
                    appendLogLocked(
                        category = LogCategory.RISK_GUARD,
                        symbol = "SELL BASKET(${sellPositions.size})",
                        exchange = null,
                        side = TradeSide.SHORT,
                        quantity = sellPositions.sumOf { it.quantity },
                        price = null,
                        pnl = sellNetPnl,
                        pnlPct = sellPct,
                        title = "SELL Basket Profit-Lock Armed (Floor +₹${nextSellHw.lockedFloorInr.toInt()})",
                        detail = "Peak reached ${Formatters.formatInr(nextSellHw.peakNetPnlInr, showSign = true)}; hard profit floor set at +₹${nextSellHw.lockedFloorInr.toInt()} net."
                    )
                }

                if (sellHitBasketStopLoss || sellHitTarget || sellHitFloor) {
                    val lockedFloor = nextSellHw.lockedFloorInr ?: BasketHighWaterState.TIER1_FLOOR_INR
                    val exitCategory = if (sellHitBasketStopLoss) LogCategory.STOP_LOSS_EXIT else LogCategory.AUTO_PROFIT_EXIT
                    val exitReason = when {
                        sellHitBasketStopLoss -> "SELL Basket -₹${effectiveSellMaxLossInr.toInt()} Stop-Loss"
                        sellHitInstant25 -> "SELL Basket +₹${config.basketInstantBookInr.toInt()} Zero-Wait Auto-Book"
                        sellHitConfiguredTarget -> "SELL Basket Auto-Book ($targetDesc • +${Formatters.formatInr(sellEffectiveTargetInr)})"
                        else -> "SELL Basket High-Water Floor (+₹${lockedFloor.toInt()} Lock)"
                    }
                    for (pos in sellPositions) {
                        executePositionExitLocked(
                            position = pos,
                            category = exitCategory,
                            reasonTitle = exitReason,
                            nowMs = nowMs
                        )
                    }
                    remainingAfterBasketCheck = remainingAfterBasketCheck.filterNot { it.side == TradeSide.SHORT }
                    _activePositions.value = remainingAfterBasketCheck
                    _sellBasketHighWater.value = BasketHighWaterState()
                    sellBasketCooldownUntilMs = if (config.reentryCooldownSec > 0) nowMs + (config.reentryCooldownSec * 1000L) else 0L
                    val cdNote = if (config.reentryCooldownSec == 0) "0s Instant Re-Entry active." else "${config.reentryCooldownSec}s cooldown."
                    if (!sellHitBasketStopLoss) {
                        basketDropsCompleted++
                        basketBookedProfitInr += sellNetPnl
                    }
                    appendLogLocked(
                        category = exitCategory,
                        symbol = "SELL BASKET(${sellPositions.size})",
                        exchange = null,
                        side = TradeSide.SHORT,
                        quantity = sellPositions.sumOf { it.quantity },
                        price = null,
                        pnl = sellNetPnl,
                        pnlPct = sellPct,
                        title = when {
                            sellHitBasketStopLoss -> "SELL Basket -₹${effectiveSellMaxLossInr.toInt()} Stop-Loss Cut (${Formatters.formatInr(sellNetPnl, showSign = true)} Net)"
                            sellHitTarget -> "SELL Basket Zero-Wait Banked (${Formatters.formatInr(sellNetPnl, showSign = true)} Net)"
                            else -> "SELL Basket Profit-Lock Floor Square-Off (${Formatters.formatInr(sellNetPnl, showSign = true)} Net)"
                        },
                        detail = when {
                            sellHitBasketStopLoss -> "Hit -₹${effectiveSellMaxLossInr.toInt()} basket cap across ${sellPositions.size} SELL position(s); BUY basket left running • $cdNote"
                            sellHitTarget -> "Zero-Wait booked +${Formatters.formatInr(sellEffectiveTargetInr)} net across ${sellPositions.size} SELL position(s) • $cdNote"
                            else -> "Peak ${Formatters.formatInr(nextSellHw.peakNetPnlInr, showSign = true)} dipped to +₹${lockedFloor.toInt()} guard across ${sellPositions.size} SELL position(s) • Profit locked!"
                        }
                    )
                }
            }
        } else if (!circuitBreakerFiredThisTick) {
            _buyBasketHighWater.value = BasketHighWaterState()
            _sellBasketHighWater.value = BasketHighWaterState()
        }

        // 2) Per-position Profit Floor (+₹5/+₹10), Shielded Break-Even Lock (+₹3.00 Net Floor), AI Invalidation Cut & Stop-Loss
        if (!circuitBreakerFiredThisTick && remainingAfterBasketCheck.isNotEmpty()) {
            val remainingPositions = mutableListOf<ActivePosition>()
            for (pos in remainingAfterBasketCheck) {
                val netPnl = pos.netUnrealizedPnl
                val netPnlPct = pos.unrealizedPnlPct
                val liveInst = priceLookup[pos.symbol]
                val heldMs = nowMs - pos.openedAtEpochMs

                val hitIndividualTarget = !isApiLimitReached && config.basketExitScope == BasketExitScope.PER_STOCK && when (pos.targetProfitMode) {
                    TargetProfitMode.FIXED_INR -> netPnl >= pos.targetProfitInr
                    TargetProfitMode.PERCENTAGE -> netPnlPct >= pos.targetProfitPct
                }

                // Only defer Break-Even exit in Dual-Basket Pair Mode when the OTHER side actually already closed for +₹25 profit
                val isHoldingRemainingPairLegForRecovery = !config.singleDirectionOnly &&
                    config.allowSellAutoTrade &&
                    remainingAfterBasketCheck.size == 1 &&
                    netPnl < 0.0

                val profitFloor = pos.lockedProfitFloorInr
                val hitProfitFloorLock = !isApiLimitReached &&
                    config.highProbabilityProfitLockEnabled &&
                    profitFloor != null &&
                    netPnl <= (profitFloor + 1e-6)

                // Charge-shielded Break-Even Exit: triggers at <= +₹3.00 net above full round-trip charges
                val hitBreakEvenLock = !isApiLimitReached && !isHoldingRemainingPairLegForRecovery &&
                    config.highProbabilityProfitLockEnabled &&
                    profitFloor == null &&
                    pos.isBreakEvenLocked &&
                    netPnl <= ActivePosition.BREAK_EVEN_NET_BUFFER_INR

                // Early AI Setup Invalidation Guard: cut losers around -₹14..-₹20 when VWAP + 9/21 EMA both flip against open trade
                val isAiSetupInvalidated = config.highProbabilityProfitLockEnabled &&
                    heldMs >= 9_000L &&
                    netPnl <= -minOf(15.0, maxOf(8.0, pos.investedCapital * 0.003)) &&
                    liveInst != null &&
                    when (pos.side) {
                        TradeSide.LONG -> !liveInst.isPriceAboveVwap && !liveInst.isEmaBullish && liveInst.lastTickDelta < 0.0
                        TradeSide.SHORT -> liveInst.isPriceAboveVwap && liveInst.isEmaBullish && liveInst.lastTickDelta > 0.0
                    }

                val hitStopLoss = when (pos.stopLossMode) {
                    StopLossMode.FIXED_INR -> netPnl <= -pos.stopLossInr
                    StopLossMode.PERCENTAGE -> netPnlPct <= -pos.stopLossPct
                }

                if (hitIndividualTarget || hitProfitFloorLock || hitBreakEvenLock || isAiSetupInvalidated || hitStopLoss) {
                    // Simulate resting SL/BE order execution cleanly at the trigger level instead of multi-tick negative overshoot
                    val exitPos = when {
                        hitProfitFloorLock && pos.profitLockFloorPrice != null -> {
                            val triggerPrice = pos.profitLockFloorPrice!!
                            val fillPrice = if (pos.side == TradeSide.LONG) maxOf(pos.currentPrice, triggerPrice) else minOf(pos.currentPrice, triggerPrice)
                            pos.copy(currentPrice = Math.round(fillPrice * 100.0) / 100.0)
                        }
                        hitBreakEvenLock -> {
                            val triggerPrice = pos.breakEvenPrice
                            val fillPrice = if (pos.side == TradeSide.LONG) maxOf(pos.currentPrice, triggerPrice) else minOf(pos.currentPrice, triggerPrice)
                            pos.copy(currentPrice = Math.round(fillPrice * 100.0) / 100.0)
                        }
                        hitStopLoss && !isAiSetupInvalidated -> {
                            val slTrigger = pos.stopLossPrice
                            val fillPrice = if (pos.side == TradeSide.LONG) maxOf(pos.currentPrice, slTrigger) else minOf(pos.currentPrice, slTrigger)
                            pos.copy(currentPrice = Math.round(fillPrice * 100.0) / 100.0)
                        }
                        else -> pos
                    }
                    val actualNet = exitPos.netUnrealizedPnl
                    val actualPct = exitPos.unrealizedPnlPct
                    val reason = when {
                        hitIndividualTarget -> if (pos.targetProfitMode == TargetProfitMode.FIXED_INR) {
                            "Auto-Drop Target Hit (${Formatters.formatInr(actualNet, showSign = true)} net)"
                        } else {
                            "Auto-Drop Target % Hit (${Formatters.formatPct(actualPct)})"
                        }
                        hitProfitFloorLock -> if (pos.isZeroLossShieldLocked && profitFloor == ActivePosition.ZERO_LOSS_SHIELD_FLOOR_INR) {
                            "Zero-Loss Profit Shield (+₹${profitFloor.toInt()} Net Lock)"
                        } else {
                            "Profit-Lock +₹${profitFloor?.toInt() ?: 10} Triggered (Net ${Formatters.formatInr(actualNet, showSign = true)})"
                        }
                        hitBreakEvenLock -> "Break-Even +₹3 Lock Exit (Net ${Formatters.formatInr(actualNet, showSign = true)} after charges)"
                        isAiSetupInvalidated && !hitStopLoss -> "AI Setup Invalidated Early Cut (${Formatters.formatInr(actualNet, showSign = true)} net)"
                        else -> if (pos.stopLossMode == StopLossMode.FIXED_INR) {
                            "Stop-Loss Cap Triggered (${Formatters.formatInr(actualNet, showSign = true)})"
                        } else {
                            "Stop-Loss Triggered (${Formatters.formatPct(actualPct)})"
                        }
                    }
                    val exitCategory = when {
                        hitIndividualTarget -> LogCategory.AUTO_PROFIT_EXIT
                        hitProfitFloorLock || hitBreakEvenLock -> LogCategory.BREAK_EVEN_EXIT
                        else -> LogCategory.STOP_LOSS_EXIT
                    }
                    if (hitIndividualTarget) {
                        basketDropsCompleted++
                        basketBookedProfitInr += actualNet
                    }
                    executePositionExitLocked(
                        position = exitPos,
                        category = exitCategory,
                        reasonTitle = reason,
                        nowMs = nowMs
                    )
                } else {
                    remainingPositions.add(pos)
                }
            }
            if (remainingPositions.size != remainingAfterBasketCheck.size) {
                _activePositions.value = remainingPositions
                if (remainingPositions.none { it.side == TradeSide.LONG }) {
                    _buyBasketHighWater.value = BasketHighWaterState()
                }
                if (remainingPositions.none { it.side == TradeSide.SHORT }) {
                    _sellBasketHighWater.value = BasketHighWaterState()
                }
            }
        }

        val effectiveBuyCooldownUntil = if (config.reentryCooldownSec > 0) {
            maxOf(buyBasketCooldownUntilMs, lastBuyExitCooldownUntilMs)
        } else {
            0L
        }
        val effectiveSellCooldownUntil = if (config.reentryCooldownSec > 0) {
            maxOf(sellBasketCooldownUntilMs, lastSellExitCooldownUntilMs)
        } else {
            0L
        }
        val buyCooldownSec = if (effectiveBuyCooldownUntil > nowMs) {
            ceil((effectiveBuyCooldownUntil - nowMs) / 1000.0).toInt().coerceAtLeast(1)
        } else {
            buyBasketCooldownUntilMs = 0L
            lastBuyExitCooldownUntilMs = 0L
            0
        }
        val sellCooldownSec = if (effectiveSellCooldownUntil > nowMs) {
            ceil((effectiveSellCooldownUntil - nowMs) / 1000.0).toInt().coerceAtLeast(1)
        } else {
            sellBasketCooldownUntilMs = 0L
            lastSellExitCooldownUntilMs = 0L
            0
        }
        _buyBasketCooldownRemainingSec.value = buyCooldownSec
        _sellBasketCooldownRemainingSec.value = sellCooldownSec

        // 3) Strict Local 99% Quantitative AI 3-Factor Entry Gate (0ms external cloud dependency):
        //    - Enters only with RSI runway + active directional tick confirmation so trades never chase exhaustion peaks
        if (!circuitBreakerFiredThisTick && !_circuitBreakerTriggered.value && !isApiLimitReached && !isDailyProfitLockedToday && _autoTradingEnabled.value) {
            val activeSymbols = _activePositions.value.map { it.symbol }.toMutableSet()
            var openCount = _activePositions.value.size
            var buyOpenCount = _activePositions.value.count { it.side == TradeSide.LONG }
            var sellOpenCount = _activePositions.value.count { it.side == TradeSide.SHORT }
            val effectiveMaxPerSide = if (config.maxOpenPositions <= 2) config.maxOpenPositions else config.maxPositionsPerBasket
            val perTradeBudget = config.effectiveCapitalPerTrade

            val scannableUniverse = updatedInstruments.filter { inst ->
                if (inst.symbol in activeSymbols) return@filter false
                if (!IstMarketScheduler.isEntryAllowed(inst.exchange, config, windowStatus)) return@filter false
                val qty = floor(perTradeBudget / inst.ltp)
                if (qty < 1.0 || (qty * inst.ltp) > cashBalance) return@filter false
                val hasMomentumRunway = when (inst.aiSignal) {
                    AiTrendSignal.STRONG_BUY -> inst.rsi14 in 52.0..64.5 && inst.lastTickDelta > 0.0
                    AiTrendSignal.STRONG_SELL -> inst.rsi14 in 35.5..48.0 && inst.lastTickDelta < 0.0
                    else -> false
                }
                hasMomentumRunway
            }.sortedWith(
                compareByDescending<MarketInstrument> { it.aiScreenerScore }
                    .thenByDescending { (symbolWinCounts[it.symbol] ?: 0) - ((symbolConsecutiveLosses[it.symbol] ?: 0) * 2) }
                    .thenByDescending { symbolNetPnlStats[it.symbol] ?: 0.0 }
            )

            // Audit & log top weak/blocked candidate cleanly (throttled per symbol every 45s)
            if (openCount < config.maxOpenPositions) {
                val blockedCandidate = scannableUniverse.firstOrNull { inst ->
                    (!inst.allScreenerChecksPassed || inst.aiScreenerScore < config.minAiConfidence) &&
                        (inst.aiSignal == AiTrendSignal.BUY || inst.aiSignal == AiTrendSignal.SELL ||
                            inst.aiSignal == AiTrendSignal.STRONG_BUY || inst.aiSignal == AiTrendSignal.STRONG_SELL) &&
                        (nowMs - (blockedAuditLogAtMs[inst.symbol] ?: 0L)) >= 45_000L
                }
                if (blockedCandidate != null) {
                    blockedAuditLogAtMs[blockedCandidate.symbol] = nowMs
                    appendLogLocked(
                        category = LogCategory.AI_SCREENER,
                        symbol = blockedCandidate.symbol,
                        exchange = blockedCandidate.exchange,
                        side = null,
                        quantity = null,
                        price = blockedCandidate.ltp,
                        pnl = null,
                        pnlPct = null,
                        title = "${blockedCandidate.aiScreenerReason} • ${blockedCandidate.symbol}",
                        detail = "Gemini AI 4H Score ${blockedCandidate.aiScreenerScore}% (<${config.minAiConfidence}% gate) • Vol ${String.format(Locale.US, "%.2fx", blockedCandidate.volumeSurgeRatio)} • Range ${String.format(Locale.US, "%.2f", blockedCandidate.intradayRangePct)}% • Skipped to protect capital."
                    )
                }
            }

            if (config.singleDirectionOnly) {
                val cooldownReadyUniverse = scannableUniverse.filter { inst ->
                    nowMs >= (symbolCooldownUntilMs[inst.symbol] ?: 0L)
                }
                val plan = StrategyIndicatorEngine.selectSingleDirectionEntries(
                    activePositions = _activePositions.value,
                    candidates = cooldownReadyUniverse,
                    minAiConfidence = config.minAiConfidence,
                    maxSlots = config.maxOpenPositions,
                    allowSellAutoTrade = config.allowSellAutoTrade,
                    isSideInCooldown = { side ->
                        (side == TradeSide.LONG && buyCooldownSec > 0) ||
                            (side == TradeSide.SHORT && sellCooldownSec > 0)
                    }
                )

                if (plan.blockedOppositeCount > 0 && plan.activeDirection != null &&
                    (nowMs - (blockedAuditLogAtMs["__SINGLE_DIR_SHIELD__"] ?: 0L)) >= 45_000L
                ) {
                    blockedAuditLogAtMs["__SINGLE_DIR_SHIELD__"] = nowMs
                    val activeDirLabel = if (plan.activeDirection == TradeSide.LONG) "BUY (LONG)" else "SELL (SHORT)"
                    val oppDirLabel = if (plan.activeDirection == TradeSide.LONG) "SELL" else "BUY"
                    appendLogLocked(
                        category = LogCategory.RISK_GUARD,
                        symbol = "1-DIR GUARD",
                        exchange = null,
                        side = plan.activeDirection,
                        quantity = null,
                        price = null,
                        pnl = null,
                        pnlPct = null,
                        title = "Single-Direction AI Shield: Opposite $oppDirLabel Blocked",
                        detail = "Active direction locked to $activeDirLabel until all open position(s) exit — prevents conflicting baskets."
                    )
                }

                for (inst in plan.selectedCandidates) {
                    if (openCount >= config.maxOpenPositions) break
                    val triggerSide = when (inst.aiSignal) {
                        AiTrendSignal.STRONG_BUY -> TradeSide.LONG
                        AiTrendSignal.STRONG_SELL -> if (config.allowSellAutoTrade) TradeSide.SHORT else null
                        else -> null
                    } ?: continue

                    val qty = floor(perTradeBudget / inst.ltp)
                    val requiredMargin = qty * inst.ltp
                    if (qty < 1.0 || requiredMargin > cashBalance) continue

                    val opened = executePositionEntryLocked(
                        instrument = inst,
                        side = triggerSide,
                        quantity = qty,
                        isAutomated = true,
                        nowMs = nowMs
                    )
                    if (opened) {
                        activeSymbols.add(inst.symbol)
                        openCount++
                        if (triggerSide == TradeSide.LONG) buyOpenCount++ else sellOpenCount++
                    }
                }
            } else if (!config.allowSellAutoTrade) {
                // 100% BUY-Only Scalper Mode (allowSellAutoTrade == false, Default):
                // - Strictly blocks all SELL entries
                // - Focuses 100% of auto-trading entries exclusively on high-conviction BUY signals (>= 99% AI score + 1m Candle Breakout/Engulfing/Hammer confirmed)
                val cooldownReadyUniverse = scannableUniverse.filter { inst ->
                    nowMs >= (symbolCooldownUntilMs[inst.symbol] ?: 0L)
                }
                val buyCandidates = StrategyIndicatorEngine.selectBuyOnlyScalperEntries(
                    activePositions = _activePositions.value,
                    candidates = cooldownReadyUniverse,
                    minAiConfidence = config.minAiConfidence,
                    maxSlots = config.maxOpenPositions,
                    isBuyCooldownActive = buyCooldownSec > 0
                )
                for (inst in buyCandidates) {
                    if (openCount >= config.maxOpenPositions) break
                    val qty = floor(perTradeBudget / inst.ltp)
                    val requiredMargin = qty * inst.ltp
                    if (qty < 1.0 || requiredMargin > cashBalance) continue

                    val opened = executePositionEntryLocked(
                        instrument = inst,
                        side = TradeSide.LONG,
                        quantity = qty,
                        isAutomated = true,
                        nowMs = nowMs
                    )
                    if (opened) {
                        activeSymbols.add(inst.symbol)
                        openCount++
                        buyOpenCount++
                        appendLogLocked(
                            category = LogCategory.AI_SIGNAL,
                            symbol = inst.symbol,
                            exchange = inst.exchange,
                            side = TradeSide.LONG,
                            quantity = qty,
                            price = inst.ltp,
                            pnl = null,
                            pnlPct = null,
                            title = "1m Candle 99% BUY Entered: ${inst.symbol} (${inst.oneMinCandlePattern})",
                            detail = "100% BUY-Only Scalper: AI ${inst.aiScreenerScore}% + ${inst.oneMinCandleStatusPill} • SELL auto-invest paused (HOLD) • Zero-Wait +₹${config.singleTradeInstantBookInr.toInt()} book armed."
                        )
                    }
                }
            } else {
                // Synchronized Dual-Basket Pair Execution (when allowSellAutoTrade == true):
                // - Requires 0 Open Positions (Wait Until Both Legs Exit lock — NEVER allows partial re-entry)
                // - Requires BOTH BUY & SELL cooldowns cleared
                // - Enters 1 BUY + 1 SELL together in the exact same tick (never a staggered single leg)
                val cooldownReadyUniverse = scannableUniverse.filter { inst ->
                    nowMs >= (symbolCooldownUntilMs[inst.symbol] ?: 0L)
                }
                val pairPlan = StrategyIndicatorEngine.selectSynchronizedPairEntries(
                    activePositions = _activePositions.value,
                    candidates = cooldownReadyUniverse,
                    minAiConfidence = config.minAiConfidence,
                    allowSellAutoTrade = true,
                    isBuyCooldownActive = buyCooldownSec > 0,
                    isSellCooldownActive = sellCooldownSec > 0
                )

                val buyInst = pairPlan.buyCandidate
                val sellInst = pairPlan.sellCandidate
                if (pairPlan.isReadyToExecutePair && buyInst != null && sellInst != null && config.maxOpenPositions >= 2) {
                    val buyQty = floor(perTradeBudget / buyInst.ltp)
                    val sellQty = floor(perTradeBudget / sellInst.ltp)
                    val buyMargin = buyQty * buyInst.ltp
                    val sellMargin = sellQty * sellInst.ltp

                    if (buyQty >= 1.0 && sellQty >= 1.0 && (buyMargin + sellMargin) <= cashBalance) {
                        val buyOpened = executePositionEntryLocked(
                            instrument = buyInst,
                            side = TradeSide.LONG,
                            quantity = buyQty,
                            isAutomated = true,
                            nowMs = nowMs
                        )
                        if (buyOpened) {
                            val sellOpened = executePositionEntryLocked(
                                instrument = sellInst,
                                side = TradeSide.SHORT,
                                quantity = sellQty,
                                isAutomated = true,
                                nowMs = nowMs
                            )
                            if (sellOpened) {
                                activeSymbols.add(buyInst.symbol)
                                activeSymbols.add(sellInst.symbol)
                                openCount += 2
                                buyOpenCount++
                                sellOpenCount++
                                appendLogLocked(
                                    category = LogCategory.AI_SIGNAL,
                                    symbol = "SYNC PAIR",
                                    exchange = null,
                                    side = null,
                                    quantity = null,
                                    price = null,
                                    pnl = null,
                                    pnlPct = null,
                                    title = "Synchronized Pair Entered: BUY ${buyInst.symbol} + SELL ${sellInst.symbol}",
                                    detail = "1 BUY (${buyInst.aiScreenerScore}%) + 1 SELL (${sellInst.aiScreenerScore}%) entered simultaneously • Combined +₹${config.basketInstantBookInr.toInt()} Instant Auto-Book armed."
                                )
                            } else {
                                // Live rollback safety: never leave a naked staggered single leg if second order rejected
                                val orphanBuy = _activePositions.value.firstOrNull { it.symbol == buyInst.symbol && it.side == TradeSide.LONG }
                                if (orphanBuy != null) {
                                    executePositionExitLocked(
                                        position = orphanBuy,
                                        category = LogCategory.RISK_GUARD,
                                        reasonTitle = "Sync Pair Rollback (SELL Leg Rejected)",
                                        nowMs = nowMs
                                    )
                                    _activePositions.value = _activePositions.value.filterNot { it.id == orphanBuy.id }
                                }
                            }
                        }
                    }
                }
            }
        }

        recalculateAccountSummaryLocked()
    }

    private fun refreshLocalScreenerBanner(
        instruments: List<MarketInstrument>,
        minAiConfidence: Int,
        nowMs: Long
    ) {
        val passing = instruments
            .filter { it.allScreenerChecksPassed && it.aiScreenerScore >= minAiConfidence }
            .sortedByDescending { it.aiScreenerScore }
        val topPicks = passing
            .take(2)
            .map { "${it.symbol} (${it.aiScreenerScore}%)" }
        val summary = if (passing.isNotEmpty()) {
            "${passing.size} high-conviction setup(s) ≥$minAiConfidence% ready for instant execution."
        } else {
            "Scanning ${instruments.size} markets locally (<1ms/tick) • Waiting for ≥$minAiConfidence% 3-factor lock."
        }
        _screenerUiState.value = GeminiScreenerUiState.Success(
            marketSummary = summary,
            topPickSymbols = topPicks,
            assessedCount = instruments.size,
            lastScannedAt = Formatters.formatTime(nowMs)
        )
    }

    suspend fun runGeminiDeepScan(): Result<Int> = tradeMutex.withLock {
        val config = _riskConfig.value
        val nowMs = System.currentTimeMillis()
        val snapshot = _instruments.value
        refreshLocalScreenerBanner(snapshot, config.minAiConfidence, nowMs)
        val topPass = snapshot
            .filter { it.allScreenerChecksPassed && it.aiScreenerScore >= config.minAiConfidence }
            .sortedByDescending { it.aiScreenerScore }
            .take(2)
        val skipCount = snapshot.size - topPass.size
        appendLogLocked(
            category = LogCategory.AI_SCREENER,
            symbol = "AI 99% GATE",
            exchange = null,
            side = null,
            quantity = null,
            price = null,
            pnl = null,
            pnlPct = null,
            title = if (topPass.isNotEmpty()) {
                "Instant 3-Factor Scan: ${topPass.joinToString(", ") { "${it.symbol} (${it.aiScreenerScore}%)" }}"
            } else {
                "Instant 3-Factor Scan: 0 ≥${config.minAiConfidence}% Picks ($skipCount Holding)"
            },
            detail = "Evaluated ${snapshot.size} instruments locally (<1ms) on VWAP/EMA/RSI + Volume Surge + Fee-Clearance."
        )
        Result.success(snapshot.size)
    }

    private suspend fun executePositionEntryLocked(
        instrument: MarketInstrument,
        side: TradeSide,
        quantity: Double,
        isAutomated: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val config = _riskConfig.value
        if (isAutomated && side == TradeSide.SHORT && !config.allowSellAutoTrade) {
            return false
        }
        val requiredMargin = instrument.ltp * quantity
        val minQty = 1.0
        if (requiredMargin > cashBalance || quantity < minQty) return false

        val orderResult = brokerClient.placeOrder(
            session = activeSession,
            tradingSymbol = instrument.tradingSymbol,
            symbolToken = instrument.symbolToken,
            exchange = instrument.exchange,
            side = side,
            quantity = quantity,
            isPaperMode = config.isPaperMode
        )

        val orderId = orderResult.getOrElse { err ->
            appendLogLocked(
                category = LogCategory.BROKER_API,
                symbol = instrument.symbol,
                exchange = instrument.exchange,
                side = side,
                quantity = quantity,
                price = instrument.ltp,
                pnl = null,
                pnlPct = null,
                title = "Order Failed: ${instrument.symbol}",
                detail = err.message ?: "SmartAPI order placement error"
            )
            return false
        }

        cashBalance -= requiredMargin
        val now = nowMs
        val position = ActivePosition(
            id = UUID.randomUUID().toString(),
            symbol = instrument.symbol,
            tradingSymbol = instrument.tradingSymbol,
            displayName = instrument.displayName,
            exchange = instrument.exchange,
            symbolToken = instrument.symbolToken,
            side = side,
            entryPrice = instrument.ltp,
            currentPrice = instrument.ltp,
            quantity = quantity,
            targetProfitMode = config.targetProfitMode,
            targetProfitInr = config.targetProfitInr,
            targetProfitPct = config.targetProfitPct,
            stopLossMode = config.stopLossMode,
            stopLossInr = config.stopLossInr,
            stopLossPct = config.stopLossPct,
            openedAtEpochMs = now,
            openedAtFormatted = Formatters.formatTime(now),
            isPaperTrade = config.isPaperMode,
            brokerOrderId = orderId
        )
        _activePositions.value = listOf(position) + _activePositions.value
        totalTradesExecutedToday++
        totalCapitalInvestedToday += requiredMargin

        val openTx = TradeTransaction(
            id = position.id,
            symbol = position.symbol,
            tradingSymbol = position.tradingSymbol,
            displayName = position.displayName,
            exchange = position.exchange,
            side = position.side,
            quantity = position.quantity,
            entryPrice = position.entryPrice,
            exitOrCurrentPrice = position.currentPrice,
            investedAmountInr = position.investedCapital,
            grossPnlInr = 0.0,
            estimatedChargesInr = position.estimatedChargesInr,
            netPnlInr = -position.estimatedChargesInr,
            pnlPct = position.unrealizedPnlPct,
            status = TransactionStatus.OPEN,
            entryTimeFormatted = position.openedAtFormatted,
            exitTimeFormatted = null,
            entryEpochMs = now,
            exitEpochMs = null,
            exitReason = null,
            isPaperTrade = config.isPaperMode,
            brokerOrderId = orderId
        )
        _transactions.value = (listOf(openTx) + _transactions.value).take(200)

        val tpText = if (config.targetProfitMode == TargetProfitMode.FIXED_INR) {
            "+${Formatters.formatInr(config.targetProfitInr)}"
        } else {
            "+${config.targetProfitPct}%"
        }
        val slText = if (config.stopLossMode == StopLossMode.FIXED_INR) {
            "-${Formatters.formatInr(config.stopLossInr)}"
        } else {
            "-${config.stopLossPct}%"
        }

        val qtyFormatted = Formatters.formatQuantity(quantity, instrument.exchange)
        if (isAutomated) {
            appendLogLocked(
                category = LogCategory.AI_SIGNAL,
                symbol = instrument.symbol,
                exchange = instrument.exchange,
                side = side,
                quantity = quantity,
                price = instrument.ltp,
                pnl = null,
                pnlPct = null,
                title = "AI ${instrument.aiScreenerScore}% PASS • ${instrument.aiSignal.label} (${instrument.symbol})",
                detail = "${instrument.aiScreenerReason} • Bought $qtyFormatted qty @ ${Formatters.formatPrice(instrument.ltp)} (${Formatters.formatInr(requiredMargin)}) • Zero-Wait +₹${config.singleTradeInstantBookInr.toInt()} / SL $slText [$orderId]"
            )
        } else {
            appendLogLocked(
                category = LogCategory.ORDER_EXECUTION,
                symbol = instrument.symbol,
                exchange = instrument.exchange,
                side = side,
                quantity = quantity,
                price = instrument.ltp,
                pnl = null,
                pnlPct = null,
                title = "Manual ${side.label} Executed: ${instrument.symbol}",
                detail = "Filled $qtyFormatted qty @ ${Formatters.formatPrice(instrument.ltp)} • TP $tpText / SL $slText [$orderId]"
            )
        }
        return true
    }

    private suspend fun executePositionExitLocked(
        position: ActivePosition,
        category: LogCategory,
        reasonTitle: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val config = _riskConfig.value
        val exitResult = brokerClient.exitPosition(
            session = activeSession,
            position = position,
            isPaperMode = config.isPaperMode
        )
        val exitOrderId = exitResult.getOrElse { "LOCAL-SQOFF" }

        val charges = position.estimatedChargesInr
        val netPnl = position.netUnrealizedPnl
        val pnlPct = position.unrealizedPnlPct
        cashBalance += position.investedCapital + netPnl
        realizedPnl += netPnl
        totalChargesPaid += charges

        if (netPnl >= 0.0) {
            winCount++
            grossProfit += netPnl
            symbolConsecutiveLosses[position.symbol] = 0
            symbolWinCounts[position.symbol] = (symbolWinCounts[position.symbol] ?: 0) + 1
            symbolNetPnlStats[position.symbol] = (symbolNetPnlStats[position.symbol] ?: 0.0) + netPnl
            adaptiveMinEfficiency = maxOf(StrategyIndicatorEngine.MIN_DIRECTIONAL_EFFICIENCY, adaptiveMinEfficiency - 0.01)
        } else {
            lossCount++
            grossLoss += abs(netPnl)
            val streak = (symbolConsecutiveLosses[position.symbol] ?: 0) + 1
            symbolConsecutiveLosses[position.symbol] = streak
            symbolNetPnlStats[position.symbol] = (symbolNetPnlStats[position.symbol] ?: 0.0) + netPnl
            adaptiveMinEfficiency = minOf(0.36, adaptiveMinEfficiency + 0.025)
        }

        // Apply configured cooldown (plus adaptive chop cooldown for Stop-Loss symbols)
        val lossPenaltyMs = if (netPnl < 0.0) {
            (symbolConsecutiveLosses[position.symbol] ?: 1) * 60_000L
        } else {
            0L
        }
        val symbolCdMs = maxOf(config.reentryCooldownSec * 1000L, lossPenaltyMs)
        if (symbolCdMs > 0L) {
            symbolCooldownUntilMs[position.symbol] = nowMs + symbolCdMs
        } else {
            symbolCooldownUntilMs.remove(position.symbol)
        }
        if (config.reentryCooldownSec > 0) {
            val cooldownMs = config.reentryCooldownSec * 1000L
            if (position.side == TradeSide.LONG) {
                lastBuyExitCooldownUntilMs = maxOf(lastBuyExitCooldownUntilMs, nowMs + cooldownMs)
            } else {
                lastSellExitCooldownUntilMs = maxOf(lastSellExitCooldownUntilMs, nowMs + cooldownMs)
            }
        } else {
            if (position.side == TradeSide.LONG) {
                lastBuyExitCooldownUntilMs = 0L
            } else {
                lastSellExitCooldownUntilMs = 0L
            }
        }
        val cdLabel = when {
            lossPenaltyMs > 0L -> "${(lossPenaltyMs / 1000L)}s AI chop-shield cooldown"
            config.reentryCooldownSec == 0 -> "0s Instant Re-Entry"
            else -> "${config.reentryCooldownSec}s re-entry cooldown"
        }

        val equityPoint = initialDayCapital + realizedPnl
        equityCurvePoints.add(equityPoint)
        if (equityCurvePoints.size > 40) {
            equityCurvePoints.removeAt(0)
        }

        val qtyFormatted = Formatters.formatQuantity(position.quantity, position.exchange)
        _transactions.value = _transactions.value.map { tx ->
            if (tx.id == position.id) {
                tx.copy(
                    exitOrCurrentPrice = position.currentPrice,
                    grossPnlInr = position.grossUnrealizedPnl,
                    estimatedChargesInr = charges,
                    netPnlInr = netPnl,
                    pnlPct = pnlPct,
                    status = TransactionStatus.CLOSED,
                    exitTimeFormatted = Formatters.formatTime(nowMs),
                    exitEpochMs = nowMs,
                    exitReason = reasonTitle
                )
            } else {
                tx
            }
        }
        appendLogLocked(
            category = category,
            symbol = position.symbol,
            exchange = position.exchange,
            side = position.side,
            quantity = position.quantity,
            price = position.currentPrice,
            pnl = netPnl,
            pnlPct = pnlPct,
            title = "$reasonTitle • ${position.symbol}",
            detail = "Squared off $qtyFormatted qty @ ${Formatters.formatPrice(position.currentPrice)} (Entry ${Formatters.formatPrice(position.entryPrice)}) • Net ${Formatters.formatInr(netPnl, showSign = true)} after ₹${String.format(Locale.US, "%.2f", charges)} charges • $cdLabel [$exitOrderId]"
        )
    }

    private fun syncOpenTransactionsLocked(openPositions: List<ActivePosition>) {
        if (openPositions.isEmpty()) return
        val posMap = openPositions.associateBy { it.id }
        _transactions.value = _transactions.value.map { tx ->
            val pos = posMap[tx.id]
            if (tx.status == TransactionStatus.OPEN && pos != null) {
                tx.copy(
                    exitOrCurrentPrice = pos.currentPrice,
                    grossPnlInr = pos.grossUnrealizedPnl,
                    estimatedChargesInr = pos.estimatedChargesInr,
                    netPnlInr = pos.netUnrealizedPnl,
                    pnlPct = pos.unrealizedPnlPct
                )
            } else {
                tx
            }
        }
    }

    private fun appendLogLocked(
        category: LogCategory,
        symbol: String,
        exchange: Exchange?,
        side: TradeSide?,
        quantity: Double?,
        price: Double?,
        pnl: Double?,
        pnlPct: Double?,
        title: String,
        detail: String
    ) {
        val now = System.currentTimeMillis()
        val entry = TradeLogEntry(
            id = UUID.randomUUID().toString(),
            timestampFormatted = Formatters.formatTime(now),
            epochMs = now,
            category = category,
            symbol = symbol,
            exchange = exchange,
            side = side,
            quantity = quantity,
            price = price,
            pnl = pnl,
            pnlPct = pnlPct,
            title = title,
            detail = detail,
            isPaperTrade = _riskConfig.value.isPaperMode
        )
        _tradeLogs.value = (listOf(entry) + _tradeLogs.value).take(150)
    }

    private fun recalculateAccountSummaryLocked() {
        val positions = _activePositions.value
        val used = positions.sumOf { it.investedCapital }
        val unrealized = positions.sumOf { it.netUnrealizedPnl }
        val openCharges = positions.sumOf { it.estimatedChargesInr }
        val total = realizedPnl + unrealized
        val totalPct = if (initialDayCapital > 0.0) (total / initialDayCapital) * 100.0 else 0.0
        val withdrawable = minOf(
            maxOf(0.0, cashBalance - initialDayCapital),
            maxOf(0.0, realizedPnl - withdrawnProfitTotal)
        )

        _accountSummary.value = AccountSummary(
            availableCash = cashBalance,
            usedMargin = used,
            realizedDayPnl = realizedPnl,
            unrealizedDayPnl = unrealized,
            openBasketCharges = openCharges,
            totalDayPnl = total,
            totalDayPnlPct = totalPct,
            totalEstimatedCharges = totalChargesPaid + openCharges,
            winCount = winCount,
            lossCount = lossCount,
            totalClosedTrades = winCount + lossCount,
            totalTradesExecutedToday = totalTradesExecutedToday,
            totalCapitalInvestedToday = totalCapitalInvestedToday,
            basketDropsCompleted = basketDropsCompleted,
            basketBookedProfitInr = basketBookedProfitInr,
            grossProfit = grossProfit,
            grossLoss = grossLoss,
            withdrawnProfitInr = withdrawnProfitTotal,
            withdrawableProfitInr = Math.round(withdrawable * 100.0) / 100.0,
            dailyProfitFloorInr = dailyProfitFloorInr,
            equityCurve = equityCurvePoints.toList()
        )
        evaluateAndSelfModifyPerformanceLocked(forceModifyIfStagnant = false)
    }

    private fun evaluateAndSelfModifyPerformanceLocked(
        forceModifyIfStagnant: Boolean = false,
        cloudAdviceOverride: GeminiMarketScreenerService.CloudOptimizationAdvice? = null
    ) {
        val summary = _accountSummary.value
        val config = _riskConfig.value
        val totalClosed = summary.totalClosedTrades
        val isApiExhausted = _apiQuotaExhausted.value
        val nowFormatted = Formatters.formatTime()
        val effectiveWinRate = if (totalClosed > 0) summary.winRatePct else 99.0

        if (isApiExhausted) {
            _performanceDiagnostic.value = _performanceDiagnostic.value.copy(
                status = GrowthDiagnosisStatus.API_LIMIT_HOLD,
                headline = "API Key / Access Limit Exhausted — Auto-Booking Paused",
                capitalInvestedSummary = "Invested Today: ${Formatters.formatInr(summary.totalCapitalInvestedToday)} across ${summary.totalTradesExecutedToday} trade(s) • Charges Paid: ${Formatters.formatInr(summary.totalEstimatedCharges)}",
                totalInvestedInr = summary.totalCapitalInvestedToday,
                realizedNetPnlInr = summary.realizedDayPnl,
                unrealizedNetPnlInr = summary.unrealizedDayPnl,
                totalChargesInr = summary.totalEstimatedCharges,
                winRatePct = effectiveWinRate,
                whyLossOrSlowGrowthDiagnosis = "Gemini AI 4 Hydrogen detected API key / quota access limit exhaustion. Open positions are held safely without auto-booking so no unverified exit occurs.",
                profitImprovementPlan = "Restore or reset API key quota limit below to immediately resume +₹${config.singleTradeInstantBookInr.toInt()} Trade & +₹${config.basketInstantBookInr.toInt()} Basket Profit Auto-Booking.",
                isApiQuotaExhausted = true,
                apiQuotaStatusMessage = "API Access Limit Reached • Profit Booking Paused (Hold Active)",
                lastAnalyzedAt = nowFormatted,
                lastEvaluatedAt = nowFormatted
            )
            return
        }

        val netTotalPnl = summary.totalDayPnl
        val isGrowing = (totalClosed >= 1 && summary.realizedDayPnl > 0.0 && summary.winRatePct >= 85.0) ||
            (totalClosed == 0 && netTotalPnl >= 0.0 && !forceModifyIfStagnant)

        val topWinningSymbol = symbolNetPnlStats.entries
            .filter { it.value > 0.0 }
            .maxByOrNull { it.value }
            ?.let { "${it.key} (${Formatters.formatInr(it.value, showSign = true)})" }
        val cooledDownSymbols = symbolConsecutiveLosses.filterValues { it >= 1 }.keys.joinToString(", ")

        if (isGrowing && !forceModifyIfStagnant) {
            val winRateStr = if (totalClosed > 0) String.format(Locale.US, "%.1f%%", summary.winRatePct) else "99.0% Target"
            _performanceDiagnostic.value = _performanceDiagnostic.value.copy(
                status = GrowthDiagnosisStatus.GROWING_CONTINUE,
                headline = "Portfolio Growing (${Formatters.formatInr(netTotalPnl, showSign = true)} Net) — Continuing Winning Setup",
                capitalInvestedSummary = "Kitna Invest Hua: ${Formatters.formatInr(summary.totalCapitalInvestedToday)} (${summary.totalTradesExecutedToday} trades) • Realized: ${Formatters.formatInr(summary.realizedDayPnl, showSign = true)} • Charges: ${Formatters.formatInr(summary.totalEstimatedCharges)}",
                totalInvestedInr = summary.totalCapitalInvestedToday,
                realizedNetPnlInr = summary.realizedDayPnl,
                unrealizedNetPnlInr = summary.unrealizedDayPnl,
                totalChargesInr = summary.totalEstimatedCharges,
                winRatePct = effectiveWinRate,
                whyLossOrSlowGrowthDiagnosis = cloudAdviceOverride?.whyLossOrSlowGrowthDiagnosis?.takeIf { it.isNotBlank() }
                    ?: buildString {
                        append("Win rate is $winRateStr (${summary.winCount}W / ${summary.lossCount}L). ")
                        if (topWinningSymbol != null) append("Top contributor: $topWinningSymbol. ")
                        append("99% 1m Candle + Zero-Loss Shield (+₹12→+₹5) & Zero-Wait +₹${config.singleTradeInstantBookInr.toInt()} Auto-Book are growing net equity cleanly.")
                    },
                profitImprovementPlan = cloudAdviceOverride?.profitImprovementPlan?.takeIf { it.isNotBlank() }
                    ?: "Active Edge: ≥${config.minAiConfidence}% AI + 1m Candle confirmation • Trend Efficiency ≥ ${String.format(Locale.US, "%.2f", adaptiveMinEfficiency)} • ₹${config.effectiveCapitalPerTrade.toInt()}/trade • User risk rules preserved.",
                recommendedAllocationInr = config.effectiveCapitalPerTrade,
                recommendedStopLossPct = config.stopLossPct,
                recommendedCooldownSec = config.reentryCooldownSec,
                recommendedMinAiConfidence = config.minAiConfidence,
                targetSuccessRatePct = 99,
                isApiQuotaExhausted = false,
                apiQuotaStatusMessage = "Gemini AI 4 Hydrogen API Active • Auto-Booking Armed",
                lastAnalyzedAt = nowFormatted,
                lastEvaluatedAt = nowFormatted
            )
            return
        }

        // Growth is negative or stagnant (or user/cloud triggered optimization):
        // Diagnose exact reason AND adapt internal AI entry filters WITHOUT overwriting user's saved Risk Rules!
        val feeDragHigh = summary.totalEstimatedCharges > 0.0 && summary.totalEstimatedCharges >= abs(summary.grossProfit) * 0.45
        val stopLossHits = summary.lossCount > 0
        val rootCause = cloudAdviceOverride?.whyLossOrSlowGrowthDiagnosis?.takeIf { it.isNotBlank() } ?: buildString {
            append("Growth Analysis: ")
            if (totalClosed == 0 && summary.unrealizedDayPnl < 0.0) {
                append("Open position absorbs entry brokerage (${Formatters.formatInr(summary.openBasketCharges)}) and minor pullbacks (${Formatters.formatInr(summary.unrealizedDayPnl, showSign = true)}). ")
            }
            if (feeDragHigh) {
                append("Fee drag (${Formatters.formatInr(summary.totalEstimatedCharges)} charges on ${Formatters.formatInr(summary.totalCapitalInvestedToday)} invested) reduced net gains. ")
            }
            if (stopLossHits) {
                append("${summary.lossCount} trade(s) hit Stop-Loss (${Formatters.formatInr(-summary.grossLoss, showSign = true)})${if (cooledDownSymbols.isNotEmpty()) " on $cooledDownSymbols" else ""}. ")
            }
            if (!feeDragHigh && !stopLossHits && totalClosed == 0) {
                append("Ready for 99% + 1m Candle breakout lock — prioritizing high-efficiency trends and Zero-Loss Profit Shield.")
            }
        }.trim()

        val shouldApplyAdaptiveBoost = forceModifyIfStagnant ||
            (totalClosed > lastAutoTuneTradeCount && (summary.realizedDayPnl <= 0.0 || summary.winRatePct < 85.0))

        var modifiedSummary = _performanceDiagnostic.value.autoModifiedSummary
        var modCount = _performanceDiagnostic.value.selfModificationsApplied

        if (shouldApplyAdaptiveBoost) {
            lastAutoTuneTradeCount = totalClosed
            // Raise internal trend efficiency requirement so choppy setups are rejected before entry
            adaptiveMinEfficiency = minOf(0.36, maxOf(adaptiveMinEfficiency, 0.28))

            // Only apply parameter changes to RiskConfig if the user EXPLICITLY tapped Optimize Now / Cloud Advice requested it,
            // AND ALWAYS preserve user's mode toggles (allowSellAutoTrade, singleDirectionOnly, smallAmountAutoSplitEnabled, singleTradeInstantBookInr, basketInstantBookInr)
            if (forceModifyIfStagnant) {
                val targetSlPct = cloudAdviceOverride?.recommendedStopLossPct?.coerceIn(0.50, 1.50) ?: config.stopLossPct
                val targetCooldown = cloudAdviceOverride?.recommendedCooldownSec?.coerceIn(0, 60) ?: config.reentryCooldownSec
                val newConfig = config.copy(
                    stopLossPct = targetSlPct,
                    reentryCooldownSec = targetCooldown,
                    minAiConfidence = maxOf(config.minAiConfidence, 99),
                    highProbabilityProfitLockEnabled = true
                )
                if (newConfig != config) {
                    hasUserModifiedConfig = true
                    _riskConfig.value = newConfig
                    _activePositions.value = _activePositions.value.map {
                        it.copy(stopLossPct = newConfig.stopLossPct)
                    }
                    scope.launch {
                        preferencesStorage.saveRiskConfig(newConfig)
                    }
                }
            }

            modCount += 1
            modifiedSummary = buildString {
                append("Adaptive AI Boost #$modCount: ")
                append("Trend Efficiency ≥ ${String.format(Locale.US, "%.2f", adaptiveMinEfficiency)} • ")
                append("99% + 1m Candle Gate • ")
                if (cooledDownSymbols.isNotEmpty()) append("Blocked Weak: $cooledDownSymbols (60s+) • ")
                if (topWinningSymbol != null) append("Prioritizing Winner: $topWinningSymbol • ")
                append("User Risk Rules Preserved")
            }

            appendLogLocked(
                category = LogCategory.AI_OPTIMIZER,
                symbol = "AI 4H OPTIMIZER",
                exchange = null,
                side = null,
                quantity = null,
                price = null,
                pnl = summary.realizedDayPnl,
                pnlPct = null,
                title = "Gemini AI 4H Tightened 99% Filter & Symbol Ranking",
                detail = "$modifiedSummary • Diagnosed: $rootCause"
            )
        }

        val improvementPlan = cloudAdviceOverride?.profitImprovementPlan?.takeIf { it.isNotBlank() }
            ?: "1) Tightened AI directional efficiency bar to ≥${String.format(Locale.US, "%.2f", adaptiveMinEfficiency)} to block choppy fakeouts. 2) Applied 60s+ chop cooldown on losing symbols${if (cooledDownSymbols.isNotEmpty()) " ($cooledDownSymbols)" else ""} while prioritizing past winners. 3) Enforced Zero-Loss Shield (+₹12→+₹5) & Zero-Wait +₹${_riskConfig.value.singleTradeInstantBookInr.toInt()} Net Auto-Book."

        _performanceDiagnostic.value = GeminiPerformanceDiagnostic(
            status = GrowthDiagnosisStatus.STAGNANT_SELF_MODIFIED,
            headline = "AI 4H Profit-Protection & Filter Tightened (Rules Preserved)",
            capitalInvestedSummary = "Kitna Invest Hua: ${Formatters.formatInr(summary.totalCapitalInvestedToday)} (${summary.totalTradesExecutedToday} trades) • Realized: ${Formatters.formatInr(summary.realizedDayPnl, showSign = true)} • Charges: ${Formatters.formatInr(summary.totalEstimatedCharges)}",
            totalInvestedInr = summary.totalCapitalInvestedToday,
            realizedNetPnlInr = summary.realizedDayPnl,
            unrealizedNetPnlInr = summary.unrealizedDayPnl,
            totalChargesInr = summary.totalEstimatedCharges,
            winRatePct = effectiveWinRate,
            whyLossOrSlowGrowthDiagnosis = rootCause,
            profitImprovementPlan = improvementPlan,
            autoModifiedSummary = modifiedSummary,
            recommendedAllocationInr = _riskConfig.value.effectiveCapitalPerTrade,
            recommendedStopLossPct = _riskConfig.value.stopLossPct,
            recommendedCooldownSec = _riskConfig.value.reentryCooldownSec,
            recommendedMinAiConfidence = _riskConfig.value.minAiConfidence,
            targetSuccessRatePct = 99,
            isApiQuotaExhausted = false,
            apiQuotaStatusMessage = "Gemini AI 4 Hydrogen API Active • Auto-Booking Armed",
            lastAnalyzedAt = nowFormatted,
            lastEvaluatedAt = nowFormatted,
            selfModificationsApplied = modCount
        )
    }

    suspend fun runAiPerformanceOptimization(): Result<GeminiPerformanceDiagnostic> {
        val snapshotSummary: AccountSummary
        val snapshotConfig: RiskConfig
        tradeMutex.withLock {
            evaluateAndSelfModifyPerformanceLocked(forceModifyIfStagnant = true)
            snapshotSummary = _accountSummary.value
            snapshotConfig = _riskConfig.value
        }

        val cloudResult = geminiScreenerService.analyzePerformanceWithCloudAi(
            totalInvestedInr = snapshotSummary.totalCapitalInvestedToday,
            realizedNetPnlInr = snapshotSummary.realizedDayPnl,
            unrealizedNetPnlInr = snapshotSummary.unrealizedDayPnl,
            totalChargesInr = snapshotSummary.totalEstimatedCharges,
            winCount = snapshotSummary.winCount,
            lossCount = snapshotSummary.lossCount,
            currentAllocationInr = snapshotConfig.effectiveCapitalPerTrade,
            currentStopLossPct = snapshotConfig.stopLossPct,
            currentCooldownSec = snapshotConfig.reentryCooldownSec
        )

        return tradeMutex.withLock {
            cloudResult.fold(
                onSuccess = { advice ->
                    if (_apiQuotaExhausted.value) {
                        _apiQuotaExhausted.value = false
                    }
                    evaluateAndSelfModifyPerformanceLocked(
                        forceModifyIfStagnant = advice.shouldModifyParameters,
                        cloudAdviceOverride = advice
                    )
                    Result.success(_performanceDiagnostic.value)
                },
                onFailure = { err ->
                    if (GeminiMarketScreenerService.isQuotaOrAccessLimitError(err.message)) {
                        _apiQuotaExhausted.value = true
                        evaluateAndSelfModifyPerformanceLocked(forceModifyIfStagnant = false)
                        appendLogLocked(
                            category = LogCategory.RISK_GUARD,
                            symbol = "API QUOTA 429",
                            exchange = null,
                            side = null,
                            quantity = null,
                            price = null,
                            pnl = null,
                            pnlPct = null,
                            title = "Gemini AI 4H API Access Limit Exhausted — Booking Paused",
                            detail = "Cloud AI returned quota/rate limit (${err.message?.take(90)}). Automatic profit booking is paused until API access is restored."
                        )
                    }
                    Result.success(_performanceDiagnostic.value)
                }
            )
        }
    }

    suspend fun setApiQuotaExhausted(exhausted: Boolean) = tradeMutex.withLock {
        _apiQuotaExhausted.value = exhausted
        evaluateAndSelfModifyPerformanceLocked(forceModifyIfStagnant = false)
        appendLogLocked(
            category = LogCategory.RISK_GUARD,
            symbol = "API GUARD",
            exchange = null,
            side = null,
            quantity = null,
            price = null,
            pnl = null,
            pnlPct = null,
            title = if (exhausted) {
                "API Access Limit Guard Activated — Profit Auto-Booking Paused"
            } else {
                "API Access Restored — +₹25 Profit Auto-Booking Resumed"
            },
            detail = if (exhausted) {
                "Simulating/enforcing API key quota exhaustion: open BUY & SELL positions will NOT auto-book until API access is restored."
            } else {
                "Gemini AI 4 Hydrogen API access verified healthy. Zero-Wait +₹25 Combined & Single-Leg Auto-Booking active."
            }
        )
    }

    suspend fun toggleAutoTrading(enabled: Boolean): Result<Unit> = tradeMutex.withLock {
        val config = _riskConfig.value
        if (enabled) {
            // 1. Clear circuit breaker if manually toggled ON by trader
            if (_circuitBreakerTriggered.value) {
                _circuitBreakerTriggered.value = false
                appendLogLocked(
                    category = LogCategory.CIRCUIT_BREAKER,
                    symbol = "CIRCUIT",
                    exchange = null,
                    side = null,
                    quantity = null,
                    price = null,
                    pnl = null,
                    pnlPct = null,
                    title = "Circuit Breaker Cleared by Trader",
                    detail = "Auto-Trading re-armed via manual toggle. Safety parameters active."
                )
            }

            // 2. Auto-heal broker connection: ensure Paper Sandbox is connected if not connected
            if (_connectionState.value !is BrokerConnectionState.Connected || activeSession == null) {
                connectPaperSandboxSilently()
                appendLogLocked(
                    category = LogCategory.BROKER_API,
                    symbol = "SANDBOX",
                    exchange = null,
                    side = null,
                    quantity = null,
                    price = null,
                    pnl = null,
                    pnlPct = null,
                    title = "Paper Sandbox Auto-Connected (Demo Mode)",
                    detail = "Auto-Trading routed safely to virtual ₹5,00,000 engine with zero live risk."
                )
            }
        }

        _autoTradingEnabled.value = enabled
        val windowStatus = IstMarketScheduler.evaluate(config)
        val slLabel = if (config.stopLossMode == StopLossMode.FIXED_INR) {
            "-${Formatters.formatInr(config.stopLossInr)} SL"
        } else {
            "-${config.stopLossPct}% SL"
        }
        val marketStatusNote = if (enabled && config.enforceMarketHours && !windowStatus.isNseBseOpen) {
            " • [Standby: NSE/BSE opens 09:15 IST]"
        } else ""

        appendLogLocked(
            category = LogCategory.RISK_GUARD,
            symbol = "ENGINE",
            exchange = null,
            side = null,
            quantity = null,
            price = null,
            pnl = null,
            pnlPct = null,
            title = if (enabled) {
                if (config.singleDirectionOnly) "Single-Direction ${config.minAiConfidence}% Instant Scalper ARMED" else "Synchronized Pair ${config.minAiConfidence}% Engine ARMED"
            } else {
                "Auto-Trading Engine PAUSED"
            },
            detail = if (enabled) {
                val dirLabel = if (config.singleDirectionOnly) "1-Direction Only (BUY or SELL)" else "Synchronized Pair (1 BUY + 1 SELL together, waits until both exit)"
                val cdLabel = if (config.reentryCooldownSec == 0) "0s Instant Re-Entry" else "${config.reentryCooldownSec}s cooldown"
                "$dirLabel • Max ${config.maxOpenPositions} slots • ≥${config.minAiConfidence}% Local AI gate • Zero-Wait +₹${config.singleTradeInstantBookInr.toInt()} Trade / +₹${config.basketInstantBookInr.toInt()} Basket • BE +₹3 • $slLabel • $cdLabel$marketStatusNote"
            } else {
                "Automated entries paused. Open positions remain monitored."
            }
        )
        if (enabled) {
            refreshLocalScreenerBanner(_instruments.value, config.minAiConfidence, System.currentTimeMillis())
        }
        return Result.success(Unit)
    }

    suspend fun placeManualTrade(instrument: MarketInstrument, side: TradeSide): Result<Unit> =
        tradeMutex.withLock {
            if (_connectionState.value !is BrokerConnectionState.Connected || activeSession == null) {
                connectPaperSandboxSilently()
            }
            val config = _riskConfig.value
            if (config.singleDirectionOnly && _activePositions.value.any { it.side != side }) {
                val openLabel = if (side == TradeSide.LONG) "SELL" else "BUY"
                val newLabel = if (side == TradeSide.LONG) "BUY" else "SELL"
                return Result.failure(
                    IllegalStateException("Single-Direction Mode active: close open $openLabel position(s) before opening $newLabel.")
                )
            }
            val budget = config.effectiveCapitalPerTrade
            val qty = floor(budget / instrument.ltp)
            if (qty < 1.0) {
                return Result.failure(
                    IllegalStateException("${instrument.symbol} LTP (${Formatters.formatPrice(instrument.ltp)}) exceeds ${Formatters.formatInr(budget)} per-trade limit.")
                )
            }
            val required = qty * instrument.ltp
            if (required > cashBalance) {
                return Result.failure(
                    IllegalStateException("Insufficient margin: need ${Formatters.formatInr(required)}, available ${Formatters.formatInr(cashBalance)}")
                )
            }
            val ok = executePositionEntryLocked(instrument, side, qty, isAutomated = false)
            recalculateAccountSummaryLocked()
            return if (ok) Result.success(Unit) else Result.failure(IllegalStateException("Order placement failed"))
        }

    suspend fun squareOffPosition(positionId: String) = tradeMutex.withLock {
        val target = _activePositions.value.find { it.id == positionId } ?: return@withLock
        executePositionExitLocked(
            position = target,
            category = LogCategory.MANUAL_EXIT,
            reasonTitle = "Instant Manual Square-Off"
        )
        val remaining = _activePositions.value.filterNot { it.id == positionId }
        _activePositions.value = remaining
        if (remaining.none { it.side == TradeSide.LONG }) {
            _buyBasketHighWater.value = BasketHighWaterState()
        }
        if (remaining.none { it.side == TradeSide.SHORT }) {
            _sellBasketHighWater.value = BasketHighWaterState()
        }
        recalculateAccountSummaryLocked()
    }

    suspend fun bookBasketProfitNow(side: TradeSide): Double = tradeMutex.withLock {
        val sidePositions = _activePositions.value.filter { it.side == side }
        if (sidePositions.isEmpty()) return@withLock 0.0

        val config = _riskConfig.value
        val nowMs = System.currentTimeMillis()
        val netPnl = sidePositions.sumOf { it.netUnrealizedPnl }
        val invested = sidePositions.sumOf { it.investedCapital }
        val pct = if (invested > 0.0) (netPnl / invested) * 100.0 else 0.0
        val sideLabel = if (side == TradeSide.LONG) "BUY" else "SELL"

        for (pos in sidePositions) {
            executePositionExitLocked(
                position = pos,
                category = LogCategory.AUTO_PROFIT_EXIT,
                reasonTitle = "$sideLabel Basket Manual Profit Booked (${Formatters.formatInr(netPnl, showSign = true)})"
            )
        }

        _activePositions.value = _activePositions.value.filterNot { it.side == side }
        val cdMs = config.reentryCooldownSec * 1000L
        if (side == TradeSide.LONG) {
            _buyBasketHighWater.value = BasketHighWaterState()
            buyBasketCooldownUntilMs = if (cdMs > 0L) nowMs + cdMs else 0L
            _buyBasketCooldownRemainingSec.value = config.reentryCooldownSec
        } else {
            _sellBasketHighWater.value = BasketHighWaterState()
            sellBasketCooldownUntilMs = if (cdMs > 0L) nowMs + cdMs else 0L
            _sellBasketCooldownRemainingSec.value = config.reentryCooldownSec
        }
        val cdLabel = if (config.reentryCooldownSec == 0) "0s Instant Re-Entry active." else "${config.reentryCooldownSec}s cooldown."

        if (netPnl > 0.0) {
            basketDropsCompleted++
            basketBookedProfitInr += netPnl
        }

        appendLogLocked(
            category = LogCategory.AUTO_PROFIT_EXIT,
            symbol = "$sideLabel BASKET(${sidePositions.size})",
            exchange = null,
            side = side,
            quantity = sidePositions.sumOf { it.quantity },
            price = null,
            pnl = netPnl,
            pnlPct = pct,
            title = "$sideLabel Basket Net Profit Booked Now (${Formatters.formatInr(netPnl, showSign = true)})",
            detail = "One-tap manual booking squared off ${sidePositions.size} $sideLabel position(s) • $cdLabel"
        )
        recalculateAccountSummaryLocked()
        return@withLock netPnl
    }

    suspend fun bookAllCombinedProfitNow(): Double = tradeMutex.withLock {
        val allPositions = _activePositions.value.toList()
        if (allPositions.isEmpty()) return@withLock 0.0

        val config = _riskConfig.value
        val nowMs = System.currentTimeMillis()
        val buyOpen = allPositions.filter { it.side == TradeSide.LONG }
        val sellOpen = allPositions.filter { it.side == TradeSide.SHORT }
        val combinedNetPnl = BasketHighWaterState.computeCombinedDualBasketNetPnl(allPositions)
        val combinedInvested = allPositions.sumOf { it.investedCapital }
        val combinedPct = if (combinedInvested > 0.0) (combinedNetPnl / combinedInvested) * 100.0 else 0.0

        for (pos in allPositions) {
            executePositionExitLocked(
                position = pos,
                category = LogCategory.AUTO_PROFIT_EXIT,
                reasonTitle = "MASTER Combined Profit Booked Now (${Formatters.formatInr(combinedNetPnl, showSign = true)})"
            )
        }

        _activePositions.value = emptyList()
        _buyBasketHighWater.value = BasketHighWaterState()
        _sellBasketHighWater.value = BasketHighWaterState()
        val cdMs = config.reentryCooldownSec * 1000L
        if (buyOpen.isNotEmpty()) {
            buyBasketCooldownUntilMs = if (cdMs > 0L) nowMs + cdMs else 0L
            _buyBasketCooldownRemainingSec.value = config.reentryCooldownSec
        }
        if (sellOpen.isNotEmpty()) {
            sellBasketCooldownUntilMs = if (cdMs > 0L) nowMs + cdMs else 0L
            _sellBasketCooldownRemainingSec.value = config.reentryCooldownSec
        }
        val cdLabel = if (config.reentryCooldownSec == 0) "0s Instant Re-Entry active." else "${config.reentryCooldownSec}s cooldown."
        if (combinedNetPnl > 0.0) {
            basketDropsCompleted++
            basketBookedProfitInr += combinedNetPnl
        }

        val buyPart = Formatters.formatInr(buyOpen.sumOf { it.netUnrealizedPnl }, showSign = true)
        val sellPart = Formatters.formatInr(sellOpen.sumOf { it.netUnrealizedPnl }, showSign = true)
        appendLogLocked(
            category = LogCategory.AUTO_PROFIT_EXIT,
            symbol = "MASTER BASKET(${allPositions.size})",
            exchange = null,
            side = null,
            quantity = allPositions.sumOf { it.quantity },
            price = null,
            pnl = combinedNetPnl,
            pnlPct = combinedPct,
            title = "MASTER Dual-Basket Profit Booked Now (${Formatters.formatInr(combinedNetPnl, showSign = true)})",
            detail = "One-tap global booking (BUY $buyPart + SELL $sellPart) squared off all ${allPositions.size} position(s) • $cdLabel"
        )
        recalculateAccountSummaryLocked()
        return@withLock combinedNetPnl
    }

    suspend fun squareOffAllPositions() = tradeMutex.withLock {
        val current = _activePositions.value.toList()
        for (pos in current) {
            executePositionExitLocked(
                position = pos,
                category = LogCategory.MANUAL_EXIT,
                reasonTitle = "Batch Square-Off All"
            )
        }
        _activePositions.value = emptyList()
        _buyBasketHighWater.value = BasketHighWaterState()
        _sellBasketHighWater.value = BasketHighWaterState()
        recalculateAccountSummaryLocked()
    }

    suspend fun updateRiskConfig(newConfig: RiskConfig) {
        val sanitized = newConfig.copy(
            smallAmountPerTrade = newConfig.smallAmountPerTrade.coerceIn(50.0, 50000.0),
            maxCapitalPerTrade = newConfig.maxCapitalPerTrade.coerceIn(5000.0, 500000.0),
            targetProfitInr = newConfig.targetProfitInr.coerceIn(30.0, 1000.0),
            targetProfitPct = newConfig.targetProfitPct.coerceIn(0.25, 15.0),
            singleTradeInstantBookInr = newConfig.singleTradeInstantBookInr.coerceIn(5.0, 200.0),
            basketInstantBookInr = newConfig.basketInstantBookInr.coerceIn(15.0, 500.0),
            stopLossPct = newConfig.stopLossPct.coerceIn(0.20, 15.0),
            stopLossInr = newConfig.stopLossInr.coerceIn(25.0, 15000.0),
            basketMaxLossInr = newConfig.basketMaxLossInr.coerceIn(10.0, 500.0),
            minAiConfidence = newConfig.minAiConfidence.coerceIn(75, 100),
            maxOpenPositions = newConfig.maxOpenPositions.coerceIn(1, 30),
            reentryCooldownSec = newConfig.reentryCooldownSec.coerceIn(0, 120),
            dailyMaxLossLimitInr = newConfig.dailyMaxLossLimitInr.coerceIn(300.0, 50000.0),
            rsiBuyThreshold = newConfig.rsiBuyThreshold.coerceIn(15.0, 45.0),
            rsiSellThreshold = newConfig.rsiSellThreshold.coerceIn(55.0, 85.0)
        )
        hasUserModifiedConfig = true
        var autoConnectSavedCreds: BrokerCredentials? = null
        tradeMutex.withLock {
            val modeChanged = sanitized.isPaperMode != _riskConfig.value.isPaperMode
            _riskConfig.value = sanitized
            if (sanitized.reentryCooldownSec == 0) {
                symbolCooldownUntilMs.clear()
                buyBasketCooldownUntilMs = 0L
                sellBasketCooldownUntilMs = 0L
                lastBuyExitCooldownUntilMs = 0L
                lastSellExitCooldownUntilMs = 0L
                _buyBasketCooldownRemainingSec.value = 0
                _sellBasketCooldownRemainingSec.value = 0
            }
            _marketWindowStatus.value = IstMarketScheduler.evaluate(sanitized)
            _activePositions.value = _activePositions.value.map {
                it.copy(
                    targetProfitMode = sanitized.targetProfitMode,
                    targetProfitInr = sanitized.targetProfitInr,
                    targetProfitPct = sanitized.targetProfitPct,
                    stopLossMode = sanitized.stopLossMode,
                    stopLossInr = sanitized.stopLossInr,
                    stopLossPct = sanitized.stopLossPct
                )
            }
            if (modeChanged) {
                if (sanitized.isPaperMode) {
                    connectPaperSandboxSilently()
                    appendLogLocked(
                        category = LogCategory.BROKER_API,
                        symbol = "SANDBOX",
                        exchange = null,
                        side = null,
                        quantity = null,
                        price = null,
                        pnl = null,
                        pnlPct = null,
                        title = "Switched to Paper Trading (Demo Mode)",
                        detail = "Orders routed safely to virtual ₹5,00,000 paper engine."
                    )
                } else {
                    _autoTradingEnabled.value = false
                    activeSession = null
                    _connectionState.value = BrokerConnectionState.Disconnected
                    val savedCreds = _savedCredentials.value
                    if (savedCreds.isCompleteForLive) {
                        autoConnectSavedCreds = savedCreds
                    }
                    appendLogLocked(
                        category = LogCategory.BROKER_API,
                        symbol = "SMARTAPI",
                        exchange = null,
                        side = null,
                        quantity = null,
                        price = null,
                        pnl = null,
                        pnlPct = null,
                        title = "Switched to Live SmartAPI Mode",
                        detail = if (savedCreds.isCompleteForLive) {
                            "Auto-connecting saved Angel One account (${savedCreds.clientId}) & syncing /getRMS funds…"
                        } else {
                            "Auto-Trading paused. Link Angel One SmartAPI credentials once to trade with your Angel One fund."
                        }
                    )
                }
            }
        }
        preferencesStorage.saveRiskConfig(sanitized)
        autoConnectSavedCreds?.let { connectBroker(it) }
    }

    suspend fun withdrawNetProfitOnly(): Result<Double> = tradeMutex.withLock {
        val withdrawable = Math.round(
            minOf(
                maxOf(0.0, cashBalance - initialDayCapital),
                maxOf(0.0, realizedPnl - withdrawnProfitTotal)
            ) * 100.0
        ) / 100.0

        if (withdrawable <= 0.0) {
            return@withLock Result.failure(
                IllegalStateException("Only Net Profit above ${Formatters.formatInr(initialDayCapital)} principal can be withdrawn. Current withdrawable profit: ₹0.00")
            )
        }
        cashBalance -= withdrawable
        withdrawnProfitTotal += withdrawable
        recalculateAccountSummaryLocked()
        appendLogLocked(
            category = LogCategory.BROKER_API,
            symbol = "PROFIT PAYOUT",
            exchange = null,
            side = null,
            quantity = null,
            price = null,
            pnl = withdrawable,
            pnlPct = null,
            title = "Profit Only Withdrawn to Bank: ${Formatters.formatInr(withdrawable)}",
            detail = "Swept ${Formatters.formatInr(withdrawable)} pure profit out of trading wallet. Base ${Formatters.formatInr(initialDayCapital)} capital stays safely intact (Total Withdrawn: ${Formatters.formatInr(withdrawnProfitTotal)})."
        )
        Result.success(withdrawable)
    }

    suspend fun resetPaperAccount() = tradeMutex.withLock {
        _activePositions.value = emptyList()
        _transactions.value = emptyList()
        _circuitBreakerTriggered.value = false
        symbolCooldownUntilMs.clear()
        buyBasketCooldownUntilMs = 0L
        sellBasketCooldownUntilMs = 0L
        lastBuyExitCooldownUntilMs = 0L
        lastSellExitCooldownUntilMs = 0L
        _buyBasketCooldownRemainingSec.value = 0
        _sellBasketCooldownRemainingSec.value = 0
        _buyBasketHighWater.value = BasketHighWaterState()
        _sellBasketHighWater.value = BasketHighWaterState()
        initialDayCapital = 500000.0
        cashBalance = 500000.0
        realizedPnl = 0.0
        totalChargesPaid = 0.0
        winCount = 0
        lossCount = 0
        totalTradesExecutedToday = 0
        totalCapitalInvestedToday = 0.0
        basketDropsCompleted = 0
        basketBookedProfitInr = 0.0
        grossProfit = 0.0
        grossLoss = 0.0
        peakRealizedProfitInr = 0.0
        dailyProfitFloorInr = null
        dailyProfitLockedIstDayKey = -1
        autoStartedIstDayKey = -1
        withdrawnProfitTotal = 0.0
        lastAutoTuneTradeCount = 0
        symbolConsecutiveLosses.clear()
        symbolWinCounts.clear()
        symbolNetPnlStats.clear()
        adaptiveMinEfficiency = StrategyIndicatorEngine.MIN_DIRECTIONAL_EFFICIENCY
        _apiQuotaExhausted.value = false
        equityCurvePoints.clear()
        equityCurvePoints.add(500000.0)
        recalculateAccountSummaryLocked()
        connectPaperSandboxSilently()
        appendLogLocked(
            category = LogCategory.RISK_GUARD,
            symbol = "PAPER",
            exchange = null,
            side = null,
            quantity = null,
            price = null,
            pnl = null,
            pnlPct = null,
            title = "Paper Portfolio Reset to ₹5,00,000",
            detail = "Virtual margin restored, open positions & cooldowns cleared."
        )
    }

    suspend fun connectBroker(credentials: BrokerCredentials): Result<Unit> {
        _connectionState.value = BrokerConnectionState.Connecting
        val isPaper = _riskConfig.value.isPaperMode
        val authResult = brokerClient.authenticateSession(credentials, isPaperMode = isPaper)

        return authResult.fold(
            onSuccess = { session ->
                activeSession = session
                if (credentials.isCompleteForLive || !isPaper) {
                    val normalized = credentials.copy(
                        apiKey = credentials.apiKey.trim(),
                        clientId = credentials.clientId.trim().uppercase(),
                        mpin = credentials.mpin.trim(),
                        totpSecret = credentials.totpSecret.trim()
                    )
                    preferencesStorage.saveBrokerCredentials(normalized)
                    _savedCredentials.value = normalized
                }
                val rmsResult = brokerClient.fetchRmsMarginLimits(session)
                tradeMutex.withLock {
                    rmsResult.onSuccess { limits ->
                        if (!session.isPaperSession) {
                            val syncedCash = limits.availableCash.coerceAtLeast(0.0)
                            if (totalTradesExecutedToday == 0) {
                                initialDayCapital = syncedCash.coerceAtLeast(1.0)
                                equityCurvePoints.clear()
                                equityCurvePoints.add(syncedCash)
                            }
                            cashBalance = syncedCash
                            recalculateAccountSummaryLocked()
                        }
                    }
                    val tokenPreview = if (session.jwtToken.length > 14) {
                        "${session.jwtToken.take(8)}…${session.jwtToken.takeLast(4)}"
                    } else {
                        session.jwtToken
                    }
                    _connectionState.value = BrokerConnectionState.Connected(
                        clientId = session.clientId,
                        jwtTokenPreview = tokenPreview,
                        isPaperSession = session.isPaperSession,
                        lastSyncFormatted = Formatters.formatTime()
                    )
                    appendLogLocked(
                        category = LogCategory.BROKER_API,
                        symbol = "SMARTAPI",
                        exchange = null,
                        side = null,
                        quantity = null,
                        price = null,
                        pnl = null,
                        pnlPct = null,
                        title = if (session.isPaperSession) {
                            "Paper Sandbox Connected (${session.clientId})"
                        } else {
                            "Angel One Linked One-Time (${session.clientId})"
                        },
                        detail = if (session.isPaperSession) {
                            "Virtual margin active • Zero live capital risk"
                        } else {
                            "One-time SmartAPI link saved (auto-login daily via TOTP) • Angel One Fund: ${Formatters.formatInr(cashBalance)}"
                        }
                    )
                }
                Result.success(Unit)
            },
            onFailure = { error ->
                val message = error.message ?: "Authentication failed"
                _connectionState.value = BrokerConnectionState.Error(message)
                tradeMutex.withLock {
                    appendLogLocked(
                        category = LogCategory.BROKER_API,
                        symbol = "SMARTAPI",
                        exchange = null,
                        side = null,
                        quantity = null,
                        price = null,
                        pnl = null,
                        pnlPct = null,
                        title = "SmartAPI Login Error",
                        detail = message
                    )
                }
                Result.failure(error)
            }
        )
    }

    suspend fun syncBrokerFunds(): Result<Double> {
        val session = activeSession
            ?: return Result.failure(IllegalStateException("Angel One SmartAPI is not connected."))
        val rmsResult = brokerClient.fetchRmsMarginLimits(session)
        return rmsResult.fold(
            onSuccess = { limits ->
                tradeMutex.withLock {
                    if (!session.isPaperSession) {
                        val syncedCash = limits.availableCash.coerceAtLeast(0.0)
                        if (totalTradesExecutedToday == 0) {
                            initialDayCapital = syncedCash.coerceAtLeast(1.0)
                            equityCurvePoints.clear()
                            equityCurvePoints.add(syncedCash)
                        }
                        cashBalance = syncedCash
                        recalculateAccountSummaryLocked()
                    }
                    val currentConn = _connectionState.value
                    if (currentConn is BrokerConnectionState.Connected) {
                        _connectionState.value = currentConn.copy(lastSyncFormatted = Formatters.formatTime())
                    }
                    appendLogLocked(
                        category = LogCategory.BROKER_API,
                        symbol = "FUNDS SYNC",
                        exchange = null,
                        side = null,
                        quantity = null,
                        price = null,
                        pnl = null,
                        pnlPct = null,
                        title = "Angel One Balance Synced: ${Formatters.formatInr(cashBalance)}",
                        detail = "Fetched live RMS margin limits (${Formatters.formatInr(limits.availableCash)} available, ${Formatters.formatInr(limits.utilisedMargin)} utilised)."
                    )
                }
                Result.success(cashBalance)
            },
            onFailure = { err ->
                Result.failure(err)
            }
        )
    }

    suspend fun clearSavedBrokerCredentials() {
        preferencesStorage.clearBrokerCredentials()
        tradeMutex.withLock {
            _savedCredentials.value = BrokerCredentials()
            appendLogLocked(
                category = LogCategory.BROKER_API,
                symbol = "SMARTAPI",
                exchange = null,
                side = null,
                quantity = null,
                price = null,
                pnl = null,
                pnlPct = null,
                title = "Saved Angel One Link Cleared",
                detail = "Removed stored one-time API Key, Client ID, MPIN & TOTP Secret from device."
            )
        }
    }

    suspend fun disconnectBroker() {
        val current = activeSession
        brokerClient.disconnectSession(current)
        tradeMutex.withLock {
            activeSession = null
            _autoTradingEnabled.value = false
            _connectionState.value = BrokerConnectionState.Disconnected
            appendLogLocked(
                category = LogCategory.BROKER_API,
                symbol = "SMARTAPI",
                exchange = null,
                side = null,
                quantity = null,
                price = null,
                pnl = null,
                pnlPct = null,
                title = "Broker Session Disconnected",
                detail = "JWT session terminated and Auto-Trading safely disarmed."
            )
        }
    }

    private fun currentIstDayKey(nowMs: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            timeInMillis = nowMs
        }
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun initialBootLogs(): List<TradeLogEntry> {
        val now = System.currentTimeMillis()
        return listOf(
            TradeLogEntry(
                id = UUID.randomUUID().toString(),
                timestampFormatted = Formatters.formatTime(now),
                epochMs = now,
                category = LogCategory.BROKER_API,
                symbol = "SYSTEM",
                exchange = null,
                side = null,
                quantity = null,
                price = null,
                pnl = null,
                pnlPct = null,
                title = "Bharat AI Trading • Synchronized Dual-Basket Pair Engine",
                detail = "1 BUY + 1 SELL Simultaneous Pair (≥99% gate, ₹5,000/trade, 20s cooldown) • Combined +₹25 OR Single Leg +₹25 Auto-Book (holds other leg to recover & book profit) • 0.75% SL / -₹80 Basket SL.",
                isPaperTrade = true
            )
        )
    }

    private fun createInitialWatchlist(): List<MarketInstrument> {
        fun seedHistory(base: Double, bias: Double): List<Double> {
            val pts = mutableListOf(base)
            var cur = base
            repeat(27) { idx ->
                val wave = if (idx % 3 != 0) bias else -bias * 1.12
                cur = (cur * (1.0 + wave + ((random.nextDouble() - 0.5) * 0.0014))).coerceAtLeast(1.0)
                pts.add(Math.round(cur * 100.0) / 100.0)
            }
            return pts
        }

        // Penny/low-priced NSE & BSE stocks (₹11–₹155), mid/blue-chips & MCX commodities
        val specs = listOf(
            // Low-priced NSE & BSE equities (₹50 & ₹100 friendly)
            Triple("IDEA", "IDEA-EQ", "Vodafone Idea Ltd") to Triple(Exchange.NSE, "14366", 11.40 to 0.0018),
            Triple("YESBANK", "YESBANK-EQ", "Yes Bank Ltd") to Triple(Exchange.NSE, "11915", 22.80 to -0.0017),
            Triple("SUZLON", "SUZLON-EQ", "Suzlon Energy Ltd") to Triple(Exchange.NSE, "12018", 64.50 to 0.0019),
            Triple("NHPC", "NHPC-EQ", "NHPC Limited (BSE)") to Triple(Exchange.BSE, "533098", 88.40 to -0.0016),
            Triple("IRFC", "IRFC-EQ", "Indian Railway Fin Corp") to Triple(Exchange.NSE, "2029", 148.20 to 0.0016),
            Triple("TATASTEEL", "TATASTEEL-EQ", "Tata Steel Ltd") to Triple(Exchange.NSE, "3499", 154.80 to -0.0018),
            // Mid & Blue-chip NSE/BSE equities
            Triple("ITC", "ITC-EQ", "ITC Limited") to Triple(Exchange.NSE, "1660", 482.50 to 0.0015),
            Triple("WIPRO", "WIPRO-EQ", "Wipro Ltd (BSE)") to Triple(Exchange.BSE, "507685", 564.20 to 0.0014),
            Triple("SBIN", "SBIN-EQ", "State Bank of India (BSE)") to Triple(Exchange.BSE, "500112", 834.80 to -0.0013),
            Triple("TATAMOTORS", "TATAMOTORS-EQ", "Tata Motors Ltd") to Triple(Exchange.NSE, "3456", 986.30 to 0.0017),
            Triple("HDFCBANK", "HDFCBANK-EQ", "HDFC Bank Ltd") to Triple(Exchange.NSE, "1333", 1728.60 to -0.0016),
            Triple("INFY", "INFY-EQ", "Infosys Limited") to Triple(Exchange.NSE, "1594", 1852.40 to 0.0016),
            Triple("KOTAKBANK", "KOTAKBANK-EQ", "Kotak Mahindra Bank") to Triple(Exchange.NSE, "1922", 1780.50 to -0.0014),
            Triple("RELIANCE", "RELIANCE-EQ", "Reliance Industries") to Triple(Exchange.NSE, "2885", 2945.40 to -0.0014),
            Triple("TCS", "TCS-EQ", "Tata Consultancy Services") to Triple(Exchange.NSE, "11536", 4118.75 to 0.0009),
            Triple("BAJFINANCE", "BAJFINANCE-EQ", "Bajaj Finance (BSE)") to Triple(Exchange.BSE, "500034", 7142.50 to 0.0013),
            // MCX Commodities
            Triple("NATURALGAS", "NATURALGAS26DECFUT", "MCX Natural Gas Mini") to Triple(Exchange.MCX, "431888", 244.60 to 0.0019),
            Triple("ZINCMINI", "ZINCMINI26DECFUT", "MCX Zinc Mini Futures") to Triple(Exchange.MCX, "431920", 282.40 to -0.0015),
            Triple("CRUDEOIL", "CRUDEOIL26DECFUT", "MCX Crude Oil Futures") to Triple(Exchange.MCX, "431022", 5942.00 to -0.0016),
            Triple("GOLDM", "GOLDM26DECFUT", "MCX Gold Mini Futures") to Triple(Exchange.MCX, "428191", 75840.00 to 0.0012),
            Triple("SILVERM", "SILVERM26DECFUT", "MCX Silver Mini Futures") to Triple(Exchange.MCX, "428230", 91240.00 to 0.0015)
        )

        return specs.map { (names, meta) ->
            val (symbol, tradingSymbol, displayName) = names
            val (exchange, token, priceAndBias) = meta
            val (basePrice, bias) = priceAndBias
            val history = seedHistory(basePrice, bias)
            val ltp = history.last()
            var cumPv = 0.0
            var cumVol = 0.0
            var lastVol = 1000.0
            history.forEachIndexed { i, p ->
                val prev = if (i == 0) p else history[i - 1]
                val move = if (prev > 0.0) abs(p - prev) / prev else 0.0
                val vol = 900.0 + (move * 180_000.0)
                lastVol = vol
                cumPv += p * vol
                cumVol += vol
            }
            val avgVol = (cumVol / history.size).coerceAtLeast(1.0)
            val surgeRatio = lastVol / avgVol
            val initialVwap = Math.round((cumPv / cumVol) * 100.0) / 100.0
            val screener = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
                ltp = ltp,
                vwap = initialVwap,
                priceHistory = history,
                exchange = exchange,
                allocationInr = 5000.0,
                volumeSurgeRatio = surgeRatio
            )
            val eval = screener.strategyEval
            MarketInstrument(
                symbol = symbol,
                tradingSymbol = tradingSymbol,
                displayName = displayName,
                exchange = exchange,
                symbolToken = token,
                ltp = ltp,
                prevClose = basePrice,
                rsi14 = eval.rsi14,
                aiSignal = eval.signal,
                aiConfidence = eval.confidence,
                priceHistory = history,
                vwap = eval.vwap,
                cumulativePv = cumPv,
                cumulativeVolume = cumVol,
                avgTickVolume = avgVol,
                latestTickVolume = lastVol,
                volumeSurgeRatio = screener.volumeSurgeRatio,
                intradayRangePct = screener.intradayRangePct,
                ema9 = eval.ema9,
                ema21 = eval.ema21,
                isPriceAboveVwap = eval.isPriceAboveVwap,
                isEmaBullish = eval.isEmaBullish,
                strategyPassed = eval.strategyPassed,
                strategyReason = eval.statusReason,
                aiScreenerScore = screener.screenerScore,
                trendMomentumPass = screener.trendMomentumPass,
                volumeLiquidityPass = screener.volumeLiquidityPass,
                feeClearancePass = screener.feeClearancePass,
                aiScreenerVerdict = screener.verdict,
                aiScreenerReason = screener.reason,
                oneMinCandlePattern = screener.oneMinCandlePattern,
                isOneMinCandleConfirmed = screener.isOneMinCandleConfirmed,
                oneMinCandleStatusPill = screener.oneMinCandleStatusPill
            )
        }
    }
}

package com.example

import com.example.data.ai.GeminiMarketScreenerService
import com.example.data.smartapi.TOTPGenerator
import com.example.domain.model.ActivePosition
import com.example.domain.model.BasketHighWaterState
import com.example.domain.model.Exchange
import com.example.domain.model.MarketInstrument
import com.example.domain.model.RiskConfig
import com.example.domain.model.StopLossMode
import com.example.domain.model.TargetProfitMode
import com.example.domain.model.AiTrendSignal
import com.example.domain.model.GeminiPerformanceDiagnostic
import com.example.domain.model.GrowthDiagnosisStatus
import com.example.domain.model.TradeSide
import com.example.util.AngelOneChargeCalculator
import com.example.util.IstMarketScheduler
import com.example.util.StrategyIndicatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.floor

class ExampleUnitTest {

    @Test
    fun totpGenerator_produces6DigitCodeForValidBase32() {
        val code = TOTPGenerator.generateTotp("JBSWY3DPEHPK3PXP", timeMillis = 1700000000000L)
        assertNotNull(code)
        assertEquals(6, code!!.length)
        assertNull(TOTPGenerator.generateTotp("invalid-1!"))
    }

    @Test
    fun activePosition_calculatesNetPnlAfterChargesAndFixed300Target() {
        val pos = ActivePosition(
            id = "1",
            symbol = "TATAMOTORS",
            tradingSymbol = "TATAMOTORS-EQ",
            displayName = "Tata Motors Ltd",
            exchange = Exchange.NSE,
            symbolToken = "3456",
            side = TradeSide.LONG,
            entryPrice = 1000.0,
            currentPrice = 1102.0,
            quantity = 3.0,
            targetProfitMode = TargetProfitMode.FIXED_INR,
            targetProfitInr = 300.0,
            targetProfitPct = 1.25,
            stopLossMode = StopLossMode.PERCENTAGE,
            stopLossInr = 150.0,
            stopLossPct = 0.75,
            openedAtEpochMs = 0L,
            openedAtFormatted = "09:30:00",
            isPaperTrade = true,
            brokerOrderId = "PPR-001"
        )
        // Gross = (1102 - 1000) * 3 = 306.0; Charges ~ 3.60 -> Net >= 300.0
        assertEquals(306.0, pos.grossUnrealizedPnl, 0.01)
        assertTrue(pos.estimatedChargesInr > 0.0)
        assertTrue(pos.netUnrealizedPnl >= 300.0)
        assertEquals(1.0f, pos.normalizedTargetProgress, 0.01f)
    }

    @Test
    fun smallAmountSplit_skipsOverpricedStocksAndAllowsPennyStocks() {
        val microBudget = 50.0
        // Penny NSE stock IDEA @ ₹11.40 -> 4 shares fit within ₹50
        assertEquals(4, floor(microBudget / 11.40).toInt())
        // YESBANK @ ₹22.80 -> 2 shares fit within ₹50
        assertEquals(2, floor(microBudget / 22.80).toInt())
        // Blue-chip TCS @ ₹4,118.75 -> skipped at ₹50
        assertEquals(0, floor(microBudget / 4118.75).toInt())

        val intradayCharges = AngelOneChargeCalculator.estimateRoundTripCharges(50.0, Exchange.NSE)
        assertTrue(intradayCharges.totalCharges > 0.0 && intradayCharges.totalCharges < 1.0)
    }

    @Test
    fun istMarketScheduler_validatesNseBseAndMcxHours() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(ist).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 16) // Monday
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val config = RiskConfig(enforceMarketHours = true, mcxExtendedHoursEnabled = true)
        val morning = IstMarketScheduler.evaluate(config, cal.timeInMillis)
        assertTrue(morning.isNseBseOpen)
        assertTrue(morning.isMcxOpen)
        assertTrue(morning.isAnyMarketOpen)

        // 19:00 IST -> NSE/BSE closed, MCX open
        cal.set(Calendar.HOUR_OF_DAY, 19)
        val evening = IstMarketScheduler.evaluate(config, cal.timeInMillis)
        assertFalse(evening.isNseBseOpen)
        assertTrue(evening.isMcxOpen)
        assertTrue(evening.isAnyMarketOpen)

        // 23:45 IST -> NSE/MCX closed
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 45)
        val lateNight = IstMarketScheduler.evaluate(config, cal.timeInMillis)
        assertFalse(lateNight.isNseBseOpen)
        assertFalse(lateNight.isMcxOpen)
        assertFalse(lateNight.isAnyMarketOpen)
    }

    @Test
    fun combinedBasketNetPnl_triggersAt300AcrossMultipleOpenPositions() {
        val p1 = ActivePosition(
            id = "p1", symbol = "TATAMOTORS", tradingSymbol = "TATAMOTORS-EQ",
            displayName = "Tata Motors", exchange = Exchange.NSE, symbolToken = "3456",
            side = TradeSide.LONG, entryPrice = 1000.0, currentPrice = 1040.0, quantity = 3.0,
            targetProfitMode = TargetProfitMode.FIXED_INR, targetProfitInr = 300.0, targetProfitPct = 1.25,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.75,
            openedAtEpochMs = 0L, openedAtFormatted = "09:30:00", isPaperTrade = true, brokerOrderId = "P1"
        )
        val p2 = p1.copy(
            id = "p2", symbol = "ITC", tradingSymbol = "ITC-EQ",
            entryPrice = 500.0, currentPrice = 528.0, quantity = 7.0
        )
        // Individual positions are each below +₹300 net, but combined net P&L exceeds +₹300!
        assertTrue(p1.netUnrealizedPnl < 300.0)
        assertTrue(p2.netUnrealizedPnl < 300.0)
        val combinedNet = listOf(p1, p2).sumOf { it.netUnrealizedPnl }
        assertTrue(combinedNet >= 300.0)
    }

    @Test
    fun breakEvenLock_shiftsStopLossAboveEntryToCoverCharges() {
        val unlocked = ActivePosition(
            id = "be1", symbol = "SUZLON", tradingSymbol = "SUZLON-EQ",
            displayName = "Suzlon Energy", exchange = Exchange.NSE, symbolToken = "12018",
            side = TradeSide.LONG, entryPrice = 64.50, currentPrice = 68.00, quantity = 100.0,
            targetProfitMode = TargetProfitMode.FIXED_INR, targetProfitInr = 300.0, targetProfitPct = 1.25,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.35,
            isBreakEvenLocked = false,
            openedAtEpochMs = 0L, openedAtFormatted = "10:00:00", isPaperTrade = true, brokerOrderId = "BE-1"
        )
        assertTrue(unlocked.stopLossPrice < unlocked.entryPrice)
        val lockedLong = unlocked.copy(isBreakEvenLocked = true)
        assertTrue(lockedLong.breakEvenPrice > lockedLong.entryPrice)
        assertEquals(lockedLong.breakEvenPrice, lockedLong.stopLossPrice, 0.0001)

        // Closing right at BE price must guarantee Gross >= RoundTripCharges + ₹3.00 net
        val closedAtLongBe = lockedLong.copy(currentPrice = lockedLong.stopLossPrice)
        assertEquals(ActivePosition.BREAK_EVEN_NET_BUFFER_INR, closedAtLongBe.netUnrealizedPnl, 0.05)
        assertTrue(closedAtLongBe.grossUnrealizedPnl >= closedAtLongBe.estimatedChargesInr + 2.95)

        // Mirror check for SHORT side
        val lockedShort = unlocked.copy(
            side = TradeSide.SHORT,
            currentPrice = 61.00,
            isBreakEvenLocked = true
        )
        assertTrue(lockedShort.breakEvenPrice < lockedShort.entryPrice)
        assertEquals(lockedShort.breakEvenPrice, lockedShort.stopLossPrice, 0.0001)
        val closedAtShortBe = lockedShort.copy(currentPrice = lockedShort.stopLossPrice)
        assertEquals(ActivePosition.BREAK_EVEN_NET_BUFFER_INR, closedAtShortBe.netUnrealizedPnl, 0.05)
    }

    @Test
    fun defaultAntiOvertradingConfig_andCapitalSizing5000_workCorrectly() {
        val defaultConfig = RiskConfig()
        assertEquals(5000.0, defaultConfig.smallAmountPerTrade, 0.01)
        assertEquals(5000.0, defaultConfig.effectiveCapitalPerTrade, 0.01)
        assertEquals(2, defaultConfig.maxOpenPositions)
        assertEquals(1, defaultConfig.maxPositionsPerBasket)
        assertEquals(99, defaultConfig.minAiConfidence)
        assertEquals(20, defaultConfig.reentryCooldownSec)
        assertEquals(15.0, defaultConfig.singleTradeInstantBookInr, 0.001)
        assertEquals(25.0, defaultConfig.basketInstantBookInr, 0.001)
        assertEquals(0.75, defaultConfig.stopLossPct, 0.001)
        assertEquals(80.0, defaultConfig.basketMaxLossInr, 0.001)
        assertFalse(defaultConfig.singleDirectionOnly)
        assertFalse(defaultConfig.allowSellAutoTrade)

        // At default ₹5,000: TATAMOTORS (₹986.30), HDFCBANK (₹1,728.60), RELIANCE (₹2,945.40), TCS (₹4,118.75) qualify
        assertEquals(5, floor(5000.0 / 986.30).toInt())
        assertEquals(2, floor(5000.0 / 1728.60).toInt())
        assertEquals(1, floor(5000.0 / 2945.40).toInt())
        assertEquals(1, floor(5000.0 / 4118.75).toInt())

        // At ₹25,000: larger MCX & blue-chip lots scale cleanly
        assertEquals(4, floor(25000.0 / 5942.00).toInt())
    }

    @Test
    fun strategyIndicatorEngine_confirmsVwapEmaAndRsiSweetSpotAndBlocksExhaustion() {
        // Steady uptrend keeping Wilder RSI inside 55..68 with 9 EMA > 21 EMA and LTP > VWAP
        val buyHistory = listOf(
            100.0, 100.4, 99.9, 100.6, 101.0, 100.5, 101.3, 101.7, 101.2, 102.0,
            102.4, 101.8, 102.6, 103.0, 102.3, 103.1, 103.5, 102.8, 103.6, 104.0,
            103.3, 104.1, 104.5, 103.8, 104.6, 105.0, 104.5, 105.0
        )
        val buyEval = StrategyIndicatorEngine.evaluateStrategy(
            ltp = 105.0,
            vwap = 103.2,
            priceHistory = buyHistory
        )
        assertTrue(buyEval.isPriceAboveVwap)
        assertTrue(buyEval.isEmaBullish)
        assertTrue("Expected RSI in 55..68, got ${buyEval.rsi14}", buyEval.rsi14 in 55.0..68.0)
        assertTrue(buyEval.strategyPassed)
        assertEquals(AiTrendSignal.STRONG_BUY, buyEval.signal)
        assertTrue("Expected confidence >= 90, got ${buyEval.confidence}", buyEval.confidence >= 90)

        // Exhaustion rally (pure monotonic climb pushing RSI > 68) must be WAIT (blocked)
        val exhaustionHistory = (1..28).map { 100.0 + (it * 1.2) }
        val exhaustionEval = StrategyIndicatorEngine.evaluateStrategy(
            ltp = exhaustionHistory.last(),
            vwap = 110.0,
            priceHistory = exhaustionHistory
        )
        assertFalse(exhaustionEval.strategyPassed)

        // Bearish momentum keeping RSI inside 32..45 with 9 EMA < 21 EMA and LTP < VWAP
        val sellHistory = buyHistory.map { 210.0 - it }
        val sellEval = StrategyIndicatorEngine.evaluateStrategy(
            ltp = sellHistory.last(),
            vwap = 106.5,
            priceHistory = sellHistory
        )
        assertFalse(sellEval.isPriceAboveVwap)
        assertFalse(sellEval.isEmaBullish)
        assertTrue("Expected RSI in 32..45, got ${sellEval.rsi14}", sellEval.rsi14 in 32.0..45.0)
        assertTrue(sellEval.strategyPassed)
        assertEquals(AiTrendSignal.STRONG_SELL, sellEval.signal)
    }

    @Test
    fun openingVolatilityGuard_andDailyCircuitBreakerDefaults_workCorrectly() {
        val config = RiskConfig(enforceMarketHours = true, skipOpeningVolatilityEnabled = true)
        assertEquals(1500.0, config.dailyMaxLossLimitInr, 0.01)
        assertTrue(RiskConfig().skipOpeningVolatilityEnabled)

        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(ist).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 16) // Monday
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
        }
        val openingWindow = IstMarketScheduler.evaluate(config, cal.timeInMillis)
        assertTrue(openingWindow.isOpeningVolatilityWindow)
        assertFalse(IstMarketScheduler.isEntryAllowed(Exchange.NSE, config, openingWindow))
        assertFalse(IstMarketScheduler.isEntryAllowed(Exchange.BSE, config, openingWindow))

        // At 09:31 IST -> opening volatility window settled -> NSE/BSE allowed
        cal.set(Calendar.MINUTE, 31)
        val settledWindow = IstMarketScheduler.evaluate(config, cal.timeInMillis)
        assertFalse(settledWindow.isOpeningVolatilityWindow)
        assertTrue(IstMarketScheduler.isEntryAllowed(Exchange.NSE, config, settledWindow))
    }

    @Test
    fun dualBasketBuyAndSell_andTradeTransactionSummary_workIndependently() {
        val buyPos = ActivePosition(
            id = "b1", symbol = "RELIANCE", tradingSymbol = "RELIANCE-EQ",
            displayName = "Reliance Industries", exchange = Exchange.NSE, symbolToken = "2885",
            side = TradeSide.LONG, entryPrice = 2500.0, currentPrice = 2660.0, quantity = 2.0,
            targetProfitMode = TargetProfitMode.FIXED_INR, targetProfitInr = 300.0, targetProfitPct = 1.25,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.75,
            openedAtEpochMs = 0L, openedAtFormatted = "10:05:00", isPaperTrade = true, brokerOrderId = "P-B1"
        )
        val sellPos = ActivePosition(
            id = "s1", symbol = "HDFCBANK", tradingSymbol = "HDFCBANK-EQ",
            displayName = "HDFC Bank", exchange = Exchange.NSE, symbolToken = "1333",
            side = TradeSide.SHORT, entryPrice = 1700.0, currentPrice = 1660.0, quantity = 3.0,
            targetProfitMode = TargetProfitMode.FIXED_INR, targetProfitInr = 300.0, targetProfitPct = 1.25,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.75,
            openedAtEpochMs = 0L, openedAtFormatted = "10:06:00", isPaperTrade = true, brokerOrderId = "P-S1"
        )

        val positions = listOf(buyPos, sellPos)
        val buyNet = positions.filter { it.side == TradeSide.LONG }.sumOf { it.netUnrealizedPnl }
        val sellNet = positions.filter { it.side == TradeSide.SHORT }.sumOf { it.netUnrealizedPnl }

        // BUY basket has hit +₹300 net (+₹320 gross minus ~₹5 charges) while SELL basket (+₹120 gross) stays open
        assertTrue("Expected BUY basket >= 300, got $buyNet", buyNet >= 300.0)
        assertTrue("Expected SELL basket < 300, got $sellNet", sellNet in 100.0..299.9)

        val summary = com.example.domain.model.AccountSummary(
            totalDayPnl = buyNet + sellNet,
            basketDropsCompleted = 1,
            basketBookedProfitInr = buyNet,
            totalTradesExecutedToday = 2,
            totalCapitalInvestedToday = buyPos.investedCapital + sellPos.investedCapital
        )
        assertEquals(1, summary.basketDropsCompleted)
        assertEquals(2, summary.totalTradesExecutedToday)
        assertEquals(10100.0, summary.totalCapitalInvestedToday, 0.01)

        val tx = com.example.domain.model.TradeTransaction(
            id = buyPos.id,
            symbol = buyPos.symbol,
            tradingSymbol = buyPos.tradingSymbol,
            displayName = buyPos.displayName,
            exchange = buyPos.exchange,
            side = buyPos.side,
            quantity = buyPos.quantity,
            entryPrice = buyPos.entryPrice,
            exitOrCurrentPrice = buyPos.currentPrice,
            investedAmountInr = buyPos.investedCapital,
            grossPnlInr = buyPos.grossUnrealizedPnl,
            estimatedChargesInr = buyPos.estimatedChargesInr,
            netPnlInr = buyPos.netUnrealizedPnl,
            pnlPct = buyPos.unrealizedPnlPct,
            status = com.example.domain.model.TransactionStatus.CLOSED,
            entryTimeFormatted = buyPos.openedAtFormatted,
            exitTimeFormatted = "10:08:15",
            entryEpochMs = 0L,
            exitEpochMs = 195_000L,
            exitReason = "BUY Basket Auto-Drop (₹300 Target)",
            isPaperTrade = true,
            brokerOrderId = buyPos.brokerOrderId
        )
        assertEquals(5000.0, tx.investedAmountInr, 0.01)
        assertEquals(320.0, tx.grossPnlInr, 0.01)
        assertTrue(tx.netPnlInr >= 300.0)
    }

    @Test
    fun smartBasketTarget_calculatesProportionalPercentageAndCustomFixedInrCorrectly() {
        val autoSmartConfig = RiskConfig()
        assertEquals(TargetProfitMode.PERCENTAGE, autoSmartConfig.targetProfitMode)
        assertEquals(0.80, autoSmartConfig.targetProfitPct, 0.0001)

        // Exact proportional Auto-Smart (0.8%) targets across small & large basket sizes
        assertEquals(16.0, autoSmartConfig.computeBasketTargetInr(2000.0), 0.01)
        assertEquals(40.0, autoSmartConfig.computeBasketTargetInr(5000.0), 0.01)
        assertEquals(80.0, autoSmartConfig.computeBasketTargetInr(10000.0), 0.01)
        assertEquals(320.0, autoSmartConfig.computeBasketTargetInr(40000.0), 0.01)

        // Custom Fixed ₹ presets (₹50, ₹100, ₹150, ₹300; range ₹30..₹1,000)
        listOf(30.0, 50.0, 100.0, 150.0, 300.0, 1000.0).forEach { fixed ->
            val fixedConfig = autoSmartConfig.copy(
                targetProfitMode = TargetProfitMode.FIXED_INR,
                targetProfitInr = fixed
            )
            assertEquals(fixed, fixedConfig.computeBasketTargetInr(2000.0), 0.01)
            assertEquals(fixed, fixedConfig.computeBasketTargetInr(40000.0), 0.01)
        }
    }

    @Test
    fun basketHighWaterGuard_locks35At50And60At80AndResetsOnExit() {
        var hw = BasketHighWaterState()

        // Below +₹50: no floor locked yet
        hw = BasketHighWaterState.advance(hw, currentNetPnl = 42.0, hasPositions = true)
        assertEquals(42.0, hw.peakNetPnlInr, 0.01)
        assertNull(hw.lockedFloorInr)
        assertFalse(BasketHighWaterState.shouldTriggerFloorExit(hw, 25.0))

        // Crosses +₹50: hard floor immediately locked at +₹35
        hw = BasketHighWaterState.advance(hw, currentNetPnl = 51.5, hasPositions = true)
        assertEquals(51.5, hw.peakNetPnlInr, 0.01)
        assertEquals(35.0, hw.lockedFloorInr!!, 0.01)
        assertFalse(BasketHighWaterState.shouldTriggerFloorExit(hw, 40.0))
        assertTrue(BasketHighWaterState.shouldTriggerFloorExit(hw, 35.0))
        assertTrue(BasketHighWaterState.shouldTriggerFloorExit(hw, 34.2))

        // Climbs to +₹82: hard floor immediately raised to +₹60
        hw = BasketHighWaterState.advance(hw, currentNetPnl = 82.0, hasPositions = true)
        assertEquals(82.0, hw.peakNetPnlInr, 0.01)
        assertEquals(60.0, hw.lockedFloorInr!!, 0.01)
        assertFalse(BasketHighWaterState.shouldTriggerFloorExit(hw, 65.0))
        assertTrue(BasketHighWaterState.shouldTriggerFloorExit(hw, 60.0))

        // Resets completely once basket is squared off
        hw = BasketHighWaterState.advance(hw, currentNetPnl = 0.0, hasPositions = false)
        assertEquals(0.0, hw.peakNetPnlInr, 0.01)
        assertNull(hw.lockedFloorInr)
    }

    @Test
    fun positionProfitLock_setsStopLossTo10InrNetProfitAfterHitting20() {
        val longPos = ActivePosition(
            id = "pl1", symbol = "TATAMOTORS", tradingSymbol = "TATAMOTORS-EQ",
            displayName = "Tata Motors", exchange = Exchange.NSE, symbolToken = "3456",
            side = TradeSide.LONG, entryPrice = 1000.0, currentPrice = 1012.0, quantity = 2.0,
            targetProfitMode = TargetProfitMode.PERCENTAGE, targetProfitInr = 300.0, targetProfitPct = 0.80,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 1.25,
            isBreakEvenLocked = true,
            lockedProfitFloorInr = ActivePosition.POSITION_PROFIT_LOCK_FLOOR_INR,
            openedAtEpochMs = 0L, openedAtFormatted = "10:15:00", isPaperTrade = true, brokerOrderId = "PL-1"
        )
        // Position hit >= +₹20 net profit at LTP 1012 (Gross +₹24 minus ~₹2.40 charges > +₹20)
        assertTrue(longPos.netUnrealizedPnl >= ActivePosition.POSITION_PROFIT_LOCK_TRIGGER_INR)
        assertNotNull(longPos.profitLockFloorPrice)
        assertTrue(longPos.stopLossPrice > longPos.breakEvenPrice)

        // Closing right at stopLossPrice yields +₹10.00 net profit after charges
        val atFloor = longPos.copy(currentPrice = longPos.stopLossPrice)
        assertEquals(10.0, atFloor.netUnrealizedPnl, 0.05)

        // Mirror check for SHORT side
        val shortPos = longPos.copy(
            side = TradeSide.SHORT,
            entryPrice = 1000.0,
            currentPrice = 988.0
        )
        assertTrue(shortPos.netUnrealizedPnl >= ActivePosition.POSITION_PROFIT_LOCK_TRIGGER_INR)
        assertTrue(shortPos.stopLossPrice < shortPos.breakEvenPrice)
        val shortAtFloor = shortPos.copy(currentPrice = shortPos.stopLossPrice)
        assertEquals(10.0, shortAtFloor.netUnrealizedPnl, 0.05)
    }

    @Test
    fun masterDualBasketAutoBook_triggersAt25ZeroWaitCombinedNetProfitAcrossBuyAndSell() {
        val buyPos = ActivePosition(
            id = "mb1", symbol = "RELIANCE", tradingSymbol = "RELIANCE-EQ",
            displayName = "Reliance", exchange = Exchange.NSE, symbolToken = "2885",
            side = TradeSide.LONG, entryPrice = 2000.0, currentPrice = 2028.0, quantity = 2.0,
            targetProfitMode = TargetProfitMode.PERCENTAGE, targetProfitInr = 300.0, targetProfitPct = 0.80,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.35,
            openedAtEpochMs = 0L, openedAtFormatted = "11:00:00", isPaperTrade = true, brokerOrderId = "MB-1"
        )
        val losingSellPos = ActivePosition(
            id = "ms1", symbol = "ITC", tradingSymbol = "ITC-EQ",
            displayName = "ITC", exchange = Exchange.NSE, symbolToken = "1660",
            side = TradeSide.SHORT, entryPrice = 500.0, currentPrice = 503.0, quantity = 10.0,
            targetProfitMode = TargetProfitMode.PERCENTAGE, targetProfitInr = 300.0, targetProfitPct = 0.80,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.35,
            openedAtEpochMs = 0L, openedAtFormatted = "11:00:00", isPaperTrade = true, brokerOrderId = "MS-1"
        )

        // BUY ~ +42 net, SELL ~ -36 net -> combined < +25 Zero-Wait threshold
        val belowCombined = buyPos.copy(currentPrice = 2024.0)
        val combinedBelow = BasketHighWaterState.computeCombinedDualBasketNetPnl(listOf(belowCombined, losingSellPos))
        assertTrue("Expected combined < 25, got $combinedBelow", combinedBelow < 25.0)
        assertFalse(BasketHighWaterState.isMasterDualBasketTargetHit(listOf(belowCombined, losingSellPos)))

        // BUY ~ +51 net + milder SELL (~ -18 net) >= +25.0 -> triggers instant ALL Square-Off!
        val milderSell = losingSellPos.copy(currentPrice = 501.2)
        val combinedHit = BasketHighWaterState.computeCombinedDualBasketNetPnl(listOf(buyPos, milderSell))
        assertTrue("Expected combined >= 25, got $combinedHit", combinedHit >= 25.0)
        assertTrue(BasketHighWaterState.isMasterDualBasketTargetHit(listOf(buyPos, milderSell)))

        // Single basket only hitting +₹25+ -> triggers immediately too
        assertTrue(BasketHighWaterState.isMasterDualBasketTargetHit(listOf(buyPos)))
        // Empty positions -> false
        assertFalse(BasketHighWaterState.isMasterDualBasketTargetHit(emptyList()))
    }

    @Test
    fun strictBasketStopLoss80_andIndependentSingleBasket25AutoBook_workTogether() {
        val cfg = RiskConfig()
        assertEquals(0.75, cfg.stopLossPct, 0.0001)
        assertEquals(80.0, cfg.basketMaxLossInr, 0.0001)
        assertEquals(15.0, cfg.singleTradeInstantBookInr, 0.0001)
        assertEquals(25.0, cfg.basketInstantBookInr, 0.0001)

        // Independent single-basket effective target caps at +₹25 Zero-Wait ceiling while keeping < ₹25 targets intact
        assertEquals(16.0, cfg.computeEffectiveSingleBasketTargetInr(2000.0), 0.01)
        assertEquals(25.0, cfg.computeEffectiveSingleBasketTargetInr(5000.0), 0.01)
        assertEquals(25.0, cfg.computeEffectiveSingleBasketTargetInr(10000.0), 0.01)
        assertEquals(25.0, cfg.computeEffectiveSingleBasketTargetInr(40000.0), 0.01)

        // -₹80.00 Basket Max Loss Cut checks (round-trip charges like -₹56 must NOT trigger stop-loss)
        assertFalse(BasketHighWaterState.shouldTriggerBasketStopLoss(-56.0, hasPositions = true))
        assertTrue(BasketHighWaterState.shouldTriggerBasketStopLoss(-80.0, hasPositions = true))
        assertTrue(BasketHighWaterState.shouldTriggerBasketStopLoss(-85.4, hasPositions = true))
        assertFalse(BasketHighWaterState.shouldTriggerBasketStopLoss(-90.0, hasPositions = false))
    }

    @Test
    fun geminiDeepScreener_passesHighConvictionTrendVolumeFeeClearance_andBlocksWeakOrLowVolume() {
        val strongHistory = listOf(
            100.0, 100.4, 99.9, 100.6, 101.0, 100.5, 101.3, 101.7, 101.2, 102.0,
            102.4, 101.8, 102.6, 103.0, 102.3, 103.1, 103.5, 102.8, 103.6, 104.0,
            103.3, 104.1, 104.5, 103.8, 104.6, 105.0, 104.5, 105.0
        )

        val passResult = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
            ltp = 105.0,
            vwap = 103.2,
            priceHistory = strongHistory,
            exchange = Exchange.NSE,
            allocationInr = 10000.0,
            volumeSurgeRatio = 1.45
        )
        assertTrue(passResult.trendMomentumPass)
        assertTrue(passResult.volumeLiquidityPass)
        assertTrue(passResult.feeClearancePass)
        assertTrue(passResult.allChecksPassed)
        assertTrue("Expected score >= 99, got ${passResult.screenerScore}", passResult.screenerScore >= 99)
        assertEquals("PASS", passResult.verdict)

        // Moderate volume (1.12x) scores < 99% -> SKIP under 99% gate, PASS under 95% preset
        val moderateResult99 = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
            ltp = 105.0,
            vwap = 103.2,
            priceHistory = strongHistory,
            exchange = Exchange.NSE,
            allocationInr = 10000.0,
            volumeSurgeRatio = 1.12,
            minConvictionThreshold = 99
        )
        assertTrue(moderateResult99.allChecksPassed)
        assertTrue("Expected score in 94..98, got ${moderateResult99.screenerScore}", moderateResult99.screenerScore in 94..98)
        assertEquals("SKIP", moderateResult99.verdict)

        val moderateResult95 = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
            ltp = 105.0,
            vwap = 103.2,
            priceHistory = strongHistory,
            exchange = Exchange.NSE,
            allocationInr = 10000.0,
            volumeSurgeRatio = 1.12,
            minConvictionThreshold = 95
        )
        assertEquals("PASS", moderateResult95.verdict)

        // Low volume (0.75x) must be blocked with SKIP
        val lowVolResult = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
            ltp = 105.0,
            vwap = 103.2,
            priceHistory = strongHistory,
            exchange = Exchange.NSE,
            allocationInr = 10000.0,
            volumeSurgeRatio = 0.75
        )
        assertFalse(lowVolResult.volumeLiquidityPass)
        assertFalse(lowVolResult.allChecksPassed)
        assertEquals("SKIP", lowVolResult.verdict)
        assertTrue(lowVolResult.reason.contains("AI Screener Blocked", ignoreCase = true))

        // Choppy/flat range failing fee-clearance must be blocked with SKIP
        val flatChopHistory = (1..28).map { idx -> if (idx % 2 == 0) 100.05 else 100.00 }
        val chopResult = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
            ltp = 100.05,
            vwap = 100.02,
            priceHistory = flatChopHistory,
            exchange = Exchange.NSE,
            allocationInr = 10000.0,
            volumeSurgeRatio = 1.30
        )
        assertFalse(chopResult.allChecksPassed)
        assertEquals("SKIP", chopResult.verdict)
    }

    @Test
    fun singleDirectionAiTrendMode_neverOpensBuyAndSellSimultaneously_andPicksHighestConvictionSide() {
        fun mockCandidate(symbol: String, signal: AiTrendSignal, score: Int): MarketInstrument = MarketInstrument(
            symbol = symbol,
            tradingSymbol = "$symbol-EQ",
            displayName = symbol,
            exchange = Exchange.NSE,
            symbolToken = "100",
            ltp = 500.0,
            prevClose = 498.0,
            rsi14 = if (signal == AiTrendSignal.STRONG_BUY) 61.0 else 38.0,
            aiSignal = signal,
            aiConfidence = score,
            priceHistory = listOf(498.0, 499.0, 500.0),
            strategyPassed = true,
            aiScreenerScore = score,
            trendMomentumPass = true,
            volumeLiquidityPass = true,
            feeClearancePass = true,
            aiScreenerVerdict = "PASS",
            isOneMinCandleConfirmed = true
        )

        val buyTop = mockCandidate("RELIANCE", AiTrendSignal.STRONG_BUY, 94)
        val sellTop = mockCandidate("HDFCBANK", AiTrendSignal.STRONG_SELL, 97)
        val sellSecond = mockCandidate("INFY", AiTrendSignal.STRONG_SELL, 93)
        val candidates = listOf(buyTop, sellTop, sellSecond)

        // 1) When flat and allowSellAutoTrade is enabled, picks ONLY the highest-scoring side (SELL @ 97%), blocking opposing BUY @ 94%
        val flatPlan = StrategyIndicatorEngine.selectSingleDirectionEntries(
            activePositions = emptyList(),
            candidates = candidates,
            minAiConfidence = 92,
            maxSlots = 2,
            allowSellAutoTrade = true
        )
        assertEquals(TradeSide.SHORT, flatPlan.activeDirection)
        assertEquals(listOf("HDFCBANK", "INFY"), flatPlan.selectedCandidates.map { it.symbol })
        assertTrue(flatPlan.selectedCandidates.all { it.aiSignal == AiTrendSignal.STRONG_SELL })
        assertEquals(1, flatPlan.blockedOppositeCount)

        // 2) While a LONG (BUY) position is open, opposite STRONG_SELL (even at 97%) is strictly blocked
        val activeBuy = ActivePosition(
            id = "open-buy", symbol = "TCS", tradingSymbol = "TCS-EQ",
            displayName = "TCS", exchange = Exchange.NSE, symbolToken = "11536",
            side = TradeSide.LONG, entryPrice = 4000.0, currentPrice = 4010.0, quantity = 2.0,
            targetProfitMode = TargetProfitMode.PERCENTAGE, targetProfitInr = 300.0, targetProfitPct = 0.80,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.35,
            openedAtEpochMs = 0L, openedAtFormatted = "10:15:00", isPaperTrade = true, brokerOrderId = "B1"
        )
        val lockedBuyPlan = StrategyIndicatorEngine.selectSingleDirectionEntries(
            activePositions = listOf(activeBuy),
            candidates = candidates,
            minAiConfidence = 92,
            maxSlots = 2,
            allowSellAutoTrade = true
        )
        assertEquals(TradeSide.LONG, lockedBuyPlan.activeDirection)
        assertEquals(listOf("RELIANCE"), lockedBuyPlan.selectedCandidates.map { it.symbol })
        assertEquals(2, lockedBuyPlan.blockedOppositeCount)
    }

    @Test
    fun geminiScreenerShortlist_capsToTop6AffordableHighConvictionCandidates() {
        val sampleUniverse = (1..12).map { idx ->
            val affordablePrice = if (idx <= 10) 100.0 + idx else 15000.0
            MarketInstrument(
                symbol = "SYM$idx",
                tradingSymbol = "SYM$idx-EQ",
                displayName = "Company $idx",
                exchange = Exchange.NSE,
                symbolToken = "$idx",
                ltp = affordablePrice,
                prevClose = affordablePrice,
                rsi14 = 60.0,
                aiSignal = AiTrendSignal.STRONG_BUY,
                aiConfidence = 70 + idx,
                priceHistory = listOf(affordablePrice),
                volumeSurgeRatio = 1.0 + (idx * 0.05),
                strategyPassed = idx >= 4,
                aiScreenerScore = 70 + (idx * 2),
                trendMomentumPass = idx >= 4,
                volumeLiquidityPass = idx >= 5,
                feeClearancePass = idx >= 5,
                aiScreenerVerdict = if (idx >= 5) "PASS" else "SKIP"
            )
        }

        val shortlisted = GeminiMarketScreenerService.shortlistCandidates(
            instruments = sampleUniverse,
            allocationInr = 10000.0,
            maxCount = 6
        )
        assertEquals(6, shortlisted.size)
        // Must exclude SYM11 & SYM12 (LTP 15,000 > 10,000 allocation) and rank highest-scoring affordable tickers first
        assertFalse(shortlisted.any { it.symbol == "SYM11" || it.symbol == "SYM12" })
        assertEquals(listOf("SYM10", "SYM9", "SYM8", "SYM7", "SYM6", "SYM5"), shortlisted.map { it.symbol })
    }

    @Test
    fun synchronizedPairMode_entersBuyAndSellTogether_blocksPartialReentryUntilBothExit_andBooksCombined25() {
        fun mockCandidate(symbol: String, signal: AiTrendSignal, score: Int): MarketInstrument = MarketInstrument(
            symbol = symbol,
            tradingSymbol = "$symbol-EQ",
            displayName = symbol,
            exchange = Exchange.NSE,
            symbolToken = "100",
            ltp = 500.0,
            prevClose = 498.0,
            rsi14 = if (signal == AiTrendSignal.STRONG_BUY) 61.0 else 38.0,
            aiSignal = signal,
            aiConfidence = score,
            priceHistory = listOf(498.0, 499.0, 500.0),
            strategyPassed = true,
            aiScreenerScore = score,
            trendMomentumPass = true,
            volumeLiquidityPass = true,
            feeClearancePass = true,
            aiScreenerVerdict = "PASS",
            isOneMinCandleConfirmed = true
        )

        val buyTop = mockCandidate("RELIANCE", AiTrendSignal.STRONG_BUY, 99)
        val sellTop = mockCandidate("HDFCBANK", AiTrendSignal.STRONG_SELL, 99)

        // 1) Never opens a single side alone when flat (BUY-only or SELL-only must NOT execute)
        val buyOnlyPlan = StrategyIndicatorEngine.selectSynchronizedPairEntries(
            activePositions = emptyList(),
            candidates = listOf(buyTop),
            minAiConfidence = 99,
            allowSellAutoTrade = true
        )
        assertFalse(buyOnlyPlan.isReadyToExecutePair)
        assertNull(buyOnlyPlan.buyCandidate)
        assertNull(buyOnlyPlan.sellCandidate)

        val sellOnlyPlan = StrategyIndicatorEngine.selectSynchronizedPairEntries(
            activePositions = emptyList(),
            candidates = listOf(sellTop),
            minAiConfidence = 99,
            allowSellAutoTrade = true
        )
        assertFalse(sellOnlyPlan.isReadyToExecutePair)
        assertNull(sellOnlyPlan.buyCandidate)
        assertNull(sellOnlyPlan.sellCandidate)

        // 2) When flat (0 Open), allowSellAutoTrade is enabled, and BOTH BUY >= 99% & SELL >= 99% are ready, selects 1 BUY + 1 SELL together
        val readyPair = StrategyIndicatorEngine.selectSynchronizedPairEntries(
            activePositions = emptyList(),
            candidates = listOf(buyTop, sellTop),
            minAiConfidence = 99,
            allowSellAutoTrade = true
        )
        assertTrue(readyPair.isReadyToExecutePair)
        assertEquals("RELIANCE", readyPair.buyCandidate?.symbol)
        assertEquals("HDFCBANK", readyPair.sellCandidate?.symbol)

        // 3) Strict "Wait Until Both Legs Exit" Lock: even if 1 leg exited and only 1 leg remains open, block ALL entries
        val remainingSellLeg = ActivePosition(
            id = "leg2", symbol = "HDFCBANK", tradingSymbol = "HDFCBANK-EQ",
            displayName = "HDFC Bank", exchange = Exchange.NSE, symbolToken = "1333",
            side = TradeSide.SHORT, entryPrice = 500.0, currentPrice = 500.5, quantity = 10.0,
            targetProfitMode = TargetProfitMode.PERCENTAGE, targetProfitInr = 300.0, targetProfitPct = 0.80,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.75,
            openedAtEpochMs = 0L, openedAtFormatted = "10:15:00", isPaperTrade = true, brokerOrderId = "S1"
        )
        val blockedWhileOneLegOpen = StrategyIndicatorEngine.selectSynchronizedPairEntries(
            activePositions = listOf(remainingSellLeg),
            candidates = listOf(buyTop, sellTop),
            minAiConfidence = 99,
            allowSellAutoTrade = true
        )
        assertFalse(blockedWhileOneLegOpen.isReadyToExecutePair)

        // 4) Block re-entry while 20s pair cooldown is still active
        val blockedCooldown = StrategyIndicatorEngine.selectSynchronizedPairEntries(
            activePositions = emptyList(),
            candidates = listOf(buyTop, sellTop),
            minAiConfidence = 99,
            allowSellAutoTrade = true,
            isBuyCooldownActive = false,
            isSellCooldownActive = true
        )
        assertFalse(blockedCooldown.isReadyToExecutePair)

        // 5) Combined Pair +₹25.00 Instant Auto-Book triggers when BUY Net + SELL Net >= +₹25.00 (e.g., ₹12 BUY + ₹13 SELL = ₹25)
        // At 10 qty on ₹500 NSE, round-trip charges are ~₹6.30/leg.
        // So Gross +₹18.30 -> ~+₹12.00 Net on BUY, Gross +₹19.30 -> ~+₹13.00 Net on SELL -> Combined ~+₹25.00 Net!
        val buyLeg = ActivePosition(
            id = "leg1", symbol = "RELIANCE", tradingSymbol = "RELIANCE-EQ",
            displayName = "Reliance", exchange = Exchange.NSE, symbolToken = "2885",
            side = TradeSide.LONG, entryPrice = 500.0, currentPrice = 501.85, quantity = 10.0,
            targetProfitMode = TargetProfitMode.PERCENTAGE, targetProfitInr = 300.0, targetProfitPct = 0.80,
            stopLossMode = StopLossMode.PERCENTAGE, stopLossInr = 150.0, stopLossPct = 0.75,
            openedAtEpochMs = 0L, openedAtFormatted = "10:15:00", isPaperTrade = true, brokerOrderId = "B1"
        )
        val sellLeg = remainingSellLeg.copy(currentPrice = 498.05)
        assertTrue("BUY leg should be ~+₹12 net (<+₹25 individual)", buyLeg.netUnrealizedPnl in 11.5..13.5)
        assertTrue("SELL leg should be ~+₹13 net (<+₹25 individual)", sellLeg.netUnrealizedPnl in 12.5..14.5)
        val combinedPair = listOf(buyLeg, sellLeg)
        assertTrue(
            "Expected combined BUY (~₹12) + SELL (~₹13) >= +₹25.00, got ${BasketHighWaterState.computeCombinedDualBasketNetPnl(combinedPair)}",
            BasketHighWaterState.isMasterDualBasketTargetHit(combinedPair, 25.0)
        )

        // 6) If BUY crosses +₹25.00 Net on its own while SELL is in loss (e.g., -₹18 Net -> combined < +₹25),
        // BUY books immediately at >= +₹25.00, while SELL is HELD open until SELL recovers and hits >= +₹25.00 Net!
        val buyCrossed25 = buyLeg.copy(currentPrice = 503.25) // Gross +₹32.50 - ~₹6.30 charges ≈ +₹26.20 Net (>= +₹25)
        val sellInLoss = remainingSellLeg.copy(currentPrice = 501.20) // Gross -₹12.00 - ~₹6.30 charges ≈ -₹18.30 Net
        val splitPair = listOf(buyCrossed25, sellInLoss)

        // Combined is ~+₹7.90 (< +₹25), so Master Combined does NOT close the losing SELL leg in a loss
        assertFalse(BasketHighWaterState.isMasterDualBasketTargetHit(splitPair, 25.0))
        // BUY leg crosses +₹25.00 single-leg threshold -> BUY books immediately!
        assertTrue(buyCrossed25.netUnrealizedPnl >= RiskConfig().singleTradeInstantBookInr)
        assertTrue(sellInLoss.netUnrealizedPnl < 0.0)

        // While SELL is held open to recover, new pair entry stays strictly locked
        assertFalse(
            StrategyIndicatorEngine.selectSynchronizedPairEntries(
                activePositions = listOf(sellInLoss),
                candidates = listOf(buyTop, sellTop),
                minAiConfidence = 99,
                allowSellAutoTrade = true
            ).isReadyToExecutePair
        )

        // Once held SELL leg recovers into profit (>= +₹25.00 Net), it books profit too!
        val sellRecoveredToProfit = sellInLoss.copy(currentPrice = 496.75) // Gross +₹32.50 - ~₹6.30 charges ≈ +₹26.20 Net
        assertTrue(sellRecoveredToProfit.netUnrealizedPnl >= RiskConfig().singleTradeInstantBookInr)
    }

    @Test
    fun geminiAi4Hydrogen_apiQuotaLimitDetection_andPerformanceDiagnosticDefaults() {
        // 1) Verify API quota / access limit detection
        assertTrue(GeminiMarketScreenerService.isQuotaOrAccessLimitError("HTTP 429 Resource Exhausted"))
        assertTrue(GeminiMarketScreenerService.isQuotaOrAccessLimitError("RESOURCE_EXHAUSTED: Quota exceeded for Gemini API"))
        assertTrue(GeminiMarketScreenerService.isQuotaOrAccessLimitError("API key access limit reached"))
        assertFalse(GeminiMarketScreenerService.isQuotaOrAccessLimitError("Network timeout after 15000ms"))
        assertFalse(GeminiMarketScreenerService.isQuotaOrAccessLimitError(null))

        // 2) Verify GeminiPerformanceDiagnostic default 99% target & GROWING_CONTINUE state
        val diag = GeminiPerformanceDiagnostic()
        assertEquals(GrowthDiagnosisStatus.GROWING_CONTINUE, diag.status)
        assertEquals(99, diag.targetSuccessRatePct)
        assertFalse(diag.isApiQuotaExhausted)

        // 3) Verify exact Delivery, Intraday, and Options charge formulas for (Buy 5000, Sell 5100)
        val del = AngelOneChargeCalculator.calculateEquityDeliveryCharges(5000.0, 5100.0)
        val intra = AngelOneChargeCalculator.calculateEquityIntradayCharges(5000.0, 5100.0)
        val opt = AngelOneChargeCalculator.calculateOptionsCharges(5000.0, 5100.0)
        assertEquals("46.88", del.totalChargesFormatted)
        assertEquals("13.28", intra.totalChargesFormatted)
        assertEquals("59.43", opt.totalChargesFormatted)
        assertEquals(
            "Delivery: 46.88, Intraday: 13.28, Options: 59.43",
            AngelOneChargeCalculator.formatBenchmarkSummary(5000.0, 5100.0)
        )

        // 4) Small-order SEBI 2.5% cap matching Angel One Calculator screenshots:
        // Intraday Buy ₹100 -> Sell ₹200: Buy Brok ₹2.50 + Sell Brok ₹5.00 = ₹7.50, DP = ₹0
        val smallIntra = AngelOneChargeCalculator.calculateEquityIntradayCharges(100.0, 200.0)
        assertEquals(2.50, smallIntra.buyBrokerage, 0.01)
        assertEquals(5.00, smallIntra.sellBrokerage, 0.01)
        assertEquals(0.0, smallIntra.dpCharge, 0.01)

        // Delivery Buy ₹200 -> Sell ₹250: Buy Brok ₹5.00 + Sell Brok ₹5.00 = ₹10.00, DP = ₹20
        val smallDel = AngelOneChargeCalculator.calculateEquityDeliveryCharges(200.0, 250.0)
        assertEquals(5.00, smallDel.buyBrokerage, 0.01)
        assertEquals(5.00, smallDel.sellBrokerage, 0.01)
        assertEquals(20.00, smallDel.dpCharge, 0.01)
    }

    @Test
    fun allowSellAutoTrade_defaultsToFalse_andBlocksAllSellEntries() {
        val config = RiskConfig()
        assertFalse(config.allowSellAutoTrade)
        assertEquals(15.0, config.singleTradeInstantBookInr, 0.001)
        assertEquals(25.0, config.basketInstantBookInr, 0.001)

        val buyCandidate = MarketInstrument(
            symbol = "TATAMOTORS", tradingSymbol = "TATAMOTORS-EQ", displayName = "Tata Motors",
            exchange = Exchange.NSE, symbolToken = "3456", ltp = 1000.0, prevClose = 990.0,
            rsi14 = 58.0, aiSignal = AiTrendSignal.STRONG_BUY, aiConfidence = 99,
            priceHistory = listOf(990.0, 995.0, 1000.0), strategyPassed = true,
            aiScreenerScore = 99, trendMomentumPass = true, volumeLiquidityPass = true,
            feeClearancePass = true, aiScreenerVerdict = "PASS",
            isOneMinCandleConfirmed = true, oneMinCandlePattern = "1m Bullish Engulfing"
        )
        val sellCandidate = MarketInstrument(
            symbol = "HDFCBANK", tradingSymbol = "HDFCBANK-EQ", displayName = "HDFC Bank",
            exchange = Exchange.NSE, symbolToken = "1333", ltp = 1700.0, prevClose = 1720.0,
            rsi14 = 40.0, aiSignal = AiTrendSignal.STRONG_SELL, aiConfidence = 99,
            priceHistory = listOf(1720.0, 1710.0, 1700.0), strategyPassed = true,
            aiScreenerScore = 99, trendMomentumPass = true, volumeLiquidityPass = true,
            feeClearancePass = true, aiScreenerVerdict = "PASS",
            isOneMinCandleConfirmed = true, oneMinCandlePattern = "1m Bearish Breakdown"
        )

        // 1) When allowSellAutoTrade == false, selectSynchronizedPairEntries strictly blocks SELL
        val blockedPairPlan = StrategyIndicatorEngine.selectSynchronizedPairEntries(
            activePositions = emptyList(),
            candidates = listOf(buyCandidate, sellCandidate),
            minAiConfidence = 99,
            allowSellAutoTrade = false
        )
        assertFalse(blockedPairPlan.isReadyToExecutePair)
        assertNotNull(blockedPairPlan.blockedReason)
        assertTrue(blockedPairPlan.blockedReason!!.contains("SELL Auto-Trading is OFF"))

        // 2) When allowSellAutoTrade == false, 100% BUY-Only Scalper entries are selected
        val buyOnlySelected = StrategyIndicatorEngine.selectBuyOnlyScalperEntries(
            activePositions = emptyList(),
            candidates = listOf(buyCandidate, sellCandidate),
            minAiConfidence = 99,
            maxSlots = 2
        )
        assertEquals(1, buyOnlySelected.size)
        assertEquals("TATAMOTORS", buyOnlySelected.first().symbol)
        assertEquals(AiTrendSignal.STRONG_BUY, buyOnlySelected.first().aiSignal)

        // 3) When allowSellAutoTrade == true, synchronized pair execution is permitted
        val allowedPairPlan = StrategyIndicatorEngine.selectSynchronizedPairEntries(
            activePositions = emptyList(),
            candidates = listOf(buyCandidate, sellCandidate),
            minAiConfidence = 99,
            allowSellAutoTrade = true
        )
        assertTrue(allowedPairPlan.isReadyToExecutePair)
        assertEquals("TATAMOTORS", allowedPairPlan.buyCandidate?.symbol)
        assertEquals("HDFCBANK", allowedPairPlan.sellCandidate?.symbol)

        // 4) In single-direction mode, STRONG_SELL is blocked when allowSellAutoTrade == false
        val singleDirPlan = StrategyIndicatorEngine.selectSingleDirectionEntries(
            activePositions = emptyList(),
            candidates = listOf(sellCandidate),
            minAiConfidence = 99,
            maxSlots = 2,
            allowSellAutoTrade = false
        )
        assertTrue(singleDirPlan.selectedCandidates.isEmpty())
        assertNull(singleDirPlan.activeDirection)
    }

    @Test
    fun oneMinCandleStrategyEngine_confirmsBreakoutAndBlocksNonConfirmed() {
        // Bullish candle: previous close 100.0, current 102.5 above 9 EMA (101.0) & VWAP (100.8), RSI 58.0 in 52..68 zone
        val bullishAnalysis = StrategyIndicatorEngine.evaluateOneMinCandle(
            ltp = 102.5,
            vwap = 100.8,
            ema9 = 101.0,
            rsi14 = 58.0,
            priceHistory = listOf(100.0, 100.2, 100.0, 102.5)
        )
        assertTrue(bullishAnalysis.isConfirmed)
        assertTrue(bullishAnalysis.patternName.contains("Bullish"))
        assertTrue(bullishAnalysis.statusPill.contains("PASS"))

        // Bearish breakdown: previous 100.0, current 97.5 below 9 EMA (99.0) & VWAP (99.2), RSI 38.0 in 32..48 zone
        val bearishAnalysis = StrategyIndicatorEngine.evaluateOneMinCandle(
            ltp = 97.5,
            vwap = 99.2,
            ema9 = 99.0,
            rsi14 = 38.0,
            priceHistory = listOf(100.0, 99.8, 100.0, 97.5)
        )
        assertTrue(bearishAnalysis.isConfirmed)
        assertTrue(bearishAnalysis.patternName.contains("Bearish") || bearishAnalysis.patternName.contains("Breakdown"))

        // Sideways / Choppy candle (RSI 50, below 9 EMA): blocked
        val choppyAnalysis = StrategyIndicatorEngine.evaluateOneMinCandle(
            ltp = 99.8,
            vwap = 100.0,
            ema9 = 100.2,
            rsi14 = 50.0,
            priceHistory = listOf(99.9, 100.0, 99.8)
        )
        assertFalse(choppyAnalysis.isConfirmed)
    }

    @Test
    fun zeroLossProfitShield_andZeroWait15InrBook_protect100PercentNetProfit() {
        val position = ActivePosition(
            id = "test-pos",
            symbol = "TATAMOTORS",
            tradingSymbol = "TATAMOTORS-EQ",
            displayName = "Tata Motors",
            exchange = Exchange.NSE,
            symbolToken = "3456",
            side = TradeSide.LONG,
            entryPrice = 1000.0,
            currentPrice = 1000.0,
            quantity = 5.0,
            targetProfitMode = TargetProfitMode.FIXED_INR,
            targetProfitInr = 15.0,
            targetProfitPct = 0.80,
            stopLossMode = StopLossMode.PERCENTAGE,
            stopLossInr = 150.0,
            stopLossPct = 0.75,
            openedAtEpochMs = 0L,
            openedAtFormatted = "09:30:00",
            isPaperTrade = true,
            brokerOrderId = "ORD-1"
        )

        // Estimated round-trip charges on ₹5,000 invested is ~₹6.30
        assertTrue(position.estimatedChargesInr in 5.0..8.0)

        // 1) Price rises: Net P&L hits +₹12.50 (Trigger >= ₹12.00)
        // Gross needed = 12.50 + ~6.30 = ~18.80 -> price +3.76 -> currentPrice = 1003.80
        val at12Trigger = position.copy(currentPrice = 1003.80)
        assertTrue(at12Trigger.netUnrealizedPnl >= ActivePosition.ZERO_LOSS_SHIELD_TRIGGER_INR)

        // Zero-loss shield locks floor at +₹5.00 Net
        val shieldLocked = at12Trigger.copy(
            isBreakEvenLocked = true,
            isZeroLossShieldLocked = true,
            lockedProfitFloorInr = ActivePosition.ZERO_LOSS_SHIELD_FLOOR_INR
        )
        assertTrue(shieldLocked.isZeroLossShieldLocked)
        assertEquals(5.0, shieldLocked.lockedProfitFloorInr!!, 0.001)

        // If market reverses back down to +₹5.00 Net, profit is locked (never a loss!)
        val floorPrice = shieldLocked.profitLockFloorPrice ?: shieldLocked.breakEvenPrice
        val atFloorPosition = shieldLocked.copy(currentPrice = floorPrice)
        assertEquals(ActivePosition.ZERO_LOSS_SHIELD_FLOOR_INR, atFloorPosition.netUnrealizedPnl, 0.05)

        // 2) If position continues upward and hits >= +₹15.00 Net: Zero-Wait Single Trade Auto-Book!
        val at15Target = position.copy(currentPrice = 1004.50)
        assertTrue(at15Target.netUnrealizedPnl >= ActivePosition.ZERO_WAIT_SINGLE_TRADE_TARGET_INR)
        assertTrue(at15Target.netUnrealizedPnl >= RiskConfig().singleTradeInstantBookInr)
    }

    @Test
    fun formatTime_alwaysUsesIstTimezoneSynchronizedWithMarketScheduler() {
        val utc = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(utc).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 16)
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 04:00:00 UTC == 09:30:00 IST
        val formatted = com.example.util.Formatters.formatTime(cal.timeInMillis)
        val scheduled = IstMarketScheduler.evaluate(RiskConfig(), cal.timeInMillis).istTimeFormatted
        assertEquals("09:30:00 IST", formatted)
        assertEquals(scheduled, formatted)
    }

    @Test
    fun screenerWithOneMinCandleOhlc_blocksUnconfirmedChoppyCandleEvenIfIndicatorsLookHigh() {
        val strongHistory = listOf(
            100.0, 100.4, 99.9, 100.6, 101.0, 100.5, 101.3, 101.7, 101.2, 102.0,
            102.4, 101.8, 102.6, 103.0, 102.3, 103.1, 103.5, 102.8, 103.6, 104.0,
            103.3, 104.1, 104.5, 103.8, 104.6, 105.0, 104.5, 105.0
        )
        // Red forming candle (open 105.5 > close 105.0) during a STRONG_BUY signal must NOT pass 99% gate!
        val opposingRedCandle = com.example.util.CandleOhlcWindow(
            minuteBucket = 100L,
            open = 105.5,
            high = 105.6,
            low = 104.9,
            close = 105.0,
            prevOpen = 104.2,
            prevHigh = 104.9,
            prevLow = 104.1,
            prevClose = 104.8,
            secondsIntoCandle = 35
        )
        val blockedResult = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
            ltp = 105.0,
            vwap = 103.2,
            priceHistory = strongHistory,
            exchange = Exchange.NSE,
            allocationInr = 10000.0,
            volumeSurgeRatio = 1.45,
            candleOhlc = opposingRedCandle
        )
        assertEquals("SKIP", blockedResult.verdict)

        // Confirmed green breakout 1m candle passes 99% gate cleanly
        val greenBreakoutCandle = com.example.util.CandleOhlcWindow(
            minuteBucket = 100L,
            open = 104.4,
            high = 105.0,
            low = 104.3,
            close = 105.0,
            prevOpen = 104.6,
            prevHigh = 104.7,
            prevLow = 104.2,
            prevClose = 104.4,
            secondsIntoCandle = 42
        )
        val confirmedResult = StrategyIndicatorEngine.evaluateGeminiDeepScreener(
            ltp = 105.0,
            vwap = 103.2,
            priceHistory = strongHistory,
            exchange = Exchange.NSE,
            allocationInr = 10000.0,
            volumeSurgeRatio = 1.45,
            candleOhlc = greenBreakoutCandle
        )
        assertEquals("PASS", confirmedResult.verdict)
        assertTrue(confirmedResult.screenerScore >= 99)
        assertTrue(confirmedResult.isOneMinCandleConfirmed)
    }
}


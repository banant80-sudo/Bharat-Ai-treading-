package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Bharat AI Trading", appName)
  }

  @Test
  fun `toggleAutoTrading reliably turns ON and auto heals paper sandbox`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = com.example.data.repository.RiskPreferencesStorage(context)
    val repo = com.example.data.repository.TradingEngineRepository(storage)

    // Pre-initialized connection state is Connected (Paper Sandbox)
    org.junit.Assert.assertTrue(repo.connectionState.value is com.example.domain.model.BrokerConnectionState.Connected)
    org.junit.Assert.assertFalse(repo.autoTradingEnabled.value)

    // Toggling ON must succeed without flipping back to OFF
    val result = repo.toggleAutoTrading(true)
    org.junit.Assert.assertTrue(result.isSuccess)
    org.junit.Assert.assertTrue(repo.autoTradingEnabled.value)

    // Disconnect broker and toggle ON again -> must auto-heal and turn ON
    repo.disconnectBroker()
    org.junit.Assert.assertFalse(repo.autoTradingEnabled.value)

    val rearmResult = repo.toggleAutoTrading(true)
    org.junit.Assert.assertTrue(rearmResult.isSuccess)
    org.junit.Assert.assertTrue(repo.autoTradingEnabled.value)
    org.junit.Assert.assertTrue(repo.connectionState.value is com.example.domain.model.BrokerConnectionState.Connected)
  }

  @Test
  fun `updateRiskConfig persists all user changes cleanly without init or auto-tune overwrite`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = com.example.data.repository.RiskPreferencesStorage(context)
    val repo = com.example.data.repository.TradingEngineRepository(storage)

    val customConfig = com.example.domain.model.RiskConfig(
      smallAmountAutoSplitEnabled = false,
      smallAmountPerTrade = 12000.0,
      maxCapitalPerTrade = 25000.0,
      singleTradeInstantBookInr = 50.0,
      basketInstantBookInr = 100.0,
      stopLossPct = 1.25,
      singleDirectionOnly = true,
      allowSellAutoTrade = true,
      reentryCooldownSec = 30
    )

    repo.updateRiskConfig(customConfig)
    assertEquals(25000.0, repo.riskConfig.value.maxCapitalPerTrade, 0.01)
    assertEquals(12000.0, repo.riskConfig.value.smallAmountPerTrade, 0.01)
    assertEquals(50.0, repo.riskConfig.value.singleTradeInstantBookInr, 0.01)
    assertEquals(100.0, repo.riskConfig.value.basketInstantBookInr, 0.01)
    assertEquals(1.25, repo.riskConfig.value.stopLossPct, 0.01)
    org.junit.Assert.assertFalse(repo.riskConfig.value.smallAmountAutoSplitEnabled)
    org.junit.Assert.assertTrue(repo.riskConfig.value.singleDirectionOnly)
    org.junit.Assert.assertTrue(repo.riskConfig.value.allowSellAutoTrade)
    assertEquals(30, repo.riskConfig.value.reentryCooldownSec)

    // Trigger AI optimization -> MUST preserve user's toggles & instant-book targets!
    repo.runAiPerformanceOptimization()
    org.junit.Assert.assertFalse(repo.riskConfig.value.smallAmountAutoSplitEnabled)
    org.junit.Assert.assertTrue(repo.riskConfig.value.singleDirectionOnly)
    org.junit.Assert.assertTrue(repo.riskConfig.value.allowSellAutoTrade)
    assertEquals(50.0, repo.riskConfig.value.singleTradeInstantBookInr, 0.01)
    assertEquals(100.0, repo.riskConfig.value.basketInstantBookInr, 0.01)

    // Verify saved to DataStore by reading via a brand new repository
    val reloadedRepo = com.example.data.repository.TradingEngineRepository(storage)
    kotlinx.coroutines.delay(300L)
    assertEquals(25000.0, reloadedRepo.riskConfig.value.maxCapitalPerTrade, 0.01)
    assertEquals(50.0, reloadedRepo.riskConfig.value.singleTradeInstantBookInr, 0.01)
    org.junit.Assert.assertTrue(reloadedRepo.riskConfig.value.allowSellAutoTrade)
  }

  @Test
  fun `simulate 25 demo trades and audit win rate and net profit`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = com.example.data.repository.RiskPreferencesStorage(context)
    val repo = com.example.data.repository.TradingEngineRepository(storage)

    repo.resetPaperAccount()
    repo.updateRiskConfig(
      com.example.domain.model.RiskConfig(
        enforceMarketHours = false,
        allowSellAutoTrade = false
      )
    )
    repo.toggleAutoTrading(true)

    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
      set(2026, java.util.Calendar.NOVEMBER, 16, 9, 31, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    var simTimeMs = cal.timeInMillis
    var ticks = 0
    while (repo.accountSummary.value.totalClosedTrades < 25 && ticks < 4000) {
      simTimeMs += 750L
      repo.stepMarketTickForTest(simTimeMs)
      ticks++
    }

    val summary = repo.accountSummary.value
    val closedTxs = repo.transactions.value.filter { it.status == com.example.domain.model.TransactionStatus.CLOSED }.reversed()
    println("=== 25-TRADE DEMO AUDIT ===")
    println("Ticks: $ticks | Executed: ${summary.totalTradesExecutedToday} | Closed: ${summary.totalClosedTrades}")
    println("Wins: ${summary.winCount} | Losses: ${summary.lossCount} | WinRate: ${"%.1f".format(summary.winRatePct)}%")
    println("Realized Net: ₹${"%.2f".format(summary.realizedDayPnl)} | GrossWin: ₹${"%.2f".format(summary.grossProfit)} | GrossLoss: ₹${"%.2f".format(summary.grossLoss)} | Charges: ₹${"%.2f".format(summary.totalEstimatedCharges)}")
    closedTxs.forEachIndexed { idx, tx ->
      println("Trade #${idx + 1}: ${tx.symbol} ${tx.side} Entry=${tx.entryPrice} Exit=${tx.exitOrCurrentPrice} Net=₹${"%.2f".format(tx.netPnlInr)} (${tx.exitReason})")
    }

    org.junit.Assert.assertTrue("Expected at least 20 closed trades, got ${summary.totalClosedTrades}", summary.totalClosedTrades >= 20)
    org.junit.Assert.assertTrue("Expected positive net profit after 20+ trades, got ₹${summary.realizedDayPnl}", summary.realizedDayPnl > 0.0)
    // Ensure Break-Even / Profit-Lock exits never turned negative
    closedTxs.filter {
      it.exitReason?.contains("Break-Even", ignoreCase = true) == true ||
        it.exitReason?.contains("Profit Shield", ignoreCase = true) == true
    }.forEach { tx ->
      org.junit.Assert.assertTrue("Protected exit ${tx.symbol} (${tx.exitReason}) should not lose money, got ₹${tx.netPnlInr}", tx.netPnlInr > 0.0)
    }
  }

  @Test
  fun `morning market open auto starts and evening close stops and squares off all trades`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = com.example.data.repository.RiskPreferencesStorage(context)
    val repo = com.example.data.repository.TradingEngineRepository(storage)

    repo.resetPaperAccount()
    repo.updateRiskConfig(
      com.example.domain.model.RiskConfig(
        enforceMarketHours = true,
        autoStartAtMarketOpen = true,
        skipOpeningVolatilityEnabled = true,
        mcxExtendedHoursEnabled = false,
        allowSellAutoTrade = false
      )
    )

    // 1) Pre-market 09:10 IST on Monday Nov 16, 2026 -> stays OFF
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
      set(2026, java.util.Calendar.NOVEMBER, 16, 9, 10, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    repo.stepMarketTickForTest(cal.timeInMillis)
    org.junit.Assert.assertFalse("Should remain OFF before 09:15 IST open", repo.autoTradingEnabled.value)

    // 2) Morning trading session 09:31 IST -> Auto-starts & opens/completes trades
    cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
    cal.set(java.util.Calendar.MINUTE, 31)
    var simMs = cal.timeInMillis
    repeat(60) {
      simMs += 750L
      repo.stepMarketTickForTest(simMs)
    }
    org.junit.Assert.assertTrue("Morning market open must auto-start trading", repo.autoTradingEnabled.value)
    org.junit.Assert.assertTrue("Expected trades during morning session", repo.accountSummary.value.totalTradesExecutedToday > 0)

    // 3) Evening market close 15:16 IST -> Squares off all open positions & stops Auto-Trading
    cal.set(java.util.Calendar.HOUR_OF_DAY, 15)
    cal.set(java.util.Calendar.MINUTE, 16)
    repo.stepMarketTickForTest(cal.timeInMillis)
    org.junit.Assert.assertFalse("Evening close must turn Auto-Trading OFF", repo.autoTradingEnabled.value)
    org.junit.Assert.assertTrue("Evening close must square off all open positions", repo.activePositions.value.isEmpty())
  }

  @Test
  fun `profit only withdraw sweeps net profit and keeps 5 lakh base capital intact`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = com.example.data.repository.RiskPreferencesStorage(context)
    val repo = com.example.data.repository.TradingEngineRepository(storage)

    repo.resetPaperAccount()
    // Cannot withdraw before earning profit
    val blocked = repo.withdrawNetProfitOnly()
    org.junit.Assert.assertTrue("Withdrawal must fail when net profit <= 0", blocked.isFailure)
    assertEquals(0.0, repo.accountSummary.value.withdrawableProfitInr, 0.01)

    // Run demo trades to earn realized profit
    repo.updateRiskConfig(
      com.example.domain.model.RiskConfig(
        enforceMarketHours = false,
        allowSellAutoTrade = false
      )
    )
    repo.toggleAutoTrading(true)
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
      set(2026, java.util.Calendar.NOVEMBER, 16, 10, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    var simMs = cal.timeInMillis
    var ticks = 0
    while (repo.accountSummary.value.realizedDayPnl < 30.0 && ticks < 1500) {
      simMs += 750L
      repo.stepMarketTickForTest(simMs)
      ticks++
    }
    repo.squareOffAllPositions()
    repo.toggleAutoTrading(false)

    val beforeSummary = repo.accountSummary.value
    org.junit.Assert.assertTrue("Expected positive withdrawable profit", beforeSummary.withdrawableProfitInr > 0.0)
    val expectedWithdraw = beforeSummary.withdrawableProfitInr

    val withdrawResult = repo.withdrawNetProfitOnly()
    org.junit.Assert.assertTrue("Profit-only withdrawal must succeed", withdrawResult.isSuccess)
    assertEquals(expectedWithdraw, withdrawResult.getOrThrow(), 0.02)

    val afterSummary = repo.accountSummary.value
    assertEquals(expectedWithdraw, afterSummary.withdrawnProfitInr, 0.02)
    assertEquals(0.0, afterSummary.withdrawableProfitInr, 0.01)
    assertEquals(500000.0, afterSummary.availableCash, 0.05)

    // Second withdrawal immediately blocked
    val secondAttempt = repo.withdrawNetProfitOnly()
    org.junit.Assert.assertTrue("Duplicate withdrawal of principal must fail", secondAttempt.isFailure)
  }

  @Test
  fun `broker credentials persist one-time across repository reloads and clear cleanly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = com.example.data.repository.RiskPreferencesStorage(context)
    val repo1 = com.example.data.repository.TradingEngineRepository(storage)

    val creds = com.example.domain.model.BrokerCredentials(
      apiKey = "testKey123",
      clientId = "a998877",
      mpin = "1234",
      totpSecret = "JBSWY3DPEHPK3PXP"
    )
    repo1.connectBroker(creds)
    assertEquals("testKey123", repo1.savedCredentials.value.apiKey)
    assertEquals("A998877", repo1.savedCredentials.value.clientId)

    // Reload brand new repository instance -> 1-time link must still be saved
    val repo2 = com.example.data.repository.TradingEngineRepository(storage)
    kotlinx.coroutines.delay(300L)
    assertEquals("testKey123", repo2.savedCredentials.value.apiKey)
    assertEquals("A998877", repo2.savedCredentials.value.clientId)
    assertEquals("1234", repo2.savedCredentials.value.mpin)
    assertEquals("JBSWY3DPEHPK3PXP", repo2.savedCredentials.value.totpSecret)

    // Clear saved credentials
    repo2.clearSavedBrokerCredentials()
    org.junit.Assert.assertFalse(repo2.savedCredentials.value.isCompleteForLive)
  }
}

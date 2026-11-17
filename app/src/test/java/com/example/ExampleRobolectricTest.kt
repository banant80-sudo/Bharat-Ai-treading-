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
}

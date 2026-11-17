package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ActivePosition
import com.example.domain.model.BasketExitScope
import com.example.domain.model.BasketHighWaterState
import com.example.domain.model.RiskConfig
import com.example.domain.model.StopLossMode
import com.example.domain.model.TargetProfitMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.riskDataStore: DataStore<Preferences> by preferencesDataStore(name = "quantpulse_risk_prefs")

class RiskPreferencesStorage(private val context: Context) {

    private companion object {
        const val CURRENT_DEFAULTS_VERSION = 11
    }

    private object Keys {
        val DEFAULTS_VERSION = intPreferencesKey("engine_defaults_version")
        val SMALL_AMOUNT_SPLIT = booleanPreferencesKey("small_amount_split")
        val SMALL_AMOUNT_PER_TRADE = doublePreferencesKey("small_amount_per_trade")
        val MAX_CAPITAL_PER_TRADE = doublePreferencesKey("max_capital_per_trade")
        val BASKET_EXIT_SCOPE = stringPreferencesKey("basket_exit_scope")
        val TARGET_PROFIT_MODE = stringPreferencesKey("target_profit_mode")
        val TARGET_PROFIT_INR = doublePreferencesKey("target_profit_inr")
        val TARGET_PROFIT_PCT = doublePreferencesKey("target_profit_pct")
        val SINGLE_TRADE_INSTANT_BOOK_INR = doublePreferencesKey("single_trade_instant_book_inr")
        val BASKET_INSTANT_BOOK_INR = doublePreferencesKey("basket_instant_book_inr")
        val STOP_LOSS_MODE = stringPreferencesKey("stop_loss_mode")
        val STOP_LOSS_PCT = doublePreferencesKey("stop_loss_pct")
        val STOP_LOSS_INR = doublePreferencesKey("stop_loss_inr")
        val BASKET_MAX_LOSS_INR = doublePreferencesKey("basket_max_loss_inr")
        val HIGH_PROB_PROFIT_LOCK = booleanPreferencesKey("high_prob_profit_lock")
        val SINGLE_DIRECTION_ONLY = booleanPreferencesKey("single_direction_only")
        val ALLOW_SELL_AUTO_TRADE = booleanPreferencesKey("allow_sell_auto_trade")
        val MIN_AI_CONFIDENCE = intPreferencesKey("min_ai_confidence")
        val MAX_OPEN_POSITIONS = intPreferencesKey("max_open_positions")
        val REENTRY_COOLDOWN_SEC = intPreferencesKey("reentry_cooldown_sec")
        val RSI_BUY_THRESHOLD = doublePreferencesKey("rsi_buy_threshold")
        val RSI_SELL_THRESHOLD = doublePreferencesKey("rsi_sell_threshold")
        val ENFORCE_MARKET_HOURS = booleanPreferencesKey("enforce_market_hours")
        val SKIP_OPENING_VOLATILITY = booleanPreferencesKey("skip_opening_volatility")
        val MCX_EXTENDED_HOURS = booleanPreferencesKey("mcx_extended_hours")
        val AUTO_START_AT_OPEN = booleanPreferencesKey("auto_start_at_open")
        val DAILY_MAX_LOSS_INR = doublePreferencesKey("daily_max_loss_inr")
        val IS_PAPER_MODE = booleanPreferencesKey("is_paper_mode")
    }

    val riskConfigFlow: Flow<RiskConfig> = context.riskDataStore.data.map { prefs ->
        val version = prefs[Keys.DEFAULTS_VERSION] ?: 1
        val isV2Migrated = version >= 2
        val isV3Migrated = version >= 3
        val isV4Migrated = version >= 4
        val isV5Migrated = version >= 5
        val isV6Migrated = version >= 6
        val isV7Migrated = version >= 7
        val isV8Migrated = version >= 8
        val isV9Migrated = version >= 9
        val isV10Migrated = version >= CURRENT_DEFAULTS_VERSION

        val basketScope = prefs[Keys.BASKET_EXIT_SCOPE]?.let {
            runCatching { BasketExitScope.valueOf(it) }.getOrNull()
        } ?: BasketExitScope.COMBINED_BASKET
        val tpMode = if (isV3Migrated) {
            prefs[Keys.TARGET_PROFIT_MODE]?.let {
                runCatching { TargetProfitMode.valueOf(it) }.getOrNull()
            } ?: TargetProfitMode.PERCENTAGE
        } else {
            TargetProfitMode.PERCENTAGE
        }
        val slMode = prefs[Keys.STOP_LOSS_MODE]?.let {
            runCatching { StopLossMode.valueOf(it) }.getOrNull()
        } ?: StopLossMode.PERCENTAGE

        RiskConfig(
            smallAmountAutoSplitEnabled = prefs[Keys.SMALL_AMOUNT_SPLIT] ?: true,
            smallAmountPerTrade = if (isV8Migrated) {
                (prefs[Keys.SMALL_AMOUNT_PER_TRADE] ?: 5000.0).coerceIn(50.0, 50000.0)
            } else {
                5000.0
            },
            maxCapitalPerTrade = (prefs[Keys.MAX_CAPITAL_PER_TRADE] ?: 50000.0).coerceIn(5000.0, 500000.0),
            basketExitScope = basketScope,
            targetProfitMode = tpMode,
            targetProfitInr = (prefs[Keys.TARGET_PROFIT_INR] ?: 300.0).coerceIn(30.0, 1000.0),
            targetProfitPct = if (isV3Migrated) {
                (prefs[Keys.TARGET_PROFIT_PCT] ?: 0.80).coerceIn(0.25, 15.0)
            } else {
                0.80
            },
            singleTradeInstantBookInr = if (isV10Migrated) {
                (prefs[Keys.SINGLE_TRADE_INSTANT_BOOK_INR] ?: ActivePosition.ZERO_WAIT_SINGLE_TRADE_TARGET_INR)
                    .coerceIn(5.0, 200.0)
            } else {
                ActivePosition.ZERO_WAIT_SINGLE_TRADE_TARGET_INR
            },
            basketInstantBookInr = if (isV5Migrated) {
                (prefs[Keys.BASKET_INSTANT_BOOK_INR] ?: BasketHighWaterState.MASTER_DUAL_BASKET_TARGET_INR)
                    .coerceIn(15.0, 500.0)
            } else {
                BasketHighWaterState.MASTER_DUAL_BASKET_TARGET_INR
            },
            stopLossMode = slMode,
            stopLossPct = if (isV8Migrated) {
                (prefs[Keys.STOP_LOSS_PCT] ?: 0.75).coerceIn(0.20, 15.0)
            } else {
                0.75
            },
            stopLossInr = (prefs[Keys.STOP_LOSS_INR] ?: 150.0).coerceIn(25.0, 15000.0),
            basketMaxLossInr = if (isV8Migrated) {
                (prefs[Keys.BASKET_MAX_LOSS_INR] ?: BasketHighWaterState.BASKET_MAX_LOSS_STOP_INR).coerceIn(10.0, 500.0)
            } else {
                BasketHighWaterState.BASKET_MAX_LOSS_STOP_INR
            },
            highProbabilityProfitLockEnabled = prefs[Keys.HIGH_PROB_PROFIT_LOCK] ?: true,
            singleDirectionOnly = if (isV9Migrated) (prefs[Keys.SINGLE_DIRECTION_ONLY] ?: false) else false,
            allowSellAutoTrade = prefs[Keys.ALLOW_SELL_AUTO_TRADE] ?: false,
            minAiConfidence = if (isV7Migrated) {
                (prefs[Keys.MIN_AI_CONFIDENCE] ?: 99).coerceIn(75, 100)
            } else {
                99
            },
            maxOpenPositions = if (isV5Migrated) {
                (prefs[Keys.MAX_OPEN_POSITIONS] ?: 2).coerceIn(1, 30)
            } else {
                2
            },
            rsiBuyThreshold = (prefs[Keys.RSI_BUY_THRESHOLD] ?: 34.0).coerceIn(15.0, 45.0),
            rsiSellThreshold = (prefs[Keys.RSI_SELL_THRESHOLD] ?: 66.0).coerceIn(55.0, 85.0),
            reentryCooldownSec = if (isV8Migrated) {
                (prefs[Keys.REENTRY_COOLDOWN_SEC] ?: 20).coerceIn(0, 120)
            } else {
                20
            },
            enforceMarketHours = prefs[Keys.ENFORCE_MARKET_HOURS] ?: false,
            skipOpeningVolatilityEnabled = prefs[Keys.SKIP_OPENING_VOLATILITY] ?: true,
            mcxExtendedHoursEnabled = prefs[Keys.MCX_EXTENDED_HOURS] ?: true,
            autoStartAtMarketOpen = prefs[Keys.AUTO_START_AT_OPEN] ?: true,
            dailyMaxLossLimitInr = (prefs[Keys.DAILY_MAX_LOSS_INR] ?: 1500.0).coerceIn(300.0, 50000.0),
            isPaperMode = prefs[Keys.IS_PAPER_MODE] ?: true
        )
    }

    suspend fun saveRiskConfig(config: RiskConfig) {
        context.riskDataStore.edit { prefs ->
            prefs[Keys.DEFAULTS_VERSION] = CURRENT_DEFAULTS_VERSION
            prefs[Keys.SMALL_AMOUNT_SPLIT] = config.smallAmountAutoSplitEnabled
            prefs[Keys.SMALL_AMOUNT_PER_TRADE] = config.smallAmountPerTrade.coerceIn(50.0, 50000.0)
            prefs[Keys.MAX_CAPITAL_PER_TRADE] = config.maxCapitalPerTrade.coerceIn(5000.0, 500000.0)
            prefs[Keys.BASKET_EXIT_SCOPE] = config.basketExitScope.name
            prefs[Keys.TARGET_PROFIT_MODE] = config.targetProfitMode.name
            prefs[Keys.TARGET_PROFIT_INR] = config.targetProfitInr.coerceIn(30.0, 1000.0)
            prefs[Keys.TARGET_PROFIT_PCT] = config.targetProfitPct.coerceIn(0.25, 15.0)
            prefs[Keys.SINGLE_TRADE_INSTANT_BOOK_INR] = config.singleTradeInstantBookInr.coerceIn(5.0, 200.0)
            prefs[Keys.BASKET_INSTANT_BOOK_INR] = config.basketInstantBookInr.coerceIn(15.0, 500.0)
            prefs[Keys.STOP_LOSS_MODE] = config.stopLossMode.name
            prefs[Keys.STOP_LOSS_PCT] = config.stopLossPct.coerceIn(0.20, 15.0)
            prefs[Keys.STOP_LOSS_INR] = config.stopLossInr.coerceIn(25.0, 15000.0)
            prefs[Keys.BASKET_MAX_LOSS_INR] = config.basketMaxLossInr.coerceIn(10.0, 500.0)
            prefs[Keys.HIGH_PROB_PROFIT_LOCK] = config.highProbabilityProfitLockEnabled
            prefs[Keys.SINGLE_DIRECTION_ONLY] = config.singleDirectionOnly
            prefs[Keys.ALLOW_SELL_AUTO_TRADE] = config.allowSellAutoTrade
            prefs[Keys.MIN_AI_CONFIDENCE] = config.minAiConfidence.coerceIn(75, 100)
            prefs[Keys.MAX_OPEN_POSITIONS] = config.maxOpenPositions.coerceIn(1, 30)
            prefs[Keys.REENTRY_COOLDOWN_SEC] = config.reentryCooldownSec.coerceIn(0, 120)
            prefs[Keys.RSI_BUY_THRESHOLD] = config.rsiBuyThreshold.coerceIn(15.0, 45.0)
            prefs[Keys.RSI_SELL_THRESHOLD] = config.rsiSellThreshold.coerceIn(55.0, 85.0)
            prefs[Keys.ENFORCE_MARKET_HOURS] = config.enforceMarketHours
            prefs[Keys.SKIP_OPENING_VOLATILITY] = config.skipOpeningVolatilityEnabled
            prefs[Keys.MCX_EXTENDED_HOURS] = config.mcxExtendedHoursEnabled
            prefs[Keys.AUTO_START_AT_OPEN] = config.autoStartAtMarketOpen
            prefs[Keys.DAILY_MAX_LOSS_INR] = config.dailyMaxLossLimitInr.coerceIn(300.0, 50000.0)
            prefs[Keys.IS_PAPER_MODE] = config.isPaperMode
        }
    }
}

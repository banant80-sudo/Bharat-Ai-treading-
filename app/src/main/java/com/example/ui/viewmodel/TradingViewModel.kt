package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiScreenerUiState
import com.example.data.repository.RiskPreferencesStorage
import com.example.data.repository.TradingEngineRepository
import com.example.domain.model.AccountSummary
import com.example.domain.model.ActivePosition
import com.example.domain.model.AiTrendSignal
import com.example.domain.model.BasketHighWaterState
import com.example.domain.model.BrokerConnectionState
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.Exchange
import com.example.domain.model.GeminiPerformanceDiagnostic
import com.example.domain.model.MarketInstrument
import com.example.domain.model.RiskConfig
import com.example.domain.model.TerminalTab
import com.example.domain.model.TradeLogEntry
import com.example.domain.model.TradeSide
import com.example.domain.model.TradeTransaction
import com.example.util.IstMarketScheduler
import com.example.util.MarketWindowStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SignalFilter(val label: String) {
    ALL("All Signals"),
    STRONG_BUY("Strong Buy"),
    STRONG_SELL("Strong Sell")
}

data class TradingUiState(
    val selectedTab: TerminalTab = TerminalTab.SCANNER,
    val selectedExchange: Exchange? = null,
    val signalFilter: SignalFilter = SignalFilter.ALL,
    val searchQuery: String = "",
    val isBrokerSheetOpen: Boolean = false,
    val credentials: BrokerCredentials = BrokerCredentials(),
    val connectionState: BrokerConnectionState = BrokerConnectionState.Disconnected,
    val marketWindowStatus: MarketWindowStatus = IstMarketScheduler.evaluate(RiskConfig()),
    val accountSummary: AccountSummary = AccountSummary(),
    val instruments: List<MarketInstrument> = emptyList(),
    val activePositions: List<ActivePosition> = emptyList(),
    val transactions: List<TradeTransaction> = emptyList(),
    val tradeLogs: List<TradeLogEntry> = emptyList(),
    val riskConfig: RiskConfig = RiskConfig(),
    val autoTradingEnabled: Boolean = false,
    val buyBasketCooldownRemainingSec: Int = 0,
    val sellBasketCooldownRemainingSec: Int = 0,
    val buyBasketHighWater: BasketHighWaterState = BasketHighWaterState(),
    val sellBasketHighWater: BasketHighWaterState = BasketHighWaterState(),
    val isCircuitBreakerTriggered: Boolean = false,
    val screenerUiState: GeminiScreenerUiState = GeminiScreenerUiState.Idle,
    val performanceDiagnostic: GeminiPerformanceDiagnostic = GeminiPerformanceDiagnostic()
)

class TradingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TradingEngineRepository(
        preferencesStorage = RiskPreferencesStorage(application.applicationContext)
    )

    private val selectedTab = MutableStateFlow(TerminalTab.SCANNER)
    private val selectedExchange = MutableStateFlow<Exchange?>(null)
    private val signalFilter = MutableStateFlow(SignalFilter.ALL)
    private val searchQuery = MutableStateFlow("")
    private val isBrokerSheetOpen = MutableStateFlow(false)
    private val credentials = MutableStateFlow(BrokerCredentials())

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private data class UiNavState(
        val tab: TerminalTab,
        val exchange: Exchange?,
        val signalFilter: SignalFilter,
        val query: String,
        val sheetOpen: Boolean,
        val creds: BrokerCredentials
    )

    private val navFlow = combine(
        selectedTab,
        selectedExchange,
        signalFilter,
        searchQuery,
        isBrokerSheetOpen
    ) { tab, exchange, sigFilter, query, sheetOpen ->
        UiNavState(tab, exchange, sigFilter, query, sheetOpen, credentials.value)
    }.combine(credentials) { nav, creds ->
        nav.copy(creds = creds)
    }

    private data class RepoState(
        val conn: BrokerConnectionState,
        val window: MarketWindowStatus,
        val summary: AccountSummary,
        val instruments: List<MarketInstrument>,
        val positions: List<ActivePosition>,
        val transactions: List<TradeTransaction>,
        val logs: List<TradeLogEntry>,
        val buyCooldownSec: Int,
        val sellCooldownSec: Int,
        val buyHighWater: BasketHighWaterState = BasketHighWaterState(),
        val sellHighWater: BasketHighWaterState = BasketHighWaterState()
    )

    private val repoFlow = combine(
        repository.connectionState,
        repository.marketWindowStatus,
        repository.accountSummary,
        repository.instruments,
        repository.activePositions
    ) { conn, window, summary, instruments, positions ->
        RepoState(conn, window, summary, instruments, positions, emptyList(), emptyList(), 0, 0)
    }.combine(repository.transactions) { partial, txs ->
        partial.copy(transactions = txs)
    }.combine(repository.tradeLogs) { partial, logs ->
        partial.copy(logs = logs)
    }.combine(repository.buyBasketCooldownRemainingSec) { partial, buyCooldown ->
        partial.copy(buyCooldownSec = buyCooldown)
    }.combine(repository.sellBasketCooldownRemainingSec) { partial, sellCooldown ->
        partial.copy(sellCooldownSec = sellCooldown)
    }.combine(repository.buyBasketHighWater) { partial, buyHw ->
        partial.copy(buyHighWater = buyHw)
    }.combine(repository.sellBasketHighWater) { partial, sellHw ->
        partial.copy(sellHighWater = sellHw)
    }

    val uiState: StateFlow<TradingUiState> = combine(
        navFlow,
        repoFlow,
        repository.riskConfig,
        repository.autoTradingEnabled,
        repository.circuitBreakerTriggered,
        repository.screenerUiState,
        repository.performanceDiagnostic
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val nav = flows[0] as UiNavState
        val repo = flows[1] as RepoState
        val risk = flows[2] as RiskConfig
        val autoEnabled = flows[3] as Boolean
        val circuitTriggered = flows[4] as Boolean
        val screenerState = flows[5] as GeminiScreenerUiState
        val perfDiag = flows[6] as GeminiPerformanceDiagnostic

        val filteredInstruments = repo.instruments.filter { inst ->
            val matchExchange = nav.exchange == null || inst.exchange == nav.exchange
            val matchSignal = when (nav.signalFilter) {
                SignalFilter.ALL -> true
                SignalFilter.STRONG_BUY -> inst.aiSignal == AiTrendSignal.STRONG_BUY || inst.aiSignal == AiTrendSignal.BUY
                SignalFilter.STRONG_SELL -> inst.aiSignal == AiTrendSignal.STRONG_SELL || inst.aiSignal == AiTrendSignal.SELL
            }
            val q = nav.query.trim()
            val matchQuery = q.isEmpty() ||
                inst.symbol.contains(q, ignoreCase = true) ||
                inst.displayName.contains(q, ignoreCase = true)
            matchExchange && matchSignal && matchQuery
        }

        TradingUiState(
            selectedTab = nav.tab,
            selectedExchange = nav.exchange,
            signalFilter = nav.signalFilter,
            searchQuery = nav.query,
            isBrokerSheetOpen = nav.sheetOpen,
            credentials = nav.creds,
            connectionState = repo.conn,
            marketWindowStatus = repo.window,
            accountSummary = repo.summary,
            instruments = filteredInstruments,
            activePositions = repo.positions,
            transactions = repo.transactions,
            tradeLogs = repo.logs,
            riskConfig = risk,
            autoTradingEnabled = autoEnabled,
            buyBasketCooldownRemainingSec = repo.buyCooldownSec,
            sellBasketCooldownRemainingSec = repo.sellCooldownSec,
            buyBasketHighWater = repo.buyHighWater,
            sellBasketHighWater = repo.sellHighWater,
            isCircuitBreakerTriggered = circuitTriggered,
            screenerUiState = screenerState,
            performanceDiagnostic = perfDiag
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TradingUiState()
    )

    fun selectTab(tab: TerminalTab) {
        selectedTab.value = tab
    }

    fun selectExchange(exchange: Exchange?) {
        selectedExchange.value = exchange
    }

    fun selectSignalFilter(filter: SignalFilter) {
        signalFilter.value = filter
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setBrokerSheetOpen(open: Boolean) {
        isBrokerSheetOpen.value = open
    }

    fun updateCredentials(updated: BrokerCredentials) {
        credentials.value = updated
    }

    fun toggleAutoTrading(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAutoTrading(enabled).onFailure {
                _events.emit(it.message ?: "Unable to toggle Auto-Trading")
            }
        }
    }

    fun toggleAllowSellAutoTrade(enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.riskConfig
            val updated = current.copy(allowSellAutoTrade = enabled)
            repository.updateRiskConfig(updated)
            val msg = if (enabled) {
                "Allow SELL Auto-Trading: ON (Dual-Basket BUY + SELL active)"
            } else {
                "Allow SELL Auto-Trading: OFF (HOLD) — 100% BUY-Only Scalper Active"
            }
            _events.emit(msg)
        }
    }

    fun placeManualOrder(instrument: MarketInstrument, side: TradeSide) {
        viewModelScope.launch {
            repository.placeManualTrade(instrument, side).fold(
                onSuccess = {
                    _events.emit("${side.label} ${instrument.symbol} micro-order executed")
                },
                onFailure = {
                    _events.emit(it.message ?: "Order failed")
                }
            )
        }
    }

    fun squareOffPosition(positionId: String) {
        viewModelScope.launch {
            repository.squareOffPosition(positionId)
            _events.emit("Position squared off")
        }
    }

    fun squareOffAll() {
        viewModelScope.launch {
            repository.squareOffAllPositions()
            _events.emit("All open positions squared off")
        }
    }

    fun bookBasketProfitNow(side: TradeSide) {
        viewModelScope.launch {
            val net = repository.bookBasketProfitNow(side)
            val label = if (side == TradeSide.LONG) "BUY" else "SELL"
            _events.emit("$label basket booked: ${com.example.util.Formatters.formatInr(net, showSign = true)} net")
        }
    }

    fun bookAllCombinedProfitNow() {
        viewModelScope.launch {
            val net = repository.bookAllCombinedProfitNow()
            _events.emit("Master Dual-Basket booked: ${com.example.util.Formatters.formatInr(net, showSign = true)} net")
        }
    }

    fun updateRiskConfig(config: RiskConfig) {
        viewModelScope.launch {
            repository.updateRiskConfig(config)
        }
    }

    fun resetPaperAccount() {
        viewModelScope.launch {
            repository.resetPaperAccount()
            _events.emit("Paper balance reset to ₹5,00,000")
        }
    }

    fun connectBroker() {
        viewModelScope.launch {
            val result = repository.connectBroker(credentials.value)
            result.fold(
                onSuccess = {
                    _events.emit("Broker session connected")
                    isBrokerSheetOpen.value = false
                },
                onFailure = {
                    _events.emit(it.message ?: "SmartAPI authentication error")
                }
            )
        }
    }

    fun disconnectBroker() {
        viewModelScope.launch {
            repository.disconnectBroker()
            _events.emit("Broker session disconnected")
        }
    }

    fun runGeminiDeepScan() {
        viewModelScope.launch {
            repository.runGeminiDeepScan().fold(
                onSuccess = { count ->
                    _events.emit("Instant 3-factor AI scan evaluated $count instruments")
                },
                onFailure = { error ->
                    _events.emit("Scan error: ${error.localizedMessage ?: "Request failed"}")
                }
            )
        }
    }

    fun runAiPerformanceOptimization() {
        viewModelScope.launch {
            repository.runAiPerformanceOptimization().fold(
                onSuccess = { diag ->
                    _events.emit("Gemini AI 4H: ${diag.headline}")
                },
                onFailure = { error ->
                    _events.emit("AI Optimizer: ${error.localizedMessage ?: "Completed local self-tuning"}")
                }
            )
        }
    }

    fun toggleApiQuotaLimit(exhausted: Boolean) {
        viewModelScope.launch {
            repository.setApiQuotaExhausted(exhausted)
            val msg = if (exhausted) {
                "API Access Limit Exhausted: Profit Auto-Booking Paused"
            } else {
                "API Access Restored: +₹25 Profit Auto-Booking Resumed"
            }
            _events.emit(msg)
        }
    }
}

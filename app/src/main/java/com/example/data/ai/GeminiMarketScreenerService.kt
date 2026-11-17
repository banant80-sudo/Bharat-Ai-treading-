package com.example.data.ai

import android.util.Log
import com.example.domain.model.Exchange
import com.example.domain.model.MarketInstrument
import com.example.util.AngelOneChargeCalculator
import com.example.util.Formatters
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "GeminiScreenerService"
private const val GEMINI_SCAN_TIMEOUT_MS = 45_000L
private const val MAX_CLOUD_SCAN_CANDIDATES = 6

data class CloudAiStockAssessment(
    val symbol: String,
    val score: Int,
    val trendMomentumPass: Boolean,
    val volumeLiquidityPass: Boolean,
    val feeClearancePass: Boolean,
    val verdict: String,
    val reason: String
)

sealed interface GeminiScreenerUiState {
    data object Idle : GeminiScreenerUiState
    data object Loading : GeminiScreenerUiState
    data class Success(
        val marketSummary: String,
        val topPickSymbols: List<String>,
        val assessedCount: Int,
        val lastScannedAt: String
    ) : GeminiScreenerUiState

    data class Error(val message: String) : GeminiScreenerUiState
}

class GeminiMarketScreenerService {

    private val scanMutex = Mutex()

    private val screenerSchema = Schema.obj(
        properties = mapOf(
            "marketSummary" to Schema.string(
                description = "Brief 1-sentence summary of market regime."
            ),
            "assessments" to Schema.array(
                description = "Pre-trade assessment for each shortlisted instrument.",
                items = Schema.obj(
                    properties = mapOf(
                        "symbol" to Schema.string(description = "Ticker symbol exactly as provided."),
                        "score" to Schema.integer(description = "Conviction score from 0 to 100."),
                        "trendMomentumPass" to Schema.boolean(description = "True if EMA slope, VWAP flow, and RSI align."),
                        "volumeLiquidityPass" to Schema.boolean(description = "True if VolSurge >= 1.10x with clean liquidity."),
                        "feeClearancePass" to Schema.boolean(description = "True if intraday range comfortably beats round-trip fees."),
                        "verdict" to Schema.enumeration(
                            values = listOf("PASS", "SKIP"),
                            description = "PASS only if score >= 92 and all 3 checks pass; else SKIP."
                        ),
                        "reason" to Schema.string(
                            description = "Concise 3-6 word verdict reason."
                        )
                    )
                )
            )
        )
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = screenerSchema
            temperature = 0.1f
            maxOutputTokens = 1024
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = config
        )
    }

    private val _uiState = MutableStateFlow<GeminiScreenerUiState>(GeminiScreenerUiState.Idle)
    val uiState: StateFlow<GeminiScreenerUiState> = _uiState.asStateFlow()

    suspend fun analyzeMarketSnapshot(
        instruments: List<MarketInstrument>,
        allocationInr: Double
    ): Result<Map<String, CloudAiStockAssessment>> {
        if (instruments.isEmpty()) return Result.success(emptyMap())

        return scanMutex.withLock {
            _uiState.value = GeminiScreenerUiState.Loading
            try {
                val shortlisted = shortlistCandidates(
                    instruments = instruments,
                    allocationInr = allocationInr,
                    maxCount = MAX_CLOUD_SCAN_CANDIDATES
                )
                val prompt = buildScreenerPrompt(shortlisted, allocationInr)
                val response = withContext(Dispatchers.IO) {
                    withTimeout(GEMINI_SCAN_TIMEOUT_MS) {
                        model.generateContent(prompt)
                    }
                }
                val jsonString = response.text
                    ?: throw IllegalStateException("Gemini AI returned an empty response.")

                val root = JSONObject(jsonString)
                val marketSummary = root.optString(
                    "marketSummary",
                    "Pre-trade multi-factor scan completed."
                )
                val arr = root.optJSONArray("assessments")
                    ?: throw IllegalStateException("Gemini AI response missing assessments array.")

                val parsedMap = mutableMapOf<String, CloudAiStockAssessment>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val sym = obj.optString("symbol").trim().uppercase(Locale.US)
                    if (sym.isEmpty()) continue
                    val score = obj.optInt("score", 70).coerceIn(0, 100)
                    val trendPass = obj.optBoolean("trendMomentumPass", false)
                    val volPass = obj.optBoolean("volumeLiquidityPass", false)
                    val feePass = obj.optBoolean("feeClearancePass", false)
                    val rawVerdict = obj.optString("verdict", "SKIP").uppercase(Locale.US)
                    val finalVerdict = if (rawVerdict == "PASS" && score >= 92 && trendPass && volPass && feePass) {
                        "PASS"
                    } else {
                        "SKIP"
                    }
                    val reason = obj.optString(
                        "reason",
                        if (finalVerdict == "PASS") "All 3 Gemini AI Checks Passed" else "AI Screener Blocked: Weak Momentum / High Chop"
                    )
                    parsedMap[sym] = CloudAiStockAssessment(
                        symbol = sym,
                        score = score,
                        trendMomentumPass = trendPass,
                        volumeLiquidityPass = volPass,
                        feeClearancePass = feePass,
                        verdict = finalVerdict,
                        reason = reason
                    )
                }

                val topPicks = parsedMap.values
                    .filter { it.verdict == "PASS" }
                    .sortedByDescending { it.score }
                    .take(2)
                    .map { "${it.symbol} (${it.score}%)" }

                _uiState.value = GeminiScreenerUiState.Success(
                    marketSummary = marketSummary,
                    topPickSymbols = topPicks,
                    assessedCount = parsedMap.size,
                    lastScannedAt = Formatters.formatTime()
                )
                Result.success(parsedMap)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini AI screener timed out after ${GEMINI_SCAN_TIMEOUT_MS}ms", e)
                val msg = "Gemini AI scan timed out after 45s. Tap Deep Scan to retry."
                _uiState.value = GeminiScreenerUiState.Error(msg)
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini AI screener failed", e)
                val msg = e.localizedMessage ?: "Gemini AI screener request failed."
                _uiState.value = GeminiScreenerUiState.Error(msg)
                Result.failure(e)
            }
        }
    }

    private fun buildScreenerPrompt(
        instruments: List<MarketInstrument>,
        allocationInr: Double
    ): String {
        val rows = instruments.joinToString("\n") { inst ->
            val charges = AngelOneChargeCalculator.estimateRoundTripCharges(
                allocationInr.coerceAtLeast(50.0),
                inst.exchange
            ).totalCharges
            val chargePct = if (allocationInr > 0.0) (charges / allocationInr) * 100.0 else 0.3
            String.format(
                Locale.US,
                "%s(%s): LTP=%.2f VWAP=%.2f(%s) EMA=%.2f/%.2f(%s) RSI=%.1f Vol=%.2fx Rng=%.2f%% Fee=%.2f%% Sig=%s",
                inst.symbol,
                inst.exchange.label,
                inst.ltp,
                inst.vwap,
                if (inst.isPriceAboveVwap) "ABV" else "BLW",
                inst.ema9,
                inst.ema21,
                if (inst.isEmaBullish) "BULL" else "BEAR",
                inst.rsi14,
                inst.volumeSurgeRatio,
                inst.intradayRangePct,
                chargePct,
                inst.aiSignal.name
            )
        }
        return """
            Gemini AI 4H Pre-Trade Screener for Bharat AI Trading. Capital/trade: ₹${allocationInr.toInt()}.
            Score each ticker 0-100:
            1) trendMomentumPass: 9/21 EMA + VWAP flow + RSI 55-68 (BUY) or 32-45 (SELL).
            2) volumeLiquidityPass: Vol >= 1.10x, low chop.
            3) feeClearancePass: Rng >= 2.4x Fee and >= 0.28%.
            PASS only if score >= 92 and all 3 true; else SKIP.

            Candidates (${instruments.size}):
            $rows
        """.trimIndent()
    }

    companion object {
        fun shortlistCandidates(
            instruments: List<MarketInstrument>,
            allocationInr: Double,
            maxCount: Int = MAX_CLOUD_SCAN_CANDIDATES
        ): List<MarketInstrument> {
            if (instruments.size <= maxCount) return instruments
            val affordable = instruments.filter { inst ->
                if (inst.exchange == Exchange.CRYPTO) {
                    allocationInr >= 50.0
                } else {
                    allocationInr >= inst.ltp
                }
            }
            val pool = affordable.ifEmpty { instruments }
            return pool.sortedWith(
                compareByDescending<MarketInstrument> { it.allScreenerChecksPassed }
                    .thenByDescending { it.strategyPassed }
                    .thenByDescending { it.aiScreenerScore }
                    .thenByDescending { it.volumeSurgeRatio }
            ).take(maxCount)
        }

        fun isQuotaOrAccessLimitError(message: String?): Boolean {
            if (message.isNullOrBlank()) return false
            val lower = message.lowercase(Locale.US)
            return lower.contains("429") ||
                lower.contains("quota") ||
                lower.contains("resource_exhausted") ||
                lower.contains("rate limit") ||
                lower.contains("access limit") ||
                lower.contains("api key") ||
                lower.contains("403")
        }
    }

    private val optimizerSchema = Schema.obj(
        properties = mapOf(
            "whyLossOrSlowGrowthDiagnosis" to Schema.string(
                description = "1-2 sentences explaining how much was invested, why losses or slow growth happened (e.g., fee drag, whipsaw stop-loss, or choppy entries)."
            ),
            "profitImprovementPlan" to Schema.string(
                description = "1-2 sentences explaining how Gemini AI 4 Hydrogen improves win rate toward 99% success rate."
            ),
            "shouldModifyParameters" to Schema.boolean(
                description = "True if growth is negative or win rate < 85% and parameters should be auto-modified; False if growing well (continue)."
            ),
            "recommendedAllocationInr" to Schema.double(
                description = "Recommended per-trade capital in INR (5000, 8000, or 10000)."
            ),
            "recommendedStopLossPct" to Schema.double(
                description = "Recommended per-trade stop-loss % (0.75, 0.90, or 1.00)."
            ),
            "recommendedCooldownSec" to Schema.integer(
                description = "Recommended post-exit cooldown in seconds (20, 25, or 30)."
            )
        )
    )

    private val optimizerModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = optimizerSchema
            temperature = 0.1f
            maxOutputTokens = 512
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    data class CloudOptimizationAdvice(
        val whyLossOrSlowGrowthDiagnosis: String,
        val profitImprovementPlan: String,
        val shouldModifyParameters: Boolean,
        val recommendedAllocationInr: Double,
        val recommendedStopLossPct: Double,
        val recommendedCooldownSec: Int
    )

    suspend fun analyzePerformanceWithCloudAi(
        totalInvestedInr: Double,
        realizedNetPnlInr: Double,
        unrealizedNetPnlInr: Double,
        totalChargesInr: Double,
        winCount: Int,
        lossCount: Int,
        currentAllocationInr: Double,
        currentStopLossPct: Double,
        currentCooldownSec: Int
    ): Result<CloudOptimizationAdvice> {
        return try {
            val totalTrades = winCount + lossCount
            val winRate = if (totalTrades > 0) (winCount * 100.0) / totalTrades else 99.0
            val prompt = """
                Gemini AI 4 Hydrogen Performance Analytics Coach for Bharat AI Trading.
                Target: 99% Success Rate & Consistent Net Profit Growth.
                Today's Telemetry:
                - Total Invested Today: ₹${totalInvestedInr.toInt()}
                - Realized Net P&L: ₹${String.format(Locale.US, "%.2f", realizedNetPnlInr)}
                - Open Unrealized Net P&L: ₹${String.format(Locale.US, "%.2f", unrealizedNetPnlInr)}
                - Total Brokerage & Taxes: ₹${String.format(Locale.US, "%.2f", totalChargesInr)}
                - Win/Loss: ${winCount}W / ${lossCount}L (${String.format(Locale.US, "%.1f", winRate)}%)
                - Current Params: Allocation ₹${currentAllocationInr.toInt()}, SL ${currentStopLossPct}%, Cooldown ${currentCooldownSec}s.
                If growth is positive and winRate >= 85%, set shouldModifyParameters=false and keep current params.
                If growth is negative/stagnant or losses occurred, set shouldModifyParameters=true and tune params to reduce fee drag and stop-loss whipsaws.
            """.trimIndent()

            val response = withContext(Dispatchers.IO) {
                withTimeout(30_000L) {
                    optimizerModel.generateContent(prompt)
                }
            }
            val text = response.text ?: throw IllegalStateException("Empty AI optimizer response")
            val json = JSONObject(text)
            Result.success(
                CloudOptimizationAdvice(
                    whyLossOrSlowGrowthDiagnosis = json.optString("whyLossOrSlowGrowthDiagnosis", ""),
                    profitImprovementPlan = json.optString("profitImprovementPlan", ""),
                    shouldModifyParameters = json.optBoolean("shouldModifyParameters", false),
                    recommendedAllocationInr = json.optDouble("recommendedAllocationInr", currentAllocationInr).coerceIn(5000.0, 25000.0),
                    recommendedStopLossPct = json.optDouble("recommendedStopLossPct", currentStopLossPct).coerceIn(0.50, 1.50),
                    recommendedCooldownSec = json.optInt("recommendedCooldownSec", currentCooldownSec).coerceIn(15, 60)
                )
            )
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                Result.failure(e)
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI performance optimizer error", e)
            Result.failure(e)
        }
    }
}

package com.example.data.smartapi

import com.example.domain.model.ActivePosition
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.Exchange
import com.example.domain.model.TradeSide
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class SmartApiSession(
    val clientId: String,
    val apiKey: String,
    val jwtToken: String,
    val refreshToken: String,
    val feedToken: String,
    val isPaperSession: Boolean
)

data class RmsLimits(
    val availableCash: Double,
    val utilisedMargin: Double,
    val netAvailable: Double
)

/**
 * Modular Angel One SmartAPI client wrapper.
 * Supports clean Paper Trading (Demo Mode) execution as well as live SmartAPI REST calls
 * for authentication, RMS margin limits, and order placement / square-off.
 */
class AngelOneBrokerClient {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val apiService: SmartApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://apiconnect.angelone.in/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SmartApiService::class.java)
    }

    /**
     * Authenticates with Angel One SmartAPI using API Key, Client ID, MPIN, and TOTP Secret.
     * In Paper Mode, validates inputs and establishes a local simulated JWT session without
     * risking live credentials or failing offline.
     */
    suspend fun authenticateSession(
        credentials: BrokerCredentials,
        isPaperMode: Boolean
    ): Result<SmartApiSession> = withContext(Dispatchers.IO) {
        val apiKey = credentials.apiKey.trim()
        val clientId = credentials.clientId.trim().uppercase()
        val mpin = credentials.mpin.trim()
        val totpSecret = credentials.totpSecret.trim()

        if (isPaperMode) {
            val resolvedClient = clientId.ifEmpty { "DEMO_ALGO_01" }
            val resolvedKey = apiKey.ifEmpty { "PAPER-SMARTAPI-SANDBOX" }
            val mockJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.paper_${resolvedClient.lowercase()}"
            return@withContext Result.success(
                SmartApiSession(
                    clientId = resolvedClient,
                    apiKey = resolvedKey,
                    jwtToken = mockJwt,
                    refreshToken = "paper_refresh_${System.currentTimeMillis()}",
                    feedToken = "paper_feed_ws",
                    isPaperSession = true
                )
            )
        }

        if (apiKey.isEmpty() || clientId.isEmpty() || mpin.isEmpty() || totpSecret.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("API Key, Client ID, MPIN, and TOTP Secret are all required for Live SmartAPI.")
            )
        }

        val generatedTotp = TOTPGenerator.generateTotp(totpSecret)
            ?: if (totpSecret.length == 6 && totpSecret.all { it.isDigit() }) totpSecret else null

        if (generatedTotp == null) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid Base32 TOTP Secret or 6-digit TOTP code.")
            )
        }

        return@withContext try {
            val response = apiService.loginByPassword(
                apiKey = apiKey,
                request = SmartApiLoginRequest(
                    clientCode = clientId,
                    password = mpin,
                    totp = generatedTotp
                )
            )
            val body = response.body()
            if (response.isSuccessful && body?.status == true && body.data?.jwtToken != null) {
                Result.success(
                    SmartApiSession(
                        clientId = clientId,
                        apiKey = apiKey,
                        jwtToken = body.data.jwtToken,
                        refreshToken = body.data.refreshToken.orEmpty(),
                        feedToken = body.data.feedToken.orEmpty(),
                        isPaperSession = false
                    )
                )
            } else {
                val errMsg = body?.message?.takeIf { it.isNotBlank() }
                    ?: "SmartAPI auth failed (HTTP ${response.code()})"
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Network error connecting to SmartAPI: ${e.localizedMessage ?: "Unknown"}"))
        }
    }

    /**
     * Fetches RMS margin limits from Angel One SmartAPI (/getRMS).
     */
    suspend fun fetchRmsMarginLimits(session: SmartApiSession): Result<RmsLimits> =
        withContext(Dispatchers.IO) {
            if (session.isPaperSession) {
                return@withContext Result.success(
                    RmsLimits(
                        availableCash = 500000.0,
                        utilisedMargin = 0.0,
                        netAvailable = 500000.0
                    )
                )
            }

            return@withContext try {
                val response = apiService.getRmsLimits(
                    bearerJwt = "Bearer ${session.jwtToken}",
                    apiKey = session.apiKey
                )
                val body = response.body()
                if (response.isSuccessful && body?.status == true && body.data != null) {
                    val avail = body.data.availableCash?.toDoubleOrNull() ?: 0.0
                    val used = body.data.utilisedDebits?.toDoubleOrNull() ?: 0.0
                    val net = body.data.net?.toDoubleOrNull() ?: avail
                    Result.success(RmsLimits(avail, used, net))
                } else {
                    Result.failure(IllegalStateException(body?.message ?: "Failed to fetch RMS limits"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Places an intraday MARKET entry order via Angel One SmartAPI (/placeOrder) for NSE/BSE/MCX.
     */
    suspend fun placeOrder(
        session: SmartApiSession?,
        tradingSymbol: String,
        symbolToken: String,
        exchange: Exchange,
        side: TradeSide,
        quantity: Double,
        isPaperMode: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isPaperMode || session == null || session.isPaperSession) {
            val demoOrderId = "PPR-${(System.currentTimeMillis() % 1000000L).toString().padStart(6, '0')}"
            return@withContext Result.success(demoOrderId)
        }

        return@withContext try {
            val response = apiService.placeOrder(
                bearerJwt = "Bearer ${session.jwtToken}",
                apiKey = session.apiKey,
                request = SmartApiOrderRequest(
                    variety = "NORMAL",
                    tradingSymbol = tradingSymbol,
                    symbolToken = symbolToken,
                    transactionType = side.smartApiTransactionType,
                    exchange = exchange.label,
                    orderType = "MARKET",
                    productType = "INTRADAY",
                    duration = "DAY",
                    quantity = quantity.toInt().coerceAtLeast(1).toString()
                )
            )
            val body = response.body()
            if (response.isSuccessful && body?.status == true && body.data?.orderId != null) {
                Result.success(body.data.orderId)
            } else {
                Result.failure(IllegalStateException(body?.message ?: "Order rejected by SmartAPI"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Squares off an open position by firing the opposite transaction type MARKET order.
     */
    suspend fun exitPosition(
        session: SmartApiSession?,
        position: ActivePosition,
        isPaperMode: Boolean
    ): Result<String> {
        val exitSide = if (position.side == TradeSide.LONG) TradeSide.SHORT else TradeSide.LONG
        return placeOrder(
            session = session,
            tradingSymbol = position.tradingSymbol,
            symbolToken = position.symbolToken,
            exchange = position.exchange,
            side = exitSide,
            quantity = position.quantity,
            isPaperMode = isPaperMode
        )
    }

    /**
     * Logs out and invalidates the Angel One SmartAPI JWT session.
     */
    suspend fun disconnectSession(session: SmartApiSession?): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (session == null || session.isPaperSession) {
                return@withContext Result.success(Unit)
            }
            return@withContext try {
                apiService.logout(
                    bearerJwt = "Bearer ${session.jwtToken}",
                    apiKey = session.apiKey,
                    request = SmartApiLogoutRequest(clientCode = session.clientId)
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

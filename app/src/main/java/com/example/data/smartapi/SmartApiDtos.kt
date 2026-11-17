package com.example.data.smartapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SmartApiLoginRequest(
    @Json(name = "clientcode") val clientCode: String,
    @Json(name = "password") val password: String,
    @Json(name = "totp") val totp: String
)

@JsonClass(generateAdapter = true)
data class SmartApiLoginResponse(
    @Json(name = "status") val status: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "errorcode") val errorCode: String? = null,
    @Json(name = "data") val data: SmartApiAuthData? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiAuthData(
    @Json(name = "jwtToken") val jwtToken: String? = null,
    @Json(name = "refreshToken") val refreshToken: String? = null,
    @Json(name = "feedToken") val feedToken: String? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiRmsResponse(
    @Json(name = "status") val status: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "data") val data: SmartApiRmsData? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiRmsData(
    @Json(name = "net") val net: String? = null,
    @Json(name = "availablecash") val availableCash: String? = null,
    @Json(name = "availableintradaypayin") val availableIntradayPayin: String? = null,
    @Json(name = "utiliseddebits") val utilisedDebits: String? = null,
    @Json(name = "m2mrealized") val m2mRealized: String? = null,
    @Json(name = "m2munrealized") val m2mUnrealized: String? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiOrderRequest(
    @Json(name = "variety") val variety: String = "NORMAL",
    @Json(name = "tradingsymbol") val tradingSymbol: String,
    @Json(name = "symboltoken") val symbolToken: String,
    @Json(name = "transactiontype") val transactionType: String,
    @Json(name = "exchange") val exchange: String,
    @Json(name = "ordertype") val orderType: String = "MARKET",
    @Json(name = "producttype") val productType: String = "INTRADAY",
    @Json(name = "duration") val duration: String = "DAY",
    @Json(name = "price") val price: String = "0",
    @Json(name = "squareoff") val squareOff: String = "0",
    @Json(name = "stoploss") val stopLoss: String = "0",
    @Json(name = "quantity") val quantity: String
)

@JsonClass(generateAdapter = true)
data class SmartApiOrderResponse(
    @Json(name = "status") val status: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "data") val data: SmartApiOrderData? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiOrderData(
    @Json(name = "script") val script: String? = null,
    @Json(name = "orderid") val orderId: String? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiLogoutRequest(
    @Json(name = "clientcode") val clientCode: String
)

@JsonClass(generateAdapter = true)
data class SmartApiGenericResponse(
    @Json(name = "status") val status: Boolean = false,
    @Json(name = "message") val message: String = ""
)

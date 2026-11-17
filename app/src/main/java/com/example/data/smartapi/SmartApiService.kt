package com.example.data.smartapi

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit contract for Angel One SmartAPI v1 REST endpoints.
 * Base URL: https://apiconnect.angelone.in/
 */
interface SmartApiService {

    @POST("rest/auth/angelbroking/user/v1/loginByPassword")
    suspend fun loginByPassword(
        @Header("X-PrivateKey") apiKey: String,
        @Header("X-UserType") userType: String = "USER",
        @Header("X-SourceID") sourceId: String = "WEB",
        @Header("X-ClientLocalIP") localIp: String = "127.0.0.1",
        @Header("X-ClientPublicIP") publicIp: String = "127.0.0.1",
        @Header("X-MACAddress") macAddress: String = "00:00:00:00:00:00",
        @Body request: SmartApiLoginRequest
    ): Response<SmartApiLoginResponse>

    @GET("rest/secure/angelbroking/user/v1/getRMS")
    suspend fun getRmsLimits(
        @Header("Authorization") bearerJwt: String,
        @Header("X-PrivateKey") apiKey: String,
        @Header("X-UserType") userType: String = "USER",
        @Header("X-SourceID") sourceId: String = "WEB",
        @Header("X-ClientLocalIP") localIp: String = "127.0.0.1",
        @Header("X-ClientPublicIP") publicIp: String = "127.0.0.1",
        @Header("X-MACAddress") macAddress: String = "00:00:00:00:00:00"
    ): Response<SmartApiRmsResponse>

    @POST("rest/secure/angelbroking/order/v1/placeOrder")
    suspend fun placeOrder(
        @Header("Authorization") bearerJwt: String,
        @Header("X-PrivateKey") apiKey: String,
        @Header("X-UserType") userType: String = "USER",
        @Header("X-SourceID") sourceId: String = "WEB",
        @Header("X-ClientLocalIP") localIp: String = "127.0.0.1",
        @Header("X-ClientPublicIP") publicIp: String = "127.0.0.1",
        @Header("X-MACAddress") macAddress: String = "00:00:00:00:00:00",
        @Body request: SmartApiOrderRequest
    ): Response<SmartApiOrderResponse>

    @POST("rest/secure/angelbroking/user/v1/logout")
    suspend fun logout(
        @Header("Authorization") bearerJwt: String,
        @Header("X-PrivateKey") apiKey: String,
        @Header("X-UserType") userType: String = "USER",
        @Header("X-SourceID") sourceId: String = "WEB",
        @Body request: SmartApiLogoutRequest
    ): Response<SmartApiGenericResponse>
}

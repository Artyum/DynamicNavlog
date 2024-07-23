package com.artyum.dynamicnavlog.openaip

import android.util.Log
import com.artyum.dynamicnavlog.BuildConfig
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface OpenAIPService {
    @GET("system/health")
    suspend fun checkSystemHealth(): Response<JsonObject>

    @GET("airports")
    suspend fun getAirports(
        @Header("x-openaip-client-id") idToken: String,
        @Query("pos") position: String,
        @Query("dist") distance: Int
    ): Response<JsonObject>
}

object OpenAIPClient {
    private const val baseUrl = "https://api.core.openaip.net/api/"
    private const val idToken = BuildConfig.ID_TOKEN

    private val retrofit: Retrofit = Retrofit.Builder().baseUrl(baseUrl).addConverterFactory(GsonConverterFactory.create()).build()
    private val openAIPService: OpenAIPService = retrofit.create(OpenAIPService::class.java)

    suspend fun checkHealth(): JsonObject? {
        val tag = "OpenAIPClient checkHealth"
        return try {
            val response = openAIPService.checkSystemHealth()
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.d(tag, "Error: ${response.errorBody()?.string() ?: "null errorBody"}")
                null
            }
        } catch (e: Exception) {
            Log.d(tag, "Exception: ${e.message}")
            null
        }
    }

    suspend fun getAirports(position: String, distance: Int): JsonObject? {
        val tag = "OpenAIPClient getAirports"
        return try {
            val response = openAIPService.getAirports(idToken, position, distance)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.d(tag, "Error: ${response.errorBody()?.string() ?: "null errorBody"}")
                null
            }
        } catch (e: Exception) {
            Log.d(tag, "Exception: ${e.message}")
            null
        }
    }
}
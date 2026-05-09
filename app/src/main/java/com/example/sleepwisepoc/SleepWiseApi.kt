package com.example.sleepwisepoc

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// ─── Request / response models (match server/main.py) ────────────────────────

data class StageTick(
    val t: String,
    val stage: String,
    val conf: Float,
    val stable: Boolean,
)

data class SessionUpload(
    val user_id: String,
    val window_start: String,
    val window_end: String,
    val started_at: String,
    val ended_at: String? = null,
    val fired_at: String? = null,
    val fired_reason: String? = null,
    val stages: List<StageTick> = emptyList(),
)

data class SessionRecord(
    val id: Long,
    val user_id: String,
    val window_start: String,
    val window_end: String,
    val started_at: String,
    val ended_at: String?,
    val fired_at: String?,
    val fired_reason: String?,
    val stages: List<StageTick>,
    val created_at: String,
)

data class WeeklyReport(
    val user_id: String,
    val sessions: List<SessionRecord>,
    val fired_count: Int,
    val favorable_count: Int,
    val fallback_count: Int,
    val avg_window_minutes: Float,
)

data class HealthCheckResponse(
    val service: String,
    val status: String,
    val sessions_stored: Int,
)

// ─── API surface ─────────────────────────────────────────────────────────────

interface SleepWiseApi {
    @GET("/")
    suspend fun health(): HealthCheckResponse

    @POST("/sessions")
    suspend fun uploadSession(@Body session: SessionUpload): SessionRecord

    @GET("/sessions/{user_id}")
    suspend fun listSessions(@Path("user_id") userId: String): List<SessionRecord>

    @GET("/sessions/{user_id}/weekly")
    suspend fun weeklyReport(@Path("user_id") userId: String): WeeklyReport

    @DELETE("/sessions/{user_id}")
    suspend fun clearUser(@Path("user_id") userId: String): Map<String, Any>
}

// ─── Client ──────────────────────────────────────────────────────────────────

object ApiClient {
    /**
     * 10.0.2.2 is the Android emulator's loopback to the host machine.
     * For physical devices, override via [withBaseUrl] using the host LAN IP.
     */
    const val EMULATOR_BASE_URL = "http://10.0.2.2:5000/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api: SleepWiseApi = withBaseUrl(EMULATOR_BASE_URL)

    fun withBaseUrl(baseUrl: String): SleepWiseApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SleepWiseApi::class.java)
}

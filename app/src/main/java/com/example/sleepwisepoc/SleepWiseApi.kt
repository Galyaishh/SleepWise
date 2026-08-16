package com.example.sleepwisepoc

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
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
    val stage: String,          // binary alarm stage (Deep/Light) — drives the wake decision
    val conf: Float,
    val stable: Boolean,
    // 4-stage report label (Wake/Light/Deep/REM) from the separate report model.
    // Display-only; null on older sessions / when the report model isn't loaded.
    @com.google.gson.annotations.SerializedName("report_stage")
    val reportStage: String? = null,
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

    @GET("/me")
    suspend fun whoami(): Map<String, String>

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
    const val EMULATOR_BASE_URL = "http://10.0.2.2:5000/"
    const val PROD_BASE_URL = "https://sleepwise-backend-8kvx.onrender.com/"

    /**
     * Pulls a fresh Firebase ID token before every request. Firebase caches
     * the token for ~1h; .getIdToken(false) returns cached if still valid,
     * otherwise refreshes — so this is cheap on the hot path.
     */
    private val authInterceptor = Interceptor { chain ->
        val user = FirebaseAuth.getInstance().currentUser
        val token: String? = user?.let {
            runCatching {
                runBlocking { it.getIdToken(false).await().token }
            }.onFailure { Log.w(TAG, "Firebase ID token fetch failed: ${it.message}") }
                .getOrNull()
        }
        val req = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(req)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        // Generous timeouts: Render free tier cold-starts can take 30-40s after idle.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: SleepWiseApi = withBaseUrl(PROD_BASE_URL)

    fun withBaseUrl(baseUrl: String): SleepWiseApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SleepWiseApi::class.java)

    private const val TAG = "ApiClient"
}

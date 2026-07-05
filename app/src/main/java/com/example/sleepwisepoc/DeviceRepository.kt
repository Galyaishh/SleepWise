package com.example.sleepwisepoc

import android.content.Context
import android.util.Log

/**
 * Manages the device-scoped backend token issued by POST /devices/register.
 * This token is separate from Firebase auth — it identifies the device in the
 * backend's device table and is used for all session API calls.
 *
 * In-memory cache is populated on first call to ensureRegistered(), either by
 * loading from SharedPreferences (subsequent launches) or by calling the backend
 * (first launch). The ApiClient interceptor reads cachedToken() synchronously.
 */
object DeviceRepository {

    @Volatile private var _token: String? = null
    @Volatile private var _userId: String? = null

    fun cachedToken(): String? = _token
    fun cachedUserId(): String? = _userId

    /**
     * Idempotent — safe to call on every sign-in. Fast if token is already cached.
     * On first launch: calls POST /devices/register and persists the result.
     */
    suspend fun ensureRegistered(ctx: Context) {
        if (_token != null) return

        val storedToken = DeviceStore.getToken(ctx)
        if (storedToken != null) {
            _token = storedToken
            _userId = DeviceStore.getUserId(ctx)
            Log.d(TAG, "loaded cached device token, user_id=$_userId")
            return
        }

        try {
            Log.d(TAG, "registering new device with backend")
            val response = ApiClient.api.registerDevice()
            DeviceStore.save(ctx, response.token, response.user_id)
            _token = response.token
            _userId = response.user_id
            Log.d(TAG, "device registered: user_id=${response.user_id}")
        } catch (t: Throwable) {
            Log.w(TAG, "device registration failed — offline or backend down: ${t.message}")
        }
    }

    private const val TAG = "DeviceRepository"
}

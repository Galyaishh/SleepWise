package com.example.sleepwisepoc

import android.content.Context
import android.util.Log

object DeviceRepository {
    private const val TAG = "DeviceRepository"

    /**
     * Ensures the device has a registered (user_id, token) pair.
     * Reads from DataStore on first call; registers via the backend if absent.
     * Sets the auth token on ApiClient so all subsequent requests are authenticated.
     */
    suspend fun ensureRegistered(context: Context) {
        val store = DeviceStore(context)
        val existing = store.load()
        if (existing != null) {
            val (userId, token) = existing
            Log.d(TAG, "loaded device user_id=$userId")
            ApiClient.setAuthToken(token)
            return
        }
        try {
            val response = ApiClient.api.registerDevice()
            store.save(response.user_id, response.token)
            ApiClient.setAuthToken(response.token)
            Log.d(TAG, "registered new device user_id=${response.user_id}")
        } catch (t: Throwable) {
            Log.w(TAG, "device registration failed — will retry next launch: ${t.message}")
        }
    }
}

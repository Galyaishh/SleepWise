package com.example.sleepwisepoc

import android.content.Context

/**
 * Lightweight SharedPreferences wrapper for the device token issued by the backend
 * at POST /devices/register. SharedPreferences is used (over DataStore) so the
 * interceptor in ApiClient can read synchronously without runBlocking.
 */
object DeviceStore {
    private const val PREFS   = "sleepwise_device"
    private const val KEY_TOKEN   = "device_token"
    private const val KEY_USER_ID = "device_user_id"

    fun getToken(ctx: Context): String? =
        prefs(ctx).getString(KEY_TOKEN, null)

    fun getUserId(ctx: Context): String? =
        prefs(ctx).getString(KEY_USER_ID, null)

    fun save(ctx: Context, token: String, userId: String) {
        prefs(ctx).edit().putString(KEY_TOKEN, token).putString(KEY_USER_ID, userId).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

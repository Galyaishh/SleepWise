package com.example.sleepwisepoc

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.authDataStore by preferencesDataStore(name = "auth")

private val KEY_USER_ID = stringPreferencesKey("user_id")
private val KEY_TOKEN   = stringPreferencesKey("token")

class DeviceStore(private val context: Context) {

    suspend fun load(): Pair<String, String>? {
        val prefs = context.authDataStore.data.firstOrNull() ?: return null
        val userId = prefs[KEY_USER_ID] ?: return null
        val token  = prefs[KEY_TOKEN]   ?: return null
        return userId to token
    }

    suspend fun save(userId: String, token: String) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_TOKEN]   = token
        }
    }
}

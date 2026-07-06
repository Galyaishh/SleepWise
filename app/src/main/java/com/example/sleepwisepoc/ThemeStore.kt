package com.example.sleepwisepoc

import android.content.Context

/**
 * Persists the user's dark/light theme choice. Defaults to dark (the app's
 * primary "Night" palette). Backed by SharedPreferences so Compose can read
 * it synchronously at startup, mirroring the lightweight store pattern used
 * elsewhere in the app.
 */
object ThemeStore {
    private const val PREFS = "sleepwise_theme"
    private const val KEY_DARK = "dark_theme"

    fun isDark(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DARK, true)

    fun setDark(ctx: Context, dark: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DARK, dark).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

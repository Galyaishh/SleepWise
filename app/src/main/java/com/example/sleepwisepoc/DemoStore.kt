package com.example.sleepwisepoc

import android.content.Context

/**
 * Toggles the report's "sample data" mode: when on, the Sleep report shows 7
 * synthetic 4-stage nights (SleepMockData) instead of the real backend history.
 * Used for demos/presentations where a clean full week is wanted. Defaults off
 * (real data). Backed by SharedPreferences, mirroring [ThemeStore].
 */
object DemoStore {
    private const val PREFS = "sleepwise_demo"
    private const val KEY = "demo_data"

    fun isDemo(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY, false)

    fun setDemo(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY, on).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

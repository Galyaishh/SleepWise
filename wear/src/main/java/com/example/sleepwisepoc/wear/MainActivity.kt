package com.example.sleepwisepoc.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Launcher activity for the Wear app. Shows a small status screen AND — crucially —
 * starts [HrStreamService] from the FOREGROUND. Android 12+ forbids starting a
 * foreground service from the background, so the phone's /cmd/start can be denied
 * if the watch app is asleep. Opening this activity (a foreground user action) is
 * the reliable way to start streaming; the phone command remains a best-effort
 * shortcut for when the app is already in the foreground.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        val title = TextView(this).apply {
            text = "SleepWise"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Starting…"
            textSize = 12f
            gravity = Gravity.CENTER
        }
        container.addView(title)
        container.addView(status)
        setContentView(container)

        if (hasSensorPermission()) {
            startStreaming()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.BODY_SENSORS), 100
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure streaming is running whenever the app is opened (foreground start
        // is always allowed; starting an already-running service is a no-op).
        if (hasSensorPermission()) startStreaming()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasSensorPermission()) startStreaming()
        else status.text = "Needs the body-sensors permission to track."
    }

    private fun hasSensorPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    private fun startStreaming() {
        val intent = Intent(this, HrStreamService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            status.text = "Tracking active.\nKeep the watch on your wrist."
            Log.d(TAG, "HrStreamService started from foreground activity")
        } catch (t: Throwable) {
            status.text = "Couldn't start tracking: ${t.message}"
            Log.w(TAG, "startStreaming failed: ${t.message}")
        }
    }

    companion object { private const val TAG = "WearMainActivity" }
}

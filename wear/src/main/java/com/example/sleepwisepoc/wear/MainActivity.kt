package com.example.sleepwisepoc.wear

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Minimal launcher activity for the Wear app — it just shows "SleepWise active"
 * and requests BODY_SENSORS the first time the user opens it. All real work
 * happens in HrStreamService, started/stopped by the phone via MessageClient.
 */
class MainActivity : Activity() {

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
        val status = TextView(this).apply {
            text = "Ready.\nStart tracking from the phone."
            textSize = 12f
            gravity = Gravity.CENTER
        }
        container.addView(title)
        container.addView(status)
        setContentView(container)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                100,
            )
        }
    }
}

package com.example.sleepwisepoc.alarm

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color

/**
 * Full-screen alarm screen shown over the lock screen when the smart alarm
 * fires. Launched via the full-screen intent on [AlarmRingService]'s
 * notification. A single big "Dismiss" button stops the ring service.
 *
 * The actual sound + vibration live in AlarmRingService — this activity is just
 * the visual + dismiss affordance, so dismissing here tells the service to stop.
 */
class AlarmRingingActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn the screen on.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D0F22"))
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "🌙"
            textSize = 64f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Good morning"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "SleepWise woke you at a light moment"
            textSize = 14f
            setTextColor(Color.parseColor("#A0A3B1"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        })
        root.addView(Button(this).apply {
            text = "Dismiss"
            textSize = 18f
            setOnClickListener {
                AlarmRingService.stop(this@AlarmRingingActivity)
                finish()
            }
        })
        setContentView(root)
    }

    override fun onDestroy() {
        // Belt-and-suspenders: ensure the ring stops if the activity goes away.
        AlarmRingService.stop(this)
        super.onDestroy()
    }
}

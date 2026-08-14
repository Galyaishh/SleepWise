package com.example.sleepwisepoc.wear

import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives start/stop commands from the phone over Wearable Data Layer.
 * The phone sends a message to PATH_CMD_START when the user taps "Start tracking"
 * (or PATH_CMD_STOP when they tap stop / a session ends), and we toggle the
 * watch-side foreground HR-stream service accordingly.
 */
class PhoneCommandListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${event.path}")
        when (event.path) {
            WearProtocol.PATH_CMD_START -> {
                // Bring the watch app to the foreground so it can legally start the
                // foreground service (Android 12+ blocks background FGS starts).
                // MainActivity.onResume() then starts streaming.
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { Log.w(TAG, "could not launch watch activity: ${it.message}") }
                // Best-effort direct start too (works if the app is already foreground).
                runCatching {
                    val intent = Intent(this, HrStreamService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                    else startService(intent)
                }.onFailure { Log.w(TAG, "cmd/start FGS denied (app backgrounded): ${it.message}") }
            }
            WearProtocol.PATH_CMD_STOP -> {
                stopService(Intent(this, HrStreamService::class.java))
            }
        }
    }

    companion object {
        private const val TAG = "PhoneCommandListener"
    }
}

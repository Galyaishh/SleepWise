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
                val intent = Intent(this, HrStreamService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
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

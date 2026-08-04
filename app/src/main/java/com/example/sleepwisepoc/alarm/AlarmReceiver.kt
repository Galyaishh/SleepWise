package com.example.sleepwisepoc.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sleepwisepoc.db.SleepWiseDatabase
import com.example.sleepwisepoc.service.SessionLog
import com.example.sleepwisepoc.service.SleepMonitoringService
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action} extras=${intent.extras?.keySet()?.toList()}")
        SessionLog.log(context, "AlarmReceiver onReceive action=${intent.action}")

        when (intent.action) {
            ACTION_FIRE_ALARM -> {
                // Start the foreground ring service — it owns the looping
                // ringtone + vibration + full-screen wake. (A bare notification
                // only plays the sound once, which read as "no real alarm".)
                AlarmRingService.start(context)
                SessionLog.log(context, "AlarmReceiver: AlarmRingService started")
            }
            ACTION_TICK -> {
                // Doze-proof backstop: drive a prediction pass even when the
                // process is idle and coroutine timers are frozen.
                SessionLog.log(context, "AlarmReceiver: TICK → service")
                SleepMonitoringService.triggerTick(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // Check Room for an active PENDING session that still has time left.
                // If found, restart SleepMonitoringService so monitoring continues
                // after a device reboot. The service will call resumeFromDb() (via tick)
                // or detect the matching window in onStartCommand and restore state.
                val result = goAsync()
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val dao = SleepWiseDatabase.get(context).sessionDao()
                        val nowMs = System.currentTimeMillis()
                        val active = dao.getPending()
                            .filter { entity ->
                                runCatching {
                                    Instant.parse(entity.windowEnd).toEpochMilli() > nowMs
                                }.getOrDefault(false)
                            }
                            .maxByOrNull { it.createdAt }

                        if (active != null) {
                            val startMs = runCatching { Instant.parse(active.windowStart).toEpochMilli() }.getOrNull()
                            val endMs   = runCatching { Instant.parse(active.windowEnd).toEpochMilli() }.getOrNull()
                            if (startMs != null && endMs != null) {
                                Log.d(TAG, "BOOT_COMPLETED: active session id=${active.id} — restarting service")
                                SessionLog.log(context, "BOOT: found active session id=${active.id}, restarting service")
                                SleepMonitoringService.resume(context, startMs, endMs)
                            }
                        } else {
                            Log.d(TAG, "BOOT_COMPLETED: no active session in Room — nothing to restore")
                        }
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }

    private fun showAlarmNotification(context: Context) {
        ensureChannel(context)

        val tapIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }

        val tapPi = tapIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Wake up — SleepWise")
            .setContentText("Time to start your day")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 600, 400, 600, 400, 600))
            .setAutoCancel(true)
            .setFullScreenIntent(tapPi, true)
            .setContentIntent(tapPi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "alarm notification posted (id=$NOTIFICATION_ID)")
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smart Alarm",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "SleepWise smart wake-up alarm"
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(channel)
        Log.d(TAG, "notification channel created: $CHANNEL_ID")
    }

    companion object {
        const val TAG = "AlarmReceiver"
        const val ACTION_FIRE_ALARM = "com.example.sleepwisepoc.ACTION_FIRE_ALARM"
        const val ACTION_TICK = "com.example.sleepwisepoc.ACTION_TICK"
        const val CHANNEL_ID = "smart_alarm"
        const val NOTIFICATION_ID = 1001
    }
}

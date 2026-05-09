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

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action} extras=${intent.extras?.keySet()?.toList()}")

        when (intent.action) {
            ACTION_FIRE_ALARM -> showAlarmNotification(context)
            Intent.ACTION_BOOT_COMPLETED -> Log.d(TAG, "BOOT_COMPLETED — scheduling restoration not implemented yet")
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
        const val CHANNEL_ID = "smart_alarm"
        const val NOTIFICATION_ID = 1001
    }
}

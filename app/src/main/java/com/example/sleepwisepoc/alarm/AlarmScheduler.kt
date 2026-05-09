package com.example.sleepwisepoc.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmScheduler {
    const val TAG = "AlarmScheduler"
    private const val REQUEST_CODE = 42

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun scheduleAt(context: Context, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Log.w(TAG, "cannot schedule exact alarms — permission not granted")
            return
        }

        val pi = pendingIntent(context, mutable = false)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        Log.d(TAG, "scheduled fire at ${timeFmt.format(Date(triggerAtMillis))} (${triggerAtMillis}ms)")
    }

    fun scheduleInMinutes(context: Context, minutes: Long) {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        Log.d(TAG, "scheduling test alarm in ${minutes}min")
        scheduleAt(context, triggerAt)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, mutable = false))
        Log.d(TAG, "alarm canceled")
    }

    private fun pendingIntent(context: Context, mutable: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
            `package` = context.packageName
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        flags = flags or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}

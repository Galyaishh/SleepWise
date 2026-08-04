package com.example.sleepwisepoc.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sleepwisepoc.schedule.SleepScheduleStore
import com.example.sleepwisepoc.service.SessionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Foreground service that ACTUALLY rings the alarm — a looping ringtone on the
 * alarm audio stream plus a looping vibration, held by a wake lock, until the
 * user dismisses it.
 *
 * Before this existed, AlarmReceiver only posted a notification whose one-shot
 * sound played once (or was suppressed) — "notification but no alarm". This
 * service owns the MediaPlayer + Vibrator so the alarm sustains like a real
 * alarm clock, and posts a full-screen-intent notification that launches
 * [AlarmRingingActivity] over the lock screen.
 */
class AlarmRingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                SessionLog.log(this, "AlarmRingService: STOP")
                stopRinging()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startRinging()
        }
        return START_STICKY
    }

    private fun startRinging() {
        SessionLog.log(this, "AlarmRingService: START ringing")
        ensureChannel()
        startForegroundWithNotification()
        acquireWakeLock()
        startVibration()
        scope.launch {
            val uriStr = SleepScheduleStore(this@AlarmRingService).schedule.first()
                .forDate(LocalDate.now()).alarmSoundUri
            val preferredUri = uriStr?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
            startSound(preferredUri)
        }
    }

    private fun startForegroundWithNotification() {
        // Full-screen intent → AlarmRingingActivity (shows over lock screen).
        val fsIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val fsPi = PendingIntent.getActivity(
            this, 0, fsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissPi = PendingIntent.getService(
            this, 1, Intent(this, AlarmRingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Wake up — SleepWise")
            .setContentText("Time to start your day")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fsPi, true)
            .setContentIntent(fsPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPi)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startSound(preferredUri: Uri? = null) {
        val uri = preferredUri
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmRingService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "alarm sound started: $uri")
        } catch (t: Throwable) {
            Log.e(TAG, "failed to start alarm sound", t)
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = vib
        val pattern = longArrayOf(0, 800, 600)  // wait, buzz, gap — repeats
        try {
            vib?.vibrate(VibrationEffect.createWaveform(pattern, 0))  // 0 = repeat from index 0
        } catch (t: Throwable) {
            Log.w(TAG, "vibration failed: ${t.message}")
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "sleepwise:alarm",
        ).apply { acquire(AUTO_STOP_MS) }
    }

    private fun stopRinging() {
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Alarm Ringing", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Active smart-alarm ring"
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                // No channel sound — the service owns the looping MediaPlayer.
                setSound(null, null)
            }
        )
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        private const val CHANNEL_ID = "alarm_ringing"
        private const val NOTIF_ID = 1002
        const val ACTION_STOP = "com.example.sleepwisepoc.ACTION_STOP_ALARM"
        // Safety: auto-release the wake lock after 5 min so we never pin the
        // screen on indefinitely if something goes wrong.
        private const val AUTO_STOP_MS = 5L * 60 * 1000

        fun start(context: Context) {
            val intent = Intent(context, AlarmRingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

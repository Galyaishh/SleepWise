package com.example.sleepwisepoc.service

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File-backed logger for overnight sessions.
 *
 * Logcat's circular buffer (~1 MB) overwrites entries within a few hours, so
 * the morning-after dump of an 8-hour run is missing almost all the data we
 * need. This appends every important event to ${filesDir}/session.log, which
 * survives buffer rollover, app force-close, and reboot.
 *
 * Pull from a connected device with:
 *   adb shell run-as com.example.sleepwisepoc cat files/session.log
 *
 * Calls are tagged "SessionLog" in logcat too, so live monitoring still works.
 */
object SessionLog {
    private const val TAG = "SessionLog"
    private const val FILE_NAME = "session.log"
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private const val PREV_FILE_NAME = "session_prev.log"

    /**
     * Start a fresh log for a new session, but ROTATE the previous one to
     * session_prev.log first so a crash-restart (which re-enters onStartCommand)
     * can never destroy the prior night's record. Pull both with:
     *   adb shell run-as <pkg> cat files/session.log
     *   adb shell run-as <pkg> cat files/session_prev.log
     */
    fun reset(ctx: Context) {
        runCatching {
            val cur = File(ctx.filesDir, FILE_NAME)
            if (cur.exists() && cur.length() > 0) {
                cur.copyTo(File(ctx.filesDir, PREV_FILE_NAME), overwrite = true)
            }
            cur.writeText("")
            log(ctx, "===== session log reset (previous rotated to $PREV_FILE_NAME) =====")
        }
    }

    /** Append one line + log to logcat. Safe to call from any thread. */
    fun log(ctx: Context, msg: String) {
        val ts = timeFmt.format(Date())
        Log.d(TAG, msg)
        runCatching {
            File(ctx.filesDir, FILE_NAME).appendText("$ts  $msg\n")
        }
    }
}

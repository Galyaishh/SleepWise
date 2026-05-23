package com.example.sleepwisepoc.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Sends one-shot commands to the paired watch over Wearable Data Layer.
 * Used by SleepMonitoringService to tell the watch when to start/stop
 * streaming heart rate.
 */
object WearCommand {
    private const val TAG = "WearCommand"

    suspend fun startStreaming(ctx: Context): Boolean = send(ctx, WearProtocol.PATH_CMD_START)
    suspend fun stopStreaming(ctx: Context): Boolean = send(ctx, WearProtocol.PATH_CMD_STOP)

    private suspend fun send(ctx: Context, path: String): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(ctx).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "no connected watch node — command '$path' not delivered")
                return false
            }
            val msgClient = Wearable.getMessageClient(ctx)
            nodes.forEach { node ->
                msgClient.sendMessage(node.id, path, ByteArray(0)).await()
            }
            Log.d(TAG, "sent '$path' to ${nodes.size} node(s)")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "send '$path' failed: ${t.message}")
            false
        }
    }
}

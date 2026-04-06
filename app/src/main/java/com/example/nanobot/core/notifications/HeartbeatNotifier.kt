package com.example.nanobot.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.nanobot.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface HeartbeatNotificationSink {
    fun notifyExecuted(summary: String): Boolean
    fun notifyFailed(summary: String): Boolean
}

@Singleton
class HeartbeatNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : HeartbeatNotificationSink {
    override fun notifyExecuted(summary: String): Boolean {
        return notify(
            notificationId = HEARTBEAT_EXECUTED_ID,
            title = context.getString(R.string.heartbeat_executed_title),
            message = summary.ifBlank { context.getString(R.string.heartbeat_executed_fallback) }
        )
    }

    override fun notifyFailed(summary: String): Boolean {
        return notify(
            notificationId = HEARTBEAT_FAILED_ID,
            title = context.getString(R.string.heartbeat_failed_title),
            message = summary.ifBlank { context.getString(R.string.heartbeat_failed_fallback) }
        )
    }

    private fun notify(notificationId: Int, title: String, message: String): Boolean {
        ensureChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.heartbeat_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.heartbeat_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "nanobot_heartbeat"
        const val HEARTBEAT_EXECUTED_ID = 31001
        const val HEARTBEAT_FAILED_ID = 31002
    }
}

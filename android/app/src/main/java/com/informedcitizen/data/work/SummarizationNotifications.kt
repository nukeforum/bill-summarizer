package com.informedcitizen.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.informedcitizen.MainActivity
import com.informedcitizen.R

object SummarizationNotifications {
    private const val CHANNEL_ID = "summarization_progress"
    const val NOTIFICATION_ID = 4711

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.summarization_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.summarization_channel_desc)
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        currentIndex: Int,
        total: Int,
        indeterminate: Boolean,
    ): Notification {
        ensureChannel(context)

        val stopPi = PendingIntent.getBroadcast(
            context, 0,
            Intent(StopSummarizationReceiver.ACTION_STOP)
                .setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openSettingsPi = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_AI_SETTINGS
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.summarization_title))
            .setContentText(
                if (indeterminate) context.getString(R.string.summarization_text_starting)
                else context.getString(R.string.summarization_text_progress, currentIndex, total),
            )
            .setProgress(total.coerceAtLeast(1), currentIndex, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.summarization_stop), stopPi)
            .addAction(0, context.getString(R.string.summarization_open_settings), openSettingsPi)
            .build()
    }
}

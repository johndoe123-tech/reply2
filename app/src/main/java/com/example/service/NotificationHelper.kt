package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object NotificationHelper {

    const val CHANNEL_ALERT_ID = "autoreply_alerts_channel"
    const val CHANNEL_ACTIVITY_ID = "autoreply_activity_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel 1: High Priority User Escalations & Tool Calls
            val alertName = "AutoReply User Escalations & Alerts"
            val alertDesc = "Alerts when the AI detects tool escalation or personal questions needing manual reply"
            val alertChannel = NotificationChannel(CHANNEL_ALERT_ID, alertName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = alertDesc
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertChannel)

            // Channel 2: Activity Logs on Every Auto-Reply
            val activityName = "AutoReply Activity Logs"
            val activityDesc = "Notifications sent every time the AI automatically replies to a WhatsApp message"
            val activityChannel = NotificationChannel(CHANNEL_ACTIVITY_ID, activityName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = activityDesc
            }
            notificationManager.createNotificationChannel(activityChannel)
        }
    }

    fun postAutoReplyNotification(context: Context, contactName: String, incomingText: String, replyText: String) {
        createChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIVITY_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Auto-replied to $contactName")
            .setContentText("\"$incomingText\" → \"$replyText\"")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Incoming from $contactName:\n\"$incomingText\"\n\nAI Auto-Reply Sent:\n\"$replyText\"")
            )
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notificationId, notification)
    }

    fun postUserAlertNotification(context: Context, contactName: String, incomingText: String, reason: String) {
        createChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("New message from $contactName needs your reply")
            .setContentText("\"$incomingText\" • $reason")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Incoming from $contactName:\n\"$incomingText\"\n\nReason / Tool Call Alert:\n$reason")
            )
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = (System.currentTimeMillis() % 100000).toInt() + 50000
        notificationManager.notify(notificationId, notification)
    }
}

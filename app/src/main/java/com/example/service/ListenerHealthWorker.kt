package com.example.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.work.*
import com.example.data.db.ActivityLogEntry
import com.example.data.db.AppDatabase
import java.util.concurrent.TimeUnit

class ListenerHealthWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(context)
        val flatListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        val serviceComponent = ComponentName(context, WhatsAppNotificationListenerService::class.java)
        val isEnabled = flatListeners.contains(serviceComponent.flattenToString()) || flatListeners.contains(serviceComponent.flattenToShortString())

        if (isEnabled) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.service.notification.NotificationListenerService.requestRebind(serviceComponent)
                }
                AutoReplyForegroundService.startService(context)
                db.activityLogDao().insert(
                    ActivityLogEntry(
                        eventType = "HEALTH_CHECK",
                        detail = "Listener re-bound & foreground service health check OK"
                    )
                )
            } catch (e: Exception) {
                db.activityLogDao().insert(
                    ActivityLogEntry(
                        eventType = "ERROR",
                        detail = "Health check rebind error: ${e.message}"
                    )
                )
            }
        } else {
            db.activityLogDao().insert(
                ActivityLogEntry(
                    eventType = "ERROR",
                    detail = "Notification Listener is NOT enabled in Android System Settings!"
                )
            )
            NotificationHelper.postDiagnosticErrorNotification(
                context = context,
                detail = "WhatsApp AutoReply listener is disabled in system settings. Please grant notification access."
            )
        }

        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<ListenerHealthWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                            .build()
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "listener_health_check",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                // Ignore schedule errors if WorkManager not initialized
            }
        }
    }
}

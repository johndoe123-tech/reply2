package com.example.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.data.db.AppDatabase
import com.example.data.db.Message
import com.example.data.supabase.SupabaseSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DirectReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DIRECT_REPLY) return

        val contactId = intent.getStringExtra(EXTRA_CONTACT_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val results: Bundle? = RemoteInput.getResultsFromIntent(intent)
        val replyText = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()

        if (replyText.isNullOrBlank()) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val target = WhatsAppReplyCache.get(contactId)

                var sentSuccessfully = false
                if (target != null) {
                    sentSuccessfully = sendWhatsAppReply(context, target.pendingIntent, target.remoteInput, replyText)
                }

                // Insert into local DB as user's manual message
                val msg = Message(
                    contactId = contactId,
                    sender = "me",
                    text = replyText,
                    timestamp = System.currentTimeMillis(),
                    wasAutoReplied = false
                )
                val msgId = db.messageDao().insertMessage(msg)
                SupabaseSyncRepository(db).syncMessage(msg.copy(id = msgId))
                db.contactDao().updateLastMessageAt(contactId, System.currentTimeMillis())

                // Update notification feedback to user
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val feedbackText = if (sentSuccessfully) {
                    "Reply sent to $contactId: \"$replyText\""
                } else {
                    "Saved locally for $contactId: \"$replyText\""
                }

                val updatedNotification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ACTIVITY_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Replied to $contactId")
                    .setContentText(feedbackText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(feedbackText))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .build()

                if (notificationId != -1) {
                    notificationManager.notify(notificationId, updatedNotification)
                }
            } catch (e: Exception) {
                Log.e("DirectReplyReceiver", "Error processing direct reply", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendWhatsAppReply(
        context: Context,
        pendingIntent: android.app.PendingIntent,
        remoteInput: android.app.RemoteInput,
        replyText: String
    ): Boolean {
        return try {
            val replyIntent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            android.app.RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, bundle)
            pendingIntent.send(context, 0, replyIntent)
            true
        } catch (e: Exception) {
            Log.e("DirectReplyReceiver", "Failed to fire WhatsApp reply intent", e)
            false
        }
    }

    companion object {
        const val ACTION_DIRECT_REPLY = "com.example.ACTION_DIRECT_REPLY"
        const val EXTRA_CONTACT_ID = "extra_contact_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }
}

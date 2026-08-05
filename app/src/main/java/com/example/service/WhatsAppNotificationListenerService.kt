package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.data.db.AppDatabase
import com.example.data.db.Contact
import com.example.data.db.KnownRelation
import com.example.data.db.Message
import com.example.data.supabase.SupabaseSyncRepository
import com.example.data.ollama.OllamaRepository
import com.example.data.pref.UserPreferencesRepository
import com.example.domain.ContactResolver
import com.example.domain.DecisionEngine
import com.example.domain.DecisionResult
import com.example.domain.MemoryUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WhatsAppNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: AppDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var ollamaRepo: OllamaRepository
    private lateinit var decisionEngine: DecisionEngine
    private lateinit var memoryUpdater: MemoryUpdater
    private lateinit var contactResolver: ContactResolver

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        prefsRepo = UserPreferencesRepository.getInstance(this)
        ollamaRepo = OllamaRepository()
        decisionEngine = DecisionEngine(db, ollamaRepo)
        memoryUpdater = MemoryUpdater(db, ollamaRepo)
        contactResolver = ContactResolver(this)
        createAlertNotificationChannel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected to WhatsApp system stream.")
        try {
            AutoReplyForegroundService.startService(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service from listener: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected. Requesting rebind...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(android.content.ComponentName(this, WhatsAppNotificationListenerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting rebind: ${e.message}")
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "Task removed from recents. Re-starting foreground service and rebinding listener.")
        try {
            AutoReplyForegroundService.startService(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestRebind(android.content.ComponentName(this, WhatsAppNotificationListenerService::class.java))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onTaskRemoved: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: ""
        if (pkgName != "com.whatsapp" && pkgName != "com.whatsapp.w4b") {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence("android.hiddenConversationTitle")?.toString()?.trim()
            ?: ""

        var rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (rawText.isNullOrBlank()) {
            rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        }
        if (rawText.isNullOrBlank()) {
            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!textLines.isNullOrEmpty()) {
                rawText = textLines.lastOrNull()?.toString()?.trim()
            }
        }
        if (rawText.isNullOrBlank()) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val lastMsg = messages.lastOrNull()
                if (lastMsg is Bundle) {
                    rawText = lastMsg.getCharSequence("text")?.toString()?.trim()
                }
            }
        }

        val extractedText = rawText ?: ""

        if (rawTitle.isBlank() || extractedText.isBlank()) return

        // Ignore standard WhatsApp system notifications (e.g. "WhatsApp" - "Checking for new messages")
        val rawTitleTrimmed = rawTitle.trim()
        val rawTextTrimmed = extractedText.trim()
        if (rawTitleTrimmed.equals("WhatsApp", ignoreCase = true) &&
            (rawTextTrimmed.contains("Checking for new messages", ignoreCase = true) ||
             rawTextTrimmed.contains("WhatsApp Web is currently active", ignoreCase = true) ||
             rawTextTrimmed.contains("WhatsApp Web", ignoreCase = true))
        ) {
            return
        }

        // Ignore summary/bundled notifications
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        // 1. Group Chat Detection Heuristics
        if (isGroupChat(rawTitle, extractedText, extras, notification.flags)) {
            Log.d(TAG, "Ignored group chat message from: $rawTitle")
            return
        }

        // 2. Capture WhatsApp RemoteInput & PendingIntent for quick reply
        var (pendingIntent, remoteInput) = findReplyAction(notification)
        if (pendingIntent == null || remoteInput == null) {
            Log.w(TAG, "No direct RemoteInput reply action found in notification for $rawTitle")
        }

        serviceScope.launch {
            try {
                // Ensure foreground control service is running
                try {
                    AutoReplyForegroundService.startService(this@WhatsAppNotificationListenerService)
                } catch (e: Exception) {
                    // ignore start failure if already running
                }

                // Check if Auto-Reply is globally enabled
                val prefs = prefsRepo.userPreferencesFlow.first()
                if (!prefs.autoReplyEnabled) {
                    Log.d(TAG, "Auto-reply is globally disabled in settings.")
                    return@launch
                }

                // Resolve Contact Identity
                val resolvedName = contactResolver.resolveContactName(rawTitle)
                val knownRelation: KnownRelation? = db.knownRelationDao().getByPhoneNumber(rawTitle)
                val relationshipLabel = knownRelation?.relationshipLabel ?: "Friend"

                val contactId = resolvedName

                // Store reply action in cache if available, or retrieve cached action for background reply
                var activePendingIntent = pendingIntent
                var activeRemoteInput = remoteInput

                if (activePendingIntent != null && activeRemoteInput != null) {
                    WhatsAppReplyCache.store(contactId, activePendingIntent, activeRemoteInput)
                } else {
                    val cached = WhatsAppReplyCache.get(contactId)
                    if (cached != null) {
                        activePendingIntent = cached.pendingIntent
                        activeRemoteInput = cached.remoteInput
                        Log.i(TAG, "Using cached WhatsApp RemoteInput reply action for $contactId")
                    }
                }

                // Upsert Contact
                var contact = db.contactDao().getContactById(contactId)
                if (contact == null) {
                    contact = Contact(
                        contactId = contactId,
                        phoneNumber = rawTitle,
                        displayName = resolvedName,
                        relationshipLabel = relationshipLabel,
                        isGroup = false,
                        lastMessageAt = System.currentTimeMillis()
                    )
                    db.contactDao().insertOrUpdateContact(contact)
                    SupabaseSyncRepository(db).syncContact(contact)
                } else {
                    db.contactDao().updateLastMessageAt(contactId, System.currentTimeMillis())
                }

                // Insert incoming message
                val incomingMsg = Message(
                    contactId = contactId,
                    sender = "them",
                    text = extractedText,
                    timestamp = System.currentTimeMillis(),
                    wasAutoReplied = false
                )
                val msgId = db.messageDao().insertMessage(incomingMsg)
                SupabaseSyncRepository(db).syncMessage(incomingMsg.copy(id = msgId))

                // Check hard bypass for doNotRespond contacts
                if (contact.doNotRespond) {
                    Log.i(TAG, "Contact $contactId is set to doNotRespond. Skipping LLM decision engine.")
                    NotificationHelper.postUserAlertNotification(
                        context = this@WhatsAppNotificationListenerService,
                        contactName = contactId,
                        incomingText = extractedText,
                        reason = "This contact is set to manual-reply only."
                    )
                    memoryUpdater.updateMemoryForContact(
                        contactId = contactId,
                        ollamaUrl = prefs.ollamaUrl,
                        modelName = prefs.selectedModel
                    )
                    return@launch
                }

                // 3. Process message through Decision Engine
                val decision = decisionEngine.processIncomingMessage(
                    contactId = contactId,
                    incomingText = extractedText,
                    ollamaUrl = prefs.ollamaUrl,
                    modelName = prefs.selectedModel
                )

                when (decision) {
                    is DecisionResult.AutoReply -> {
                        val finalPI = activePendingIntent
                        val finalRI = activeRemoteInput
                        if (finalPI != null && finalRI != null) {
                            val success = sendWhatsAppReply(finalPI, finalRI, decision.replyText)
                            if (success) {
                                // Insert outgoing reply into DB
                                val replyMsg = Message(
                                    contactId = contactId,
                                    sender = "me",
                                    text = decision.replyText,
                                    timestamp = System.currentTimeMillis(),
                                    wasAutoReplied = true
                                )
                                val replyId = db.messageDao().insertMessage(replyMsg)
                                SupabaseSyncRepository(db).syncMessage(replyMsg.copy(id = replyId))

                                // Async memory update
                                memoryUpdater.updateMemoryForContact(
                                    contactId = contactId,
                                    ollamaUrl = prefs.ollamaUrl,
                                    modelName = prefs.selectedModel
                                )

                                // Post local notification telling user about every auto-reply sent
                                NotificationHelper.postAutoReplyNotification(
                                    context = this@WhatsAppNotificationListenerService,
                                    contactName = contactId,
                                    incomingText = extractedText,
                                    replyText = decision.replyText
                                )
                            } else {
                                NotificationHelper.postUserAlertNotification(
                                    context = this@WhatsAppNotificationListenerService,
                                    contactName = contactId,
                                    incomingText = extractedText,
                                    reason = "Failed to send quick reply action."
                                )
                            }
                        } else {
                            NotificationHelper.postUserAlertNotification(
                                context = this@WhatsAppNotificationListenerService,
                                contactName = contactId,
                                incomingText = extractedText,
                                reason = "Reply action missing in WhatsApp notification."
                            )
                        }
                    }
                    is DecisionResult.NotifyOnly -> {
                        Log.i(TAG, "NotifyOnly / Tool Call triggered for $contactId: ${decision.reason}")
                        NotificationHelper.postUserAlertNotification(
                            context = this@WhatsAppNotificationListenerService,
                            contactName = contactId,
                            incomingText = extractedText,
                            reason = decision.reason
                        )
                        // Also update contact memory and behavior on NotifyOnly path so history is learned
                        memoryUpdater.updateMemoryForContact(
                            contactId = contactId,
                            ollamaUrl = prefs.ollamaUrl,
                            modelName = prefs.selectedModel
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing WhatsApp notification", e)
            }
        }
    }

    private fun isGroupChat(title: String, text: String, extras: Bundle, flags: Int): Boolean {
        // 1. Explicit Android system flags for group conversations
        if (extras.getBoolean("android.isGroupConversation", false) ||
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) {
            return true
        }

        // 2. Check for group summary container notification
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return true
        }

        // 3. Check for WhatsApp group conversation title key
        val hiddenTitle = extras.getCharSequence("android.hiddenConversationTitle")?.toString()?.trim()
        if (!hiddenTitle.isNull_or_blank()) {
            val extraTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: title
            if (hiddenTitle != extraTitle) {
                return true
            }
        }

        return false
    }

    private fun findReplyAction(notification: Notification): Pair<PendingIntent?, android.app.RemoteInput?> {
        // 1. Check standard notification actions
        val actions = notification.actions
        if (actions != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (ri in remoteInputs) {
                    if (ri.allowFreeFormInput || !ri.resultKey.isNullOrBlank() || action.title?.toString()?.contains("reply", ignoreCase = true) == true) {
                        return Pair(action.actionIntent, ri)
                    }
                }
            }
        }

        // 2. Check WearableExtender actions (WhatsApp attaches Android Wear reply actions with RemoteInput)
        try {
            val wearableExtender = NotificationCompat.WearableExtender(notification)
            for (action in wearableExtender.actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (ri in remoteInputs) {
                    if (ri.allowFreeFormInput || !ri.resultKey.isNullOrBlank() || action.title?.toString()?.contains("reply", ignoreCase = true) == true) {
                        val sysRemoteInput = android.app.RemoteInput.Builder(ri.resultKey)
                            .setLabel(ri.label)
                            .setChoices(ri.choices)
                            .setAllowFreeFormInput(ri.allowFreeFormInput)
                            .build()
                        return Pair(action.actionIntent, sysRemoteInput)
                    }
                }
            }
        } catch (e: Exception) {
            // Extender parsing error ignore
        }

        return Pair(null, null)
    }

    private fun sendWhatsAppReply(
        pendingIntent: PendingIntent,
        remoteInput: android.app.RemoteInput,
        replyText: String
    ): Boolean {
        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            android.app.RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            pendingIntent.send(this, 0, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fire RemoteInput pending intent", e)
            false
        }
    }

    private fun postAlertNotification(contactName: String, originalMessage: String, reason: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setSmallResourceIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("New message from $contactName needs your reply")
            .setContentText("\"$originalMessage\" • $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("\"$originalMessage\"\n\nReason: $reason"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), alertNotification)
    }

    private fun NotificationCompat.Builder.setSmallResourceIcon(iconRes: Int): NotificationCompat.Builder {
        return this.setSmallIcon(iconRes)
    }

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AutoReply User Alerts"
            val descriptionText = "Alerts when messages require manual user reply"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ALERT_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()

    companion object {
        private const val TAG = "WhatsAppListener"
        const val CHANNEL_ALERT_ID = "autoreply_alerts_channel"
    }
}

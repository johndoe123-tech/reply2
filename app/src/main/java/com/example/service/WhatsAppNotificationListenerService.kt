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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.data.db.ActivityLogEntry
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

    private val respondedKeys = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    private val stopReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_LISTENING) {
                Log.i(TAG, "Stop action received from foreground notification.")
                serviceScope.launch {
                    prefsRepo.updateAutoReplyEnabled(false)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        prefsRepo = UserPreferencesRepository.getInstance(this)
        ollamaRepo = OllamaRepository()
        decisionEngine = DecisionEngine(db, ollamaRepo)
        memoryUpdater = MemoryUpdater(db, ollamaRepo)
        contactResolver = ContactResolver(this)
        createAlertNotificationChannel()
        createForegroundChannel()
        promoteToForeground()
        registerStopReceiver()
    }

    private fun registerStopReceiver() {
        val filter = android.content.IntentFilter(ACTION_STOP_LISTENING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {}
    }

    private fun createForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "AutoReply Control Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Persistent notification indicating WhatsApp AutoReply service is active." }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun promoteToForeground() {
        val notificationIntent = Intent(this, com.example.MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(ACTION_STOP_LISTENING).setPackage(packageName)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("AutoReply is Active")
            .setContentText("Listening to WhatsApp notifications & generating local AI replies.")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected to WhatsApp system stream.")
        serviceScope.launch {
            db.activityLogDao().insert(
                ActivityLogEntry(
                    timestamp = System.currentTimeMillis(),
                    eventType = "LISTENER_CONNECTED",
                    detail = "Connected to WhatsApp notification stream."
                )
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected. Requesting rebind...")
        try {
            serviceScope.launch {
                db.activityLogDao().insert(
                    ActivityLogEntry(
                        timestamp = System.currentTimeMillis(),
                        eventType = "LISTENER_DISCONNECTED",
                        detail = "Disconnected from WhatsApp notification stream."
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging listener disconnect: ${e.message}")
        }
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
        Log.i(TAG, "Task removed from recents — listener remains foreground and bound.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(android.content.ComponentName(this, WhatsAppNotificationListenerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error in onTaskRemoved: ${e.message}")
            }
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

        // Dedup by sbn.key + message text + postTime so we ignore re-posted notifications of the SAME message,
        // but NEVER drop new incoming messages from the same sender (which share sbn.key).
        val dedupKey = "${sbn.key}_${rawTitle}_${extractedText}_${sbn.postTime}"
        if (respondedKeys.contains(dedupKey)) {
            return
        }
        respondedKeys.add(dedupKey)
        if (respondedKeys.size > 200) {
            val iterator = respondedKeys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        // 2. Capture WhatsApp RemoteInput & PendingIntent for quick reply
        var (pendingIntent, remoteInput) = findReplyAction(notification)
        if (pendingIntent == null || remoteInput == null) {
            Log.w(TAG, "No direct RemoteInput reply action found in notification for $rawTitle")
        }

        serviceScope.launch {
            try {
                // Check if Auto-Reply is globally enabled
                val prefs = prefsRepo.userPreferencesFlow.first()
                if (!prefs.autoReplyEnabled) {
                    Log.d(TAG, "Auto-reply is globally disabled in settings.")
                    return@launch
                }

                // Resolve Contact Identity
                val resolvedName = contactResolver.resolveContactName(rawTitle)
                val contactId = deriveStableConversationKey(sbn, rawTitle)
                val knownRelation: KnownRelation? = db.knownRelationDao().getByPhoneNumber(rawTitle)
                val relationshipLabel = knownRelation?.relationshipLabel ?: "Friend"

                if (contactId.startsWith("name_")) {
                    db.activityLogDao().insert(
                        ActivityLogEntry(
                            timestamp = System.currentTimeMillis(),
                            contactId = contactId,
                            eventType = "IDENTITY_WARNING",
                            detail = "Identity for '$resolvedName' relies on display name only and could collide with another contact sharing the same name."
                        )
                    )
                }

                // Log received message in Activity Logs
                db.activityLogDao().insert(
                    ActivityLogEntry(
                        timestamp = System.currentTimeMillis(),
                        contactId = contactId,
                        eventType = "MESSAGE_RECEIVED",
                        detail = "Received message: \"$extractedText\""
                    )
                )

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

                // Check WiFi connectivity if Ollama URL is local/LAN
                if (isLocalUrl(prefs.ollamaUrl)) {
                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNetwork = cm?.activeNetwork
                    val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }
                    val hasWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                    if (!hasWifi) {
                        val wifiErrorMsg = "No WiFi connection — cannot reach Ollama server (${prefs.ollamaUrl}) on local network."
                        db.activityLogDao().insert(
                            ActivityLogEntry(
                                timestamp = System.currentTimeMillis(),
                                contactId = contactId,
                                eventType = "ERROR",
                                detail = wifiErrorMsg
                            )
                        )
                        NotificationHelper.postDiagnosticErrorNotification(
                            context = this@WhatsAppNotificationListenerService,
                            detail = "Message from $contactId couldn't be processed — phone lost WiFi connection to your Ollama server."
                        )
                        return@launch
                    }
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

                                // Log Auto-Reply in Activity Log
                                db.activityLogDao().insert(
                                    ActivityLogEntry(
                                        timestamp = System.currentTimeMillis(),
                                        contactId = contactId,
                                        eventType = "AUTO_REPLIED",
                                        detail = "Auto-replied: \"${decision.replyText}\""
                                    )
                                )

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
                                val sendFailDetail = "Failed to execute RemoteInput reply action."
                                db.activityLogDao().insert(
                                    ActivityLogEntry(
                                        timestamp = System.currentTimeMillis(),
                                        contactId = contactId,
                                        eventType = "ERROR",
                                        detail = sendFailDetail
                                    )
                                )
                                NotificationHelper.postUserAlertNotification(
                                    context = this@WhatsAppNotificationListenerService,
                                    contactName = contactId,
                                    incomingText = extractedText,
                                    reason = sendFailDetail
                                )
                            }
                        } else {
                            val missingActionDetail = "Reply action missing in WhatsApp notification for $contactId."
                            db.activityLogDao().insert(
                                ActivityLogEntry(
                                    timestamp = System.currentTimeMillis(),
                                    contactId = contactId,
                                    eventType = "ERROR",
                                    detail = missingActionDetail
                                )
                            )
                            NotificationHelper.postUserAlertNotification(
                                context = this@WhatsAppNotificationListenerService,
                                contactName = contactId,
                                incomingText = extractedText,
                                reason = missingActionDetail
                            )
                        }
                    }
                    is DecisionResult.NotifyOnly -> {
                        Log.i(TAG, "NotifyOnly / Tool Call triggered for $contactId: ${decision.reason}")
                        db.activityLogDao().insert(
                            ActivityLogEntry(
                                timestamp = System.currentTimeMillis(),
                                contactId = contactId,
                                eventType = "NOTIFIED_USER",
                                detail = "Escalated: ${decision.reason}"
                            )
                        )
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
                val stackSummary = e.stackTrace.take(3).joinToString("; ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                val errorDetail = "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"} ($stackSummary)"
                try {
                    db.activityLogDao().insert(
                        ActivityLogEntry(
                            timestamp = System.currentTimeMillis(),
                            contactId = null,
                            eventType = "ERROR",
                            detail = errorDetail
                        )
                    )
                    NotificationHelper.postDiagnosticErrorNotification(
                        context = this@WhatsAppNotificationListenerService,
                        detail = "Background AutoReply Error: ${e.message ?: "Unknown error"}"
                    )
                } catch (logErr: Exception) {
                    Log.e(TAG, "Failed to insert error log: ${logErr.message}")
                }
                Log.e(TAG, "Error processing WhatsApp notification", e)
            }
        }
    }

    private fun isLocalUrl(url: String): Boolean {
        val clean = url.lowercase().removePrefix("http://").removePrefix("https://").substringBefore(":")
        return clean == "localhost" || clean == "127.0.0.1" ||
               clean.startsWith("192.168.") || clean.startsWith("10.") ||
               clean.startsWith("172.16.") || clean.startsWith("172.17.") ||
               clean.startsWith("172.18.") || clean.startsWith("172.19.") ||
               clean.startsWith("172.20.") || clean.startsWith("172.21.") ||
               clean.startsWith("172.22.") || clean.startsWith("172.23.") ||
               clean.startsWith("172.24.") || clean.startsWith("172.25.") ||
               clean.startsWith("172.26.") || clean.startsWith("172.27.") ||
               clean.startsWith("172.28.") || clean.startsWith("172.29.") ||
               clean.startsWith("172.30.") || clean.startsWith("172.31.")
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

        // 3. Check if conversationTitle is explicitly set and differs from title
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val extraTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: title
        if (!conversationTitle.isNullOrBlank() && conversationTitle != extraTitle) {
            if (text.contains(":") && !text.startsWith("http")) {
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

    private fun deriveStableConversationKey(sbn: StatusBarNotification, rawTitle: String): String {
        // 1. Prefer the notification's shortcutId — Android's Conversation/People API
        val shortcutId = sbn.notification?.shortcutId
        if (!shortcutId.isNullOrBlank()) {
            return "sc_$shortcutId"
        }

        // 2. Fall back to the phone number URI from EXTRA_PEOPLE_LIST / EXTRA_PEOPLE, if present
        val extras = sbn.notification?.extras
        if (extras != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val people = extras.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST)
                    val phoneUri = people?.firstOrNull { it.uri?.startsWith("tel:") == true }?.uri
                    if (!phoneUri.isNullOrBlank()) {
                        val cleanNum = phoneUri.removePrefix("tel:").filter { it.isDigit() || it == '+' }
                        if (cleanNum.isNotBlank()) {
                            return "tel_$cleanNum"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract Person URI from notification: ${e.message}")
            }

            try {
                @Suppress("DEPRECATION")
                val stringPeople = extras.getStringArrayList(Notification.EXTRA_PEOPLE)
                val phoneUri = stringPeople?.firstOrNull { it.startsWith("tel:") }
                if (!phoneUri.isNullOrBlank()) {
                    val cleanNum = phoneUri.removePrefix("tel:").filter { it.isDigit() || it == '+' }
                    if (cleanNum.isNotBlank()) {
                        return "tel_$cleanNum"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract URI from EXTRA_PEOPLE: ${e.message}")
            }
        }

        // 3. Last resort: display-name-based key (current behavior).
        return "name_${rawTitle.trim().lowercase()}"
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
        const val FOREGROUND_CHANNEL_ID = "autoreply_foreground_channel"
        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val ACTION_STOP_LISTENING = "com.example.service.STOP_LISTENING"
    }
}

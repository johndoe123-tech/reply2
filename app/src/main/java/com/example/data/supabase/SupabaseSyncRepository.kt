package com.example.data.supabase

import android.util.Log
import com.example.data.db.*
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class SyncResult(val successCount: Int, val failedCount: Int)

class SupabaseSyncRepository(private val db: AppDatabase) {

    private val client get() = SupabaseClientProvider.client
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun logSyncError(table: String, contactId: String?, error: Exception) {
        val detail = "Cloud sync failed [$table]: ${error.javaClass.simpleName}: ${error.message}"
        Log.e("SupabaseSync", detail, error)
        scope.launch {
            try {
                db.activityLogDao().insert(
                    ActivityLogEntry(
                        timestamp = System.currentTimeMillis(),
                        contactId = contactId,
                        eventType = "CLOUD_SYNC_ERROR",
                        detail = detail
                    )
                )
            } catch (_: Exception) { /* avoid crash-looping on logging itself */ }
        }
    }

    // --- Suspend sync methods for one-shot / force sync ---

    suspend fun syncContactSuspend(contact: Contact) {
        try {
            client.from("contacts").upsert(contact.toRow())
        } catch (e: Exception) {
            logSyncError("contacts", contact.contactId, e)
            throw e
        }
    }

    suspend fun syncContactMemorySuspend(memory: ContactMemory) {
        try {
            client.from("contact_memories").upsert(memory.toRow())
        } catch (e: Exception) {
            logSyncError("contact_memories", memory.contactId, e)
            throw e
        }
    }

    suspend fun syncBehaviorProfileSuspend(profile: BehaviorProfile) {
        try {
            client.from("behavior_profiles").upsert(profile.toRow())
        } catch (e: Exception) {
            logSyncError("behavior_profiles", profile.contactId, e)
            throw e
        }
    }

    suspend fun syncKnownRelationSuspend(relation: KnownRelation) {
        try {
            client.from("known_relations").upsert(relation.toRow())
        } catch (e: Exception) {
            logSyncError("known_relations", null, e)
            throw e
        }
    }

    suspend fun syncMessageSuspend(message: Message) {
        try {
            client.from("messages").upsert(message.toRow()) {
                onConflict = "user_id,contact_id,local_id"
            }
        } catch (e: Exception) {
            logSyncError("messages", message.contactId, e)
            throw e
        }
    }

    suspend fun syncPersonalMemorySuspend(memory: PersonalMemory) {
        try {
            client.from("personal_memory").upsert(memory.toRow())
        } catch (e: Exception) {
            logSyncError("personal_memory", null, e)
            throw e
        }
    }

    suspend fun forceFullSync(): SyncResult {
        var success = 0
        var failed = 0

        db.contactDao().getAllContactsOnce().forEach {
            runCatching { syncContactSuspend(it) }
                .onSuccess { success++ }
                .onFailure { failed++ }
        }
        db.contactMemoryDao().getAllOnce().forEach {
            runCatching { syncContactMemorySuspend(it) }
                .onSuccess { success++ }
                .onFailure { failed++ }
        }
        db.behaviorProfileDao().getAllOnce().forEach {
            runCatching { syncBehaviorProfileSuspend(it) }
                .onSuccess { success++ }
                .onFailure { failed++ }
        }
        db.knownRelationDao().getAllOnce().forEach {
            runCatching { syncKnownRelationSuspend(it) }
                .onSuccess { success++ }
                .onFailure { failed++ }
        }
        db.messageDao().getAllOnce().forEach {
            runCatching { syncMessageSuspend(it) }
                .onSuccess { success++ }
                .onFailure { failed++ }
        }
        db.personalMemoryDao().getPersonalMemory()?.let { pm ->
            runCatching { syncPersonalMemorySuspend(pm) }
                .onSuccess { success++ }
                .onFailure { failed++ }
        }

        return SyncResult(success, failed)
    }

    // --- Fire-and-forget background sync calls ---

    fun syncPersonalMemory(memory: PersonalMemory) {
        scope.launch {
            try {
                client.from("personal_memory").upsert(memory.toRow())
            } catch (e: Exception) {
                logSyncError("personal_memory", null, e)
            }
        }
    }

    fun syncContact(contact: Contact) {
        scope.launch {
            try {
                client.from("contacts").upsert(contact.toRow())
            } catch (e: Exception) {
                logSyncError("contacts", contact.contactId, e)
            }
        }
    }

    fun syncContactMemory(memory: ContactMemory) {
        scope.launch {
            try {
                client.from("contact_memories").upsert(memory.toRow())
            } catch (e: Exception) {
                logSyncError("contact_memories", memory.contactId, e)
            }
        }
    }

    fun syncBehaviorProfile(profile: BehaviorProfile) {
        scope.launch {
            try {
                client.from("behavior_profiles").upsert(profile.toRow())
            } catch (e: Exception) {
                logSyncError("behavior_profiles", profile.contactId, e)
            }
        }
    }

    fun syncKnownRelation(relation: KnownRelation) {
        scope.launch {
            try {
                client.from("known_relations").upsert(relation.toRow())
            } catch (e: Exception) {
                logSyncError("known_relations", null, e)
            }
        }
    }

    fun syncMessage(message: Message) {
        scope.launch {
            try {
                client.from("messages").upsert(message.toRow()) {
                    onConflict = "user_id,contact_id,local_id"
                }
            } catch (e: Exception) {
                logSyncError("messages", message.contactId, e)
            }
        }
    }

    fun syncAppSettings(ollamaUrl: String, selectedModel: String, autoReplyEnabled: Boolean) {
        scope.launch {
            try {
                client.from("app_settings").upsert(
                    AppSettingsRow(
                        id = 1,
                        ollamaUrl = ollamaUrl,
                        selectedModel = selectedModel,
                        autoReplyEnabled = autoReplyEnabled
                    )
                )
            } catch (e: Exception) {
                logSyncError("app_settings", null, e)
            }
        }
    }

    // --- Restore Data on Login / Launch ---

    suspend fun restoreAllDataFromCloud(context: android.content.Context? = null) {
        try {
            // 1. Personal Memory
            val pmRows = client.from("personal_memory").select().decodeList<PersonalMemoryRow>()
            pmRows.firstOrNull()?.let {
                db.personalMemoryDao().insertOrUpdatePersonalMemory(it.toEntity())
            }

            // 2. Contacts
            val contactRows = client.from("contacts").select().decodeList<ContactRow>()
            for (row in contactRows) {
                db.contactDao().insertOrUpdateContact(row.toEntity())
            }

            // 3. Contact Memories
            val memoryRows = client.from("contact_memories").select().decodeList<ContactMemoryRow>()
            for (row in memoryRows) {
                db.contactMemoryDao().insertOrUpdateMemory(row.toEntity())
            }

            // 4. Behavior Profiles
            val profileRows = client.from("behavior_profiles").select().decodeList<BehaviorProfileRow>()
            for (row in profileRows) {
                db.behaviorProfileDao().insertOrUpdateProfile(row.toEntity())
            }

            // 5. Known Relations
            val relationRows = client.from("known_relations").select().decodeList<KnownRelationRow>()
            for (row in relationRows) {
                db.knownRelationDao().insertOrUpdateRelation(row.toEntity())
            }

            // 6. Messages
            val messageRows = client.from("messages").select().decodeList<MessageRow>()
            for (row in messageRows) {
                db.messageDao().insertMessage(row.toEntity())
            }

            // 7. App Settings
            if (context != null) {
                try {
                    val settingsRows = client.from("app_settings").select().decodeList<AppSettingsRow>()
                    settingsRows.firstOrNull()?.let { s ->
                        val prefs = com.example.data.pref.UserPreferencesRepository.getInstance(context)
                        prefs.updateOllamaUrl(s.ollamaUrl)
                        prefs.updateSelectedModel(s.selectedModel)
                        prefs.updateAutoReplyEnabled(s.autoReplyEnabled)
                        Log.i("SupabaseSync", "Restored app settings: Ollama URL=${s.ollamaUrl}")
                    }
                } catch (e: Exception) {
                    logSyncError("app_settings_restore", null, e)
                }
            }

            Log.i("SupabaseSync", "Cloud restore completed successfully!")
        } catch (e: Exception) {
            logSyncError("all_tables_restore", null, e)
        }
    }
}

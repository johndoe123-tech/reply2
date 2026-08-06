package com.example.data.supabase

import android.util.Log
import com.example.data.db.*
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SupabaseSyncRepository(private val db: AppDatabase) {

    private val client get() = SupabaseClientProvider.client
    private val scope = CoroutineScope(Dispatchers.IO)

    // --- Fire-and-forget background sync calls ---

    fun syncPersonalMemory(memory: PersonalMemory) {
        scope.launch {
            try {
                client.from("personal_memory").upsert(memory.toRow())
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Error syncing personal memory: ${e.message}")
            }
        }
    }

    fun syncContact(contact: Contact) {
        scope.launch {
            try {
                client.from("contacts").upsert(contact.toRow())
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Error syncing contact: ${e.message}")
            }
        }
    }

    fun syncContactMemory(memory: ContactMemory) {
        scope.launch {
            try {
                client.from("contact_memories").upsert(memory.toRow())
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Error syncing contact memory: ${e.message}")
            }
        }
    }

    fun syncBehaviorProfile(profile: BehaviorProfile) {
        scope.launch {
            try {
                client.from("behavior_profiles").upsert(profile.toRow())
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Error syncing behavior profile: ${e.message}")
            }
        }
    }

    fun syncKnownRelation(relation: KnownRelation) {
        scope.launch {
            try {
                client.from("known_relations").upsert(relation.toRow())
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Error syncing known relation: ${e.message}")
            }
        }
    }

    fun syncMessage(message: Message) {
        scope.launch {
            try {
                client.from("messages").upsert(message.toRow())
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Error syncing message: ${e.message}")
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
                Log.e("SupabaseSync", "Error syncing app settings: ${e.message}")
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
                    Log.e("SupabaseSync", "Failed to restore app settings or table missing: ${e.message}")
                }
            }

            Log.i("SupabaseSync", "Cloud restore completed successfully!")
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to restore data from Supabase: ${e.message}")
        }
    }
}

package com.example.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "autoreply_settings")

data class UserPreferences(
    val ollamaUrl: String,
    val selectedModel: String,
    val autoReplyEnabled: Boolean,
    val connectionStatus: String
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val OLLAMA_URL = stringPreferencesKey("ollama_url")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")
        val CONNECTION_STATUS = stringPreferencesKey("connection_status")
        val LAST_USER_ID = stringPreferencesKey("last_user_id")
    }

    val lastUserIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_USER_ID]
    }

    suspend fun updateLastUserId(userId: String?) {
        context.dataStore.edit { preferences ->
            if (userId == null) {
                preferences.remove(PreferencesKeys.LAST_USER_ID)
            } else {
                preferences[PreferencesKeys.LAST_USER_ID] = userId
            }
        }
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            UserPreferences(
                ollamaUrl = preferences[PreferencesKeys.OLLAMA_URL] ?: "http://192.168.1.5:11434",
                selectedModel = preferences[PreferencesKeys.SELECTED_MODEL] ?: "llama3",
                autoReplyEnabled = preferences[PreferencesKeys.AUTO_REPLY_ENABLED] ?: true,
                connectionStatus = preferences[PreferencesKeys.CONNECTION_STATUS] ?: "Not Tested"
            )
        }

    suspend fun updateOllamaUrl(url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.OLLAMA_URL] = cleanUrl
        }
    }

    suspend fun updateSelectedModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL] = model
        }
    }

    suspend fun updateAutoReplyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_REPLY_ENABLED] = enabled
        }
    }

    suspend fun updateConnectionStatus(status: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONNECTION_STATUS] = status
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

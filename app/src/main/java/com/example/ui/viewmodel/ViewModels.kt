package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.*
import com.example.data.supabase.SupabaseSyncRepository
import com.example.data.ollama.OllamaModelInfo
import com.example.data.ollama.OllamaRepository
import com.example.data.pref.UserPreferencesRepository
import com.example.domain.DecisionEngine
import com.example.domain.DecisionResult
import com.example.service.AutoReplyForegroundService
import com.example.service.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// -------------------------------------------------------------
// Home ViewModel
// -------------------------------------------------------------
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefsRepo = UserPreferencesRepository.getInstance(application)
    private val ollamaRepo = OllamaRepository()
    private val decisionEngine = DecisionEngine(db, ollamaRepo)

    val userPreferences = prefsRepo.userPreferencesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    val recentActivity: StateFlow<List<Message>> = db.messageDao().getRecentActivityFeed(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isNotificationAccessGranted = MutableStateFlow(false)
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    private val _simulatingState = MutableStateFlow<String?>(null)
    val simulatingState: StateFlow<String?> = _simulatingState.asStateFlow()

    init {
        checkNotificationAccess()
    }

    fun checkNotificationAccess() {
        val context = getApplication<Application>()
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val isGranted = flat != null && flat.contains(context.packageName)
        _isNotificationAccessGranted.value = isGranted
    }

    fun toggleAutoReply(enable: Boolean) {
        val context = getApplication<Application>()
        if (enable) {
            AutoReplyForegroundService.startService(context)
        } else {
            AutoReplyForegroundService.stopService(context)
        }
    }

    fun triggerSimulationTest(contactName: String, testMessage: String) {
        viewModelScope.launch {
            _simulatingState.value = "Processing simulation with Ollama..."
            try {
                val prefs = prefsRepo.userPreferencesFlow.first()
                val contactId = contactName.ifBlank { "Test Contact" }

                // Upsert Contact
                var contact = db.contactDao().getContactById(contactId)
                if (contact == null) {
                    contact = Contact(
                        contactId = contactId,
                        phoneNumber = "+15550199",
                        displayName = contactId,
                        relationshipLabel = "Friend"
                    )
                    db.contactDao().insertOrUpdateContact(contact)
                }

                // Insert incoming message
                val incomingMsg = Message(
                    contactId = contactId,
                    sender = "them",
                    text = testMessage,
                    timestamp = System.currentTimeMillis(),
                    wasAutoReplied = false
                )
                db.messageDao().insertMessage(incomingMsg)

                // Decision Engine Evaluation
                val decision = decisionEngine.processIncomingMessage(
                    contactId = contactId,
                    incomingText = testMessage,
                    ollamaUrl = prefs.ollamaUrl,
                    modelName = prefs.selectedModel
                )

                when (decision) {
                    is DecisionResult.AutoReply -> {
                        val replyMsg = Message(
                            contactId = contactId,
                            sender = "me",
                            text = decision.replyText,
                            timestamp = System.currentTimeMillis(),
                            wasAutoReplied = true
                        )
                        db.messageDao().insertMessage(replyMsg)
                        _simulatingState.value = "Auto-Replied: \"${decision.replyText}\""

                        NotificationHelper.postAutoReplyNotification(
                            context = getApplication(),
                            contactName = contactId,
                            incomingText = testMessage,
                            replyText = decision.replyText
                        )
                    }
                    is DecisionResult.NotifyOnly -> {
                        _simulatingState.value = "Notify-Only Alert: ${decision.reason}"

                        NotificationHelper.postUserAlertNotification(
                            context = getApplication(),
                            contactName = contactId,
                            incomingText = testMessage,
                            reason = decision.reason
                        )
                    }
                }
            } catch (e: Exception) {
                _simulatingState.value = "Simulation Error: ${e.localizedMessage}"
            }
        }
    }

    fun clearSimulationState() {
        _simulatingState.value = null
    }
}

// -------------------------------------------------------------
// Settings ViewModel
// -------------------------------------------------------------
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefsRepo = UserPreferencesRepository.getInstance(application)
    private val ollamaRepo = OllamaRepository()

    val userPreferences = prefsRepo.userPreferencesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    val personalMemory = db.personalMemoryDao().getPersonalMemoryFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    private val _availableModels = MutableStateFlow<List<OllamaModelInfo>>(emptyList())
    val availableModels: StateFlow<List<OllamaModelInfo>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun updateOllamaUrl(url: String) {
        viewModelScope.launch { prefsRepo.updateOllamaUrl(url) }
    }

    fun selectModel(model: String) {
        viewModelScope.launch { prefsRepo.updateSelectedModel(model) }
    }

    fun updateAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateAutoReplyEnabled(enabled) }
    }

    fun fetchModels() {
        viewModelScope.launch {
            _isLoadingModels.value = true
            val url = userPreferences.value?.ollamaUrl ?: "http://192.168.1.5:11434"
            val result = ollamaRepo.fetchModels(url)
            result.onSuccess { models ->
                _availableModels.value = models
                _statusMessage.value = "Fetched ${models.size} models from Ollama."
                prefsRepo.updateConnectionStatus("Connected (${models.size} models)")
            }.onFailure { error ->
                val msg = error.localizedMessage ?: ""
                if (msg.contains("refused", ignoreCase = true) ||
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("unreachable", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true)
                ) {
                    _statusMessage.value = "Can't reach $url — check the Ollama URL and that your phone and Ollama server are on the same network."
                } else {
                    _statusMessage.value = "Failed to fetch models: $msg"
                }
                prefsRepo.updateConnectionStatus("Connection Failed")
            }
            _isLoadingModels.value = false
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val url = userPreferences.value?.ollamaUrl ?: "http://192.168.1.5:11434"
            val result = ollamaRepo.testConnection(url)
            result.onSuccess { success ->
                if (success) {
                    _statusMessage.value = "Connection Successful!"
                    prefsRepo.updateConnectionStatus("Connected")
                } else {
                    _statusMessage.value = "Server returned no models."
                    prefsRepo.updateConnectionStatus("No models found")
                }
            }.onFailure { error ->
                val msg = error.localizedMessage ?: ""
                if (msg.contains("refused", ignoreCase = true) ||
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("unreachable", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true)
                ) {
                    _statusMessage.value = "Can't reach $url — check the Ollama URL and that your phone and Ollama server are on the same network."
                } else {
                    _statusMessage.value = "Connection Failed: $msg"
                }
                prefsRepo.updateConnectionStatus("Offline")
            }
        }
    }

    fun savePersonalMemory(aboutMe: String, systemPrompt: String, sharingRules: String) {
        viewModelScope.launch {
            val current = personalMemory.value ?: PersonalMemory()
            val updated = current.copy(
                aboutMe = aboutMe,
                globalSystemPrompt = systemPrompt,
                sharingRules = sharingRules
            )
            db.personalMemoryDao().insertOrUpdatePersonalMemory(updated)
            SupabaseSyncRepository(db).syncPersonalMemory(updated)
            _statusMessage.value = "Personal memory updated!"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}

// -------------------------------------------------------------
// Contacts ViewModel
// -------------------------------------------------------------
class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    val contacts: StateFlow<List<Contact>> = db.contactDao().getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateRelationshipLabel(contactId: String, newLabel: String) {
        viewModelScope.launch {
            db.contactDao().updateRelationshipLabel(contactId, newLabel)
        }
    }

    fun addNewContact(
        name: String,
        phoneNumber: String,
        relationshipLabel: String,
        notes: String,
        gender: String? = null,
        doNotRespond: Boolean = false,
        allowOtherLanguages: Boolean = true
    ) {
        viewModelScope.launch {
            val contactId = name.trim()
            if (contactId.isBlank()) return@launch

            val cleanPhone = phoneNumber.trim()
            val cleanLabel = relationshipLabel.trim().ifBlank { "Friend" }

            val contact = Contact(
                contactId = contactId,
                phoneNumber = if (cleanPhone.isBlank()) null else cleanPhone,
                displayName = contactId,
                relationshipLabel = cleanLabel,
                isGroup = false,
                doNotRespond = doNotRespond,
                gender = gender?.ifBlank { null },
                allowOtherLanguages = allowOtherLanguages,
                lastMessageAt = System.currentTimeMillis()
            )
            db.contactDao().insertOrUpdateContact(contact)
            SupabaseSyncRepository(db).syncContact(contact)

            if (cleanPhone.isNotBlank()) {
                val relation = KnownRelation(
                    phoneNumber = cleanPhone,
                    name = contactId,
                    relationshipLabel = cleanLabel
                )
                db.knownRelationDao().insertOrUpdateRelation(relation)
                SupabaseSyncRepository(db).syncKnownRelation(relation)
            }

            if (notes.isNotBlank()) {
                val memory = ContactMemory(
                    contactId = contactId,
                    summary = "Manually created contact. Notes: $notes",
                    importantFacts = notes,
                    lastUpdated = System.currentTimeMillis()
                )
                db.contactMemoryDao().insertOrUpdateMemory(memory)
                SupabaseSyncRepository(db).syncContactMemory(memory)
            }

            val behavior = BehaviorProfile(
                contactId = contactId,
                toneFormalCasual = if (cleanLabel.lowercase().contains("boss") || cleanLabel.lowercase().contains("client")) "formal" else "casual",
                avgMessageLength = 25,
                usesEmojis = true,
                commonGreetings = "Hey!",
                humorLevel = "medium",
                preferredLanguage = "English"
            )
            db.behaviorProfileDao().insertOrUpdateProfile(behavior)
            SupabaseSyncRepository(db).syncBehaviorProfile(behavior)
        }
    }

    fun importMultipleContacts(
        deviceContacts: List<com.example.domain.DeviceContact>,
        defaultRelationship: String = "Friend"
    ) {
        viewModelScope.launch {
            val syncRepo = SupabaseSyncRepository(db)
            deviceContacts.forEach { devContact ->
                val contactId = devContact.name.trim().ifBlank { devContact.phoneNumber?.trim() ?: "Unknown Contact" }
                val cleanPhone = devContact.phoneNumber?.trim()
                val normalizedPhone = cleanPhone?.replace(Regex("[^0-9+]"), "")

                // Preserve existing contact configuration if present
                val existingContact = db.contactDao().getContactById(contactId)
                val contact = Contact(
                    contactId = contactId,
                    phoneNumber = if (!cleanPhone.isNullOrBlank()) cleanPhone else existingContact?.phoneNumber,
                    displayName = contactId,
                    relationshipLabel = existingContact?.relationshipLabel ?: defaultRelationship,
                    isGroup = false,
                    doNotRespond = existingContact?.doNotRespond ?: false,
                    gender = existingContact?.gender,
                    allowOtherLanguages = existingContact?.allowOtherLanguages ?: true,
                    lastMessageAt = existingContact?.lastMessageAt ?: System.currentTimeMillis()
                )
                db.contactDao().insertOrUpdateContact(contact)
                syncRepo.syncContact(contact)

                if (!cleanPhone.isNullOrBlank()) {
                    val relation = KnownRelation(
                        phoneNumber = cleanPhone,
                        name = contactId,
                        relationshipLabel = contact.relationshipLabel
                    )
                    db.knownRelationDao().insertOrUpdateRelation(relation)
                    syncRepo.syncKnownRelation(relation)

                    if (!normalizedPhone.isNullOrBlank() && normalizedPhone != cleanPhone) {
                        val normRelation = KnownRelation(
                            phoneNumber = normalizedPhone,
                            name = contactId,
                            relationshipLabel = contact.relationshipLabel
                        )
                        db.knownRelationDao().insertOrUpdateRelation(normRelation)
                        syncRepo.syncKnownRelation(normRelation)
                    }
                }

                val existingMemory = db.contactMemoryDao().getMemoryForContact(contactId)
                if (existingMemory == null) {
                    val memory = ContactMemory(
                        contactId = contactId,
                        summary = "Imported contact from device/SIM: $contactId.",
                        importantFacts = "Phone: ${cleanPhone ?: "None"}. Imported into managed contacts memory.",
                        lastUpdated = System.currentTimeMillis()
                    )
                    db.contactMemoryDao().insertOrUpdateMemory(memory)
                    syncRepo.syncContactMemory(memory)
                }

                val existingBehavior = db.behaviorProfileDao().getProfileForContact(contactId)
                if (existingBehavior == null) {
                    val behavior = BehaviorProfile(
                        contactId = contactId,
                        toneFormalCasual = "casual",
                        avgMessageLength = 25,
                        usesEmojis = true,
                        commonGreetings = "Hey!",
                        humorLevel = "medium",
                        preferredLanguage = "English"
                    )
                    db.behaviorProfileDao().insertOrUpdateProfile(behavior)
                    syncRepo.syncBehaviorProfile(behavior)
                }
            }
        }
    }

    fun fetchDeviceContacts(): List<com.example.domain.DeviceContact> {
        return com.example.domain.ContactResolver(getApplication()).fetchAllDeviceContacts()
    }
}

// -------------------------------------------------------------
// Contact Detail ViewModel
// -------------------------------------------------------------
class ContactDetailViewModel(
    application: Application,
    val contactId: String
) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    val contact = db.contactDao().getContactFlow(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val contactMemory = db.contactMemoryDao().getMemoryFlow(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val behaviorProfile = db.behaviorProfileDao().getProfileFlow(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> = db.messageDao().getMessagesForContactFlow(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveContactDetails(
        relationshipLabel: String,
        summary: String,
        importantFacts: String,
        tone: String,
        humor: String,
        language: String,
        doNotRespond: Boolean = false,
        gender: String? = null,
        allowOtherLanguages: Boolean = true
    ) {
        viewModelScope.launch {
            db.contactDao().updateContactControls(contactId, relationshipLabel, doNotRespond, gender, allowOtherLanguages)

            val existingMem = contactMemory.value ?: ContactMemory(contactId = contactId)
            val updatedMem = existingMem.copy(
                summary = summary,
                importantFacts = importantFacts,
                lastUpdated = System.currentTimeMillis()
            )
            db.contactMemoryDao().insertOrUpdateMemory(updatedMem)
            SupabaseSyncRepository(db).syncContactMemory(updatedMem)

            val existingProf = behaviorProfile.value ?: BehaviorProfile(contactId = contactId)
            val updatedProf = existingProf.copy(
                toneFormalCasual = tone,
                humorLevel = humor,
                preferredLanguage = language
            )
            db.behaviorProfileDao().insertOrUpdateProfile(updatedProf)
            SupabaseSyncRepository(db).syncBehaviorProfile(updatedProf)
        }
    }

    fun clearChatHistoryOnly() {
        viewModelScope.launch {
            db.messageDao().deleteMessagesForContact(contactId)
        }
    }

    fun deleteContactHistory() {
        viewModelScope.launch {
            db.messageDao().deleteMessagesForContact(contactId)
            db.contactMemoryDao().deleteMemory(contactId)
            db.behaviorProfileDao().deleteProfile(contactId)
            db.contactDao().deleteContact(contactId)
        }
    }
}

// -------------------------------------------------------------
// Known Relations ViewModel ("Family/Friend Directory")
// -------------------------------------------------------------
class KnownRelationsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    val relations: StateFlow<List<KnownRelation>> = db.knownRelationDao().getAllKnownRelations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addOrUpdateRelation(phoneNumber: String, name: String, label: String) {
        viewModelScope.launch {
            val rel = KnownRelation(phoneNumber = phoneNumber.trim(), name = name.trim(), relationshipLabel = label.trim())
            db.knownRelationDao().insertOrUpdateRelation(rel)
            SupabaseSyncRepository(db).syncKnownRelation(rel)
        }
    }

    fun deleteRelation(phoneNumber: String) {
        viewModelScope.launch {
            db.knownRelationDao().deleteRelation(phoneNumber)
        }
    }
}

// -------------------------------------------------------------
// Memory Manager ViewModel
// -------------------------------------------------------------
class MemoryManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    val personalMemory = db.personalMemoryDao().getPersonalMemoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val contactMemories = db.contactMemoryDao().getAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearContactMemory(contactId: String) {
        viewModelScope.launch {
            db.contactMemoryDao().deleteMemory(contactId)
            db.behaviorProfileDao().deleteProfile(contactId)
        }
    }
}

// -------------------------------------------------------------
// Profile ViewModel
// -------------------------------------------------------------
data class UserProfileStats(
    val totalContacts: Int = 0,
    val totalMemories: Int = 0,
    val knownRelations: Int = 0,
    val autoRepliedCount: Int = 0
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val syncRepo = SupabaseSyncRepository(db)

    val personalMemory = db.personalMemoryDao().getPersonalMemoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stats: StateFlow<UserProfileStats> = combine(
        db.contactDao().getAllContacts(),
        db.contactMemoryDao().getAllMemories(),
        db.knownRelationDao().getAllKnownRelations(),
        db.messageDao().getRecentActivityFeed(500)
    ) { contacts, memories, relations, messages ->
        UserProfileStats(
            totalContacts = contacts.size,
            totalMemories = memories.size,
            knownRelations = relations.size,
            autoRepliedCount = messages.count { it.wasAutoReplied }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfileStats())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun savePersonalMemory(aboutMe: String, systemPrompt: String, sharingRules: String) {
        viewModelScope.launch {
            val current = personalMemory.value ?: PersonalMemory()
            val updated = current.copy(
                aboutMe = aboutMe,
                globalSystemPrompt = systemPrompt,
                sharingRules = sharingRules
            )
            db.personalMemoryDao().insertOrUpdatePersonalMemory(updated)
            syncRepo.syncPersonalMemory(updated)
            _statusMessage.value = "Profile memory & persona updated!"
        }
    }

    fun triggerCloudRestore() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                syncRepo.restoreAllDataFromCloud()
                _statusMessage.value = "Cloud data synced successfully!"
            } catch (e: Exception) {
                _statusMessage.value = "Sync error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}

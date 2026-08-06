package com.example.data.supabase

import com.example.data.db.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppSettingsRow(
    val id: Int = 1,
    @SerialName("ollama_url") val ollamaUrl: String? = "http://192.168.1.5:11434",
    @SerialName("selected_model") val selectedModel: String? = "llama3",
    @SerialName("auto_reply_enabled") val autoReplyEnabled: Boolean? = true
)

@Serializable
data class PersonalMemoryRow(
    val id: Int = 1,
    @SerialName("about_me") val aboutMe: String? = "",
    @SerialName("global_system_prompt") val globalSystemPrompt: String? = "",
    @SerialName("sharing_rules") val sharingRules: String? = ""
)

fun PersonalMemory.toRow() = PersonalMemoryRow(
    id = id,
    aboutMe = aboutMe,
    globalSystemPrompt = globalSystemPrompt,
    sharingRules = sharingRules
)

fun PersonalMemoryRow.toEntity() = PersonalMemory(
    id = id,
    aboutMe = aboutMe ?: "",
    globalSystemPrompt = globalSystemPrompt ?: "",
    sharingRules = sharingRules ?: ""
)

@Serializable
data class ContactRow(
    @SerialName("contact_id") val contactId: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("relationship_label") val relationshipLabel: String? = "Friend",
    @SerialName("is_group") val isGroup: Boolean? = false,
    @SerialName("do_not_respond") val doNotRespond: Boolean? = false,
    val gender: String? = null,
    @SerialName("allow_other_languages") val allowOtherLanguages: Boolean? = true,
    @SerialName("created_at") val createdAt: Long? = System.currentTimeMillis(),
    @SerialName("last_message_at") val lastMessageAt: Long? = System.currentTimeMillis()
)

fun Contact.toRow() = ContactRow(
    contactId = contactId,
    phoneNumber = phoneNumber,
    displayName = displayName,
    relationshipLabel = relationshipLabel,
    isGroup = isGroup,
    doNotRespond = doNotRespond,
    gender = gender,
    allowOtherLanguages = allowOtherLanguages,
    createdAt = createdAt,
    lastMessageAt = lastMessageAt
)

fun ContactRow.toEntity() = Contact(
    contactId = contactId,
    phoneNumber = phoneNumber,
    displayName = displayName,
    relationshipLabel = relationshipLabel ?: "Friend",
    isGroup = isGroup ?: false,
    doNotRespond = doNotRespond ?: false,
    gender = gender,
    allowOtherLanguages = allowOtherLanguages ?: true,
    createdAt = createdAt ?: System.currentTimeMillis(),
    lastMessageAt = lastMessageAt ?: System.currentTimeMillis()
)

@Serializable
data class ContactMemoryRow(
    @SerialName("contact_id") val contactId: String,
    val summary: String? = "",
    val nicknames: String? = null,
    @SerialName("shared_interests") val sharedInterests: String? = null,
    @SerialName("important_facts") val importantFacts: String? = null,
    @SerialName("last_updated") val lastUpdated: Long? = System.currentTimeMillis()
)

fun ContactMemory.toRow() = ContactMemoryRow(
    contactId = contactId,
    summary = summary,
    nicknames = nicknames,
    sharedInterests = sharedInterests,
    importantFacts = importantFacts,
    lastUpdated = lastUpdated
)

fun ContactMemoryRow.toEntity() = ContactMemory(
    contactId = contactId,
    summary = summary ?: "",
    nicknames = nicknames,
    sharedInterests = sharedInterests,
    importantFacts = importantFacts,
    lastUpdated = lastUpdated ?: System.currentTimeMillis()
)

@Serializable
data class BehaviorProfileRow(
    @SerialName("contact_id") val contactId: String,
    @SerialName("tone_formal_casual") val toneFormalCasual: String? = "casual",
    @SerialName("avg_message_length") val avgMessageLength: Int? = 25,
    @SerialName("uses_emojis") val usesEmojis: Boolean? = true,
    @SerialName("common_greetings") val commonGreetings: String? = "Hey!",
    @SerialName("humor_level") val humorLevel: String? = "medium",
    @SerialName("preferred_language") val preferredLanguage: String? = "English"
)

fun BehaviorProfile.toRow() = BehaviorProfileRow(
    contactId = contactId,
    toneFormalCasual = toneFormalCasual,
    avgMessageLength = avgMessageLength,
    usesEmojis = usesEmojis,
    commonGreetings = commonGreetings,
    humorLevel = humorLevel,
    preferredLanguage = preferredLanguage
)

fun BehaviorProfileRow.toEntity() = BehaviorProfile(
    contactId = contactId,
    toneFormalCasual = toneFormalCasual ?: "casual",
    avgMessageLength = avgMessageLength ?: 25,
    usesEmojis = usesEmojis ?: true,
    commonGreetings = commonGreetings ?: "Hey!",
    humorLevel = humorLevel ?: "medium",
    preferredLanguage = preferredLanguage ?: "English"
)

@Serializable
data class KnownRelationRow(
    @SerialName("phone_number") val phoneNumber: String,
    val name: String? = "",
    @SerialName("relationship_label") val relationshipLabel: String? = "Friend"
)

fun KnownRelation.toRow() = KnownRelationRow(
    phoneNumber = phoneNumber,
    name = name,
    relationshipLabel = relationshipLabel
)

fun KnownRelationRow.toEntity() = KnownRelation(
    phoneNumber = phoneNumber,
    name = name ?: "",
    relationshipLabel = relationshipLabel ?: "Friend"
)

@Serializable
data class MessageRow(
    val id: Long? = null,
    @SerialName("local_id") val localId: Long? = null,
    @SerialName("contact_id") val contactId: String,
    val sender: String? = "",
    val text: String? = "",
    val timestamp: Long? = System.currentTimeMillis(),
    @SerialName("was_auto_replied") val wasAutoReplied: Boolean? = false
)

fun Message.toRow() = MessageRow(
    id = null,
    localId = id,
    contactId = contactId,
    sender = sender,
    text = text,
    timestamp = timestamp,
    wasAutoReplied = wasAutoReplied
)

fun MessageRow.toEntity() = Message(
    id = localId ?: id ?: 0L,
    contactId = contactId,
    sender = sender ?: "",
    text = text ?: "",
    timestamp = timestamp ?: System.currentTimeMillis(),
    wasAutoReplied = wasAutoReplied ?: false
)

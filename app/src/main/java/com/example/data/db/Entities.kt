package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val contactId: String,
    val phoneNumber: String?,
    val displayName: String?,
    val relationshipLabel: String = "Friend",
    val isGroup: Boolean = false,
    val doNotRespond: Boolean = false,
    val gender: String? = null, // "male" | "female" | null
    val allowOtherLanguages: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "contact_memories")
data class ContactMemory(
    @PrimaryKey val contactId: String,
    val summary: String = "",
    val nicknames: String? = null,
    val sharedInterests: String? = null,
    val importantFacts: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "behavior_profiles")
data class BehaviorProfile(
    @PrimaryKey val contactId: String,
    val toneFormalCasual: String = "casual",
    val avgMessageLength: Int = 25,
    val usesEmojis: Boolean = true,
    val commonGreetings: String = "Hey!",
    val humorLevel: String = "medium",
    val preferredLanguage: String = "English"
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: String,
    val sender: String, // "them" or "me"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val wasAutoReplied: Boolean = false
)

@Entity(tableName = "personal_memory")
data class PersonalMemory(
    @PrimaryKey val id: Int = 1,
    val aboutMe: String = "I am a software engineer living in Seattle. I like coding, coffee, and basketball.",
    val globalSystemPrompt: String = "You are an AI assistant replying on WhatsApp on behalf of the user. Keep replies concise, natural, and matching the tone of the sender. Never make up plans or commitments without user confirmation.",
    val sharingRules: String = "OK to share: my general location city, public hobbies. DO NOT share: exact schedule, address, financial info, or details from other people's chats."
)

@Entity(tableName = "known_relations")
data class KnownRelation(
    @PrimaryKey val phoneNumber: String,
    val name: String,
    val relationshipLabel: String
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val contactId: String? = null,
    val eventType: String, // "MESSAGE_RECEIVED" | "AUTO_REPLIED" | "NOTIFIED_USER" | "ERROR" | "LISTENER_CONNECTED" | "LISTENER_DISCONNECTED" | "HEALTH_CHECK"
    val detail: String
)


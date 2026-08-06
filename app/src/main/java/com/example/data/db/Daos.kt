package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY lastMessageAt DESC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE contactId = :contactId LIMIT 1")
    suspend fun getContactById(contactId: String): Contact?

    @Query("SELECT * FROM contacts WHERE contactId = :contactId LIMIT 1")
    fun getContactFlow(contactId: String): Flow<Contact?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContact(contact: Contact)

    @Query("UPDATE contacts SET lastMessageAt = :timestamp WHERE contactId = :contactId")
    suspend fun updateLastMessageAt(contactId: String, timestamp: Long)

    @Query("UPDATE contacts SET relationshipLabel = :label WHERE contactId = :contactId")
    suspend fun updateRelationshipLabel(contactId: String, label: String)

    @Query("UPDATE contacts SET relationshipLabel = :label, doNotRespond = :doNotRespond, gender = :gender, allowOtherLanguages = :allowOtherLanguages WHERE contactId = :contactId")
    suspend fun updateContactControls(contactId: String, label: String, doNotRespond: Boolean, gender: String?, allowOtherLanguages: Boolean = true)

    @Query("DELETE FROM contacts WHERE contactId = :contactId")
    suspend fun deleteContact(contactId: String)
}

@Dao
interface ContactMemoryDao {
    @Query("SELECT * FROM contact_memories WHERE contactId = :contactId LIMIT 1")
    suspend fun getMemoryForContact(contactId: String): ContactMemory?

    @Query("SELECT * FROM contact_memories WHERE contactId = :contactId LIMIT 1")
    fun getMemoryFlow(contactId: String): Flow<ContactMemory?>

    @Query("SELECT * FROM contact_memories")
    fun getAllMemories(): Flow<List<ContactMemory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMemory(memory: ContactMemory)

    @Query("DELETE FROM contact_memories WHERE contactId = :contactId")
    suspend fun deleteMemory(contactId: String)
}

@Dao
interface BehaviorProfileDao {
    @Query("SELECT * FROM behavior_profiles WHERE contactId = :contactId LIMIT 1")
    suspend fun getProfileForContact(contactId: String): BehaviorProfile?

    @Query("SELECT * FROM behavior_profiles WHERE contactId = :contactId LIMIT 1")
    fun getProfileFlow(contactId: String): Flow<BehaviorProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: BehaviorProfile)

    @Query("DELETE FROM behavior_profiles WHERE contactId = :contactId")
    suspend fun deleteProfile(contactId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun getMessagesForContactFlow(contactId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(contactId: String, limit: Int = 15): List<Message>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentActivityFeed(limit: Int = 20): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Query("DELETE FROM messages WHERE contactId = :contactId")
    suspend fun deleteMessagesForContact(contactId: String)
}

@Dao
interface PersonalMemoryDao {
    @Query("SELECT * FROM personal_memory WHERE id = 1 LIMIT 1")
    fun getPersonalMemoryFlow(): Flow<PersonalMemory?>

    @Query("SELECT * FROM personal_memory WHERE id = 1 LIMIT 1")
    suspend fun getPersonalMemory(): PersonalMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePersonalMemory(memory: PersonalMemory)
}

@Dao
interface KnownRelationDao {
    @Query("SELECT * FROM known_relations ORDER BY name ASC")
    fun getAllKnownRelations(): Flow<List<KnownRelation>>

    @Query("SELECT * FROM known_relations WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getByPhoneNumber(phoneNumber: String): KnownRelation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRelation(relation: KnownRelation)

    @Query("DELETE FROM known_relations WHERE phoneNumber = :phoneNumber")
    suspend fun deleteRelation(phoneNumber: String)
}

@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ActivityLogEntry)

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 50): Flow<List<ActivityLogEntry>>

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 50): List<ActivityLogEntry>

    @Query("DELETE FROM activity_logs")
    suspend fun clearLogs()
}


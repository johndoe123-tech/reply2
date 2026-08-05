package com.example.domain

import com.example.data.db.*
import com.example.data.supabase.SupabaseSyncRepository
import com.example.data.ollama.OllamaChatMessage
import com.example.data.ollama.OllamaRepository

class MemoryUpdater(
    private val db: AppDatabase,
    private val ollamaRepository: OllamaRepository
) {
    /**
     * Updates running memory summary and updates heuristic behavior profile.
     */
    suspend fun updateMemoryForContact(
        contactId: String,
        ollamaUrl: String,
        modelName: String
    ) {
        val messages = db.messageDao().getRecentMessages(contactId, limit = 20).reversed()
        if (messages.size < 3) return // wait until enough context exists

        val existingMemory = db.contactMemoryDao().getMemoryForContact(contactId)
            ?: ContactMemory(contactId = contactId)

        // 1. Heuristic updates for BehaviorProfile
        var totalLength = 0
        var emojiCount = 0
        val emojisRegex = Regex("[\uD83C-\uDBFF\uDC00-\uDFFF]")

        for (m in messages) {
            totalLength += m.text.length
            if (emojisRegex.containsMatchIn(m.text)) emojiCount++
        }
        val avgLen = (totalLength / messages.size).coerceAtLeast(10)
        val usesEmojis = emojiCount > 0

        val existingProfile = db.behaviorProfileDao().getProfileForContact(contactId)
            ?: BehaviorProfile(contactId = contactId)

        val updatedProfile = existingProfile.copy(
            avgMessageLength = avgLen,
            usesEmojis = usesEmojis
        )
        db.behaviorProfileDao().insertOrUpdateProfile(updatedProfile)
        SupabaseSyncRepository(db).syncBehaviorProfile(updatedProfile)

        // 2. LLM Summarization for Contact Memory
        val conversationText = messages.joinToString("\n") { "${it.sender.uppercase()}: ${it.text}" }

        val prompt = buildString {
            appendLine("Summarize the key facts, preferences, and long-term context about this person based on this chat log.")
            appendLine("Keep the summary concise (max 3 sentences). Also extract any nicknames or shared interests.")
            appendLine("Existing Memory Summary: ${existingMemory.summary}")
            appendLine("Existing Facts: ${existingMemory.importantFacts ?: "None"}")
            appendLine("Chat Log:")
            appendLine(conversationText)
            appendLine()
            appendLine("Output strictly formatted as:")
            appendLine("Summary: <concise summary>")
            appendLine("Important Facts: <bulleted facts>")
        }

        val chatMessages = listOf(
            OllamaChatMessage(role = "system", content = "You are a memory summarizer assistant. Keep facts concise and factual."),
            OllamaChatMessage(role = "user", content = prompt)
        )

        val result = ollamaRepository.generateChat(baseUrl = ollamaUrl, model = modelName, messages = chatMessages)
        val rawResult = result.getOrNull()?.content ?: return

        var newSummary = existingMemory.summary
        var newFacts = existingMemory.importantFacts

        val summaryMatch = Regex("""Summary:\s*(.*)""").find(rawResult)
        if (summaryMatch != null) {
            newSummary = summaryMatch.groupValues[1].trim()
        }

        val factsMatch = Regex("""Important Facts:\s*([\s\S]*)""").find(rawResult)
        if (factsMatch != null) {
            newFacts = factsMatch.groupValues[1].trim()
        }

        val updatedMemory = existingMemory.copy(
            summary = newSummary.ifBlank { existingMemory.summary },
            importantFacts = newFacts ?: existingMemory.importantFacts,
            lastUpdated = System.currentTimeMillis()
        )

        db.contactMemoryDao().insertOrUpdateMemory(updatedMemory)
        SupabaseSyncRepository(db).syncContactMemory(updatedMemory)
    }
}

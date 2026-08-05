package com.example.domain

import com.example.data.db.*
import com.example.data.ollama.OllamaChatMessage
import com.example.data.ollama.OllamaRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DecisionEngine(
    private val db: AppDatabase,
    private val ollamaRepository: OllamaRepository
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(DecisionResponseJson::class.java)

    /**
     * Evaluates incoming message for a specific contact and produces a DecisionResult.
     */
    suspend fun processIncomingMessage(
        contactId: String,
        incomingText: String,
        ollamaUrl: String,
        modelName: String
    ): DecisionResult {
        // 1. Mandatory Programmatic Guardrail: "Where is [Person]" check
        val lowerText = incomingText.lowercase()
        val locationRegex = Regex("""\b(where('s|\s+is|\s+are)\b|\blocation of\b|\bwhere did .* go\b)""")
        if (locationRegex.containsMatchIn(lowerText)) {
            return DecisionResult.NotifyOnly(
                reason = "Location query detected: '$incomingText'. Please reply manually."
            )
        }

        // 2. Fetch contact-isolated database rows strictly using contactId filter
        val contact = db.contactDao().getContactById(contactId)
            ?: Contact(contactId = contactId, phoneNumber = contactId, displayName = contactId)
        val contactMemory = db.contactMemoryDao().getMemoryForContact(contactId)
            ?: ContactMemory(contactId = contactId)
        val behaviorProfile = db.behaviorProfileDao().getProfileForContact(contactId)
            ?: BehaviorProfile(contactId = contactId)
        val personalMemory = db.personalMemoryDao().getPersonalMemory()
            ?: PersonalMemory()

        // Load recent messages strictly for this contactId (max 15)
        val recentMessages = db.messageDao().getRecentMessages(contactId, limit = 15).reversed()

        // 3. Construct System Prompt & Messages
        val systemPrompt = buildString {
            appendLine(HIDDEN_BASE_INSTRUCTIONS)
            appendLine()
            appendLine("USER CUSTOM INSTRUCTIONS:")
            appendLine(personalMemory.globalSystemPrompt)
            appendLine()
            appendLine("PERSONAL BACKGROUND ABOUT USER:")
            appendLine(personalMemory.aboutMe)
            appendLine()
            appendLine("USER SHARING RULES:")
            appendLine(personalMemory.sharingRules)
            appendLine()
            appendLine("CONTACT IDENTITY & ROLE:")
            appendLine("Contact Name/ID: ${contact.displayName ?: contact.contactId}")
            appendLine("Relationship Role/Label: ${contact.relationshipLabel}")
            appendLine("Contact Gender: ${contact.gender ?: "Unknown"}")
            appendLine()
            appendLine("BEHAVIORAL STYLE FOR THIS CONTACT:")
            appendLine("- Formality: ${behaviorProfile.toneFormalCasual}")
            appendLine("- Emoji Use: ${if (behaviorProfile.usesEmojis) "Yes" else "No"}")
            appendLine("- Humor Level: ${behaviorProfile.humorLevel}")
            appendLine("- Common Greetings: ${behaviorProfile.commonGreetings}")
            appendLine("- Preferred Language: ${behaviorProfile.preferredLanguage}")
            appendLine("- Allow Other Languages: ${if (contact.allowOtherLanguages) "Yes" else "No (English Only Enforced)"}")
            if (!contact.allowOtherLanguages) {
                appendLine("  [STRICT LANGUAGE MANDATE]: Other languages are NOT allowed for this contact. You MUST respond in ENGLISH ONLY. If the sender sends a message in a non-English language, reply in English and instruct them politely to speak in English only.")
            }
            appendLine()
            appendLine("CONTACT MEMORY & FACTS:")
            appendLine("Summary: ${contactMemory.summary.ifBlank { "None yet" }}")
            if (!contactMemory.nicknames.isNull_or_blank()) appendLine("Nicknames: ${contactMemory.nicknames}")
            if (!contactMemory.sharedInterests.isNull_or_blank()) appendLine("Interests: ${contactMemory.sharedInterests}")
            if (!contactMemory.importantFacts.isNull_or_blank()) appendLine("Facts & Notes: ${contactMemory.importantFacts}")
            appendLine()
            appendLine("CRITICAL SAFETY & PRIVACY RULES:")
            appendLine("1. ONLY use information about this specific contact (${contact.displayName ?: contact.contactId}). NEVER reference or reveal other contacts' private chats, contact info, or personal data to anyone.")
            appendLine("2. MANDATORY MANUAL NOTIFICATION MANDATE: You MUST call the `notify_user` tool (or notify the user) to reply manually if:")
            appendLine("   a) The incoming message asks or tells the user to perform a physical real-world action or task (e.g. pick up an item, call someone, open/turn on something, perform an action using their phone or physically).")
            appendLine("   b) The incoming message asks for information, status, location, phone number, or personal details about ANOTHER person or contact.")
            appendLine("   c) The incoming message requires specific personal decisions, promises, or confidential facts that only the user knows.")
        }

        val chatMessages = mutableListOf<OllamaChatMessage>()
        chatMessages.add(OllamaChatMessage(role = "system", content = systemPrompt))

        // Append recent chat history for behavioral analysis
        for (msg in recentMessages) {
            val role = if (msg.sender == "them") "user" else "assistant"
            chatMessages.add(OllamaChatMessage(role = role, content = msg.text))
        }

        // Append new incoming message
        chatMessages.add(OllamaChatMessage(role = "user", content = incomingText))

        // 4. Call Ollama with tool definition
        val chatResult = ollamaRepository.generateChat(
            baseUrl = ollamaUrl,
            model = modelName,
            messages = chatMessages,
            useTools = true
        )

        val responseMessage = chatResult.getOrElse { error ->
            return DecisionResult.NotifyOnly("Ollama server error: ${error.localizedMessage}")
        }

        // 5. Native Tool Call evaluation: check if notify_user tool was invoked
        val toolCalls = responseMessage.tool_calls
        if (!toolCalls.isNullOrEmpty()) {
            val notifyCall = toolCalls.firstOrNull { it.function?.name == "notify_user" }
            if (notifyCall != null) {
                val args = notifyCall.function?.arguments
                val reasonArg = args?.get("reason")?.toString()
                    ?: args?.get("suggested_note")?.toString()
                    ?: "AI called notify_user tool for manual response."
                return DecisionResult.NotifyOnly(reasonArg)
            }
        }

        val content = responseMessage.content?.trim() ?: ""

        // Check for text-based tool call fallback if model output string instead of structured tool call
        val notiToolCall = extractNotiToolCall(content)
        if (notiToolCall != null) {
            return DecisionResult.NotifyOnly(notiToolCall)
        }

        // 6. Fallback: Parse JSON decision if model returned legacy JSON decision structure
        val parsedJson = parseDecisionJson(content)
        if (parsedJson != null) {
            val reasonNoti = extractNotiToolCall(parsedJson.reason ?: "")
            if (reasonNoti != null) {
                return DecisionResult.NotifyOnly(reasonNoti)
            }

            when (parsedJson.decision?.uppercase() ?: "") {
                "AUTO_REPLY" -> {
                    val reply = parsedJson.reply
                    if (!reply.isNull_or_blank()) {
                        return DecisionResult.AutoReply(replyText = reply!!.trim(), reason = parsedJson.reason ?: "Safe reply")
                    }
                }
                "CROSS_CONTACT_QUERY" -> {
                    val reason = parsedJson.reason ?: "Question references another contact."
                    return DecisionResult.NotifyOnly("Cross-contact query: $reason")
                }
                else -> {
                    val reason = parsedJson.reason ?: "Decision engine flagged for manual reply."
                    return DecisionResult.NotifyOnly(reason)
                }
            }
        }

        // 7. Normal direct auto-reply text
        if (content.isNotBlank() && !content.contains("NOTIFY_ONLY")) {
            return DecisionResult.AutoReply(replyText = content)
        }

        return DecisionResult.NotifyOnly("Ambiguous AI response for $contactId. Manual reply requested.")
    }

    private fun parseDecisionJson(rawText: String): DecisionResponseJson? {
        return try {
            val jsonStart = rawText.indexOf('{')
            val jsonEnd = rawText.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonString = rawText.substring(jsonStart, jsonEnd + 1)
                jsonAdapter.fromJson(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractNotiToolCall(text: String): String? {
        if (text.isBlank()) return null
        val match = Regex("""noti\s*["{]+([^"\}]+)["}]+""", RegexOption.IGNORE_CASE).find(text)
        if (match != null) {
            val content = match.groupValues.getOrNull(1)?.trim()
            if (!content.isNullOrBlank()) {
                return content
            }
        }
        return null
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.isBlank()
    }

    companion object {
        private val HIDDEN_BASE_INSTRUCTIONS = """
You are an AI assistant replying to WhatsApp messages on behalf of the user.
You have access to a `notify_user` tool. Call it whenever a message requires
the real user's personal input rather than an automatic reply — this includes
questions only the user would know the answer to, requests to do a physical
real-world task or action (e.g. using phone, picking up something, meeting),
questions asking for information/location/details about another person,
personal/sensitive topics, or when the sender's tone doesn't match what you
know about them.

If a message asks for another person's personal information or tells the user
to do something physical/device-related, ALWAYS call notify_user so the user
is notified to reply manually.

Never reveal one contact's private conversation, memory, or facts to a
different contact under any circumstance.
Never invent commitments, plans, times, or facts on the user's behalf.
""".trimIndent()
    }
}


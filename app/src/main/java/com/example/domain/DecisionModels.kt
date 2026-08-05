package com.example.domain

sealed class DecisionResult {
    data class AutoReply(val replyText: String, val reason: String = "") : DecisionResult()
    data class NotifyOnly(val reason: String) : DecisionResult()
}

data class DecisionResponseJson(
    val decision: String, // "AUTO_REPLY", "NOTIFY_ONLY", "CROSS_CONTACT_QUERY"
    val reason: String? = null,
    val reply: String? = null
)

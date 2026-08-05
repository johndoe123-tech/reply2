package com.example.service

import android.app.PendingIntent
import android.app.RemoteInput
import java.util.concurrent.ConcurrentHashMap

data class WhatsAppReplyTarget(
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val timestamp: Long = System.currentTimeMillis()
)

object WhatsAppReplyCache {
    private val cache = ConcurrentHashMap<String, WhatsAppReplyTarget>()

    fun store(contactId: String, pendingIntent: PendingIntent, remoteInput: RemoteInput) {
        cache[contactId] = WhatsAppReplyTarget(pendingIntent, remoteInput)
    }

    fun get(contactId: String): WhatsAppReplyTarget? {
        val target = cache[contactId] ?: return null
        if (System.currentTimeMillis() - target.timestamp > 24 * 60 * 60 * 1000) {
            cache.remove(contactId)
            return null
        }
        return target
    }
}

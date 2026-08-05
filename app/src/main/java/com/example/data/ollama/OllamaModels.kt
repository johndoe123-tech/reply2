package com.example.data.ollama

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OllamaModelDetails(
    val format: String? = null,
    val family: String? = null,
    val parameter_size: String? = null,
    val quantization_level: String? = null
)

@JsonClass(generateAdapter = true)
data class OllamaModelInfo(
    val name: String,
    val modified_at: String? = null,
    val size: Long? = null,
    val details: OllamaModelDetails? = null
)

@JsonClass(generateAdapter = true)
data class OllamaModelListResponse(
    val models: List<OllamaModelInfo>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class OllamaToolProperty(
    val type: String,
    val description: String
)

@JsonClass(generateAdapter = true)
data class OllamaToolParams(
    val type: String = "object",
    val properties: Map<String, OllamaToolProperty>,
    val required: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OllamaToolFunction(
    val name: String,
    val description: String,
    val parameters: OllamaToolParams
)

@JsonClass(generateAdapter = true)
data class OllamaTool(
    val type: String = "function",
    val function: OllamaToolFunction
)

@JsonClass(generateAdapter = true)
data class OllamaToolCallFunction(
    val name: String,
    val arguments: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class OllamaToolCall(
    val function: OllamaToolCallFunction? = null
)

@JsonClass(generateAdapter = true)
data class OllamaChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String? = null,
    val tool_calls: List<OllamaToolCall>? = null
)

@JsonClass(generateAdapter = true)
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val tools: List<OllamaTool>? = null,
    val options: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class OllamaChatResponse(
    val model: String? = null,
    val created_at: String? = null,
    val message: OllamaChatMessage? = null,
    val done: Boolean = true
)

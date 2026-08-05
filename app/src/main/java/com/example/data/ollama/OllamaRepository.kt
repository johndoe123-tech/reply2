package com.example.data.ollama

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface OllamaApi {
    @GET("api/tags")
    suspend fun getModels(): OllamaModelListResponse

    @POST("api/chat")
    suspend fun chat(@Body request: OllamaChatRequest): OllamaChatResponse
}

class OllamaRepository {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    suspend fun fetchModels(baseUrl: String): Result<List<OllamaModelInfo>> {
        return runCatching {
            val api = buildRetrofit(baseUrl).create(OllamaApi::class.java)
            val response = api.getModels()
            response.models ?: emptyList()
        }
    }

    val notifyUserTool = OllamaTool(
        type = "function",
        function = OllamaToolFunction(
            name = "notify_user",
            description = "Call this when the incoming message requires the real user to personally respond — for example: it asks something only the user would know, asks the user to do a real-world/physical/device action, asks where another person is, requires a personal decision, seems private/sensitive, or the sender's tone/behavior doesn't match their known profile (possible impersonation or unknown identity). Do NOT call this for normal small talk, greetings, or questions answerable from this contact's own conversation history.",
            parameters = OllamaToolParams(
                type = "object",
                properties = mapOf(
                    "reason" to OllamaToolProperty(
                        type = "string",
                        description = "Brief reason this needs the user's personal reply"
                    ),
                    "suggested_note" to OllamaToolProperty(
                        type = "string",
                        description = "Optional short note to show the user about what's being asked"
                    )
                ),
                required = listOf("reason")
            )
        )
    )

    suspend fun generateChat(
        baseUrl: String,
        model: String,
        messages: List<OllamaChatMessage>,
        useTools: Boolean = true
    ): Result<OllamaChatMessage> {
        return runCatching {
            val api = buildRetrofit(baseUrl).create(OllamaApi::class.java)
            val request = OllamaChatRequest(
                model = model,
                messages = messages,
                stream = false,
                tools = if (useTools) listOf(notifyUserTool) else null
            )
            val response = api.chat(request)
            response.message ?: throw IllegalStateException("Empty response from Ollama")
        }
    }

    suspend fun testConnection(baseUrl: String): Result<Boolean> {
        return runCatching {
            val models = fetchModels(baseUrl).getOrThrow()
            models.isNotEmpty()
        }
    }
}

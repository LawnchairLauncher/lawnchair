package app.lawnchair.one

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * One - Claude AI integration for System launcher
 * Minimal, honest, useful
 */
class OneAPI(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "one_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun setApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun getApiKey(): String? {
        return encryptedPrefs.getString(KEY_API_KEY, null)
    }

    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    fun clearApiKey() {
        encryptedPrefs.edit().remove(KEY_API_KEY).apply()
    }

    suspend fun sendMessage(
        messages: List<Message>,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("API key not configured"))
        }

        try {
            val request = ClaudeRequest(
                model = MODEL_SONNET_4,
                max_tokens = 1024,
                system = systemPrompt,
                messages = messages.map {
                    ClaudeMessage(role = it.role, content = it.content)
                }
            )

            val requestBody = json.encodeToString(request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", API_VERSION)
                .addHeader("content-type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    Exception("API error ${response.code}: $errorBody")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val claudeResponse = json.decodeFromString<ClaudeResponse>(responseBody)
            val content = claudeResponse.content.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("No content in response"))

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL_SONNET_4 = "claude-sonnet-4-20250514"
        private const val KEY_API_KEY = "claude_api_key"

        const val DEFAULT_SYSTEM_PROMPT = """You are One, a helpful assistant integrated into the System launcher.
You are minimal, direct, and useful. You follow Dieter Rams' principles: less but better.
Keep responses concise and actionable. No unnecessary words.
You can help with: app suggestions, quick answers, device tips, and general assistance.
Be honest about your limitations."""
    }
}

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeRequest(
    val model: String,
    val max_tokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>
)

@Serializable
private data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeResponse(
    val content: List<ContentBlock>,
    val model: String,
    val stop_reason: String? = null
)

@Serializable
private data class ContentBlock(
    val type: String,
    val text: String
)

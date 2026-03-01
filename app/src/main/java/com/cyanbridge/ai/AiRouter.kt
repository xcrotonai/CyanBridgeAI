package com.cyanbridge.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

enum class AiProvider(val displayName: String, val emoji: String) {
    GEMINI("Gemini Flash 2.0", "🔷"),
    OPENAI("ChatGPT GPT-4o", "🟢"),
    CLAUDE("Claude Sonnet", "🟠")
}

data class AiResponse(
    val text: String,
    val provider: AiProvider,
    val isError: Boolean = false
)

class AiRouter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    var activeProvider: AiProvider = AiProvider.GEMINI
    var geminiKey: String = ""
    var openaiKey: String = ""
    var claudeKey: String = ""

    // Conversation memory — keeps last 6 exchanges
    private val history = mutableListOf<Pair<String, String>>()

    fun clearHistory() = history.clear()

    fun hasKey(provider: AiProvider): Boolean = when (provider) {
        AiProvider.GEMINI -> geminiKey.isNotBlank()
        AiProvider.OPENAI -> openaiKey.isNotBlank()
        AiProvider.CLAUDE -> claudeKey.isNotBlank()
    }

    // ── Plain text question ──────────────────────────────────
    suspend fun ask(question: String): AiResponse {
        history.add("user" to question)
        return try {
            val answer = when (activeProvider) {
                AiProvider.GEMINI -> callGemini(question, null)
                AiProvider.OPENAI -> callOpenAI(question, null)
                AiProvider.CLAUDE -> callClaude(question, null)
            }
            history.add("assistant" to answer)
            if (history.size > 14) history.removeAt(0) // keep last 7 pairs
            AiResponse(answer, activeProvider)
        } catch (e: Exception) {
            AiResponse("Error: ${e.message}", activeProvider, isError = true)
        }
    }

    // ── Vision: photo from glasses → AI ─────────────────────
    suspend fun askWithPhoto(question: String, photo: Bitmap): AiResponse {
        return try {
            val b64 = photo.toBase64()
            val answer = when (activeProvider) {
                AiProvider.GEMINI -> callGemini(question, b64)
                AiProvider.OPENAI -> callOpenAI(question, b64)
                AiProvider.CLAUDE -> callClaude(question, b64)
            }
            history.add("assistant" to answer)
            AiResponse(answer, activeProvider)
        } catch (e: Exception) {
            AiResponse("Error: ${e.message}", activeProvider, isError = true)
        }
    }

    // ── GEMINI ───────────────────────────────────────────────
    private suspend fun callGemini(text: String, imageB64: String?): String =
        withContext(Dispatchers.IO) {
            val parts = JSONArray()
            if (imageB64 != null) {
                parts.put(JSONObject().put("inline_data",
                    JSONObject().put("mime_type", "image/jpeg").put("data", imageB64)))
            }
            parts.put(JSONObject().put("text", text))

            val contents = JSONArray()
            // Add history
            for (i in history.indices step 1) {
                val (role, content) = history[i]
                val geminiRole = if (role == "user") "user" else "model"
                contents.put(JSONObject()
                    .put("role", geminiRole)
                    .put("parts", JSONArray().put(JSONObject().put("text", content))))
            }
            // Add current message
            contents.put(JSONObject().put("role", "user").put("parts", parts))

            val body = JSONObject()
                .put("contents", contents)
                .put("systemInstruction", JSONObject().put("parts",
                    JSONArray().put(JSONObject().put("text",
                        "You are an AI assistant built into HeyCyan smart glasses. " +
                        "Be concise and helpful. When describing images, be specific and practical."))))
                .put("generationConfig", JSONObject()
                    .put("maxOutputTokens", 1024)
                    .put("temperature", 0.7))
                .toString()

            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: throw Exception("Empty response from Gemini")
            val json = JSONObject(respBody)

            if (json.has("error")) {
                throw Exception(json.getJSONObject("error").getString("message"))
            }

            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }

    // ── OPENAI ───────────────────────────────────────────────
    private suspend fun callOpenAI(text: String, imageB64: String?): String =
        withContext(Dispatchers.IO) {
            val messages = JSONArray()

            // System prompt
            messages.put(JSONObject()
                .put("role", "system")
                .put("content", "You are an AI assistant built into HeyCyan smart glasses. " +
                    "Be concise and helpful. When describing images, be specific and practical."))

            // History
            history.takeLast(12).forEach { (role, content) ->
                messages.put(JSONObject().put("role", role).put("content", content))
            }

            // Current message
            val content = JSONArray()
            if (imageB64 != null) {
                content.put(JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject()
                        .put("url", "data:image/jpeg;base64,$imageB64")
                        .put("detail", "high")))
            }
            content.put(JSONObject().put("type", "text").put("text", text))
            messages.put(JSONObject().put("role", "user").put("content", content))

            val body = JSONObject()
                .put("model", "gpt-4o")
                .put("messages", messages)
                .put("max_tokens", 1024)
                .toString()

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $openaiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: throw Exception("Empty response from OpenAI")
            val json = JSONObject(respBody)

            if (json.has("error")) {
                throw Exception(json.getJSONObject("error").getString("message"))
            }

            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }

    // ── CLAUDE ───────────────────────────────────────────────
    private suspend fun callClaude(text: String, imageB64: String?): String =
        withContext(Dispatchers.IO) {
            val messages = JSONArray()

            // History
            history.takeLast(12).forEach { (role, content) ->
                messages.put(JSONObject().put("role", role).put("content", content))
            }

            // Current message
            val content = JSONArray()
            if (imageB64 != null) {
                content.put(JSONObject()
                    .put("type", "image")
                    .put("source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", imageB64)))
            }
            content.put(JSONObject().put("type", "text").put("text", text))
            messages.put(JSONObject().put("role", "user").put("content", content))

            val body = JSONObject()
                .put("model", "claude-sonnet-4-20250514")
                .put("max_tokens", 1024)
                .put("system", "You are an AI assistant built into HeyCyan smart glasses. " +
                    "Be concise and helpful. When describing images, be specific and practical.")
                .put("messages", messages)
                .toString()

            val req = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", claudeKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: throw Exception("Empty response from Claude")
            val json = JSONObject(respBody)

            if (json.has("error")) {
                throw Exception(json.getJSONObject("error").getString("message"))
            }

            json.getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        }

    // ── Helper ───────────────────────────────────────────────
    private fun Bitmap.toBase64(): String {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

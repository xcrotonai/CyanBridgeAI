package com.cyanbridge.translation

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
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
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Language(val code: String, val name: String, val flag: String)

object Languages {
    val all = listOf(
        Language("en", "English", "🇬🇧"),
        Language("es", "Spanish", "🇪🇸"),
        Language("fr", "French", "🇫🇷"),
        Language("de", "German", "🇩🇪"),
        Language("it", "Italian", "🇮🇹"),
        Language("pt", "Portuguese", "🇧🇷"),
        Language("ja", "Japanese", "🇯🇵"),
        Language("zh", "Chinese", "🇨🇳"),
        Language("ko", "Korean", "🇰🇷"),
        Language("ar", "Arabic", "🇸🇦"),
        Language("hi", "Hindi", "🇮🇳"),
        Language("ru", "Russian", "🇷🇺"),
        Language("th", "Thai", "🇹🇭"),
        Language("vi", "Vietnamese", "🇻🇳"),
        Language("tr", "Turkish", "🇹🇷"),
    )
    fun byCode(code: String) = all.find { it.code == code }
}

data class TranslationResult(
    val original: String,
    val translated: String,
    val detectedFrom: String,
    val to: String,
    val isError: Boolean = false
)

class LiveTranslator(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Set your Google Cloud Translate key for best quality (optional)
    // If blank, uses Gemini as free fallback
    var googleCloudKey: String = ""
    var geminiKey: String = ""
    var targetLang: String = "en"
    var sourceLang: String = "auto"

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
        }
    }

    // Translate plain text
    suspend fun translate(text: String): TranslationResult {
        if (text.isBlank()) return TranslationResult("", "", "auto", targetLang)
        return try {
            if (googleCloudKey.isNotBlank()) googleTranslate(text)
            else geminiTranslate(text)
        } catch (e: Exception) {
            TranslationResult(text, "Translation error: ${e.message}", "auto", targetLang, isError = true)
        }
    }

    // Point glasses camera at text → OCR → translate
    suspend fun translatePhoto(bitmap: Bitmap): TranslationResult {
        return try {
            val extracted = ocrWithGemini(bitmap)
            if (extracted.isBlank()) {
                TranslationResult("", "No readable text found in the image.", "auto", targetLang)
            } else {
                translate(extracted)
            }
        } catch (e: Exception) {
            TranslationResult("", "Error: ${e.message}", "auto", targetLang, isError = true)
        }
    }

    // Speak the translated text out loud
    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.language = Locale.forLanguageTag(targetLang)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation_tts")
    }

    fun stopSpeaking() { tts?.stop() }
    fun release() { tts?.stop(); tts?.shutdown() }

    // ── Google Cloud Translate ────────────────────────────────
    // Free tier: 500,000 chars/month free, then ~$20/million chars
    // Get key: console.cloud.google.com → Translation API
    private suspend fun googleTranslate(text: String): TranslationResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("q", text)
                .put("target", targetLang)
                .apply { if (sourceLang != "auto") put("source", sourceLang) }
                .put("format", "text")
                .toString()

            val req = Request.Builder()
                .url("https://translation.googleapis.com/language/translate/v2?key=$googleCloudKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val json = JSONObject(resp.body!!.string())
            val t = json.getJSONObject("data")
                .getJSONArray("translations")
                .getJSONObject(0)

            TranslationResult(
                original = text,
                translated = t.getString("translatedText"),
                detectedFrom = t.optString("detectedSourceLanguage", "auto"),
                to = targetLang
            )
        }

    // ── Gemini translate (free, uses your Gemini key) ─────────
    private suspend fun geminiTranslate(text: String): TranslationResult =
        withContext(Dispatchers.IO) {
            val targetName = Languages.byCode(targetLang)?.name ?: targetLang
            val prompt = "Translate the following text to $targetName. " +
                "Return ONLY the translated text with absolutely no explanation or extra words:\n\n$text"

            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject()
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject()
                    .put("temperature", 0.1)
                    .put("maxOutputTokens", 512))
                .toString()

            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val translated = JSONObject(resp.body!!.string())
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text").trim()

            TranslationResult(text, translated, "auto", targetLang)
        }

    // ── OCR: extract text from glasses photo ──────────────────
    private suspend fun ocrWithGemini(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject()
                    .put("parts", JSONArray()
                        .put(JSONObject().put("inline_data", JSONObject()
                            .put("mime_type", "image/jpeg").put("data", b64)))
                        .put(JSONObject().put("text",
                            "Extract all visible text from this image exactly as it appears. " +
                            "Return only the raw text, preserving line breaks. " +
                            "If no text is visible, return empty string.")))))
                .toString()

            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            JSONObject(resp.body!!.string())
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text").trim()
        }
}

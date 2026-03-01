package com.cyanbridge.ui

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cyanbridge_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // API Keys
    var geminiKey: String
        get() = prefs.getString(KEY_GEMINI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI, value).apply()

    var openaiKey: String
        get() = prefs.getString(KEY_OPENAI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI, value).apply()

    var claudeKey: String
        get() = prefs.getString(KEY_CLAUDE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLAUDE, value).apply()

    var googleTranslateKey: String
        get() = prefs.getString(KEY_GOOGLE_TRANSLATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GOOGLE_TRANSLATE, value).apply()

    // Settings
    var activeAiProvider: String
        get() = prefs.getString(KEY_ACTIVE_AI, "GEMINI") ?: "GEMINI"
        set(value) = prefs.edit().putString(KEY_ACTIVE_AI, value).apply()

    var translationTargetLang: String
        get() = prefs.getString(KEY_TARGET_LANG, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_TARGET_LANG, value).apply()

    var autoSpeakTranslation: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply()

    companion object {
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_OPENAI = "openai_api_key"
        private const val KEY_CLAUDE = "claude_api_key"
        private const val KEY_GOOGLE_TRANSLATE = "google_translate_key"
        private const val KEY_ACTIVE_AI = "active_ai_provider"
        private const val KEY_TARGET_LANG = "translation_target_lang"
        private const val KEY_AUTO_SPEAK = "auto_speak_translation"
    }
}

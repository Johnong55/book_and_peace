package com.bookandpeace.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookandpeace.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "book_and_peace_prefs")

enum class ReaderThemeMode(
    val label: String,
    val subtitle: String,
    val isDark: Boolean
) {
    SEPIA("Giấy Sepia", "Dịu mắt nhất • Giảm ánh sáng xanh", false),
    SOFT_CREAM("Giấy Kem", "Giống sách in • Đọc ban ngày", false),
    EYE_CARE_GREEN("Xanh Dịu Mắt", "Thư giãn cơ điều tiết mắt", false),
    CHARCOAL_DARK("Than Chì Tối", "Chống quầng sáng & loạn thị", true),
    MIDNIGHT_NIGHT("Đêm Trầm", "Đọc phòng tối trước khi ngủ", true)
}

enum class PageTurnStyle(val label: String) {
    REALISTIC_BOOK("Lật góc giấy"),
    SMOOTH_SLIDE("Trượt êm")
}

data class AppSettings(
    val geminiApiKey: String = BuildConfig.DEFAULT_GEMINI_API_KEY,
    val geminiTextModel: String = "gemini-3.8-flash",
    val geminiTtsModel: String = "gemini-3.8-flash-tts",
    val geminiVoiceName: String = "Charon",
    val preferredAiMode: String = AiProviderType.AUTO,
    val preferredTtsEngine: String = TtsEngineType.AUTO,
    val customTtsEndpoint: String = "",
    val customTtsApiKey: String = "",
    val customTtsVoice: String = "alloy",
    val readerTheme: ReaderThemeMode = ReaderThemeMode.SEPIA,
    val readerFontSizeSp: Float = 19.5f,
    val readerLineHeightMultiplier: Float = 1.75f,
    val readerHorizontalMarginDp: Float = 24f,
    val readerUseSerif: Boolean = true,
    val readerPaginatedMode: Boolean = true,
    val readerPageTurnStyle: PageTurnStyle = PageTurnStyle.REALISTIC_BOOK,
    val readerHapticsEnabled: Boolean = true,
    val blueLightWarmth: Float = 0.08f,
    val screenDimmer: Float = 0f,
    val eyeRestReminderEnabled: Boolean = true,
    val playbackSpeed: Float = 1.0f
) {
    val hasGeminiKey: Boolean
        get() = geminiApiKey.isNotBlank()
}

class UserPreferences(private val context: Context) {

    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_TEXT_MODEL = stringPreferencesKey("gemini_text_model")
        val GEMINI_TTS_MODEL = stringPreferencesKey("gemini_tts_model")
        val GEMINI_VOICE_NAME = stringPreferencesKey("gemini_voice_name")
        val PREFERRED_AI_MODE = stringPreferencesKey("preferred_ai_mode")
        val PREFERRED_TTS_ENGINE = stringPreferencesKey("preferred_tts_engine")
        val CUSTOM_TTS_ENDPOINT = stringPreferencesKey("custom_tts_endpoint")
        val CUSTOM_TTS_API_KEY = stringPreferencesKey("custom_tts_api_key")
        val CUSTOM_TTS_VOICE = stringPreferencesKey("custom_tts_voice")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT_SIZE = floatPreferencesKey("reader_font_size")
        val READER_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val READER_MARGIN_DP = floatPreferencesKey("reader_margin_dp")
        val READER_FONT_STYLE = stringPreferencesKey("reader_font_style")
        val READER_PAGINATED_MODE = booleanPreferencesKey("reader_paginated_mode")
        val READER_PAGE_TURN_STYLE = stringPreferencesKey("reader_page_turn_style")
        val READER_HAPTICS_ENABLED = booleanPreferencesKey("reader_haptics_enabled")
        val BLUE_LIGHT_WARMTH = floatPreferencesKey("blue_light_warmth")
        val SCREEN_DIMMER = floatPreferencesKey("screen_dimmer")
        val EYE_REST_REMINDER = booleanPreferencesKey("eye_rest_reminder")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val storedKey = prefs[Keys.GEMINI_API_KEY]
        val effectiveKey = if (!storedKey.isNullOrBlank()) storedKey else BuildConfig.DEFAULT_GEMINI_API_KEY
        val themeName = prefs[Keys.READER_THEME] ?: ReaderThemeMode.SEPIA.name
        val theme = when (themeName) {
            "WARM_PAPER" -> ReaderThemeMode.SOFT_CREAM
            "DARK" -> ReaderThemeMode.CHARCOAL_DARK
            else -> runCatching { ReaderThemeMode.valueOf(themeName) }.getOrDefault(ReaderThemeMode.SEPIA)
        }

        AppSettings(
            geminiApiKey = effectiveKey,
            geminiTextModel = prefs[Keys.GEMINI_TEXT_MODEL] ?: "gemini-3.8-flash",
            geminiTtsModel = prefs[Keys.GEMINI_TTS_MODEL] ?: "gemini-3.8-flash-tts",
            geminiVoiceName = prefs[Keys.GEMINI_VOICE_NAME] ?: "Charon",
            preferredAiMode = prefs[Keys.PREFERRED_AI_MODE] ?: AiProviderType.AUTO,
            preferredTtsEngine = prefs[Keys.PREFERRED_TTS_ENGINE] ?: TtsEngineType.AUTO,
            customTtsEndpoint = prefs[Keys.CUSTOM_TTS_ENDPOINT] ?: "",
            customTtsApiKey = prefs[Keys.CUSTOM_TTS_API_KEY] ?: "",
            customTtsVoice = prefs[Keys.CUSTOM_TTS_VOICE] ?: "alloy",
            readerTheme = theme,
            readerFontSizeSp = prefs[Keys.READER_FONT_SIZE] ?: 19.5f,
            readerLineHeightMultiplier = prefs[Keys.READER_LINE_HEIGHT] ?: 1.75f,
            readerHorizontalMarginDp = prefs[Keys.READER_MARGIN_DP] ?: 24f,
            readerUseSerif = (prefs[Keys.READER_FONT_STYLE] ?: "SERIF") == "SERIF",
            readerPaginatedMode = prefs[Keys.READER_PAGINATED_MODE] ?: true,
            readerPageTurnStyle = runCatching {
                PageTurnStyle.valueOf(prefs[Keys.READER_PAGE_TURN_STYLE] ?: PageTurnStyle.REALISTIC_BOOK.name)
            }.getOrDefault(PageTurnStyle.REALISTIC_BOOK),
            readerHapticsEnabled = prefs[Keys.READER_HAPTICS_ENABLED] ?: true,
            blueLightWarmth = prefs[Keys.BLUE_LIGHT_WARMTH] ?: 0.08f,
            screenDimmer = prefs[Keys.SCREEN_DIMMER] ?: 0f,
            eyeRestReminderEnabled = prefs[Keys.EYE_REST_REMINDER] ?: true,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f
        )
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun setGeminiApiKey(apiKey: String) {
        context.dataStore.edit { it[Keys.GEMINI_API_KEY] = apiKey.trim() }
    }

    suspend fun setGeminiModels(textModel: String, ttsModel: String, voiceName: String) {
        context.dataStore.edit {
            it[Keys.GEMINI_TEXT_MODEL] = textModel.trim()
            it[Keys.GEMINI_TTS_MODEL] = ttsModel.trim()
            it[Keys.GEMINI_VOICE_NAME] = voiceName.trim()
        }
    }

    suspend fun setPreferredAiMode(mode: String) {
        context.dataStore.edit { it[Keys.PREFERRED_AI_MODE] = mode }
    }

    suspend fun setPreferredTtsEngine(engine: String) {
        context.dataStore.edit { it[Keys.PREFERRED_TTS_ENGINE] = engine }
    }

    suspend fun setCustomTts(endpoint: String, apiKey: String, voice: String) {
        context.dataStore.edit {
            it[Keys.CUSTOM_TTS_ENDPOINT] = endpoint.trim()
            it[Keys.CUSTOM_TTS_API_KEY] = apiKey.trim()
            it[Keys.CUSTOM_TTS_VOICE] = voice.trim().ifBlank { "alloy" }
        }
    }

    suspend fun setReaderTheme(theme: ReaderThemeMode) {
        context.dataStore.edit { it[Keys.READER_THEME] = theme.name }
    }

    suspend fun setReaderTypography(
        fontSizeSp: Float,
        lineHeight: Float,
        useSerif: Boolean,
        horizontalMarginDp: Float = 24f
    ) {
        context.dataStore.edit {
            it[Keys.READER_FONT_SIZE] = fontSizeSp.coerceIn(14f, 28f)
            it[Keys.READER_LINE_HEIGHT] = lineHeight.coerceIn(1.4f, 2.2f)
            it[Keys.READER_FONT_STYLE] = if (useSerif) "SERIF" else "SANS"
            it[Keys.READER_MARGIN_DP] = horizontalMarginDp.coerceIn(14f, 38f)
        }
    }

    suspend fun setReaderPaginatedMode(paginated: Boolean) {
        context.dataStore.edit { it[Keys.READER_PAGINATED_MODE] = paginated }
    }

    suspend fun setReaderPageTurnStyle(style: PageTurnStyle) {
        context.dataStore.edit { it[Keys.READER_PAGE_TURN_STYLE] = style.name }
    }

    suspend fun setReaderHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.READER_HAPTICS_ENABLED] = enabled }
    }

    suspend fun setEyeComfortFilters(blueLightWarmth: Float, screenDimmer: Float) {
        context.dataStore.edit {
            it[Keys.BLUE_LIGHT_WARMTH] = blueLightWarmth.coerceIn(0f, 0.42f)
            it[Keys.SCREEN_DIMMER] = screenDimmer.coerceIn(0f, 0.48f)
        }
    }

    suspend fun setEyeRestReminder(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EYE_REST_REMINDER] = enabled }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.6f, 2.25f) }
    }
}

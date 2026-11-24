package com.bookandpeace.app.data

import android.content.Context
import com.bookandpeace.app.data.ai.DeepSummaryRepository
import com.bookandpeace.app.data.ai.GeminiCloudClient
import com.bookandpeace.app.data.ai.GeminiNanoClient
import com.bookandpeace.app.data.local.BookDatabase
import com.bookandpeace.app.data.local.UserPreferences
import com.bookandpeace.app.data.remote.GutendexClient
import com.bookandpeace.app.data.remote.OpdsClient
import com.bookandpeace.app.data.remote.UrlBookDownloader
import com.bookandpeace.app.data.remote.WikisourceClient
import com.bookandpeace.app.data.tts.AndroidSystemTtsEngine
import com.bookandpeace.app.data.tts.CustomTtsEngine
import com.bookandpeace.app.data.tts.GeminiTtsEngine
import com.bookandpeace.app.data.tts.TtsRouter
import com.bookandpeace.app.service.AudioPlayerController
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Client for browsing catalogs & streaming large PDF/EPUB files safely
    val catalogHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Long-timeout client dedicated to Gemini 1M-context script & TTS generation
    val aiHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val database: BookDatabase = BookDatabase.getInstance(appContext)
    val userPreferences: UserPreferences = UserPreferences(appContext)

    val wikisourceClient = WikisourceClient(appContext, catalogHttpClient, json)
    val gutendexClient = GutendexClient(appContext, catalogHttpClient, json, wikisourceClient)
    val opdsClient = OpdsClient(catalogHttpClient)
    val urlBookDownloader = UrlBookDownloader(appContext, catalogHttpClient)

    val geminiCloudClient = GeminiCloudClient(aiHttpClient, json)
    val geminiNanoClient = GeminiNanoClient()
    val deepSummaryRepository = DeepSummaryRepository(appContext, json)

    private val geminiTtsEngine = GeminiTtsEngine(aiHttpClient, json)
    private val androidSystemTtsEngine = AndroidSystemTtsEngine(appContext)
    private val customTtsEngine = CustomTtsEngine(aiHttpClient)
    val ttsRouter = TtsRouter(geminiTtsEngine, androidSystemTtsEngine, customTtsEngine)

    val bookRepository = BookRepository(
        context = appContext,
        db = database,
        wikisourceClient = wikisourceClient,
        gutendexClient = gutendexClient,
        opdsClient = opdsClient,
        urlBookDownloader = urlBookDownloader
    )

    val audioPlayerController = AudioPlayerController(appContext)
}

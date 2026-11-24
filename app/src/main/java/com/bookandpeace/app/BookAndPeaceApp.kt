package com.bookandpeace.app

import android.app.Application
import com.bookandpeace.app.data.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BookAndPeaceApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
        appScope.launch {
            runCatching { container.deepSummaryRepository.warmUp() }
            runCatching { container.bookRepository.ensureInitialDefaults() }
        }
    }
}

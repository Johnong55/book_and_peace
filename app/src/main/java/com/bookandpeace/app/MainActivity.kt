package com.bookandpeace.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bookandpeace.app.data.local.AppSettings
import com.bookandpeace.app.navigation.BookAndPeaceRoot
import com.bookandpeace.app.ui.theme.BookAndPeaceTheme
import com.bookandpeace.app.ui.theme.EyeComfortOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingOpenBookId = MutableStateFlow<Long?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val container = (application as BookAndPeaceApp).container
        handleIncomingBookIntent(intent)

        setContent {
            val settings by container.userPreferences.settingsFlow.collectAsState(initial = AppSettings())
            val openBookId by pendingOpenBookId.collectAsState()

            LaunchedEffect(settings.readerTheme.isDark) {
                val style = if (settings.readerTheme.isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            BookAndPeaceTheme(eyeCareMode = settings.readerTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    BookAndPeaceRoot(
                        container = container,
                        externalBookIdToOpen = openBookId,
                        onConsumedExternalBook = { pendingOpenBookId.value = null }
                    )
                    EyeComfortOverlay(
                        blueLightWarmth = settings.blueLightWarmth,
                        screenDimmer = settings.screenDimmer
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingBookIntent(intent)
    }

    private fun handleIncomingBookIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        if (uri != null) {
            val container = (application as BookAndPeaceApp).container
            lifecycleScope.launch {
                runCatching { container.bookRepository.importLocalUri(uri) }
                    .onSuccess { bookId ->
                        Toast.makeText(this@MainActivity, "Đã nhập sách vào Book & Peace!", Toast.LENGTH_SHORT).show()
                        pendingOpenBookId.value = bookId
                    }
                    .onFailure { ex ->
                        Toast.makeText(
                            this@MainActivity,
                            "Không mở được file sách: ${ex.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

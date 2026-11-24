package com.bookandpeace.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bookandpeace.app.data.AppContainer
import com.bookandpeace.app.data.ai.GeminiCloudClient
import com.bookandpeace.app.data.ai.GeminiDiscoveryResult
import com.bookandpeace.app.data.ai.ShareToGeminiHelper
import com.bookandpeace.app.data.local.AppSettings
import com.bookandpeace.app.data.local.TtsEngineType
import com.bookandpeace.app.ui.reader.EyeCareColorSwatchRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by container.userPreferences.settingsFlow.collectAsState(initial = AppSettings())

    var apiKeyInput by remember { mutableStateOf("") }
    var showKeyEditor by remember { mutableStateOf(false) }
    var selectedVoice by remember { mutableStateOf("Charon") }
    var isTesting by remember { mutableStateOf(false) }
    var discoveryResult by remember { mutableStateOf<GeminiDiscoveryResult?>(null) }
    var cacheBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(settings) {
        if (apiKeyInput.isBlank() && settings.geminiApiKey.isNotBlank()) {
            apiKeyInput = settings.geminiApiKey
        }
        selectedVoice = settings.geminiVoiceName
    }

    LaunchedEffect(Unit) {
        cacheBytes = container.bookRepository.getAudioCacheSizeBytes()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Cài đặt", style = MaterialTheme.typography.headlineMedium) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Eye Comfort & Color Theme across entire app
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bảo vệ mắt & Giao diện dịu mắt", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${settings.readerTheme.label} — ${settings.readerTheme.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    EyeCareColorSwatchRow(
                        selected = settings.readerTheme,
                        onSelect = { mode -> scope.launch { container.userPreferences.setReaderTheme(mode) } }
                    )

                    Text(
                        "Lọc ánh sáng xanh ấm: ${(settings.blueLightWarmth * 100 / 0.42f).toInt()}%",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = settings.blueLightWarmth,
                        onValueChange = { w ->
                            scope.launch { container.userPreferences.setEyeComfortFilters(w, settings.screenDimmer) }
                        },
                        valueRange = 0f..0.42f
                    )

                    Text(
                        "Giảm độ chói màn hình (Đọc tối): ${(settings.screenDimmer * 100 / 0.48f).toInt()}%",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = settings.screenDimmer,
                        onValueChange = { d ->
                            scope.launch { container.userPreferences.setEyeComfortFilters(settings.blueLightWarmth, d) }
                        },
                        valueRange = 0f..0.48f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nhắc nghỉ mắt 20-20-20", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Nhắc nhẹ sau mỗi 20 phút đọc liên tục",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.eyeRestReminderEnabled,
                            onCheckedChange = { on -> scope.launch { container.userPreferences.setEyeRestReminder(on) } }
                        )
                    }
                }
            }

            item { HorizontalDivider() }

            // Section 2: API Key
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gemini AI (Miễn phí 0đ)", style = MaterialTheme.typography.titleMedium)

                    if (settings.hasGeminiKey && !showKeyEditor) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("API Key đã sẵn sàng", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${settings.geminiTextModel} • ${settings.geminiTtsModel}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                TextButton(onClick = { showKeyEditor = true }) { Text("Đổi Key") }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Google AI Studio API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { ShareToGeminiHelper.openAiStudioApiKeyPage(context) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Lấy Key 0đ")
                            }
                            OutlinedButton(
                                onClick = {
                                    val clip = ShareToGeminiHelper.readClipboardText(context)
                                    val extracted = ShareToGeminiHelper.extractGeminiApiKeyFromText(clip)
                                    if (extracted != null) apiKeyInput = extracted
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Dán Key")
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isTesting = true
                                    container.userPreferences.setGeminiApiKey(apiKeyInput.trim())
                                    val res = container.geminiCloudClient.testConnectionAndDiscoverModels(apiKeyInput.trim())
                                    discoveryResult = res
                                    if (res.isValid) {
                                        container.userPreferences.setGeminiModels(
                                            res.recommendedTextModel ?: settings.geminiTextModel,
                                            res.recommendedTtsModel ?: settings.geminiTtsModel,
                                            selectedVoice
                                        )
                                        showKeyEditor = false
                                        snackbarHostState.showSnackbar("Kết nối thành công!")
                                    }
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting && apiKeyInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Lưu & Kiểm tra kết nối")
                            }
                        }
                        discoveryResult?.let {
                            Text(
                                it.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            // Section 3: Voice & TTS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Giọng đọc Sách nói", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GeminiCloudClient.PREBUILT_VOICES) { (voiceId, _) ->
                            FilterChip(
                                selected = selectedVoice == voiceId,
                                onClick = {
                                    selectedVoice = voiceId
                                    scope.launch {
                                        container.userPreferences.setGeminiModels(
                                            settings.geminiTextModel,
                                            settings.geminiTtsModel,
                                            voiceId
                                        )
                                    }
                                },
                                label = { Text(voiceId) }
                            )
                        }
                    }
                    Text(
                        GeminiCloudClient.PREBUILT_VOICES.firstOrNull { it.first == selectedVoice }?.second.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Bộ đọc TTS mặc định", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            listOf(
                                TtsEngineType.AUTO to "Tự động",
                                TtsEngineType.GEMINI_TTS to "Gemini TTS",
                                TtsEngineType.ANDROID_TTS to "Android Offline (0đ)"
                            )
                        ) { (key, label) ->
                            FilterChip(
                                selected = settings.preferredTtsEngine == key,
                                onClick = { scope.launch { container.userPreferences.setPreferredTtsEngine(key) } },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            // Section 4: Audio Cache
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bộ nhớ đệm Sách nói", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Đang dùng: ${String.format("%.1f", cacheBytes / (1024f * 1024f))} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                container.bookRepository.clearAudioCacheBytes()
                                cacheBytes = 0L
                                snackbarHostState.showSnackbar("Đã dọn bộ nhớ đệm!")
                            }
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dọn dẹp")
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

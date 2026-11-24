package com.bookandpeace.app.ui.storyteller

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookandpeace.app.data.AppContainer
import com.bookandpeace.app.data.ai.ShareToGeminiHelper
import com.bookandpeace.app.data.local.AiProviderType
import com.bookandpeace.app.data.local.AppSettings
import com.bookandpeace.app.data.local.ScriptStatus
import com.bookandpeace.app.data.local.StoryScriptEntity
import com.bookandpeace.app.data.local.StoryStyleMode
import com.bookandpeace.app.data.local.TtsEngineType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorytellerScreen(
    bookId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val book by container.bookRepository.bookDao.observeBook(bookId).collectAsState(initial = null)
    val scripts by container.bookRepository.storyScriptDao.observeScriptsForBook(bookId).collectAsState(initial = emptyList())
    val settings by container.userPreferences.settingsFlow.collectAsState(initial = AppSettings())

    val matchedDeepSummary = remember(book?.title, book?.sourceRef) {
        book?.let { container.deepSummaryRepository.findForBook(it.title, it.sourceRef) }
    }

    var selectedStyle by remember { mutableStateOf(StoryStyleMode.VAN_VO_STANDARD) }
    var useDeepSummarySource by remember { mutableStateOf(true) }
    var showAdvanced by remember { mutableStateOf(false) }
    var selectedAiMode by remember { mutableStateOf(AiProviderType.AUTO) }
    var selectedTts by remember { mutableStateOf(TtsEngineType.AUTO) }

    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedScriptText by remember { mutableStateOf("") }
    var editingScript by remember { mutableStateOf<StoryScriptEntity?>(null) }

    val styleOptions = listOf(
        StoryStyleMode.VAN_VO_QUICK to "Kể nhanh 10p",
        StoryStyleMode.VAN_VO_STANDARD to "Kể Chuyện Sâu 20p",
        StoryStyleMode.VAN_VO_DEEP to "Phân tích sâu 30p+",
        StoryStyleMode.BEDTIME_CALM to "Đêm khuya"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                title = {
                    Column {
                        Text("Studio Kể Chuyện Sâu", style = MaterialTheme.typography.titleLarge)
                        Text(
                            book?.title.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAdvanced = !showAdvanced }) {
                        Icon(Icons.Default.Tune, contentDescription = "Tùy chọn nâng cao")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (matchedDeepSummary != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (matchedDeepSummary.hasFullNarration) {
                                            "🎧 Sách nói Phân tích Đầy đủ (~${matchedDeepSummary.estimatedMinutes} phút)"
                                        } else {
                                            "⚡ Đã có Bản Tóm Tắt Sâu có sẵn"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (matchedDeepSummary.hasFullNarration) {
                                            "Bản kể chi tiết nhiều hồi kèm bình luận sâu sắc — thu âm nghe ngay không tốn token Text AI."
                                        } else {
                                            "Dùng nguồn tóm tắt sâu giúp tiết kiệm ~95% token API Gemini."
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = useDeepSummarySource,
                                    onCheckedChange = { useDeepSummarySource = it }
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    val b = book ?: return@FilledTonalButton
                                    scope.launch {
                                        container.bookRepository.startZeroTokenAudioFromDeepSummary(
                                            bookId = b.id,
                                            entry = matchedDeepSummary,
                                            ttsEngine = selectedTts
                                        )
                                        snackbarHostState.showSnackbar("Đang thu âm trực tiếp Bản Tóm Tắt Sâu (0 token Text AI)!")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Thu âm ngay Bản Tóm Tắt Sâu (0 token AI)")
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(styleOptions) { (key, label) ->
                        FilterChip(
                            selected = selectedStyle == key,
                            onClick = { selectedStyle = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val b = book ?: return@Button
                        if (!settings.hasGeminiKey && selectedAiMode != AiProviderType.GEMINI_NANO) {
                            onOpenSettings()
                            return@Button
                        }
                        scope.launch {
                            container.bookRepository.startBackgroundStoryGeneration(
                                bookId = b.id,
                                bookTitle = b.title,
                                styleMode = selectedStyle,
                                aiProvider = selectedAiMode,
                                ttsEngine = selectedTts,
                                useDeepSummary = useDeepSummarySource
                            )
                            snackbarHostState.showSnackbar("Đang tạo kịch bản Kể Chuyện Sâu & thu âm chạy ngầm...")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tạo Sách nói Kể Chuyện Sâu (Chạy ngầm)")
                }
            }

            if (showAdvanced) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Tùy chọn nguồn AI & Giọng đọc", style = MaterialTheme.typography.titleSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    listOf(
                                        AiProviderType.AUTO to "Tự động",
                                        AiProviderType.GEMINI_CLOUD to "Gemini API",
                                        AiProviderType.GEMINI_NANO to "Gemini Nano"
                                    )
                                ) { (k, label) ->
                                    FilterChip(
                                        selected = selectedAiMode == k,
                                        onClick = { selectedAiMode = k },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    listOf(
                                        TtsEngineType.AUTO to "TTS Tự động",
                                        TtsEngineType.GEMINI_TTS to "Gemini TTS",
                                        TtsEngineType.ANDROID_TTS to "Android Offline"
                                    )
                                ) { (k, label) ->
                                    FilterChip(
                                        selected = selectedTts == k,
                                        onClick = { selectedTts = k },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val b = book ?: return@OutlinedButton
                                        scope.launch {
                                            val src = matchedDeepSummary?.toCompactAiSource()
                                                ?: container.bookRepository.getFullBookText(b.id)
                                            ShareToGeminiHelper.copyPromptAndOpenGeminiApp(
                                                context, b.title, b.author, src, selectedStyle
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Mở App Gemini")
                                }
                                OutlinedButton(
                                    onClick = {
                                        pastedScriptText = ShareToGeminiHelper.readClipboardText(context).orEmpty()
                                        showPasteDialog = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Dán kịch bản")
                                }
                            }
                        }
                    }
                }
            }

            if (scripts.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text("Bản Sách nói đã tạo (${scripts.size})", style = MaterialTheme.typography.titleMedium)
                }

                items(scripts, key = { it.id }) { script ->
                    SimpleScriptRow(
                        script = script,
                        onPlay = {
                            scope.launch {
                                val segs = container.bookRepository.storyScriptDao.getSegments(script.id)
                                if (segs.isNotEmpty()) {
                                    container.audioPlayerController.playScript(
                                        scriptId = script.id,
                                        bookId = script.bookId,
                                        bookTitle = book?.title ?: script.title,
                                        scriptTitle = script.title,
                                        segments = segs,
                                        speed = settings.playbackSpeed
                                    )
                                    onOpenPlayer()
                                }
                            }
                        },
                        onEdit = { editingScript = script },
                        onDelete = { scope.launch { container.bookRepository.deleteScript(script.id) } }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Dán kịch bản để thu âm") },
            text = {
                OutlinedTextField(
                    value = pastedScriptText,
                    onValueChange = { pastedScriptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    placeholder = { Text("Dán kịch bản vào đây...") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val b = book ?: return@Button
                    if (pastedScriptText.isNotBlank()) {
                        scope.launch {
                            container.bookRepository.createFromPastedScriptAndGenerateAudio(
                                b.id, b.title, selectedStyle, selectedTts, pastedScriptText
                            )
                        }
                        showPasteDialog = false
                    }
                }) { Text("Thu âm") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("Đóng") }
            }
        )
    }

    editingScript?.let { target ->
        var draft by remember(target.id) { mutableStateOf(target.rawScript) }
        AlertDialog(
            onDismissRequest = { editingScript = null },
            title = { Text("Sửa kịch bản") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        container.bookRepository.updateScriptAndRegenerateAudio(target, draft, selectedTts)
                    }
                    editingScript = null
                }) { Text("Lưu & Thu âm lại") }
            },
            dismissButton = {
                TextButton(onClick = { editingScript = null }) { Text("Đóng") }
            }
        )
    }
}

@Composable
private fun SimpleScriptRow(
    script: StoryScriptEntity,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isWorking = script.status == ScriptStatus.GENERATING_SCRIPT || script.status == ScriptStatus.GENERATING_AUDIO
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    script.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    script.statusMessage.ifBlank { script.status },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (script.status == ScriptStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isWorking && script.rawScript.isNotBlank()) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Sửa") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa") }
        }
        if (isWorking) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (script.status == ScriptStatus.READY) {
            FilledTonalButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Nghe Sách nói")
            }
        }
    }
}

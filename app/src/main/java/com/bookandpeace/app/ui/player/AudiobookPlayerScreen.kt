package com.bookandpeace.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookandpeace.app.data.AppContainer
import com.bookandpeace.app.data.ai.ShareToGeminiHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookPlayerScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by container.audioPlayerController.state.collectAsState()
    val listState = rememberLazyListState()

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentSegmentIndex) {
        if (state.segments.isNotEmpty() && state.currentSegmentIndex in state.segments.indices) {
            runCatching { listState.animateScrollToItem(state.currentSegmentIndex) }
        }
    }

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
                        Text(
                            state.bookTitle.ifBlank { "Trình phát Sách nói" },
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Serif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.currentSegment?.let {
                                "Đoạn ${state.currentSegmentIndex + 1}/${state.segments.size} • ${it.sectionTitle}"
                            } ?: "Chưa chọn tập nghe",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    state.currentSegment?.audioFilePath?.let { path ->
                        IconButton(onClick = {
                            val f = File(path)
                            if (f.exists()) ShareToGeminiHelper.shareAudioFile(context, f, state.bookTitle)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Chia sẻ file âm thanh")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val safeDuration = state.durationMs.coerceAtLeast(1L)
                    Slider(
                        value = state.positionMs.coerceIn(0L, safeDuration).toFloat() / safeDuration.toFloat(),
                        onValueChange = { ratio ->
                            container.audioPlayerController.seekToPosition((ratio * safeDuration).toLong())
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(state.positionMs), style = MaterialTheme.typography.labelSmall)
                        Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { container.audioPlayerController.skipPrevious() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Đoạn trước")
                        }
                        IconButton(onClick = { container.audioPlayerController.seekRelative(-10_000L) }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Lùi 10s")
                        }
                        FilledIconButton(
                            onClick = { container.audioPlayerController.togglePlayPause() },
                            modifier = Modifier.size(58.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Phát / Tạm dừng",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = { container.audioPlayerController.seekRelative(10_000L) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Tiến 10s")
                        }
                        IconButton(onClick = { container.audioPlayerController.skipNext() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Đoạn sau")
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { showSpeedDialog = true }) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${String.format("%.2g", state.playbackSpeed)}x")
                        }
                        val sleepLabel = state.sleepTimerEndEpochMs?.let { end ->
                            val minsLeft = ((end - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1L)
                            "Tắt sau ${minsLeft}p"
                        } ?: "Hẹn giờ ngủ"
                        TextButton(onClick = { showSleepDialog = true }) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(sleepLabel)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state.segments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Chưa có tập sách nói nào đang phát.")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(state.segments, key = { _, seg -> seg.id }) { idx, segment ->
                    val isCurrent = idx == state.currentSegmentIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { container.audioPlayerController.seekToSegment(idx) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${segment.sectionTitle}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = segment.toneHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = segment.spokenText,
                                fontFamily = FontFamily.Serif,
                                fontSize = 16.sp,
                                lineHeight = 25.sp,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.8f, 0.9f, 1.0f, 1.15f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Tốc độ kể chuyện") },
            text = {
                Column {
                    speeds.forEach { sp ->
                        TextButton(
                            onClick = {
                                container.audioPlayerController.setPlaybackSpeed(sp)
                                scope.launch { container.userPreferences.setPlaybackSpeed(sp) }
                                showSpeedDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${sp}x" + if (sp == 1.0f) " (Chuẩn)" else "",
                                fontWeight = if (sp == state.playbackSpeed) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Đóng") }
            }
        )
    }

    if (showSleepDialog) {
        val options = listOf(null to "Tắt hẹn giờ", 10 to "10 phút", 15 to "15 phút", 30 to "30 phút", 45 to "45 phút", 60 to "60 phút")
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Hẹn giờ tắt ngủ") },
            text = {
                Column {
                    options.forEach { (mins, label) ->
                        TextButton(
                            onClick = {
                                container.audioPlayerController.setSleepTimerMinutes(mins)
                                showSleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("Đóng") }
            }
        )
    }
}

@Composable
fun MiniAudioPlayerBar(
    container: AppContainer,
    onOpenFullPlayer: () -> Unit
) {
    val state by container.audioPlayerController.state.collectAsState()
    if (!state.hasActiveTrack) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFullPlayer),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.bookTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.currentSegment?.sectionTitle ?: "Đang phát sách nói",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { container.audioPlayerController.togglePlayPause() }) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Phát / Dừng"
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format("%02d:%02d", mins, secs)
}

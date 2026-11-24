package com.bookandpeace.app.ui.discover

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookandpeace.app.data.AppContainer
import com.bookandpeace.app.data.ai.DeepSummaryEntry
import com.bookandpeace.app.data.remote.GutendexClient
import com.bookandpeace.app.data.remote.RemoteCatalogBook
import com.bookandpeace.app.data.remote.WikisourceClient
import kotlinx.coroutines.launch

private enum class DiscoverFilter(val label: String) {
    SCIENCE("Khoa học"),
    VIETNAMESE("Văn học VN"),
    WORLD("Thế giới"),
    DEEP_SUMMARIES("Tóm tắt Sâu")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    container: AppContainer,
    onBookDownloadedAndOpen: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeFilter by remember { mutableStateOf(DiscoverFilter.SCIENCE) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RemoteCatalogBook>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var isBatchDownloadingScience by remember { mutableStateOf(false) }

    val deepSummaries = remember { container.deepSummaryRepository.getAll() }

    fun performSearch() {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            searchResults = null
            return
        }
        scope.launch {
            isSearching = true
            runCatching { container.gutendexClient.searchAllRemote(q) }
                .onSuccess { searchResults = it }
                .onFailure { snackbarHostState.showSnackbar("Lỗi tìm kiếm: ${it.localizedMessage}") }
            isSearching = false
        }
    }

    fun downloadItem(item: RemoteCatalogBook) {
        if (downloadingId != null) return
        scope.launch {
            downloadingId = item.id
            runCatching { container.bookRepository.downloadRemoteBook(item) }
                .onSuccess { id ->
                    snackbarHostState.showSnackbar("Đã tải \"${item.title}\"")
                    onBookDownloadedAndOpen(id)
                }
                .onFailure { snackbarHostState.showSnackbar("Tải thất bại: ${it.localizedMessage}") }
            downloadingId = null
        }
    }

    fun openDeepSummaryBook(entry: DeepSummaryEntry) {
        if (downloadingId != null) return
        scope.launch {
            downloadingId = entry.id
            runCatching { container.bookRepository.saveDeepSummaryAsBook(entry) }
                .onSuccess { id ->
                    snackbarHostState.showSnackbar("Đã lưu bản tóm tắt sâu \"${entry.titleVi}\"")
                    onBookDownloadedAndOpen(id)
                }
                .onFailure { snackbarHostState.showSnackbar("Lỗi: ${it.localizedMessage}") }
            downloadingId = null
        }
    }

    val currentBooks = remember(activeFilter, searchResults) {
        searchResults ?: when (activeFilter) {
            DiscoverFilter.SCIENCE -> GutendexClient.curatedScienceBooks
            DiscoverFilter.VIETNAMESE -> WikisourceClient.curatedVietnameseClassics
            DiscoverFilter.WORLD -> GutendexClient.curatedWorldClassics
            DiscoverFilter.DEEP_SUMMARIES -> emptyList()
        }
    }

    // Pre-compute summary availability once per list change (zero main-thread work during scrolling)
    val summaryBookIds = remember(currentBooks) {
        currentBooks.filter { container.deepSummaryRepository.hasSummary(it.title, it.sourceRef) }
            .map { it.id }
            .toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kho sách", style = MaterialTheme.typography.headlineMedium) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.isBlank()) searchResults = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tìm sách tiếng Việt, khoa học, thế giới...") },
                    trailingIcon = {
                        IconButton(onClick = ::performSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Tìm")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (searchResults == null) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DiscoverFilter.entries) { filter ->
                            val count = when (filter) {
                                DiscoverFilter.SCIENCE -> GutendexClient.curatedScienceBooks.size
                                DiscoverFilter.VIETNAMESE -> WikisourceClient.curatedVietnameseClassics.size
                                DiscoverFilter.WORLD -> GutendexClient.curatedWorldClassics.size
                                DiscoverFilter.DEEP_SUMMARIES -> deepSummaries.size
                            }
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick = { activeFilter = filter },
                                label = { Text("${filter.label} ($count)") }
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Kết quả tìm kiếm (${searchResults!!.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(onClick = {
                            searchQuery = ""
                            searchResults = null
                        }) { Text("Xóa lọc") }
                    }
                }
            }

            if (isSearching) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Priority quick download for Science books
            if (searchResults == null && activeFilter == DiscoverFilter.SCIENCE) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Ưu tiên sách Khoa học",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Darwin, Einstein, Newton, Faraday, Galileo...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                enabled = !isBatchDownloadingScience && downloadingId == null,
                                onClick = {
                                    scope.launch {
                                        isBatchDownloadingScience = true
                                        val ok = container.bookRepository.downloadPrioritySciencePack(5)
                                        isBatchDownloadingScience = false
                                        snackbarHostState.showSnackbar("Đã tải nhanh $ok/5 sách Khoa học vào Sách của tôi!")
                                    }
                                }
                            ) {
                                if (isBatchDownloadingScience) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Tải top 5")
                                }
                            }
                        }
                    }
                }
            }

            if (searchResults == null && activeFilter == DiscoverFilter.DEEP_SUMMARIES) {
                item {
                    Text(
                        "Nguồn Tóm tắt Sâu soạn sẵn (gồm cả Nguyễn Nhật Ánh, Khoa học, Văn học VN & Thế giới) — mở đọc & thu âm Văn Vở ngay, tiết kiệm 95–100% token API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(
                    items = deepSummaries,
                    key = { it.id },
                    contentType = { "deep_summary_row" }
                ) { entry ->
                    MinimalDeepSummaryRow(
                        entry = entry,
                        isLoading = downloadingId == entry.id,
                        onOpen = { openDeepSummaryBook(entry) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            } else {
                items(
                    items = currentBooks,
                    key = { it.id },
                    contentType = { "catalog_row" }
                ) { item ->
                    MinimalCatalogRow(
                        item = item,
                        hasDeepSummary = item.id in summaryBookIds,
                        isDownloading = downloadingId == item.id,
                        onDownload = { downloadItem(item) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MinimalCatalogRow(
    item: RemoteCatalogBook,
    hasDeepSummary: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDownloading, onClick = onDownload)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(item.author)
                    if (hasDeepSummary) append(" • ⚡ Có tóm tắt sâu")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        FilledTonalButton(
            onClick = onDownload,
            enabled = !isDownloading,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tải")
            }
        }
    }
}

@Composable
private fun MinimalDeepSummaryRow(
    entry: DeepSummaryEntry,
    isLoading: Boolean,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.titleVi,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (entry.hasFullNarration) {
                    "${entry.author} • 🎧 Sách nói đầy đủ (~${entry.estimatedMinutes}p)"
                } else {
                    "${entry.author} • ${entry.category}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = entry.vanVoPerspective,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        FilledTonalButton(
            onClick = onOpen,
            enabled = !isLoading,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("Mở")
            }
        }
    }
}

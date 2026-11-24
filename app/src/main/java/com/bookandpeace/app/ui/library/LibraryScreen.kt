package com.bookandpeace.app.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookandpeace.app.data.AppContainer
import com.bookandpeace.app.data.ai.ShareToGeminiHelper
import com.bookandpeace.app.data.local.BookEntity
import com.bookandpeace.app.data.local.ScriptStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenReader: (Long) -> Unit,
    onOpenStoryteller: (Long) -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val books by container.bookRepository.bookDao.observeAllBooks().collectAsState(initial = emptyList())
    val allScripts by container.bookRepository.storyScriptDao.observeAllScripts().collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var showImportSheet by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                runCatching { container.bookRepository.importLocalUri(uri) }
                    .onSuccess { snackbarHostState.showSnackbar("Đã thêm sách vào tủ!") }
                    .onFailure { snackbarHostState.showSnackbar("Lỗi: ${it.localizedMessage}") }
                isImporting = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri != null) {
            scope.launch {
                isImporting = true
                runCatching { container.bookRepository.importFolderUri(treeUri) }
                    .onSuccess { count -> snackbarHostState.showSnackbar("Đã nhập $count cuốn sách!") }
                    .onFailure { snackbarHostState.showSnackbar("Lỗi: ${it.localizedMessage}") }
                isImporting = false
            }
        }
    }

    val readyScripts = remember(allScripts) { allScripts.filter { it.status == ScriptStatus.READY } }
    val booksById = remember(books) { books.associateBy { it.id } }
    val readyBookIds = remember(readyScripts) { readyScripts.map { it.bookId }.toSet() }

    val filteredBooks = remember(books, searchQuery) {
        if (searchQuery.isBlank()) books
        else books.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.author.contains(searchQuery, ignoreCase = true)
        }
    }
    val summaryBookIds = remember(filteredBooks) {
        filteredBooks.filter { container.deepSummaryRepository.hasSummary(it.title, it.sourceRef) }
            .map { it.id }
            .toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sách của tôi", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = { showImportSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Nhập file sách")
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isImporting) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Đang nhập sách...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (readyScripts.isNotEmpty()) {
                item {
                    Text(
                        "Sách nói sẵn sàng nghe",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(readyScripts.take(6), key = { it.id }) { script ->
                            val title = booksById[script.bookId]?.title ?: script.title
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val segs = container.bookRepository.storyScriptDao.getSegments(script.id)
                                        if (segs.isNotEmpty()) {
                                            container.audioPlayerController.playScript(
                                                scriptId = script.id,
                                                bookId = script.bookId,
                                                bookTitle = title,
                                                scriptTitle = script.title,
                                                segments = segs
                                            )
                                            onOpenPlayer()
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Headphones,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (books.size > 3) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Tìm trong tủ sách...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (filteredBooks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Chưa có cuốn sách nào",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            "Mở Kho sách để tải sách Khoa học, Văn học Việt Nam, Thế giới hoặc nhập file EPUB/PDF từ điện thoại.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onOpenDiscover) { Text("Mở Kho sách") }
                            FilledTonalButton(onClick = { showImportSheet = true }) { Text("Nhập EPUB/PDF") }
                        }
                    }
                }
            } else {
                items(
                    items = filteredBooks,
                    key = { it.id },
                    contentType = { "book_row" }
                ) { book ->
                    SimpleBookRow(
                        book = book,
                        hasReadyAudio = book.id in readyBookIds,
                        hasDeepSummary = book.id in summaryBookIds,
                        onRead = { onOpenReader(book.id) },
                        onStoryteller = { onOpenStoryteller(book.id) },
                        onDelete = { bookToDelete = book }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(onDismissRequest = { showImportSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Nhập sách từ điện thoại", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = {
                        showImportSheet = false
                        filePicker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Chọn file (.epub, .pdf, .txt)")
                }
                FilledTonalButton(
                    onClick = {
                        showImportSheet = false
                        folderPicker.launch(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Quét cả thư mục sách")
                }
                FilledTonalButton(
                    onClick = {
                        showImportSheet = false
                        val clip = ShareToGeminiHelper.readClipboardText(context).orEmpty()
                        if (clip.startsWith("http", ignoreCase = true)) urlInput = clip
                        showUrlImportDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nhập từ Link Google Drive / URL (.epub, .pdf)")
                }
                Text(
                    "Mẹo: Khi tải file EPUB/PDF từ Google Drive hoặc trình duyệt, bạn chỉ cần bấm vào file rồi chọn \"Mở bằng Book & Peace\" là app tự nhập ngay!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showUrlImportDialog) {
        AlertDialog(
            onDismissRequest = { showUrlImportDialog = false },
            title = { Text("Tải sách từ Google Drive / URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dán link file Google Drive công khai (drive.google.com/file/d/...), link Google Docs, hoặc link trực tiếp file .epub / .pdf / .txt.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://drive.google.com/file/d/...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                ShareToGeminiHelper.readClipboardText(context)?.let { urlInput = it }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán link")
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetUrl = urlInput.trim()
                        if (targetUrl.isNotBlank()) {
                            showUrlImportDialog = false
                            scope.launch {
                                isImporting = true
                                runCatching { container.bookRepository.importFromUrl(targetUrl) }
                                    .onSuccess { newBookId ->
                                        urlInput = ""
                                        snackbarHostState.showSnackbar("Đã tải sách từ link vào Tủ sách!")
                                        onOpenReader(newBookId)
                                    }
                                    .onFailure { ex ->
                                        snackbarHostState.showSnackbar(ex.localizedMessage ?: "Không tải được link này")
                                    }
                                isImporting = false
                            }
                        }
                    }
                ) { Text("Tải về") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlImportDialog = false }) { Text("Hủy") }
            }
        )
    }

    bookToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Xóa sách?") },
            text = { Text("Xóa \"${target.title}\" khỏi tủ sách của bạn?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.bookRepository.deleteBook(target.id) }
                        bookToDelete = null
                    }
                ) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun SimpleBookRow(
    book: BookEntity,
    hasReadyAudio: Boolean,
    hasDeepSummary: Boolean,
    onRead: () -> Unit,
    onStoryteller: () -> Unit,
    onDelete: () -> Unit
) {
    val spineColor = remember(book.category) {
        when {
            book.category.contains("Khoa học", true) -> Color(0xFF2B5B84)
            book.category.contains("Nguyễn Nhật Ánh", true) -> Color(0xFFB56E29)
            else -> Color(0xFF234236)
        }
    }
    val progress = if (book.totalChapters > 0 && book.lastReadAt > 0L) {
        ((book.currentChapterIndex + 1).toFloat() / book.totalChapters.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRead)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(spineColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(book.author)
                    append(" • ")
                    append("${book.totalChapters} chương")
                    if (hasDeepSummary) append(" • ⚡ Tóm tắt sâu")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onStoryteller) {
            Icon(
                if (hasReadyAudio) Icons.Default.Headphones else Icons.Default.AutoAwesome,
                contentDescription = "Kể chuyện Văn Vở",
                tint = if (hasReadyAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Xóa",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

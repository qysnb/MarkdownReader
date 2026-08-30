package com.codex.markdownreader

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.codex.markdownreader.data.DocumentEntry
import com.codex.markdownreader.data.ReaderUiState
import com.codex.markdownreader.data.ReaderTheme
import com.codex.markdownreader.data.PageMargins
import com.codex.markdownreader.data.ReaderViewModel
import com.codex.markdownreader.ui.MarkdownReaderView
import com.codex.markdownreader.ui.ReadingProgressRail
import com.codex.markdownreader.data.readableDocumentHint
import com.codex.markdownreader.data.visibleRecentDocuments

class MainActivity : androidx.activity.ComponentActivity() {
    private val viewModel: ReaderViewModel by viewModels {
        viewModelFactory {
            initializer {
                ReaderViewModel(application)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarkdownReaderApp(viewModel)
        }
        handleExternalDocumentIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalDocumentIntent(intent)
    }

    private fun handleExternalDocumentIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        viewModel.openDocument(uri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownReaderApp(viewModel: ReaderViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = readerColorScheme(uiState.themeMode)) {
        MarkdownReaderContent(viewModel, uiState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownReaderContent(viewModel: ReaderViewModel, uiState: ReaderUiState) {
    val context = LocalContext.current
    var pendingImportSubdirs by rememberSaveable { mutableStateOf(uiState.defaultIncludeSubdirectories) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showTextScaleDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var readerScrollY by remember { mutableStateOf(0) }
    var readerScrollRange by remember { mutableStateOf(0) }
    var seekTarget by remember { mutableStateOf<Int?>(null) }
    var showProgressRail by remember { mutableStateOf(false) }
    var zoomScale by rememberSaveable(uiState.activeDocument?.uri) { mutableStateOf(1f) }
    val titleScrollState = rememberScrollState()

    LaunchedEffect(readerScrollY, readerScrollRange, uiState.activeDocument?.uri) {
        if (readerScrollRange > 0 && uiState.activeDocument != null) {
            showProgressRail = true
            delay(1200)
            showProgressRail = false
        }
    }

    BackHandler(enabled = true) {
        if (uiState.activeDocument != null) {
            viewModel.closeDocument()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.importFolder(treeUri, pendingImportSubdirs)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openDocument(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.activeDocument?.name ?: context.getString(R.string.app_name),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(titleScrollState)
                    )
                },
                navigationIcon = {
                    if (uiState.activeDocument != null) {
                        IconButton(onClick = viewModel::closeDocument) {
                            Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back))
                        }
                    }
                },
                actions = {
                    if (uiState.activeDocument == null) {
                        FilledTonalButton(onClick = { showImportDialog = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(text = context.getString(R.string.import_folder))
                        }
                    } else {
                        IconButton(onClick = { showTextScaleDialog = true }) {
                            Icon(Icons.Default.FormatSize, contentDescription = "字体大小")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.activeDocument == null) {
            HomeScreen(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                onOpenRecent = viewModel::openDocument,
                onOpenLibraryFile = viewModel::openDocument,
                onPickSingleFile = {
                    filePicker.launch(arrayOf("text/markdown", "text/plain", "*/*"))
                },
                onOpenSettings = { showSettingsDialog = true }
            )
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) {
                MarkdownReaderView(
                    modifier = Modifier.fillMaxSize(),
                    document = uiState.activeDocument!!,
                    markdown = uiState.activeMarkdown.orEmpty(),
                    savedScrollY = uiState.activeDocument?.scrollY ?: 0,
                    textScale = uiState.textScale,
                    zoomScale = zoomScale,
                    onZoomScaleChange = { zoomScale = it.coerceIn(1f, 5f) },
                    textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                    pageMargins = uiState.pageMargins,
                    onScroll = { viewModel.saveScrollPosition(uiState.activeDocument!!.uri, it) },
                    onScrollMetrics = { y, range ->
                        readerScrollY = y
                        readerScrollRange = range
                    },
                    seekTo = seekTarget,
                    onSeekHandled = { seekTarget = null }
                )
                ReadingProgressRail(
                    scrollY = readerScrollY,
                    scrollRange = readerScrollRange,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    accentColor = MaterialTheme.colorScheme.primary,
                    visible = showProgressRail,
                    onSeek = { target ->
                        seekTarget = target
                    }
                )
            }
        }
    }

    if (uiState.isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(text = context.getString(R.string.import_folder)) },
            text = {
                Column {
                    Text(text = "选择一个文件夹后，我们会扫描其中的 Markdown 文件并建立本地书库。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = pendingImportSubdirs,
                            onCheckedChange = { pendingImportSubdirs = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = context.getString(R.string.include_subfolders))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportDialog = false
                        folderPicker.launch(null)
                    }
                ) {
                    Text(text = "选择文件夹")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(text = "取消")
                }
            }
        )
    }

    if (showTextScaleDialog) {
        AlertDialog(
            onDismissRequest = { showTextScaleDialog = false },
            title = { Text(text = "字体大小") },
            text = {
                Column {
                    Text(text = "拖动调节正文与公式大小")
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = uiState.textScale,
                        onValueChange = viewModel::setTextScale,
                        valueRange = 0.85f..1.5f
                    )
                    Text(text = "${(uiState.textScale * 100).toInt()}%")
                }
            },
            confirmButton = {
                Button(onClick = { showTextScaleDialog = false }) {
                    Text(text = "完成")
                }
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            recentLimit = uiState.recentLimit,
            showRecentPaths = uiState.showRecentPaths,
            textScale = uiState.textScale,
            pageMargins = uiState.pageMargins,
            themeMode = uiState.themeMode,
            onRecentLimitChange = viewModel::setRecentLimit,
            onShowRecentPathsChange = viewModel::setShowRecentPaths,
            onTextScaleChange = viewModel::setTextScale,
            onPageMarginsChange = viewModel::setPageMargins,
            onThemeChange = viewModel::setTheme,
            onDismiss = { showSettingsDialog = false },
        )
    }
}

private fun readerColorScheme(theme: ReaderTheme) = when (theme) {
    ReaderTheme.DAY -> lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF3F6F9F),
        background = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
        surface = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
    )
    ReaderTheme.NIGHT -> darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF8FC7FF),
        background = androidx.compose.ui.graphics.Color(0xFF101418),
        surface = androidx.compose.ui.graphics.Color(0xFF101418),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF28313A),
    )
    ReaderTheme.EYE_CARE -> lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF567A4B),
        background = androidx.compose.ui.graphics.Color(0xFFF2F5DF),
        surface = androidx.compose.ui.graphics.Color(0xFFF2F5DF),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE4EBD0),
        onSurface = androidx.compose.ui.graphics.Color(0xFF263326),
    )
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    uiState: ReaderUiState,
    onOpenRecent: (Uri) -> Unit,
    onOpenLibraryFile: (Uri) -> Unit,
    onPickSingleFile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "轻量级原生 Markdown 阅读器", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "导入文件夹后即可阅读本地 Markdown 文件，自动保存最近打开记录和阅读位置。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onPickSingleFile) {
                            Icon(Icons.Default.MenuBook, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(text = "打开单个文件")
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = context.getString(R.string.recent_files))
        }
        if (uiState.recentDocuments.isEmpty()) {
            item { EmptyState(text = context.getString(R.string.empty_recent)) }
        } else {
            items(visibleRecentDocuments(uiState.recentDocuments, uiState.recentLimit), key = { it.uri }) { doc ->
                DocumentRow(doc = doc, hint = readableDocumentHint(doc, uiState.showRecentPaths), onClick = { onOpenRecent(Uri.parse(doc.uri)) })
            }
        }

        item {
            SectionHeader(title = context.getString(R.string.library))
        }
        if (uiState.libraryDocuments.isEmpty()) {
            item { EmptyState(text = context.getString(R.string.empty_library)) }
        } else {
            items(uiState.libraryDocuments, key = { it.uri }) { doc ->
                DocumentRow(doc = doc, onClick = { onOpenLibraryFile(Uri.parse(doc.uri)) })
            }
        }

        item { Spacer(Modifier.navigationBarsPadding()) }
        item {
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("设置")
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EmptyState(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text = text, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun DocumentRow(doc: DocumentEntry, hint: String = doc.hint, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    recentLimit: Int,
    showRecentPaths: Boolean,
    textScale: Float,
    pageMargins: PageMargins,
    themeMode: ReaderTheme,
    onRecentLimitChange: (Int) -> Unit,
    onShowRecentPathsChange: (Boolean) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onPageMarginsChange: (PageMargins) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("最近打开显示数量：$recentLimit")
                Slider(
                    value = recentLimit.toFloat(),
                    onValueChange = { onRecentLimitChange(it.roundToInt()) },
                    valueRange = 1f..50f,
                    steps = 48,
                )
                Text("默认字号：${(textScale * 100).toInt()}%")
                Slider(
                    value = textScale,
                    onValueChange = onTextScaleChange,
                    valueRange = 0.85f..1.5f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(showRecentPaths, onCheckedChange = onShowRecentPathsChange)
                    Text("显示最近文件路径")
                }
                Spacer(Modifier.height(8.dp))
                Text("页面边距（dp）")
                MarginSlider("上", pageMargins.top) { onPageMarginsChange(pageMargins.copy(top = it)) }
                MarginSlider("下", pageMargins.bottom) { onPageMarginsChange(pageMargins.copy(bottom = it)) }
                MarginSlider("左", pageMargins.start) { onPageMarginsChange(pageMargins.copy(start = it)) }
                MarginSlider("右", pageMargins.end) { onPageMarginsChange(pageMargins.copy(end = it)) }
                Spacer(Modifier.height(8.dp))
                Text("主题")
                ThemeChoice("日间主题", ReaderTheme.DAY, themeMode, onThemeChange)
                ThemeChoice("夜间主题", ReaderTheme.NIGHT, themeMode, onThemeChange)
                ThemeChoice("护眼主题", ReaderTheme.EYE_CARE, themeMode, onThemeChange)
                Spacer(Modifier.height(12.dp))
                Text("Markdown Reader · 版本 ${BuildConfig.VERSION_NAME}")
                Text("专注本地 Markdown 阅读")
                Text("开发者：Qyforest")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun MarginSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label：$value", modifier = Modifier.width(42.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..48f,
            steps = 47,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    value: ReaderTheme,
    selected: ReaderTheme,
    onSelected: (ReaderTheme) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == value, onClick = { onSelected(value) })
        Text(label)
    }
}

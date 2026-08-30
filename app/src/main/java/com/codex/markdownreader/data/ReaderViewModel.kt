package com.codex.markdownreader.data

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val storeFile = File(application.filesDir, "reader_state.json")
    private val backupDirectory = File(application.filesDir, "document_backups")
    private val contentResolver = application.contentResolver
    private var persistenceJob: Job? = null

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun importFolder(treeUri: Uri, includeSubdirectories: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                persistTreePermission(treeUri)
                val folder = DocumentFile.fromTreeUri(getApplication(), treeUri) ?: return@launch
                val documents = withContext(Dispatchers.IO) {
                    scanFolder(folder, includeSubdirectories)
                }
                val now = System.currentTimeMillis()
                val folderName = folder.name ?: treeUri.lastPathSegment.orEmpty()
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        importedFolders = current.importedFolders + FolderEntry(
                            treeUri = treeUri.toString(),
                            displayName = folderName,
                            includeSubdirectories = includeSubdirectories,
                            importedAt = now,
                        ),
                        libraryDocuments = mergeDocuments(current.libraryDocuments, documents),
                        defaultIncludeSubdirectories = includeSubdirectories,
                    )
                }
                persistNow()
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun openDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                persistDocumentPermission(uri)
                val meta = resolveDocumentEntry(uri)
                val backupFile = backupFileFor(uri)
                val text = withContext(Dispatchers.IO) {
                    try {
                        val sourceText = readDocumentText(uri)
                        backupDirectory.mkdirs()
                        backupFile.writeText(sourceText)
                        sourceText
                    } catch (sourceError: Exception) {
                        val savedBackup = meta?.backupPath?.let(::validBackupFile)
                        if (savedBackup != null && savedBackup.exists()) {
                            savedBackup.readText()
                        } else {
                            throw sourceError
                        }
                    }
                }
                val document = (meta ?: DocumentEntry(
                    uri = uri.toString(),
                    name = guessDocumentName(uri),
                    hint = "本地文件",
                )).copy(
                    lastOpenedAt = System.currentTimeMillis(),
                    backupPath = backupFile.absolutePath,
                )
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        activeDocument = document,
                        activeMarkdown = text,
                        recentDocuments = promoteRecent(current.recentDocuments, document),
                    )
                }
                persistNow()
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun closeDocument() {
        _uiState.update { it.copy(activeDocument = null, activeMarkdown = null) }
        schedulePersist()
    }

    fun saveScrollPosition(uri: String, scrollY: Int) {
        _uiState.update { state ->
            val updatedRecent = state.recentDocuments.map {
                if (it.uri == uri) it.copy(scrollY = scrollY, lastOpenedAt = maxOf(it.lastOpenedAt, System.currentTimeMillis())) else it
            }
            val updatedActive = state.activeDocument?.takeIf { it.uri == uri }?.copy(scrollY = scrollY)
            state.copy(
                recentDocuments = updatedRecent,
                activeDocument = updatedActive ?: state.activeDocument,
            )
        }
        schedulePersist()
    }

    private fun resolveDocumentEntry(uri: Uri): DocumentEntry? {
        val uriString = uri.toString()
        return _uiState.value.libraryDocuments.firstOrNull { it.uri == uriString }
            ?: _uiState.value.recentDocuments.firstOrNull { it.uri == uriString }
    }

    private fun promoteRecent(recent: List<DocumentEntry>, opened: DocumentEntry): List<DocumentEntry> {
        return buildList {
            add(opened)
            recent.filterNot { it.uri == opened.uri }.let { addAll(it) }
        }
    }

    private fun mergeDocuments(existing: List<DocumentEntry>, incoming: List<DocumentEntry>): List<DocumentEntry> {
        val byUri = linkedMapOf<String, DocumentEntry>()
        existing.forEach { byUri[it.uri] = it }
        incoming.forEach { byUri[it.uri] = it }
        return byUri.values.sortedBy { it.name.lowercase() }
    }

    private fun scanFolder(root: DocumentFile, includeSubdirectories: Boolean): List<DocumentEntry> {
        val result = mutableListOf<DocumentEntry>()
        fun visit(file: DocumentFile, path: String) {
            if (file.isDirectory) {
                if (!includeSubdirectories && file.uri != root.uri) return
                val directoryPath = if (file.uri == root.uri) path else "$path / ${file.name.orEmpty()}"
                file.listFiles().forEach { visit(it, directoryPath) }
                return
            }
            if (!file.isFile) return
            val name = file.name ?: return
            if (!isMarkdownName(name)) return
            result += DocumentEntry(
                uri = file.uri.toString(),
                name = name,
                folderTreeUri = root.uri.toString(),
                hint = "$path / $name",
            )
        }
        val treeName = root.name ?: root.uri.lastPathSegment.orEmpty()
        visit(root, treeName)
        return result
    }

    private fun isMarkdownName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown") || lower.endsWith(".mkdn")
    }

    private fun readDocumentText(uri: Uri): String {
        contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) return ""
            return stream.bufferedReader().readText()
        }
    }

    private fun backupFileFor(uri: Uri): File {
        return File(backupDirectory, "${Integer.toHexString(uri.toString().hashCode())}.md")
    }

    private fun validBackupFile(path: String): File? {
        val file = File(path).absoluteFile
        val root = backupDirectory.absoluteFile
        return file.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    private fun guessDocumentName(uri: Uri): String {
        return queryDisplayName(uri) ?: uri.lastPathSegment.orEmpty().ifBlank { "Untitled.md" }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun persistTreePermission(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    private fun persistDocumentPermission(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    private fun loadState(): ReaderUiState {
        if (!storeFile.exists()) return ReaderUiState()
        return try {
            val store = json.decodeFromString(ReaderStore.serializer(), storeFile.readText())
                ReaderUiState(
                    importedFolders = store.importedFolders,
                    libraryDocuments = store.libraryDocuments,
                    recentDocuments = store.recentDocuments,
                    defaultIncludeSubdirectories = store.defaultIncludeSubdirectories,
                    textScale = store.textScale,
                    recentLimit = store.recentLimit.coerceIn(1, 50),
                    showRecentPaths = store.showRecentPaths,
                    pageMargins = store.pageMargins,
                    themeMode = store.themeMode,
                )
        } catch (_: Exception) {
            ReaderUiState()
        }
    }

    private fun schedulePersist() {
        persistenceJob?.cancel()
        persistenceJob = viewModelScope.launch {
            delay(250)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        val state = _uiState.value
        val store = ReaderStore(
            importedFolders = state.importedFolders,
            libraryDocuments = state.libraryDocuments,
            recentDocuments = state.recentDocuments,
            defaultIncludeSubdirectories = state.defaultIncludeSubdirectories,
            textScale = state.textScale,
            recentLimit = state.recentLimit,
            showRecentPaths = state.showRecentPaths,
            pageMargins = state.pageMargins,
            themeMode = state.themeMode,
        )
        withContext(Dispatchers.IO) {
            storeFile.writeText(json.encodeToString(ReaderStore.serializer(), store))
        }
    }

    fun setTextScale(textScale: Float) {
        _uiState.update { it.copy(textScale = textScale.coerceIn(0.85f, 1.5f)) }
        schedulePersist()
    }

    fun setRecentLimit(limit: Int) {
        _uiState.update { it.copy(recentLimit = limit.coerceIn(1, 50)) }
        schedulePersist()
    }

    fun setShowRecentPaths(show: Boolean) {
        _uiState.update { it.copy(showRecentPaths = show) }
        schedulePersist()
    }

    fun setPageMargins(margins: PageMargins) {
        _uiState.update {
            it.copy(
                pageMargins = margins.copy(
                    top = margins.top.coerceIn(0, 48),
                    bottom = margins.bottom.coerceIn(0, 48),
                    start = margins.start.coerceIn(0, 48),
                    end = margins.end.coerceIn(0, 48),
                )
            )
        }
        schedulePersist()
    }

    fun setTheme(theme: ReaderTheme) {
        _uiState.update { it.copy(themeMode = theme) }
        schedulePersist()
    }
}

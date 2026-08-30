package com.codex.markdownreader.data

import kotlinx.serialization.Serializable

@Serializable
data class ReaderStore(
    val importedFolders: List<FolderEntry> = emptyList(),
    val libraryDocuments: List<DocumentEntry> = emptyList(),
    val recentDocuments: List<DocumentEntry> = emptyList(),
    val defaultIncludeSubdirectories: Boolean = true,
    val textScale: Float = 1.0f,
    val recentLimit: Int = 10,
    val showRecentPaths: Boolean = true,
    val pageMargins: PageMargins = PageMargins(),
    val themeMode: ReaderTheme = ReaderTheme.DAY,
)

@Serializable
data class PageMargins(
    val top: Int = 24,
    val bottom: Int = 48,
    val start: Int = 24,
    val end: Int = 24,
)

@Serializable
enum class ReaderTheme { DAY, NIGHT, EYE_CARE }

@Serializable
data class FolderEntry(
    val treeUri: String,
    val displayName: String,
    val includeSubdirectories: Boolean,
    val importedAt: Long,
)

@Serializable
data class DocumentEntry(
    val uri: String,
    val name: String,
    val folderTreeUri: String? = null,
    val lastOpenedAt: Long = 0L,
    val scrollY: Int = 0,
    val hint: String = "",
    val backupPath: String? = null,
)

data class ReaderUiState(
    val isLoading: Boolean = false,
    val activeDocument: DocumentEntry? = null,
    val activeMarkdown: String? = null,
    val importedFolders: List<FolderEntry> = emptyList(),
    val libraryDocuments: List<DocumentEntry> = emptyList(),
    val recentDocuments: List<DocumentEntry> = emptyList(),
    val defaultIncludeSubdirectories: Boolean = true,
    val textScale: Float = 1.0f,
    val recentLimit: Int = 10,
    val showRecentPaths: Boolean = true,
    val pageMargins: PageMargins = PageMargins(),
    val themeMode: ReaderTheme = ReaderTheme.DAY,
)

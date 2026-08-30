package com.codex.markdownreader.data

internal fun visibleRecentDocuments(documents: List<DocumentEntry>, limit: Int): List<DocumentEntry> =
    documents.take(limit.coerceIn(1, 50))

internal fun readableDocumentHint(document: DocumentEntry, showPath: Boolean): String {
    if (!showPath) return "本地文件"
    return document.hint.ifBlank { "本地文件" }
}

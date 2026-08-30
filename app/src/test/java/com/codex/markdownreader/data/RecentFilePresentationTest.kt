package com.codex.markdownreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFilePresentationTest {
    @Test
    fun limitsRecentDocumentsToConfiguredRange() {
        val documents = (1..12).map { DocumentEntry("uri:$it", "file$it.md") }
        assertEquals(10, visibleRecentDocuments(documents, 10).size)
        assertEquals(1, visibleRecentDocuments(documents, 0).size)
        assertEquals(12, visibleRecentDocuments(documents, 60).size)
    }

    @Test
    fun usesFolderHintWhenAvailableAndFallsBackToLocalFile() {
        val withPath = DocumentEntry("uri:1", "note.md", hint = "资料 / note.md")
        val withoutPath = DocumentEntry("uri:2", "note.md")
        assertEquals("资料 / note.md", readableDocumentHint(withPath, true))
        assertEquals("本地文件", readableDocumentHint(withoutPath, true))
        assertEquals("本地文件", readableDocumentHint(withPath, false))
        assertEquals("本地文件", readableDocumentHint(withPath.copy(hint = ""), false))
    }
}

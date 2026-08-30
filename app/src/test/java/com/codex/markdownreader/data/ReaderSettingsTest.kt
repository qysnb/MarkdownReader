package com.codex.markdownreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsTest {
    @Test
    fun marginsAreKeptWithinSupportedRange() {
        val margins = PageMargins(-2, 80, 16, 49)
        val clamped = margins.copy(
            top = margins.top.coerceIn(0, 48),
            bottom = margins.bottom.coerceIn(0, 48),
            start = margins.start.coerceIn(0, 48),
            end = margins.end.coerceIn(0, 48),
        )
        assertEquals(PageMargins(0, 48, 16, 48), clamped)
    }
}

package com.codex.markdownreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomGeometryTest {
    @Test
    fun clampsPageZoomToOneThroughFiveHundredPercent() {
        assertEquals(1f, clampZoomScale(0.4f))
        assertEquals(5f, clampZoomScale(8f))
        assertEquals(2.5f, clampZoomScale(2.5f))
    }

    @Test
    fun keepsThePinchFocusPointAtTheSameViewportCoordinate() {
        val oldScroll = 320
        val focus = 180f
        val oldScale = 1f
        val newScale = 2f

        val nextScroll = anchoredScrollOffset(oldScroll, focus, oldScale, newScale)
        val oldContentPoint = (oldScroll + focus) / oldScale
        val newContentPoint = (nextScroll + focus) / newScale

        assertEquals(oldContentPoint, newContentPoint, 1f)
    }

    @Test
    fun neverReturnsNegativeScrollOffsets() {
        assertEquals(40, anchoredScrollOffset(0, 10f, 1f, 5f))
        assertEquals(2, anchoredScrollOffset(10, 0f, 5f, 1f))
        assertTrue(anchoredScrollOffset(-100, 0f, 1f, 5f) >= 0)
    }
}

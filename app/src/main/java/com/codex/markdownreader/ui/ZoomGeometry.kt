package com.codex.markdownreader.ui

import kotlin.math.roundToInt

internal const val MIN_ZOOM_SCALE = 1f
internal const val MAX_ZOOM_SCALE = 5f

internal fun clampZoomScale(scale: Float): Float =
    scale.coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)

/**
 * Returns the scroll offset that keeps the content under a pinch focus fixed
 * at the same viewport coordinate while the content scale changes.
 */
internal fun anchoredScrollOffset(
    oldScroll: Int,
    focus: Float,
    oldScale: Float,
    newScale: Float,
): Int {
    val safeOldScale = oldScale.coerceAtLeast(Float.MIN_VALUE)
    val contentPoint = (oldScroll + focus) / safeOldScale
    return (contentPoint * newScale - focus).roundToInt().coerceAtLeast(0)
}

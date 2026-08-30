package com.codex.markdownreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ReadingProgressRail(
    scrollY: Int,
    scrollRange: Int,
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color,
    visible: Boolean,
    onSeek: (Int) -> Unit,
) {
    if (scrollRange <= 0) return
    AnimatedVisibility(visible = visible, modifier = modifier) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .pointerInput(scrollRange) {
                fun seekAt(y: Float) {
                    val fraction = (y / size.height).coerceIn(0f, 1f)
                    onSeek((fraction * scrollRange).roundToInt())
                }
                detectDragGestures(
                    onDragStart = { seekAt(it.y) },
                    onDrag = { change, _ ->
                        change.consume()
                        seekAt(change.position.y)
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxHeight()) {
            val trackWidth = 5.dp.toPx()
            val center = size.width / 2f
            val fraction = (scrollY.toFloat() / scrollRange).coerceIn(0f, 1f)
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.14f),
                topLeft = androidx.compose.ui.geometry.Offset(center - trackWidth / 2f, 12.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(trackWidth, size.height - 24.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f)
            )
            val trackHeight = size.height - 24.dp.toPx()
            val thumbHeight = 52.dp.toPx().coerceAtMost(trackHeight)
            drawRoundRect(
                color = accentColor,
                topLeft = androidx.compose.ui.geometry.Offset(
                    center - trackWidth / 2f,
                    12.dp.toPx() + (trackHeight - thumbHeight) * fraction
                ),
                size = androidx.compose.ui.geometry.Size(trackWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f)
            )
        }
    }
    }
}

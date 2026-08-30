package com.codex.markdownreader.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.NestedScrollView
import android.util.TypedValue
import android.graphics.Paint
import android.text.Spannable
import android.text.Spanned
import com.codex.markdownreader.data.DocumentEntry
import com.codex.markdownreader.data.PageMargins
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.latex.JLatexAsyncDrawableSpan
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.inlineparser.InlineProcessor
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import org.commonmark.node.Node
import io.noties.markwon.ext.latex.JLatexMathNode
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun MarkdownReaderView(
    modifier: Modifier = Modifier,
    document: DocumentEntry,
    markdown: String,
    savedScrollY: Int,
    textScale: Float,
    zoomScale: Float,
    onZoomScaleChange: (Float) -> Unit,
    textColor: Int,
    pageMargins: PageMargins,
    onScroll: (Int) -> Unit,
    onScrollMetrics: (scrollY: Int, scrollRange: Int) -> Unit,
    seekTo: Int?,
    onSeekHandled: () -> Unit,
) {
    val context = LocalContext.current
    val markwon = remember(context, textScale, textColor) { createMarkwon(context, textScale, textColor) }
    val initialScrollApplied = remember(document.uri) { mutableStateOf(false) }
    val latestOnScroll = rememberUpdatedState(onScroll)
    val latestOnZoomScaleChange = rememberUpdatedState(onZoomScaleChange)
    val scrollHandler = remember(document.uri) { Handler(Looper.getMainLooper()) }
    val pendingScroll = remember(document.uri) { mutableStateOf(savedScrollY) }
    val reportScroll = remember(document.uri) {
        Runnable { latestOnScroll.value(pendingScroll.value) }
    }

    LaunchedEffect(document.uri) {
        initialScrollApplied.value = false
    }

    DisposableEffect(document.uri) {
        onDispose {
            scrollHandler.removeCallbacksAndMessages(null)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            createReaderView(it, textScale, textColor, pageMargins) { scale ->
                latestOnZoomScaleChange.value(scale)
            }
        },
        update = { scrollView ->
            val zoomContent = scrollView.getChildAt(0) as ZoomContentLayout
            val content = zoomContent.getChildAt(0) as TextView
            val normalizedMarkdown = normalizeInlineLatex(markdown)
            val renderKey = "$textScale\u0000$textColor\u0000$normalizedMarkdown"
            if (content.tag != renderKey) {
                content.tag = renderKey
                markwon.setMarkdown(content, normalizedMarkdown)
                repairInlineLatexMetrics(content, markwon)
            }
            if (content.currentTextColor != textColor) {
                content.setTextColor(textColor)
            }
            content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * textScale)
            applyReaderPadding(content, pageMargins)
            zoomContent.contentScale = clampZoomScale(zoomScale)
            if (!initialScrollApplied.value) {
                scrollView.post {
                    if (savedScrollY > 0) {
                        scrollView.scrollTo(0, savedScrollY)
                    }
                    initialScrollApplied.value = true
                }
            }
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                pendingScroll.value = scrollY
                onScrollMetrics(scrollY, scrollRange(scrollView))
                scrollHandler.removeCallbacks(reportScroll)
                scrollHandler.postDelayed(reportScroll, 150)
            }
            scrollView.post { onScrollMetrics(scrollView.scrollY, scrollRange(scrollView)) }
            if (seekTo != null) {
                scrollView.post {
                    scrollView.scrollTo(0, seekTo.coerceIn(0, scrollRange(scrollView)))
                    onSeekHandled()
                }
            }
        }
    )
}

private fun scrollRange(scrollView: NestedScrollView): Int {
    val child = scrollView.getChildAt(0) ?: return 0
    return (child.height - scrollView.height).coerceAtLeast(0)
}

/**
 * Markwon 4.6.2 sets the inline LaTeX span's font-metric bottom to zero.
 * That asymmetric metric shifts lines containing an inline formula downward.
 */
private fun repairInlineLatexMetrics(textView: TextView, markwon: Markwon) {
    val spanned = textView.text as? Spanned ?: return
    val inlineSpans = spanned.getSpans(0, spanned.length, JLatexAsyncDrawableSpan::class.java)
        .filter { it.javaClass.simpleName == "JLatexInlineAsyncDrawableSpan" }
    if (inlineSpans.isEmpty()) return

    AsyncDrawableScheduler.unschedule(textView)
    val editable = spanned as? Spannable ?: return
    inlineSpans.forEach { original ->
        val start = spanned.getSpanStart(original)
        val end = spanned.getSpanEnd(original)
        val flags = spanned.getSpanFlags(original)
        val replacement = InlineLatexMetricsSpan(
            markwon.configuration().theme(),
            original.getDrawable()
        )
        editable.removeSpan(original)
        editable.setSpan(replacement, start, end, flags)
    }

    // Markwon already used this text hash before the replacement. Clear it so the
    // scheduler also attaches callbacks to the replacement spans.
    textView.setTag(io.noties.markwon.R.id.markwon_drawables_scheduler_last_text_hashcode, null)
    AsyncDrawableScheduler.schedule(textView)
}

private class InlineLatexMetricsSpan(
    theme: io.noties.markwon.core.MarkwonTheme,
    drawable: io.noties.markwon.image.AsyncDrawable,
) : AsyncDrawableSpan(theme, drawable, AsyncDrawableSpan.ALIGN_CENTER, false) {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val size = super.getSize(paint, text, start, end, fm)
        if (fm != null) {
            fm.bottom = fm.descent
        }
        return size
    }
}

private fun createReaderView(
    context: Context,
    textScale: Float,
    textColor: Int,
    pageMargins: PageMargins,
    onZoomScaleChange: (Float) -> Unit,
): NestedScrollView {
    val scroll = ReaderScrollView(context)
    val zoomContent = ZoomContentLayout(context)
    val text = TextView(context).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * textScale)
        setLineSpacing(0f, 1.35f)
        applyReaderPadding(this, pageMargins)
        setTextIsSelectable(true)
        movementMethod = android.text.method.LinkMovementMethod.getInstance()
    }
    zoomContent.addView(text)
    scroll.addView(
        zoomContent,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = clampZoomScale(detector.scaleFactor * zoomContent.contentScale)
            zoomContent.setContentScale(next, detector.focusX, detector.focusY, scroll)
            onZoomScaleChange(next)
            return true
        }
    })
    scaleDetector.setQuickScaleEnabled(false)
    scroll.setScaleGestureDetector(scaleDetector)
    return scroll
}

private class ReaderScrollView(context: Context) : NestedScrollView(context) {
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var scaling = false
    private var lastX = 0f
    private var lastY = 0f
    private var horizontalDrag = false

    fun setScaleGestureDetector(detector: ScaleGestureDetector) {
        scaleGestureDetector = detector
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val detector = scaleGestureDetector
        if (detector == null) return super.dispatchTouchEvent(event)

        val wasScaling = scaling
        detector.onTouchEvent(event)
        scaling = detector.isInProgress || (event.pointerCount > 1 && event.actionMasked != MotionEvent.ACTION_UP)

        if (!wasScaling && scaling) {
            // Stop a pending text selection or scroll gesture before the scale takes over.
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }

        if (wasScaling || scaling) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                scaling = false
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                horizontalDrag = false
            }
            MotionEvent.ACTION_MOVE -> {
                val child = getChildAt(0)
                val canPanHorizontally = child != null && child.width > width
                if (canPanHorizontally && kotlin.math.abs(event.x - lastX) > kotlin.math.abs(event.y - lastY)) {
                    horizontalDrag = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> horizontalDrag = false
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (horizontalDrag) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val delta = (lastX - event.x).roundToInt()
                    scrollBy(delta, 0)
                    lastX = event.x
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> horizontalDrag = false
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}

private class ZoomContentLayout(context: Context) : android.widget.FrameLayout(context) {
    private var pendingScrollX = 0
    private var pendingScrollY = 0
    private var hasPendingScroll = false
    private val applyPendingScroll = Runnable {
        val parentScroll = parent as? NestedScrollView ?: return@Runnable
        parentScroll.scrollTo(pendingScrollX, pendingScrollY)
        hasPendingScroll = false
    }

    var contentScale: Float = 1f
        set(value) {
            val normalized = clampZoomScale(value)
            if (field != normalized) {
                field = normalized
                requestLayout()
                applyChildScale()
            }
        }

    fun setContentScale(scale: Float, focusX: Float, focusY: Float, scroll: NestedScrollView) {
        val oldScale = contentScale
        val nextScale = clampZoomScale(scale)
        if (oldScale == nextScale) return
        val baseScrollX = if (hasPendingScroll) pendingScrollX else scroll.scrollX
        val baseScrollY = if (hasPendingScroll) pendingScrollY else scroll.scrollY
        val nextScrollX = anchoredScrollOffset(baseScrollX, focusX, oldScale, nextScale)
        val nextScrollY = anchoredScrollOffset(baseScrollY, focusY, oldScale, nextScale)
        contentScale = nextScale
        pendingScrollX = nextScrollX
        pendingScrollY = nextScrollY
        hasPendingScroll = true
        scroll.removeCallbacks(applyPendingScroll)
        scroll.post(applyPendingScroll)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(1)
        val child = getChildAt(0)
        if (child == null) {
            setMeasuredDimension(0, 0)
            return
        }
        child.measure(
            MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        setMeasuredDimension(
            ceil(child.measuredWidth * contentScale + paddingLeft + paddingRight).toInt(),
            ceil(child.measuredHeight * contentScale + paddingTop + paddingBottom).toInt(),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        child.layout(paddingLeft, paddingTop, paddingLeft + child.measuredWidth, paddingTop + child.measuredHeight)
        applyChildScale()
    }

    private fun applyChildScale() {
        val child = getChildAt(0) ?: return
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = contentScale
        child.scaleY = contentScale
    }
}

private fun applyReaderPadding(textView: TextView, margins: PageMargins) {
    val density = textView.resources.displayMetrics.density
    fun px(dp: Int) = (dp * density).roundToInt()
    textView.setPadding(px(margins.start), px(margins.top), px(margins.end), px(margins.bottom))
}

private fun createMarkwon(context: Context, textScale: Float, textColor: Int): Markwon {
    // JLatexMathPlugin accepts pixels, while the reader's user setting is expressed in sp.
    val formulaTextSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        20f * textScale,
        context.resources.displayMetrics
    )

    return Markwon.builder(context)
        .usePlugin(
            MarkwonInlineParserPlugin.create { factoryBuilder ->
                factoryBuilder.addInlineProcessor(LatexInlineProcessor())
            }
        )
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(
            JLatexMathPlugin.create(
                formulaTextSize,
                formulaTextSize,
                object : JLatexMathPlugin.BuilderConfigure {
                    override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                        builder.theme()
                            .inlineTextColor(textColor)
                            .blockTextColor(textColor)
                        builder.blocksEnabled(true)
                        builder.blocksLegacy(false)
                        builder.inlinesEnabled(true)
                    }
                }
            )
        )
        .build()
}

/** Handles both common inline delimiter forms before ordinary Markdown text parsing. */
internal class LatexInlineProcessor : InlineProcessor() {
    private val math = Pattern.compile("^\\$\\$([\\s\\S]+?)\\$\\$|^\\$(?!\\$)([\\s\\S]+?)(?<!\\\\)\\$(?!\\$)")

    override fun specialCharacter(): Char = '$'

    override fun parse(): Node? {
        val matched = match(math) ?: return null
        val latex = if (matched.startsWith("$$")) {
            matched.substring(2, matched.length - 2)
        } else {
            matched.substring(1, matched.length - 1)
        }
        if (latex.isBlank()) return null
        val node = JLatexMathNode()
        node.latex(latex)
        return node
    }
}

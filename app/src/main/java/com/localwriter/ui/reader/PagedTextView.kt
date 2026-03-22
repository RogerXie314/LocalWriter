package com.localwriter.ui.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 成熟阅读器页面视图（参照番茄小说/掌阅方案）。
 *
 * ┌────────────────────── 全屏 ───────────────────────┐
 * │  ░░░░ contentPaddingTop（信息栏/工具栏覆盖区） ░░░░  │
 * │  ┌──────────────────────────────────────────────┐  │
 * │  │         文字内容区（永远不变尺寸）             │  │
 * │  └──────────────────────────────────────────────┘  │
 * │  ░░░░ contentPaddingBottom（底部信息栏覆盖区）░░░░  │
 * └───────────────────────────────────────────────────┘
 *
 * 核心不变量：
 *  - contentPaddingTop/Bottom 由 Activity 从 WindowInsets 一次性设定，之后永不改变
 *  - 因此 pageWidth/pageHeight 永不改变 → pageBreaks 永远有效 → 零漂移零闪烁
 *  - 工具栏/信息栏均为 FrameLayout 覆盖层，与本 View 完全独立
 */
class PagedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ─── 内容区边距（setContentPadding 之后永不改变） ───────────────────

    var contentPaddingTop = 0
        private set
    var contentPaddingBottom = 0
        private set
    private val contentPaddingHoriz: Int
        get() = (20 * resources.displayMetrics.density + 0.5f).toInt()

    // ─── 文字配置 ──────────────────────────────────────────────────────

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        typeface = Typeface.DEFAULT
        letterSpacing = 0.02f
    }
    private var lineSpacingMult = 1.55f
    private var bgColor = 0xFFF8F3E3.toInt()

    // ─── 分页状态 ──────────────────────────────────────────────────────

    /** StaticLayout 包含完整章节文字（含缩进标题） */
    private var staticLayout: StaticLayout? = null

    /** 每页起始行号，由 buildPages() 计算，pageBreaks.size = 总页数 */
    private var pageBreaks = intArrayOf(0)

    var currentPage = 0
        private set
    val totalPages get() = pageBreaks.size

    // ─── 翻页动画 ──────────────────────────────────────────────────────

    private var isAnimating = false
    private var animBitmap: Bitmap? = null
    private var animTranslation = 0f

    // ─── 回调 ─────────────────────────────────────────────────────────

    var onPageChanged: ((page: Int, total: Int) -> Unit)? = null
    var onCenterTap: (() -> Unit)? = null
    var onPrevChapter: (() -> Unit)? = null
    var onNextChapter: (() -> Unit)? = null

    // ─── 手势 ─────────────────────────────────────────────────────────

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val w = width.toFloat()
                when {
                    e.x < w / 3f      -> flipPage(-1)
                    e.x > w * 2f / 3f -> flipPage(1)
                    else              -> onCenterTap?.invoke()
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velX: Float, velY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dx) > Math.abs(dy) &&
                    Math.abs(dx) > 80 &&
                    Math.abs(velX) > 200) {
                    flipPage(if (dx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })

    // ─── 公开 API ─────────────────────────────────────────────────────

    /**
     * 由 Activity 在 WindowInsets 回调中调用一次。
     * 之后 pageWidth/pageHeight 固定，buildPages() 的结果永远有效。
     */
    fun setContentPadding(top: Int, bottom: Int) {
        contentPaddingTop = top
        contentPaddingBottom = bottom
        if (width > 0) buildPages(preservePage = false)
    }

    fun setTextConfig(textSizePx: Float, lineSpacing: Float, textColor: Int) {
        textPaint.textSize = textSizePx
        textPaint.color = textColor
        lineSpacingMult = lineSpacing
        if (width > 0 && staticLayout != null) rebuildPages()
    }

    fun setBgColor(color: Int) {
        bgColor = color
        setBackgroundColor(color)
    }

    /**
     * 加载章节文字，startPage = 恢复阅读位置用的页码。
     * 在任何时候调用都安全（包括 contentPadding 尚未设定时）。
     */
    fun loadChapter(title: String, content: String, startPage: Int = 0) {
        val sb = SpannableStringBuilder()
        // 章节标题（加粗，略大字号）
        val titleLine = "\u3000\u3000$title\n\n"
        sb.append(titleLine)
        sb.setSpan(StyleSpan(Typeface.BOLD), 0, titleLine.length - 2, 0)
        sb.setSpan(RelativeSizeSpan(1.1f),  0, titleLine.length - 2, 0)
        // 正文（每段首行两字缩进）
        content.lines().forEach { line ->
            val trimmed = line.trimEnd()
            if (trimmed.isNotEmpty()) {
                sb.append("\u3000\u3000$trimmed\n")
            } else {
                sb.append("\n")
            }
        }
        buildPages(rawText = sb, startPage = startPage)
    }

    val currentPageScrollPercent: Float
        get() = if (totalPages <= 1) 0f else currentPage.toFloat() / (totalPages - 1)

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, maxOf(0, totalPages - 1))
        currentPage = clamped
        invalidate()
        onPageChanged?.invoke(currentPage, totalPages)
    }

    fun goToLastPage() = goToPage(totalPages - 1)

    // ─── 翻页（公开，供音量键等调用） ────────────────────────────────

    fun flipPage(direction: Int) {
        if (isAnimating) return
        val target = currentPage + direction
        if (target < 0)            { onPrevChapter?.invoke(); return }
        if (target >= totalPages)  { onNextChapter?.invoke(); return }

        val sl = staticLayout ?: run { goToPage(target); return }

        // 截取当前页位图
        val bmp = Bitmap.createBitmap(
            width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.RGB_565
        ).also { b ->
            val c = Canvas(b)
            c.drawColor(bgColor)
            drawPageOnCanvas(c, sl, currentPage)
        }

        // 切换到目标页（在位图遮挡下完成）
        currentPage = target
        invalidate()
        onPageChanged?.invoke(currentPage, totalPages)

        // 滑动旧页位图离开屏幕
        animBitmap = bmp
        animTranslation = 0f
        isAnimating = true
        val targetX = -width.toFloat() * direction
        ValueAnimator.ofFloat(0f, targetX).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animTranslation = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    animBitmap?.recycle(); animBitmap = null
                    invalidate()
                }
            })
            start()
        }
    }

    // ─── View 生命周期 ────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (staticLayout != null) rebuildPages()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDraw(canvas: Canvas) {
        val sl = staticLayout ?: return

        // 绘制当前页正文
        drawPageOnCanvas(canvas, sl, currentPage)

        // 翻页动画：旧页位图滑出
        if (isAnimating) {
            animBitmap?.let { canvas.drawBitmap(it, animTranslation, 0f, null) }
        }
    }

    // ─── 内部：分页 ───────────────────────────────────────────────────

    private var pendingText: CharSequence? = null
    private var pendingStartPage = 0

    private fun buildPages(
        rawText: CharSequence? = null,
        startPage: Int = 0,
        preservePage: Boolean = true
    ) {
        if (rawText != null) {
            pendingText = rawText
            pendingStartPage = startPage
        }
        val text = pendingText ?: return
        val w = width - 2 * contentPaddingHoriz
        val pageH = height - contentPaddingTop - contentPaddingBottom
        if (w <= 0 || pageH <= 0) return  // 尺寸未就绪，等 onSizeChanged 触发

        val sl = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, w)
            .setLineSpacing(0f, lineSpacingMult)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        staticLayout = sl

        // 逐行累加，超过 pageH 就新起一页
        val breaks = mutableListOf(0)
        var usedH = 0
        for (line in 0 until sl.lineCount) {
            val lineH = sl.getLineBottom(line) - sl.getLineTop(line)
            if (usedH + lineH > pageH && line > breaks.last()) {
                breaks.add(line)
                usedH = lineH
            } else {
                usedH += lineH
            }
        }
        pageBreaks = breaks.toIntArray()

        currentPage = if (preservePage) currentPage.coerceIn(0, maxOf(0, breaks.size - 1))
                      else pendingStartPage.coerceIn(0, maxOf(0, breaks.size - 1))
        invalidate()
        onPageChanged?.invoke(currentPage, totalPages)
    }

    private fun rebuildPages() = buildPages(preservePage = true)

    // ─── 内部：绘制单页 ───────────────────────────────────────────────

    private fun drawPageOnCanvas(canvas: Canvas, sl: StaticLayout, page: Int) {
        if (pageBreaks.isEmpty()) return
        val p = page.coerceIn(0, pageBreaks.size - 1)
        val startLine = pageBreaks[p]
        val endLine   = if (p + 1 < pageBreaks.size) pageBreaks[p + 1] else sl.lineCount
        val startY    = sl.getLineTop(startLine).toFloat()
        val pageH     = (height - contentPaddingTop - contentPaddingBottom).toFloat()

        canvas.save()
        // 平移使 startLine 的顶部恰好落在 contentPaddingTop
        canvas.translate(
            contentPaddingHoriz.toFloat(),
            contentPaddingTop.toFloat() - startY
        )
        // 裁剪到本页文字区域（StaticLayout.draw 会画所有行，clip 限制范围）
        canvas.clipRect(
            -contentPaddingHoriz.toFloat(),
            startY,
            (width - contentPaddingHoriz).toFloat(),
            startY + pageH
        )
        sl.draw(canvas)
        canvas.restore()
    }
}

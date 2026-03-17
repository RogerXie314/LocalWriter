package com.localwriter.ui.auth

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 九宫格手势密码视图
 *
 * 功能：
 *  - 绘制 3×3 点阵
 *  - 手指滑动经过节点时顺序记录路径
 *  - 抬手后通过 [OnPatternListener] 通知调用方
 *  - 支持"成功"橙色 / "错误"红色状态反馈，并在短暂延迟后自动重置
 *
 * 接入方式（XML）：
 *  <com.localwriter.ui.auth.GesturePatternView
 *      android:id="@+id/gestureLockView"
 *      android:layout_width="match_parent"
 *      android:layout_height="match_parent" />
 */
class GesturePatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ─── 常量 ─────────────────────────────────────────────────────────────

    /** 最短有效路径（节点数）*/
    val minNodes = 4

    // 颜色定义
    private val colorNormal       = 0xFFBCAAA4.toInt()  // 米灰
    private val colorSelected     = 0xFF8D6E63.toInt()  // 品牌棕
    private val colorSuccess      = 0xFF6A9E5E.toInt()  // 绿色
    private val colorError        = 0xFFE57373.toInt()  // 红色
    private val colorLineNormal   = 0x608D6E63.toInt()  // 半透明棕
    private val colorLineSuccess  = 0x606A9E5E.toInt()  // 半透明绿
    private val colorLineError    = 0x60E57373.toInt()  // 半透明红
    private val colorInnerSelected = 0xFFFFFFFF.toInt() // 选中节点内圆白色

    // ─── 状态 ─────────────────────────────────────────────────────────────

    enum class State { NORMAL, GESTURE_IN_PROGRESS, SUCCESS, ERROR }

    private var state = State.NORMAL
    private val selectedNodes    = mutableListOf<Int>()
    private val nodePositions    = Array(9) { PointF() }
    private var touchX = 0f
    private var touchY = 0f
    private var isDrawingLine = false

    // ─── 画笔 ──────────────────────────────────────────────────────────────

    private val dotPaintNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorNormal
    }
    private val dotPaintSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorSelected
    }
    private val dotPaintSuccess = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorSuccess
    }
    private val dotPaintError = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorError
    }
    private val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorInnerSelected
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = colorSelected
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = colorLineNormal
    }
    private val linePath = Path()

    // ─── 尺寸（在 onSizeChanged 中计算）─────────────────────────────────

    private var dotRadius       = 0f
    private var innerDotRadius  = 0f
    private var outerRingRadius = 0f
    private var hitRadius       = 0f

    // ─── 监听器 ─────────────────────────────────────────────────────────────

    interface OnPatternListener {
        fun onPatternStarted()
        fun onPatternComplete(pattern: List<Int>)
        fun onPatternTooShort()
    }

    var listener: OnPatternListener? = null

    // ─── 生命周期 ─────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size    = min(w, h).toFloat()
        val cell    = size / 3f
        // 将格子整体居中：offsetX/Y 补偿宽高不等时剩余空间
        val offsetX = (w - size) / 2f
        val offsetY = (h - size) / 2f

        for (row in 0..2) {
            for (col in 0..2) {
                nodePositions[row * 3 + col].set(
                    offsetX + col * cell + cell / 2f,
                    offsetY + row * cell + cell / 2f
                )
            }
        }

        dotRadius       = size * 0.055f
        innerDotRadius  = size * 0.028f
        outerRingRadius = size * 0.085f
        hitRadius       = size * 0.11f

        linePaint.strokeWidth = size * 0.018f
        ringPaint.strokeWidth = size * 0.012f
    }

    override fun onDraw(canvas: Canvas) {
        drawLines(canvas)
        drawNodes(canvas)
    }

    private fun drawLines(canvas: Canvas) {
        if (selectedNodes.isEmpty()) return

        val lineColor = when (state) {
            State.SUCCESS -> colorLineSuccess
            State.ERROR   -> colorLineError
            else          -> colorLineNormal
        }
        linePaint.color = lineColor

        linePath.reset()
        val first = nodePositions[selectedNodes[0]]
        linePath.moveTo(first.x, first.y)
        for (i in 1 until selectedNodes.size) {
            val pt = nodePositions[selectedNodes[i]]
            linePath.lineTo(pt.x, pt.y)
        }
        if (isDrawingLine && state == State.GESTURE_IN_PROGRESS) {
            linePath.lineTo(touchX, touchY)
        }
        canvas.drawPath(linePath, linePaint)
    }

    private fun drawNodes(canvas: Canvas) {
        for (i in 0..8) {
            val p   = nodePositions[i]
            val sel = i in selectedNodes

            val dotPaint = when {
                sel && state == State.SUCCESS -> dotPaintSuccess
                sel && state == State.ERROR   -> dotPaintError
                sel                           -> dotPaintSelected
                else                          -> dotPaintNormal
            }

            // 外环（仅选中节点显示）
            if (sel) {
                ringPaint.color = dotPaint.color
                ringPaint.alpha = 80
                canvas.drawCircle(p.x, p.y, outerRingRadius, ringPaint)
            }

            // 实心圆
            canvas.drawCircle(p.x, p.y, dotRadius, dotPaint)

            // 选中状态：中心白点
            if (sel) {
                canvas.drawCircle(p.x, p.y, innerDotRadius, innerDotPaint)
            }
        }
    }

    // ─── 触摸处理 ──────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 结果状态期间忽略触摸
        if (state == State.SUCCESS || state == State.ERROR) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                reset(animate = false)
                // 禁止父级（ScrollView 等）拦截后续 MOVE 事件，保证手势滑动不被截断
                parent?.requestDisallowInterceptTouchEvent(true)
                val node = findNodeNear(event.x, event.y)
                if (node >= 0) {
                    selectedNodes.add(node)
                    state = State.GESTURE_IN_PROGRESS
                    isDrawingLine = true
                    touchX = event.x
                    touchY = event.y
                    listener?.onPatternStarted()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                if (isDrawingLine) {
                    val node = findNodeNear(event.x, event.y)
                    if (node >= 0 && node !in selectedNodes) {
                        selectedNodes.add(node)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 恢复父级正常的事件拦截
                parent?.requestDisallowInterceptTouchEvent(false)
                isDrawingLine = false
                if (selectedNodes.isNotEmpty()) {
                    if (selectedNodes.size >= minNodes) {
                        listener?.onPatternComplete(selectedNodes.toList())
                    } else {
                        showError()
                        listener?.onPatternTooShort()
                    }
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findNodeNear(x: Float, y: Float): Int {
        for (i in 0..8) {
            val dx = x - nodePositions[i].x
            val dy = y - nodePositions[i].y
            if (sqrt(dx * dx + dy * dy) <= hitRadius) return i
        }
        return -1
    }

    // ─── 公开方法（供 Fragment 调用）──────────────────────────────────────

    /** 重置为初始状态 */
    fun reset(animate: Boolean = true) {
        state = State.NORMAL
        selectedNodes.clear()
        isDrawingLine = false
        invalidate()
    }

    /** 显示错误（变红，1 秒后自动重置）*/
    fun showError() {
        state = State.ERROR
        invalidate()
        postDelayed({ reset() }, 1000)
    }

    /** 显示成功（变绿，1 秒后自动重置）*/
    fun showSuccess() {
        state = State.SUCCESS
        invalidate()
        postDelayed({ reset() }, 700)
    }

    /** 获取当前路径（节点索引列表，"0-1-2-…" 格式字符串）*/
    fun getPatternString(): String = selectedNodes.joinToString(separator = "-")
}

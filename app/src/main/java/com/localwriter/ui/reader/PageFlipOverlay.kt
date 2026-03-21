package com.localwriter.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 翻页覆盖层
 *
 * 工作原理：
 * 1. 调用方在翻页前截取当前页面为 [frontBitmap]
 * 2. ScrollView 跳转到目标位置（新页已在背景渲染）
 * 3. 本 View 显示在顶层，通过父级动画（translationX）平滑水平滑出
 * 4. 动画结束后 visibility = GONE
 */
class PageFlipOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var frontBitmap: Bitmap? = null

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val bmp = frontBitmap ?: return
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
    }
}

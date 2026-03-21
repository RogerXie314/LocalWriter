package com.localwriter.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * 仿真翻页覆盖层
 *
 * 工作原理：
 * 1. 调用方在翻页前截取当前页面为 [frontBitmap]
 * 2. ScrollView 跳转到目标位置（新页已在背景渲染）
 * 3. 本 View 显示在顶层，用 Camera 3D 旋转动画使 frontBitmap "翻走"
 * 4. 动画结束后 visibility = GONE
 *
 * [flipDirection]  1 = 向前翻（从右往左）；-1 = 向后翻（从左往右）
 * [flipProgress]   0f = 完整显示；1f = 已翻走（View 变为不可见）
 */
class PageFlipOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var frontBitmap: Bitmap? = null
    var flipProgress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    var flipDirection: Int = 1

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val camera = Camera()
    private val matrix = Matrix()

    override fun onDraw(canvas: Canvas) {
        val bmp = frontBitmap ?: return
        if (flipProgress >= 1f) {
            visibility = GONE
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val prog = flipProgress.coerceIn(0f, 1f)

        // 旋转角：0° → 90°，使用二次缓入让翻页后段加速（更自然）
        val easedProg = prog * prog
        val angle = easedProg * 90f

        // 透视参数：数值越大越接近正投影，越小透视越强（通常 -8 到 -20）
        camera.save()
        camera.setLocation(0f, 0f, -12f)
        if (flipDirection > 0) {
            // 向前翻：绕右边缘旋转（pivot = 右边 → 页面向左倒）
            camera.rotateY(angle)
        } else {
            // 向后翻：绕左边缘旋转（pivot = 左边 → 页面向右倒）
            camera.rotateY(-angle)
        }
        camera.getMatrix(matrix)
        camera.restore()

        // 将矩阵原点从画布左上角移到翻转轴
        val pivotX = if (flipDirection > 0) w else 0f
        matrix.preTranslate(-pivotX, -h / 2f)
        matrix.postTranslate(pivotX, h / 2f)

        canvas.save()
        canvas.concat(matrix)

        // 绘制页面位图
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)

        // 在翻转轴靠近侧绘制渐变阴影，增加立体感
        val shadowWidth = (width * 0.06f * (1f - prog)).coerceAtLeast(4f)
        if (flipDirection > 0) {
            // 翻转轴在左侧（页面正在向左消失）：阴影在左边缘
            shadowPaint.shader = LinearGradient(
                0f, 0f, shadowWidth, 0f,
                intArrayOf(0x55000000, 0x00000000),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, shadowWidth, h, shadowPaint)
        } else {
            // 翻转轴在右侧（页面正在向右消失）：阴影在右边缘
            shadowPaint.shader = LinearGradient(
                w - shadowWidth, 0f, w, 0f,
                intArrayOf(0x00000000, 0x55000000),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(w - shadowWidth, 0f, w, h, shadowPaint)
        }

        canvas.restore()
    }
}

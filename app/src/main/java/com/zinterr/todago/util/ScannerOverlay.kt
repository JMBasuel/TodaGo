package com.zinterr.todago.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class ScannerOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val frameRect = RectF()
    private val maskPaint = Paint().apply {
        color = "#99000000".toColorInt()
    }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint().apply {
        color = "#66FFFFFF".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val rectSize = width * 0.8f
        val left = (width - rectSize) / 2
        val top = (height - rectSize) / 3
        val right = left + rectSize
        val bottom = top + rectSize
        frameRect.set(left, top, right, bottom)
        canvas.drawRect(0f, 0f, width, height, maskPaint)
        canvas.drawRoundRect(frameRect, 20f, 20f, clearPaint)
        canvas.drawRoundRect(frameRect, 20f, 20f, borderPaint)
    }
}
package com.example.faceapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.toColorInt

class OvalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val ovalPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val path = Path()

    private var backgroundColor = "#B0000000".toColorInt() // Полупрозрачный черный фон по умолчанию
    private var ovalColor = Color.TRANSPARENT // Цвет овала по умолчанию
    private var ovalWidth = 300f // Размер овала по умолчанию
    private var ovalHeight = 450f // Размер овала по умолчанию

    init {
        context.withStyledAttributes(attrs, R.styleable.OvalOverlayView, defStyleAttr, 0) {
            backgroundColor = getColor(R.styleable.OvalOverlayView_backgroundColor, backgroundColor)
            ovalColor = getColor(R.styleable.OvalOverlayView_ovalColor, ovalColor)
            ovalWidth = getDimension(R.styleable.OvalOverlayView_ovalWidth, ovalWidth)
            ovalHeight = getDimension(R.styleable.OvalOverlayView_ovalHeight, ovalHeight)
        }

        backgroundPaint.color = backgroundColor
        ovalPaint.color = ovalColor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Рисуем полупрозрачный фон
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Рисуем овал с прозрачной областью внутри
        val left = (width - ovalWidth) / 2
        val top = (height - ovalHeight) / 2
        val right = left + ovalWidth
        val bottom = top + ovalHeight

        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addOval(left, top, right, bottom, Path.Direction.CCW)
        path.setFillType(Path.FillType.INVERSE_WINDING)

        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Рисуем прозрачную область внутри овала
        canvas.drawPath(path, clearPaint)

        // Рисуем овал с заданным цветом
        path.reset()
        path.addOval(left, top, right, bottom, Path.Direction.CW)
        canvas.drawPath(path, ovalPaint)
    }
}
package com.miaadrajabi.fetch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * The collapsed floating control. A copper arc shows the active download.
 */
class BubbleOrb @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var sweep: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 360f)
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C232D.toInt() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF33404E.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFE2A15A.toInt()
    }
    private val oval = RectF()
    private val icon = ContextCompat.getDrawable(context, R.drawable.ic_stat_arrow)

    override fun onDraw(canvas: Canvas) {
        val stroke = width * 0.06f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        val inset = stroke / 2f + 1f
        oval.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(oval, fill)
        canvas.drawOval(oval, track)
        if (sweep > 0f) {
            canvas.drawArc(oval, -90f, sweep, false, arc)
        }
        val drawable = icon ?: return
        val size = (width * 0.42f).toInt()
        val left = (width - size) / 2
        val top = (height - size) / 2
        drawable.setBounds(left, top, left + size, top + size)
        drawable.draw(canvas)
    }
}

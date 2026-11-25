package com.clestial.keyboard.math

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.clestial.keyboard.R
import kotlin.math.abs
import kotlin.math.max

/**
 * Kanvas sketsa koordinat Kartesius sederhana untuk memvisualisasikan garis lurus
 * ax + by + c = 0 beserta titik potong sumbu-x, sumbu-y, dan titik acuan P.
 */
class LineSketchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var plot: LinePlotSpec? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        color = 0xFF0A84FF.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f }

    fun setPlot(spec: LinePlotSpec?) {
        plot = spec
        visibility = if (spec == null) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val spec = plot ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val fg = ContextCompat.getColor(context, R.color.fg)
        val muted = ContextCompat.getColor(context, R.color.muted)
        bgPaint.color = ContextCompat.getColor(context, R.color.bg)
        gridPaint.color = (muted and 0x00FFFFFF) or (0x33 shl 24)
        axisPaint.color = muted
        textPaint.color = fg
        textPaint.textSize = resources.displayMetrics.density * 11f

        canvas.drawRoundRect(0f, 0f, w, h, resources.displayMetrics.density * 10f, resources.displayMetrics.density * 10f, bgPaint)

        val cx = w / 2f
        val cy = h / 2f
        var maxCoord = 5.0
        spec.xIntVal?.let { if (it.isFinite()) maxCoord = max(maxCoord, abs(it) * 1.35) }
        spec.yIntVal?.let { if (it.isFinite()) maxCoord = max(maxCoord, abs(it) * 1.35) }
        spec.pointX?.let { if (it.isFinite()) maxCoord = max(maxCoord, abs(it) * 1.35) }
        spec.pointY?.let { if (it.isFinite()) maxCoord = max(maxCoord, abs(it) * 1.35) }
        val pad = resources.displayMetrics.density * 18f
        val scale = (minOf(w, h) / 2f - pad).coerceAtLeast(10f) / maxCoord.toFloat()

        fun sx(x: Double): Float = cx + (x * scale).toFloat()
        fun sy(y: Double): Float = cy - (y * scale).toFloat()

        // Garis kisi tipis & sumbu x/y
        val step = max(1, (maxCoord / 4).toInt())
        var g = -step * 4
        while (g <= step * 4) {
            if (g != 0) {
                val gx = sx(g.toDouble())
                val gy = sy(g.toDouble())
                if (gx in pad..(w - pad)) canvas.drawLine(gx, pad, gx, h - pad, gridPaint)
                if (gy in pad..(h - pad)) canvas.drawLine(pad, gy, w - pad, gy, gridPaint)
            }
            g += step
        }
        canvas.drawLine(pad, cy, w - pad, cy, axisPaint)
        canvas.drawLine(cx, pad, cx, h - pad, axisPaint)
        canvas.drawText("x", w - pad + 4f, cy + 4f, textPaint)
        canvas.drawText("y", cx + 4f, pad, textPaint)

        // Gambar garis utama
        if (abs(spec.b) < 1e-9 && abs(spec.a) > 1e-9) {
            val xv = -spec.c / spec.a
            canvas.drawLine(sx(xv), pad, sx(xv), h - pad, linePaint)
        } else if (abs(spec.b) > 1e-9) {
            val xRange = ((w / 2f - pad) / scale).toDouble()
            val y1 = (-spec.c - spec.a * (-xRange)) / spec.b
            val y2 = (-spec.c - spec.a * xRange) / spec.b
            canvas.save()
            canvas.clipRect(pad, pad, w - pad, h - pad)
            canvas.drawLine(sx(-xRange), sy(y1), sx(xRange), sy(y2), linePaint)
            canvas.restore()
        }

        val rDot = resources.displayMetrics.density * 4f
        spec.xIntVal?.let { xv ->
            val px = sx(xv)
            if (px in pad..(w - pad)) {
                dotPaint.color = 0xFFFF3B30.toInt()
                canvas.drawCircle(px, cy, rDot, dotPaint)
                spec.xInterceptLabel?.let { lbl ->
                    canvas.drawText(lbl, (px + 4f).coerceAtMost(w - pad - 48f), cy - 8f, textPaint)
                }
            }
        }
        spec.yIntVal?.let { yv ->
            val py = sy(yv)
            if (py in pad..(h - pad)) {
                dotPaint.color = 0xFF34C759.toInt()
                canvas.drawCircle(cx, py, rDot, dotPaint)
                spec.yInterceptLabel?.let { lbl ->
                    canvas.drawText(lbl, cx + 6f, (py - 6f).coerceAtLeast(pad + 12f), textPaint)
                }
            }
        }
        if (spec.pointX != null && spec.pointY != null) {
            val px = sx(spec.pointX)
            val py = sy(spec.pointY)
            if (px in pad..(w - pad) && py in pad..(h - pad)) {
                dotPaint.color = 0xFFFF9500.toInt()
                canvas.drawCircle(px, py, rDot, dotPaint)
                spec.pointLabel?.let { lbl ->
                    canvas.drawText(lbl, (px + 5f).coerceAtMost(w - pad - 48f), (py + 14f).coerceAtMost(h - 6f), textPaint)
                }
            }
        }
        if (spec.equationLabel.isNotEmpty()) {
            canvas.drawText(spec.equationLabel, pad, h - 6f, textPaint)
        }
    }
}

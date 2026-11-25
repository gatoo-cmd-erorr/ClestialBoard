package com.clestial.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Deretan stud LEGO 3D beraksen chrome berkilau untuk pemisah atau bagian atas kartu.
 */
class LegoStudStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var lastWidth = 0
    private var lastHeight = 0

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = dp(16f).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val studR = dp(5.5f)
        val gap = dp(12f)
        val step = studR * 2f + gap
        val count = max(2, ((width - dp(8f)) / step).toInt()).coerceAtMost(12)
        val totalW = count * studR * 2f + (count - 1) * gap
        val startX = (width - totalW) / 2f + studR
        val cy = height / 2f

        val topCol = ContextCompat.getColor(context, R.color.chrome_bright)
        val midCol = ContextCompat.getColor(context, R.color.chrome_mid)
        val darkCol = ContextCompat.getColor(context, R.color.chrome_dark)
        val shadowCol = ContextCompat.getColor(context, R.color.bevel_dark)
        val lineCol = ContextCompat.getColor(context, R.color.line)

        for (i in 0 until count) {
            val cx = startX + i * step
            // Bayangan bawah 3D stud.
            fillPaint.shader = null
            fillPaint.color = shadowCol
            canvas.drawCircle(cx, cy + dp(1.5f), studR, fillPaint)

            // Badan stud chrome bergradien.
            fillPaint.shader = LinearGradient(
                cx - studR, cy - studR, cx + studR, cy + studR,
                intArrayOf(topCol, midCol, darkCol),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, studR, fillPaint)

            // Garis tepi stud.
            fillPaint.shader = null
            strokePaint.strokeWidth = dp(0.9f)
            strokePaint.color = lineCol
            canvas.drawCircle(cx, cy, studR, strokePaint)

            // Kilap lengkung atas khas permukaan plastik chrome.
            strokePaint.strokeWidth = dp(1.1f)
            strokePaint.color = 0xCCFFFFFF.toInt()
            rect.set(cx - studR * 0.6f, cy - studR * 0.6f, cx + studR * 0.6f, cy + studR * 0.6f)
            canvas.drawArc(rect, 200f, 110f, false, strokePaint)
        }
        lastWidth = width
        lastHeight = height
    }
}

/**
 * Lapisan kilau chrome dan bintang berkelip di atas kartu karakter LEGO hitam-putih.
 */
class LegoSparkleOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class SparkleSpec(
        val xFrac: Float,
        val yFrac: Float,
        val sizeDp: Float,
        val phaseOffset: Float
    )

    private val sparkles = listOf(
        SparkleSpec(0.68f, 0.16f, 9f, 0.0f),
        SparkleSpec(0.91f, 0.24f, 6.5f, 0.28f),
        SparkleSpec(0.56f, 0.38f, 5.5f, 0.55f),
        SparkleSpec(0.84f, 0.64f, 8f, 0.72f),
        SparkleSpec(0.61f, 0.78f, 6f, 0.15f),
        SparkleSpec(0.14f, 0.22f, 5f, 0.42f)
    )

    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenMatrix = android.graphics.Matrix()
    private var sheenShader: LinearGradient? = null
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val starPath = Path()
    private var animPhase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            invalidate()
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val span = w * 0.28f
            sheenShader = LinearGradient(
                -span, 0f, span, h.toFloat(),
                intArrayOf(0x00FFFFFF, 0x1EFFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            sheenPaint.shader = sheenShader
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        // Sapuan cahaya diagonal lembut melintasi kartu chrome.
        val shader = sheenShader
        if (shader != null) {
            val sweep = (animPhase * 1.6f) - 0.3f
            sheenMatrix.setTranslate(width * sweep, 0f)
            shader.setLocalMatrix(sheenMatrix)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sheenPaint)
        }

        // Bintang kilau 4-sisi berkelip.
        for (sp in sparkles) {
            val wave = 0.5f + 0.5f * sin((animPhase + sp.phaseOffset) * 2.0 * PI).toFloat()
            val alpha = (35 + wave * 215).toInt().coerceIn(0, 255)
            val r = dp(sp.sizeDp) * (0.55f + 0.45f * wave)
            drawFourPointStar(canvas, width * sp.xFrac, height * sp.yFrac, r, alpha)
        }
    }

    private fun drawFourPointStar(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int) {
        val inner = r * 0.22f
        starPath.reset()
        starPath.moveTo(cx, cy - r)
        starPath.lineTo(cx + inner, cy - inner)
        starPath.lineTo(cx + r, cy)
        starPath.lineTo(cx + inner, cy + inner)
        starPath.lineTo(cx, cy + r)
        starPath.lineTo(cx - inner, cy + inner)
        starPath.lineTo(cx - r, cy)
        starPath.lineTo(cx - inner, cy - inner)
        starPath.close()

        starPaint.alpha = alpha
        canvas.drawPath(starPath, starPaint)
    }
}

/**
 * Pratinjau mini balok LEGO berkilau di layar Tema (menampilkan dua balok dengan stud 3D).
 */
class LegoBrickPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val brickRect = RectF()
    private val studRect = RectF()

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = dp(64f).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val topCol = ContextCompat.getColor(context, R.color.btn_top)
        val midCol = ContextCompat.getColor(context, R.color.btn_mid)
        val botCol = ContextCompat.getColor(context, R.color.btn_bot)
        val lipCol = ContextCompat.getColor(context, R.color.btn_lip)
        val cardTop = ContextCompat.getColor(context, R.color.card_top)
        val cardBot = ContextCompat.getColor(context, R.color.card_bottom)
        val lineCol = ContextCompat.getColor(context, R.color.line)
        val bevelCol = ContextCompat.getColor(context, R.color.bevel_dark)

        // Balok 1 (chrome mengilap)
        drawMiniBrick(
            canvas,
            x = dp(4f),
            y = dp(10f),
            w = dp(112f),
            h = dp(38f),
            studs = 3,
            topCol = topCol,
            midCol = midCol,
            botCol = botCol,
            lipCol = lipCol,
            strokeCol = topCol
        )

        // Balok 2 (obsidian/plat kartu)
        drawMiniBrick(
            canvas,
            x = dp(126f),
            y = dp(18f),
            w = dp(78f),
            h = dp(30f),
            studs = 2,
            topCol = cardTop,
            midCol = cardTop,
            botCol = cardBot,
            lipCol = bevelCol,
            strokeCol = lineCol
        )
    }

    private fun drawMiniBrick(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        studs: Int,
        topCol: Int,
        midCol: Int,
        botCol: Int,
        lipCol: Int,
        strokeCol: Int
    ) {
        val studW = dp(18f)
        val studH = dp(6f)
        val studGap = (w - studs * studW) / (studs + 1)

        // Stud di atas balok.
        for (i in 0 until studs) {
            val sx = x + studGap + i * (studW + studGap)
            studRect.set(sx, y - studH + dp(1f), sx + studW, y + dp(2f))
            fillPaint.shader = LinearGradient(
                studRect.left, studRect.top, studRect.right, studRect.top,
                intArrayOf(botCol, topCol, midCol),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(studRect, dp(2f), dp(2f), fillPaint)
            fillPaint.shader = null
            strokePaint.strokeWidth = dp(0.9f)
            strokePaint.color = strokeCol
            canvas.drawRoundRect(studRect, dp(2f), dp(2f), strokePaint)
        }

        // Bibir bawah 3D.
        brickRect.set(x, y + dp(3f), x + w, y + h + dp(3f))
        fillPaint.shader = null
        fillPaint.color = lipCol
        canvas.drawRoundRect(brickRect, dp(6f), dp(6f), fillPaint)

        // Badan balok utama.
        brickRect.set(x, y, x + w, y + h)
        fillPaint.shader = LinearGradient(
            x, y, x, y + h,
            intArrayOf(topCol, midCol, botCol),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(brickRect, dp(6f), dp(6f), fillPaint)
        fillPaint.shader = null

        strokePaint.strokeWidth = dp(1f)
        strokePaint.color = strokeCol
        canvas.drawRoundRect(brickRect, dp(6f), dp(6f), strokePaint)
    }
}

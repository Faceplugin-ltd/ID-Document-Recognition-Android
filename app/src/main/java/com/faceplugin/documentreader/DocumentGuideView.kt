package com.faceplugin.documentreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Overlay for [DocumentReaderSDK.locateDocument] corners (preview coordinates).
 * Drawn only when a detection is set — no placeholder rectangle.
 */
class DocumentGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.fp_overlay)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.fp_accent)
    }
    private val path = Path()

    /** Detected document corners in view coordinates (LT, RT, RB, LB), or null. */
    private var detected: Array<PointF>? = null

    var locked: Boolean = false
        set(value) {
            field = value
            strokePaint.color = ContextCompat.getColor(
                context,
                if (value) R.color.fp_accent else R.color.fp_muted
            )
            strokePaint.strokeWidth = if (value) 8f else 5f
            invalidate()
        }

    fun clearDetection() {
        detected = null
        invalidate()
    }

    /** Four corners in this view's pixel space, or null / empty to hide. */
    fun setDetectedCorners(corners: List<PointF>?) {
        detected = if (corners != null && corners.size >= 4 && isUsableQuad(corners)) {
            Array(4) { i -> PointF(corners[i].x, corners[i].y) }
        } else {
            null
        }
        invalidate()
    }

    private fun isUsableQuad(corners: List<PointF>): Boolean {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in corners) {
            if (!p.x.isFinite() || !p.y.isFinite()) return false
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }
        return (maxX - minX) >= 24f && (maxY - minY) >= 24f
    }

    override fun onDraw(canvas: Canvas) {
        val corners = detected ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        path.reset()
        path.moveTo(corners[0].x, corners[0].y)
        for (i in 1 until corners.size) {
            path.lineTo(corners[i].x, corners[i].y)
        }
        path.close()

        val sc = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, dimPaint)
        canvas.drawPath(path, clearPaint)
        canvas.restoreToCount(sc)
        canvas.drawPath(path, strokePaint)
    }
}

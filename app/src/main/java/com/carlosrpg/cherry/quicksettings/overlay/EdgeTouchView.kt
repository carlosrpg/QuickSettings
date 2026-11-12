package com.carlosrpg.cherry.quicksettings.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.carlosrpg.cherry.quicksettings.settings.Side
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The closed state: an invisible swipe zone along the edge that draws only the 6×72 dp hint.
 * A swipe away from the edge calls [onOpen]; a plain tap is handed to [onTap] with its screen
 * position so it can be replayed on the app underneath.
 */
@SuppressLint("ViewConstructor")
class EdgeTouchView(
    context: Context,
    var side: Side,
    private val onOpen: () -> Unit,
    private val onTap: (rawX: Float, rawY: Float) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val openDistance = 24 * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(158, 255, 255, 255) }
    private val hintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(64, 0, 0, 0) }
    private val rect = RectF()

    private var downX = 0f
    private var downY = 0f
    private var opened = false

    override fun onDraw(canvas: Canvas) {
        val w = 6 * density
        val h = 72 * density
        val inset = 6 * density
        val left = if (side == Side.LEFT) inset else width - inset - w
        val top = (height - h) / 2
        rect.set(left - 1, top - 1, left + w + 1, top + h + 1)
        canvas.drawRoundRect(rect, w, w, hintShadow)
        rect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, w / 2, w / 2, hint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                opened = false
            }
            MotionEvent.ACTION_MOVE -> if (!opened) {
                val inward = if (side == Side.LEFT) event.rawX - downX else downX - event.rawX
                if (inward > openDistance && inward > abs(event.rawY - downY)) {
                    opened = true
                    onOpen()
                }
            }
            MotionEvent.ACTION_UP -> if (!opened && hypot(event.rawX - downX, event.rawY - downY) < touchSlop) {
                onTap(event.rawX, event.rawY)
            }
        }
        return true
    }
}

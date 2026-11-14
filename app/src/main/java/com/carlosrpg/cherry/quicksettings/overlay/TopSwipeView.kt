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
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The top bar's pull-down zone: an invisible strip just below the bar that draws only a small
 * centred hint. A swipe down calls [onOpen]; a plain tap is handed to [onTap] with its screen
 * position so it can be replayed on the app underneath (its toolbar often sits right here).
 */
@SuppressLint("ViewConstructor")
class TopSwipeView(
    context: Context,
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
        val w = 72 * density
        val h = 6 * density
        val left = (width - w) / 2
        val top = 4 * density
        rect.set(left - 1, top - 1, left + w + 1, top + h + 1)
        canvas.drawRoundRect(rect, h, h, hintShadow)
        rect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, h / 2, h / 2, hint)
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
                val down = event.rawY - downY
                if (down > openDistance && down > abs(event.rawX - downX)) {
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

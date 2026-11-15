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
 * The top bar's pull-down zone: an invisible strip across the whole width just below the bar
 * (the car's status bar, a higher layer, takes touches on the bar itself) that draws only a
 * small centred hint. A pull down or a tap on the hint opens the drawer; anything else (a tap
 * elsewhere, a long press, a sideways swipe) is handed to [onPass] to be replayed on the app. A swipe down calls [onOpen]; a plain tap is handed to [onTap] with its screen
 * position so it can be replayed on the app underneath (its toolbar often sits right here).
 */
@SuppressLint("ViewConstructor")
class TopSwipeView(
    context: Context,
    private val onOpen: () -> Unit,
    private val onPass: (x0: Float, y0: Float, x1: Float, y1: Float, durationMs: Long) -> Unit,
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
        val w = 144 * density
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
            MotionEvent.ACTION_UP -> if (!opened) {
                val still = hypot(event.rawX - downX, event.rawY - downY) < touchSlop
                val held = event.eventTime - event.downTime
                if (still && held < ViewConfiguration.getLongPressTimeout() && abs(event.x - width / 2f) <= HINT_TAP_HALF_WIDTH_DP * density) {
                    onOpen()
                } else if (still) {
                    onPass(downX, downY, downX, downY, held)
                } else {
                    onPass(downX, downY, event.rawX, event.rawY, held)
                }
            }
        }
        return true
    }
}

/** Half the width that counts as a tap on the hint: the 144 dp pill plus a finger's margin. */
private const val HINT_TAP_HALF_WIDTH_DP = 96

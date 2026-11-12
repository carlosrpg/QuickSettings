package com.carlosrpg.cherry.quicksettings.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Labels and icons of other apps (the media source, the nav app), cached per package. */
object AppInfo {
    private val labels = HashMap<String, String?>()
    private val icons = HashMap<String, Bitmap?>()

    fun label(context: Context, pkg: String): String? = synchronized(labels) {
        labels.getOrPut(pkg) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
        }
    }

    fun icon(context: Context, pkg: String): Bitmap? = synchronized(icons) {
        icons.getOrPut(pkg) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(ICON_PX) }.getOrNull()
        }
    }

    fun Drawable.toBitmap(fallbackSize: Int): Bitmap? {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = intrinsicWidth.takeIf { it > 0 } ?: fallbackSize
        val h = intrinsicHeight.takeIf { it > 0 } ?: fallbackSize
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            setBounds(0, 0, w, h)
            draw(Canvas(it))
        }
    }

    private const val ICON_PX = 96
}

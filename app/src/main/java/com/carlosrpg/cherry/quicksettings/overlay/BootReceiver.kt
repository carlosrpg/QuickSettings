package com.carlosrpg.cherry.quicksettings.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the edge hint back after the head unit boots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            OverlayService.start(context)
        }
    }
}

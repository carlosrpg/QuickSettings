package com.carlosrpg.cherry.quicksettings.overlay

import android.app.Activity
import android.os.Bundle

/**
 * Opens the full drawer and goes away, drawing nothing. It is the package's launch intent
 * (MAIN + INFO, which `getLaunchIntentForPackage` prefers over the launcher icon), so a gesture
 * app that launches QuickSettings by package (JConfig does) opens the drawer, while the
 * launcher icon still opens Settings.
 */
class OpenDrawerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayService.openTop(this)
        finish()
    }
}

package com.carlosrpg.cherry.quicksettings.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** [text] as a QR code, dark on white with the standard four-module quiet zone. */
@Composable
fun QrImage(text: String, size: Dp = 260.dp) {
    val modules = remember(text) { QrCode.encode(text) }
    Canvas(Modifier.size(size)) {
        val quiet = 4
        val cell = this.size.width / (modules.size + 2 * quiet)
        drawRect(Color.White)
        for (y in modules.indices) for (x in modules.indices) {
            if (modules[y][x]) {
                // A hair larger than the cell, so no seams show between neighbours.
                drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

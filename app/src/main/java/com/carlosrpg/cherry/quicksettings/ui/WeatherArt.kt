package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.carlosrpg.cherry.quicksettings.weather.WeatherKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SunCore = listOf(Color(0xFFFFE680), Color(0xFFFFB02E))
private val SunHalo = Color(0x55FFC24D)
private val SunRay = Color(0xFFFFC53D)
private val MoonFace = listOf(Color(0xFFFBF8EA), Color(0xFFC9D2E6))
private val CloudLight = listOf(Color(0xFFFFFFFF), Color(0xFFC6D2E4))
private val CloudBack = listOf(Color(0xFFB7C3D6), Color(0xFF7F8DA6))
private val CloudStorm = listOf(Color(0xFF9AA7BE), Color(0xFF55627A))
private val Drop = listOf(Color(0xFF8CCBFF), Color(0xFF2F7BFF))
private val Bolt = listOf(Color(0xFFFFE45C), Color(0xFFFF9A1F))
private val Mist = Color(0xB3C9D4E3)

/** A full-colour picture of [kind]; clear and partly cloudy show the moon at night. */
@Composable
fun WeatherArt(kind: WeatherKind, day: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        when (kind) {
            WeatherKind.CLEAR -> if (day) sun(o(.5f, .5f), .2f * s, rays = true) else moon(o(.5f, .5f), .28f * s, stars = true)
            WeatherKind.PARTLY_CLOUDY -> {
                if (day) sun(o(.37f, .36f), .15f * s, rays = true) else moon(o(.38f, .36f), .2f * s, stars = false)
                cloud(o(.58f, .6f), .7f * s, CloudLight)
            }
            WeatherKind.CLOUDY -> {
                cloud(o(.64f, .4f), .55f * s, CloudBack)
                cloud(o(.44f, .58f), .78f * s, CloudLight)
            }
            WeatherKind.FOG -> {
                cloud(o(.5f, .4f), .74f * s, CloudLight)
                listOf(Triple(.16f, .7f, .72f), Triple(.28f, .84f, .81f), Triple(.2f, .62f, .9f)).forEach { (a, b, y) ->
                    drawLine(Mist, o(a, y), o(b, y), .055f * s, StrokeCap.Round)
                }
            }
            WeatherKind.DRIZZLE -> {
                cloud(o(.5f, .4f), .78f * s, CloudLight)
                listOf(.34f, .5f, .66f).forEachIndexed { i, x ->
                    val y = if (i % 2 == 0) .74f else .82f
                    drawLine(Brush.verticalGradient(Drop), o(x, y), o(x - .02f, y + .07f), .05f * s, StrokeCap.Round)
                }
            }
            WeatherKind.RAIN -> {
                cloud(o(.5f, .4f), .8f * s, CloudBack)
                listOf(.3f, .44f, .58f, .72f).forEachIndexed { i, x ->
                    val y = if (i % 2 == 0) .7f else .76f
                    drawLine(Brush.verticalGradient(Drop, startY = y * s, endY = (y + .17f) * s), o(x, y), o(x - .06f, y + .17f), .045f * s, StrokeCap.Round)
                }
            }
            WeatherKind.SNOW -> {
                cloud(o(.5f, .4f), .78f * s, CloudLight)
                listOf(o(.32f, .77f), o(.5f, .88f), o(.68f, .77f)).forEach { flake(it, .06f * s) }
            }
            WeatherKind.THUNDER -> {
                cloud(o(.5f, .4f), .82f * s, CloudStorm)
                val bolt = Path().apply {
                    moveTo(.54f * s, .58f * s); lineTo(.38f * s, .8f * s); lineTo(.5f * s, .8f * s)
                    lineTo(.42f * s, .98f * s); lineTo(.66f * s, .72f * s); lineTo(.53f * s, .72f * s)
                    lineTo(.62f * s, .58f * s); close()
                }
                drawPath(bolt, Brush.verticalGradient(Bolt, startY = .58f * s, endY = .98f * s))
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, rays: Boolean) {
    drawCircle(Brush.radialGradient(listOf(SunHalo, Color.Transparent), c, r * 2.2f), r * 2.2f, c)
    if (rays) for (i in 0 until 8) {
        val a = (i * PI / 4).toFloat()
        val d = Offset(cos(a), sin(a))
        drawLine(SunRay, c + d * (r * 1.4f), c + d * (r * 1.95f), r * 0.24f, StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(SunCore, c - Offset(r * .3f, r * .3f), r * 1.6f), r, c)
}

private fun DrawScope.moon(c: Offset, r: Float, stars: Boolean) {
    drawCircle(Brush.radialGradient(listOf(Color(0x33DDE6FF), Color.Transparent), c, r * 1.9f), r * 1.9f, c)
    val face = Path().apply { addOval(Rect(c, r)) }
    val bite = Path().apply { addOval(Rect(c + Offset(r * .45f, -r * .35f), r * .85f)) }
    drawPath(Path().apply { op(face, bite, PathOperation.Difference) }, Brush.linearGradient(MoonFace, c - Offset(r, r), c + Offset(r, r)))
    if (stars) {
        val s = size.minDimension
        listOf(Offset(.8f, .24f) to .03f, Offset(.86f, .5f) to .018f, Offset(.7f, .82f) to .022f).forEach { (p, k) ->
            drawCircle(Color(0xFFF4F1E1), k * s, Offset(p.x * s, p.y * s))
        }
    }
}

/** Three puffs on a rounded base, [w] wide, centred on [c]. */
private fun DrawScope.cloud(c: Offset, w: Float, colors: List<Color>) {
    var shape = Path().apply {
        addRoundRect(RoundRect(c.x - .48f * w, c.y + .02f * w, c.x + .48f * w, c.y + .26f * w, CornerRadius(.12f * w)))
    }
    listOf(Triple(-.28f, .06f, .2f), Triple(-.02f, -.08f, .3f), Triple(.26f, .04f, .22f)).forEach { (dx, dy, r) ->
        val puff = Path().apply { addOval(Rect(Offset(c.x + dx * w, c.y + dy * w), r * w)) }
        shape = Path().apply { op(shape, puff, PathOperation.Union) }
    }
    drawPath(shape, Brush.verticalGradient(colors, startY = c.y - .38f * w, endY = c.y + .26f * w))
}

private fun DrawScope.flake(c: Offset, r: Float) {
    for (i in 0 until 3) {
        val a = (i * PI / 3 + PI / 2).toFloat()
        val d = Offset(cos(a), sin(a)) * r
        drawLine(Color.White, c - d, c + d, r * .38f, StrokeCap.Round)
    }
}

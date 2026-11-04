package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Игровой пейзаж. Общие часы для виджета и ателье, без сети и геолокации. */
internal object WindowScene {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val bounds = RectF()

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    // 120 секунд день, 120 ночь; 12-секундные переходы на границах.
    fun night(now: Long): Float {
        val seconds = (now % 240_000L) / 1000f
        fun ease(x: Float): Float { val v = x.coerceIn(0f, 1f); return v * v * (3f - 2f * v) }
        return if (seconds < 120f) 1f - ease(seconds / 12f) else ease((seconds - 120f) / 12f)
    }

    fun draw(c: Canvas, unit: Float, floor: Float, now: Long) {
        val x = unit * 4.1f
        val bottom = floor - unit * 3.15f
        val top = (bottom - unit * 5.8f).coerceAtLeast(unit * 1.3f)
        val left = x - unit * 2.35f
        val right = x + unit * 2.35f
        if (bottom - top < unit * 1.5f) return
        val n = night(now)
        val t = (now % 1_000_000L) / 1000f
        val epoch = now / 90_000L
        val weather = (epoch % 5).toInt() // ясно, дождь, гроза, туман, ветер
        val weatherBlend = ((now % 90_000L) / 6000f).coerceIn(0f, 1f)
        val previous = ((epoch + 4) % 5).toInt()
        fun strength(kind: Int): Float = (if (weather == kind) weatherBlend else 0f) +
            (if (previous == kind) 1f - weatherBlend else 0f)
        val rain = strength(1) + strength(2)
        val fog = strength(3)
        val wind = strength(4)
        val storm = strength(2)

        // Внешняя рама и глубокая оконная ниша.
        fill.color = 0x26000000
        c.drawRoundRect(left - unit * 0.22f, top + unit * 0.08f, right + unit * 0.3f, bottom + unit * 0.35f, unit * 0.5f, unit * 0.5f, fill)
        fill.color = 0xFFE9DCC7.toInt()
        c.drawRoundRect(left - unit * 0.2f, top - unit * 0.2f, right + unit * 0.2f, bottom + unit * 0.2f, unit * 0.46f, unit * 0.46f, fill)
        c.save()
        bounds.set(left, top, right, bottom)
        path.reset(); path.addRoundRect(bounds, unit * 0.3f, unit * 0.3f, Path.Direction.CW)
        c.clipPath(path)
        fill.shader = LinearGradient(0f, top, 0f, bottom,
            mix(0xFF87CFE6.toInt(), 0xFF172642.toInt(), n),
            mix(0xFFDAEEDC.toInt(), 0xFF566784.toInt(), n), Shader.TileMode.CLAMP)
        c.drawRect(bounds, fill); fill.shader = null
        fill.color = 0xFFFFD576.toInt(); fill.alpha = ((1f - n) * 255).toInt()
        c.drawCircle(x - unit * 1.15f, top + unit * 1.1f, unit * 0.43f, fill)
        fill.color = 0xFFF7EACB.toInt(); fill.alpha = (n * 255).toInt()
        c.drawCircle(x + unit * 1.15f, top + unit * 1.1f, unit * 0.38f, fill)
        for (i in 0..11) {
            fill.alpha = (n * (150 + 70 * sin(t * 0.8f + i))).toInt().coerceIn(0, 255)
            c.drawCircle(left + unit * (0.3f + (i * 1.17f) % 4.1f), top + unit * (0.35f + (i * 0.61f) % 2f), unit * 0.035f, fill)
        }
        fill.alpha = 255
        // Дальний лес и полянка: деревья имеют отдельные стволы и кроны.
        val horizon = top + (bottom - top) * 0.64f
        for (i in 0..8) {
            val tx = left + i * unit * 0.65f
            val ty = horizon + sin(i * 2f) * unit * 0.18f
            fill.color = mix(0xFF548F78.toInt(), 0xFF263F49.toInt(), n)
            path.reset(); path.moveTo(tx, ty - unit * (0.9f + (i % 3) * 0.2f))
            path.lineTo(tx + unit * 0.5f, ty + unit * 0.35f); path.lineTo(tx - unit * 0.5f, ty + unit * 0.35f); path.close()
            c.drawPath(path, fill)
            stroke.color = mix(0xFF55715B.toInt(), 0xFF283A40.toInt(), n); stroke.strokeWidth = unit * 0.07f
            c.drawLine(tx, ty, tx, ty + unit * 0.55f, stroke)
        }
        fill.color = mix(0xFF8CB878.toInt(), 0xFF405B56.toInt(), n)
        c.drawOval(left - unit, horizon + unit * 0.15f, right + unit, bottom + unit * 1.4f, fill)
        fill.color = mix(0xFFB0CA88.toInt(), 0xFF4D695F.toInt(), n)
        c.drawOval(left - unit, bottom - unit * 0.65f, right + unit, bottom + unit * 1.4f, fill)
        // Тучи плавно набирают плотность вместе с дождём.
        fill.color = 0xFF647889.toInt(); fill.alpha = (rain * 175).toInt()
        c.drawOval(left - unit, top - unit * 0.3f, right + unit, top + unit * 1.0f, fill)
        fill.alpha = 255
        stroke.color = 0xFFCEE7F1.toInt(); stroke.alpha = (rain * 180).toInt(); stroke.strokeWidth = unit * 0.045f
        for (i in 0..28) {
            val rx = left + ((i * 0.79f + t * 0.24f) % 4.9f) * unit
            val ry = top + ((i * 0.61f + t * 3.2f) % ((bottom - top) / unit + 0.5f)) * unit
            c.drawLine(rx, ry, rx - unit * 0.14f, ry + unit * 0.32f, stroke)
        }
        // Одна мягкая вспышка раз в 11 секунд без резкого строба.
        val flash = ((1f + cos((t % 11f) / 0.7f * PI.toFloat())) * 0.5f).coerceIn(0f, 1f)
        if (t % 11f < 0.7f) {
            fill.color = 0xFFFFF0CF.toInt(); fill.alpha = (storm * flash * 75).toInt(); c.drawRect(bounds, fill)
        }
        fill.color = 0xFFDCE5E2.toInt(); fill.alpha = (fog * 155).toInt(); c.drawRect(bounds, fill); fill.alpha = 255
        stroke.color = 0xFFE8EAD7.toInt(); stroke.alpha = (wind * 150).toInt(); stroke.strokeWidth = unit * 0.045f
        for (i in 0..3) {
            val wx = left + ((t * 0.8f + i * 1.3f) % 5.6f - 0.5f) * unit
            val wy = horizon + unit * (i * 0.3f - 0.5f)
            bounds.set(wx, wy, wx + unit * 0.8f, wy + unit * 0.22f)
            c.drawArc(bounds, 190f, 150f, false, stroke)
        }
        stroke.alpha = 255
        // Прозрачное стекло: только узкий блик, пейзаж остаётся открытым.
        fill.color = 0x20FFFFFF
        path.reset(); path.moveTo(left + unit * 0.2f, top); path.lineTo(left + unit * 0.65f, top)
        path.lineTo(right - unit * 0.35f, bottom); path.lineTo(right - unit * 0.8f, bottom); path.close(); c.drawPath(path, fill)
        c.restore()
        stroke.color = 0xFF6B6356.toInt(); stroke.strokeWidth = unit * 0.1f
        c.drawRoundRect(left, top, right, bottom, unit * 0.3f, unit * 0.3f, stroke)
        stroke.color = 0xFFE9DCC7.toInt(); stroke.strokeWidth = unit * 0.14f
        c.drawLine(x, top, x, bottom, stroke)
        fill.color = 0xFFF0E5D3.toInt()
        c.drawRoundRect(left - unit * 0.37f, bottom, right + unit * 0.37f, bottom + unit * 0.25f, unit * 0.1f, unit * 0.1f, fill)
    }
}

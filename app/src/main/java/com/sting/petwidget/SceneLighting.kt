package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** Мягкий свет без жёстких границ световых пятен. */
internal object SceneLighting {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, width: Float, height: Float, floor: Float, bulbY: Float, on: Boolean,
             windowGlow: Float = 0f, lampX: Float = width / 2f) {
        val x = lampX
        if (!on) {
            paint.shader = LinearGradient(0f, 0f, width, height,
                0xAE1C2942.toInt(), 0x55354764, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
            if (windowGlow > 0.001f) {
                val alpha = (windowGlow.coerceIn(0f, 1f) * 44).toInt()
                // Свет затухает до середины комнаты, не осветляя её целиком.
                paint.shader = RadialGradient(width * 0.80f, floor * 0.53f, width * 0.46f,
                    intArrayOf((alpha shl 24) or 0x00FFE7AA, 0x00FFE7AA),
                    null, Shader.TileMode.CLAMP)
                c.drawRect(width * 0.36f, 0f, width, height, paint)
                paint.shader = null
            }
            return
        }
        paint.shader = RadialGradient(x, bulbY, width * 0.85f,
            intArrayOf(0x42FFE6B3, 0x12FFE6B3, 0x00FFE6B3),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width, height, paint)
        c.save()
        c.translate(x, floor)
        c.scale(1f, 0.20f)
        paint.shader = RadialGradient(0f, 0f, width * 0.43f,
            intArrayOf(0x36FFE6B3, 0x10FFE6B3, 0x00FFE6B3),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, width * 0.43f, paint)
        c.restore()
        paint.shader = RadialGradient(x, bulbY, width * 0.11f,
            intArrayOf(0x65FFF4D8, 0x00FFF4D8), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, bulbY, width * 0.11f, paint)
        paint.shader = null
    }
}

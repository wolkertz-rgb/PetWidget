package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** Мягкий свет без жёстких границ световых пятен. */
internal object SceneLighting {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Размер градиента меняется только при изменении размеров виджета. */
    class AmbientGlow(
        private val centerX: Float,
        private val centerY: Float,
        private val radius: Float,
        private val color: Int,
        private val verticalScale: Float = 1f
    ) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var lastWidth = 0f
        private var lastFloor = 0f

        fun draw(c: Canvas, width: Float, height: Float, floor: Float, strength: Float = 1f) {
            if (strength <= 0f || width <= 0f) return
            if (width != lastWidth || floor != lastFloor) {
                lastWidth = width
                lastFloor = floor
                val x = width * centerX
                val y = floor * centerY
                paint.shader = RadialGradient(x, y, width * radius,
                    color, color and 0x00FFFFFF, Shader.TileMode.CLAMP).apply {
                    if (verticalScale != 1f) setLocalMatrix(Matrix().apply { setScale(1f, verticalScale, x, y) })
                }
            }
            paint.alpha = (strength.coerceIn(0f, 1f) * 255).toInt()
            c.drawRect(0f, 0f, width, height, paint)
        }
    }

    fun draw(c: Canvas, width: Float, height: Float, floor: Float, bulbY: Float, lamp: Float, daylight: Float) {
        val x = width / 2f
        val darkness = 1f - lamp
        if (darkness > .005f) {
            paint.shader = LinearGradient(0f, 0f, width, height,
                0xAE1C2942.toInt(), 0x55354764, Shader.TileMode.CLAMP)
            paint.alpha = (255 * darkness).toInt()
            c.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
            if (daylight > .001f) {
                paint.alpha = (255 * darkness * daylight).toInt()
                paint.shader = RadialGradient(width * 0.80f, floor * 0.53f, width * 0.46f,
                    intArrayOf(0x2CFFE7AA, 0x00FFE7AA),
                    null, Shader.TileMode.CLAMP)
                c.drawRect(width * 0.36f, 0f, width, height, paint)
                paint.shader = null
            }
        }
        paint.alpha = 255
        if (lamp <= .005f) {
            return
        }
        paint.alpha = (255 * lamp).toInt()
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
        paint.alpha = 255
    }
}

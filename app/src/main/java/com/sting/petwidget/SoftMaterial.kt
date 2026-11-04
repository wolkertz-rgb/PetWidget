package com.sting.petwidget

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader

/** Матовые поверхности: общий свет сверху слева, без создания шейдера на каждый кадр. */
internal class SoftMaterial {
    private val matrix = Matrix()
    private val shaders = object : LinkedHashMap<Int, LinearGradient>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, LinearGradient>?): Boolean = size > 128
    }

    private fun tint(color: Int, target: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (Color.red(target) - Color.red(color)) * amount).toInt(),
        (Color.green(color) + (Color.green(target) - Color.green(color)) * amount).toInt(),
        (Color.blue(color) + (Color.blue(target) - Color.blue(color)) * amount).toInt()
    )

    fun apply(paint: Paint, color: Int, left: Float, top: Float, width: Float, height: Float) {
        paint.shader = null
        paint.color = color
        if (Color.alpha(color) < 255 || width <= 0f || height <= 0f) return
        val shader = shaders.getOrPut(color) {
            LinearGradient(0f, 0f, 0.7f, 1f,
                intArrayOf(tint(color, Color.WHITE, 0.18f), color, tint(color, 0xFF243D36.toInt(), 0.19f)),
                floatArrayOf(0f, 0.46f, 1f), Shader.TileMode.CLAMP)
        }
        matrix.setScale(width, height)
        matrix.postTranslate(left, top)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
    }
}

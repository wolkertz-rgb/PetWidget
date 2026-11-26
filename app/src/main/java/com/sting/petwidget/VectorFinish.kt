package com.sting.petwidget

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache

/** Shared material rules: warm upper-left light and a restrained coloured shadow. */
internal class VectorFinish {
    private data class Key(val color: Int, val l: Float, val t: Float, val r: Float, val b: Float)
    private val cache = LruCache<Key, Shader>(192)

    fun apply(paint: Paint, color: Int, l: Float, t: Float, r: Float, b: Float) {
        paint.color = color
        paint.shader = null
        if (Color.alpha(color) != 255 || r <= l || b <= t) return
        val key = Key(color, l, t, r, b)
        paint.shader = cache.get(key) ?: LinearGradient(l, t, r, b,
            intArrayOf(blend(color, 0xFFFFF0CB.toInt(), .19f), color,
                blend(color, 0xFF514A39.toInt(), .14f)),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP).also { cache.put(key, it) }
    }

    companion object {
        fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
    }
}

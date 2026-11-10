package com.sting.petwidget

import kotlin.math.cos
import kotlin.math.sin

/** Маятник в глубину кадра. Скамья и питомец используют одну проекцию. */
internal object SwingMotion {
    private const val LENGTH = 4.2f
    fun offsetY(angle: Float, mount: Float = 1f): Float =
        -.65f * mount + sin(angle) * LENGTH * .36f - (1f - cos(angle)) * LENGTH
    fun scale(angle: Float): Float = 1f + sin(angle) * LENGTH * .065f
}

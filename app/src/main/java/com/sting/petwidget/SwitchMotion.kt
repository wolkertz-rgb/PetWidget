package com.sting.petwidget

/** Нормированные движения настенной кнопки: поднести лапку, нажать, отпустить. */
internal object SwitchMotion {
    data class Pose(val reach: Float, val press: Float)

    // The back faces the viewer throughout the click and release.
    fun turn(timeMs: Long): Float = PetMotion.accent(timeMs.toFloat(), 0f, 430f, 1000f, 1400f)

    fun pose(timeMs: Long): Pose {
        val t = timeMs.toFloat()
        val reach = PetMotion.accent(t, 75f, 500f, 1000f, 1400f)
        // Полное нажатие совпадает с переключением света на 700 мс.
        val press = PetMotion.ease((t - 540f) / 160f) *
            (1f - PetMotion.ease((t - 840f) / 160f))
        return Pose(reach, press)
    }

    fun cordPull(timeMs: Long): Float {
        val t = timeMs.toFloat()
        return PetMotion.ease((t - 450f) / 250f) * (1f - PetMotion.ease((t - 795f) / 360f))
    }

    fun cordStretch(timeMs: Long): Float {
        val t = timeMs.toFloat()
        return PetMotion.ease((t - 90f) / 380f) * (1f - PetMotion.ease((t - 650f) / 580f))
    }
}

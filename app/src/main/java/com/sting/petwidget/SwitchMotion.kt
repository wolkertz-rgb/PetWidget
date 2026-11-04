package com.sting.petwidget

/** Нормированные движения настенной кнопки: поднести лапку, нажать, отпустить. */
internal object SwitchMotion {
    data class Pose(val reach: Float, val press: Float)

    fun pose(timeMs: Long): Pose {
        val t = timeMs.toFloat()
        val reach = PetMotion.ease(t / 500f) * (1f - PetMotion.ease((t - 1000f) / 400f))
        // Полное нажатие совпадает с переключением света на 700 мс.
        val press = PetMotion.ease((t - 540f) / 160f) *
            (1f - PetMotion.ease((t - 840f) / 160f))
        return Pose(reach, press)
    }
}

package com.sting.petwidget

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Индивидуальные мини-сцены для игрушек, которые остаются на полу. */
internal object InteractionMotion {
    data class FloorPose(
        val dx: Float = 0f,
        val lift: Float = 0f,
        val rotation: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f
    )

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    fun floorPose(id: String, phase: Float, dir: Int): FloorPose {
        val p = phase.coerceIn(0f, 1f)
        return when (id) {
            "robot" -> {
                // Сначала пет ставит робота и нажимает кнопку, затем тот идёт сам.
                val move = smooth((p - 0.32f) / 0.48f)
                val settle = smooth((p - 0.80f) / 0.20f)
                val steps = sin(move * PI.toFloat() * 6f) * (1f - settle)
                FloorPose(
                    dx = dir * 2.05f * move,
                    lift = -0.18f * kotlin.math.abs(steps),
                    rotation = steps * 5f,
                    scaleX = 1f + 0.035f * kotlin.math.abs(steps),
                    scaleY = 1f - 0.035f * kotlin.math.abs(steps)
                )
            }
            "top" -> {
                val spin = smooth(p / 0.22f) * smooth((1f - p) / 0.18f)
                // Юла вращается вокруг вертикальной оси, а не кувыркается целиком.
                FloorPose(rotation = sin(p * PI.toFloat() * 12f) * 3f * spin,
                    scaleX = 1f - 0.04f * spin)
            }
            "ball", "block" -> {
                val press = sin(smooth(p / 0.55f) * PI.toFloat())
                FloorPose(rotation = dir * 7f * press, scaleX = 1f + 0.05f * press,
                    scaleY = 1f - 0.06f * press)
            }
            else -> FloorPose()
        }
    }

    fun biteScale(progress: Float, amount: Float): Float {
        val stage = floor((progress.coerceIn(0f, 0.999f) * 3f)).toInt()
        return 1f - amount * (stage / 3f)
    }
}

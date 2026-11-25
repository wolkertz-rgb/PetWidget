package com.sting.petwidget

import kotlin.math.PI
import kotlin.math.sin

/** Индивидуальные мини-сцены для игрушек, которые остаются на полу. */
internal object InteractionMotion {
    const val ROBOT_TRAVEL = 0.09f
    const val PUSH_PHASE = .46f
    data class Hand(val dx: Float, val height: Float, val reach: Float)
    data class FloorPose(
        val dx: Float = 0f,
        val lift: Float = 0f,
        val rotation: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val spinTime: Float = 0f
    )

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    fun floorHand(id: String, phase: Float): Hand = when (id) {
        "robot" -> Hand(-.20f, 1.23f,
            PetMotion.accent(phase, .04f, .19f, .28f, .38f))
        "top" -> Hand(.10f * smooth((phase - .17f) / .13f), 3.01f,
            PetMotion.accent(phase, .04f, .17f, .26f, .39f))
        else -> Hand(-.3f, .7f, PetMotion.envelope(phase))
    }

    fun giftLid(phase: Float) = smooth((phase - .24f) / .31f)

    fun floorPose(id: String, phase: Float, dir: Int, robotReturns: Boolean = false): FloorPose {
        val p = phase.coerceIn(0f, 1f)
        return when (id) {
            "robot" -> {
                // Сначала пет ставит робота и нажимает кнопку, затем тот идёт сам.
                val move = if (robotReturns) smooth((p - 0.34f) / 0.30f) *
                    (1f - smooth((p - 0.66f) / 0.30f)) else smooth((p - 0.34f) / 0.56f)
                val steps = sin(move * PI.toFloat() * 6f)
                FloorPose(
                    dx = dir * move,
                    lift = -0.18f * kotlin.math.abs(steps),
                    rotation = steps * 5f,
                    scaleX = 1f + 0.035f * kotlin.math.abs(steps),
                    scaleY = 1f - 0.035f * kotlin.math.abs(steps),
                    spinTime = steps
                )
            }
            "top" -> {
                val spin = ((p - .26f) / .74f).coerceIn(0f, 1f)
                val speed = 1f - spin
                val turns = 1f - speed * speed * speed
                val settle = smooth((p - .26f) / .11f) * smooth((1f - p) / .23f)
                FloorPose(rotation = dir * 4f * sin(turns * 12f) * settle,
                    scaleX = 1f - .025f * settle, spinTime = turns * PI.toFloat() * 2f)
            }
            "ball", "block" -> {
                val press = smooth((p - .22f) / .19f) * smooth((.54f - p) / .08f)
                FloorPose(scaleX = 1f + .025f * press, scaleY = 1f - .045f * press)
            }
            else -> FloorPose()
        }
    }

}

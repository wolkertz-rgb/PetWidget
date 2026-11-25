package com.sting.petwidget

import kotlin.math.PI
import kotlin.math.sin

/** Траектория появления предмета. Нижняя точка всегда остаётся над полом. */
internal object SpawnMotion {
    const val DURATION_MS = 1_500L
    data class Pose(
        val y: Float,
        val scaleX: Float,
        val scaleY: Float,
        val rotation: Float,
        val impact: Float
    )

    fun pose(ageMs: Long, seed: Int, flexible: Boolean = false): Pose {
        val p = ((ageMs + DURATION_MS).toFloat() / DURATION_MS).coerceIn(0f, 1f)
        val direction = if (seed and 1 == 0) 1f else -1f
        return when {
            p < 0.70f -> {
                val fall = p / 0.70f
                val q = fall * fall
                Pose(
                    y = -10.5f * (1f - q),
                    scaleX = 1f,
                    scaleY = 1f,
                    rotation = direction * 9f * sin(fall * PI.toFloat()),
                    impact = 0f
                )
            }
            p < 0.82f -> {
                val q = (p - 0.70f) / 0.12f
                val squash = if (flexible) .11f * sin(q * PI.toFloat()) else 0f
                val height = 1f - squash
                Pose(0f, 1f / height, height,
                    0f, 1f - q)
            }
            else -> {
                val q = (p - 0.82f) / 0.18f
                val hop = 4f * q * (1f - q)
                Pose(-(if (flexible) .42f else .26f) * hop, 1f, 1f,
                    0f, 0f)
            }
        }
    }
}

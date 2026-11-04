package com.sting.petwidget

import kotlin.math.PI
import kotlin.math.sin

/** Траектория появления предмета. Нижняя точка всегда остаётся над полом. */
internal object SpawnMotion {
    data class Pose(
        val y: Float,
        val scaleX: Float,
        val scaleY: Float,
        val rotation: Float,
        val impact: Float
    )

    private fun easeOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val inv = 1f - x
        return 1f - inv * inv * inv
    }

    fun pose(ageMs: Long, seed: Int): Pose {
        val p = ((ageMs + 1500f) / 1500f).coerceIn(0f, 1f)
        val direction = if (seed and 1 == 0) 1f else -1f
        return when {
            p < 0.70f -> {
                val q = easeOutCubic(p / 0.70f)
                Pose(
                    y = -10.5f * (1f - q),
                    scaleX = 1f,
                    scaleY = 1f,
                    rotation = direction * 18f * (1f - q),
                    impact = 0f
                )
            }
            p < 0.82f -> {
                val q = (p - 0.70f) / 0.12f
                val squash = sin(q * PI.toFloat())
                Pose(0f, 1f + 0.16f * squash, 1f - 0.18f * squash,
                    direction * -3f * squash, 1f - q)
            }
            else -> {
                val q = (p - 0.82f) / 0.18f
                val hop = sin(q * PI.toFloat())
                Pose(-0.42f * hop, 1f - 0.04f * hop, 1f + 0.05f * hop,
                    direction * 2.5f * hop, 0f)
            }
        }
    }
}

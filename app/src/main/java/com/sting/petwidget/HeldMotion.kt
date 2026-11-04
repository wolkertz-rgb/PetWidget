package com.sting.petwidget

import kotlin.math.sin

/** Один источник движения игрушки и рук, время отсчитывается от захвата. */
internal object HeldMotion {
    data class Pose(val scale: Float = 1f, val tilt: Float = 0f, val lift: Float = 0f)

    fun pose(id: String, seconds: Float, progress: Float, envelope: Float): Pose {
        val wave = sin(seconds * 8f) * envelope
        return when (id) {
            "pizza", "cookie", "sushi" -> Pose(1f - progress * 0.15f, lift = wave * 0.05f)
            "donut" -> Pose(1f - progress * 0.12f, progress * 25f)
            "apple" -> Pose(1f - progress * 0.15f, wave * 5f)
            "coffee" -> Pose(tilt = sin(progress * Math.PI.toFloat()) * 25f)
            "icecream" -> Pose(tilt = wave * 5f, lift = wave * 0.04f)
            "dice" -> Pose(tilt = wave * 16f)
            "plushie" -> Pose(tilt = sin(seconds * 3f) * 4f * envelope)
            "balloon" -> Pose(tilt = wave * 3f, lift = -0.32f * envelope)
            "rocket" -> Pose(tilt = wave * 4f, lift = -0.4f * envelope)
            "boat" -> Pose(tilt = sin(seconds * 3f) * 8f * envelope)
            else -> Pose()
        }
    }
}

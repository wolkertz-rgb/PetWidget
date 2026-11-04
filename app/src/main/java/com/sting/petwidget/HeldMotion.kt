package com.sting.petwidget

import kotlin.math.sin

/** Один источник движения игрушки и рук, время отсчитывается от захвата. */
internal object HeldMotion {
    data class Pose(val scale: Float = 1f, val tilt: Float = 0f, val lift: Float = 0f)

    fun pose(id: String, seconds: Float, progress: Float, envelope: Float): Pose {
        val wave = sin(seconds * 6f) * envelope
        return when (id) {
            "pizza", "cookie", "sushi" -> Pose(
                InteractionMotion.biteScale(progress, 0.20f), lift = wave * 0.025f)
            "donut" -> Pose(InteractionMotion.biteScale(progress, 0.16f), progress * 18f)
            "apple" -> Pose(InteractionMotion.biteScale(progress, 0.18f), wave * 3f)
            "coffee" -> Pose(tilt = sin(progress * Math.PI.toFloat()) * 22f)
            "icecream" -> Pose(InteractionMotion.biteScale(progress, 0.13f), wave * 3f,
                wave * 0.02f)
            "dice" -> Pose(tilt = wave * 14f, lift = kotlin.math.abs(wave) * -0.08f)
            "plushie" -> Pose(tilt = sin(seconds * 2.6f) * 3f * envelope,
                scale = 1f + 0.035f * envelope)
            "balloon" -> Pose(tilt = wave * 2f, lift = -0.38f * envelope)
            "rocket" -> Pose(tilt = wave * 3f, lift = -0.52f * envelope)
            "boat" -> Pose(tilt = sin(seconds * 2.8f) * 7f * envelope,
                lift = sin(seconds * 2.8f) * 0.04f * envelope)
            "flower" -> Pose(tilt = sin(seconds * 2f) * 4f * envelope)
            else -> Pose()
        }
    }
}

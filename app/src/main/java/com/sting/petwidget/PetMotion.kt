package com.sting.petwidget

import kotlin.math.PI
import kotlin.math.sin

/** Поза действия: подготовка, жест и мягкое возвращение к нейтрали. */
internal object PetMotion {
    data class Pose(val sx: Float = 1f, val sy: Float = 1f,
                    val angle: Float = 0f, val offset: Float = 0f, val lift: Float = 0f)

    fun ease(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun envelope(p: Float): Float = ease(p / 0.18f) * ease((1f - p) / 0.22f)

    fun pose(sim: PetSim): Pose {
        val p = (sim.stateTime.toFloat() / sim.stateDur.coerceAtLeast(1)).coerceIn(0f, 1f)
        val e = envelope(p)
        val t = sim.stateTime / 1000f
        val breath = sin(sim.animPhase * 1.6f)
        return when (sim.state) {
            PetSim.State.SLEEP -> {
                // Сон — это спокойное дыхание всем телом, а не растяжение «резины».
                // В кресле питомец чуть сворачивается и кладёт голову набок.
                val inhale = breath * 0.012f
                if (sim.sleepingInBed) {
                    Pose(1.02f + inhale, 0.90f + inhale,
                        angle = -0.075f * sim.dir, lift = (breath + 1f) * 0.012f)
                } else {
                    Pose(0.98f + inhale, 0.93f + inhale,
                        angle = -0.035f * sim.dir, lift = (breath + 1f) * 0.01f)
                }
            }
            PetSim.State.SIT -> Pose(1f + 0.09f * e, 1f - 0.12f * e)
            PetSim.State.STRETCH -> Pose(1f - 0.09f * e, 1f + 0.16f * e)
            PetSim.State.HAPPY -> {
                val jump = sin(PI.toFloat() * ease((p - 0.16f) / 0.64f))
                val crouch = sin(PI.toFloat() * (p / 0.16f).coerceIn(0f, 1f))
                Pose(1f + 0.12f * crouch - 0.06f * jump,
                    1f - 0.13f * crouch + 0.07f * jump, lift = jump * 0.65f)
            }
            PetSim.State.ROLL -> {
                val turn = ease((p - 0.16f) / 0.65f)
                Pose(1f + 0.06f * e, 1f - 0.08f * e,
                    turn * 2f * PI.toFloat() * sim.dir, lift = sin(turn * PI.toFloat()) * 0.28f)
            }
            PetSim.State.SWAY -> Pose(angle = sin(p * 4f * PI.toFloat()) * 0.12f * e)
            PetSim.State.LOOK -> Pose(angle = sin(p * 2f * PI.toFloat()) * 0.055f * e)
            PetSim.State.WAVE -> Pose(1f + 0.018f * e, 1f - 0.012f * e)
            PetSim.State.SMUG -> Pose(1f + 0.04f * e, 1f - 0.02f * e, -0.06f * e * sim.dir)
            PetSim.State.BLUSH -> Pose(1f + 0.035f * e, 1f - 0.055f * e, 0.045f * e * sim.dir)
            PetSim.State.SQUEEZE -> {
                val squeeze = (0.5f + 0.5f * sin(t * 5f)) * e
                Pose(1f + 0.09f * squeeze, 1f - 0.09f * squeeze)
            }
            PetSim.State.TICKLE -> {
                val giggle = sin(t * 8f) * e
                Pose(1f + 0.035f * giggle, 1f - 0.03f * giggle, giggle * 0.045f)
            }
            PetSim.State.HUNGRY -> {
                val sob=sin(t*5f)*e
                Pose(1f+.012f*sob,1f-.018f*sob,angle=sin(t*2.3f)*.018f*e)
            }
            PetSim.State.INTERACT -> when (sim.targetItem?.defId) {
                "robot", "top", "ball", "block" ->
                    Pose(1f + 0.06f * e, 1f - 0.09f * e, 0.045f * e * sim.dir)
                "plushie" -> Pose(1f + 0.07f * e, 1f - 0.045f * e,
                    0.025f * sin(t * 2.6f) * e)
                "pizza", "sushi", "donut", "cookie", "coffee", "apple", "icecream" -> {
                    val chew = sin(t * 6f) * e
                    Pose(1f + 0.018f * chew, 1f - 0.014f * chew, 0.028f * e * sim.dir)
                }
                else -> Pose(1f + 0.025f * e, 1f - 0.02f * e, 0.03f * e * sim.dir)
            }
            PetSim.State.PULL -> Pose(1f - 0.04f * e, 1f + 0.06f * e)
            else -> Pose(1f + breath * 0.012f, 1f - breath * 0.009f)
        }
    }
}

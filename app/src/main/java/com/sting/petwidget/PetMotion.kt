package com.sting.petwidget

import kotlin.math.PI
import kotlin.math.cos
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

    /** A pose accent with independent preparation, hold and recovery. */
    fun accent(p: Float, start: Float, peak: Float, release: Float, end: Float): Float =
        ease((p - start) / (peak - start)) * ease((end - p) / (end - release))

    private fun squash(y: Float, angle: Float = 0f, lift: Float = 0f) =
        Pose(1f / y, y, angle = angle, lift = lift)

    fun pose(sim: PetSim): Pose {
        val p = (sim.stateTime.toFloat() / sim.stateDur.coerceAtLeast(1)).coerceIn(0f, 1f)
        val e = envelope(p)
        val t = sim.stateTime / 1000f
        val breath = sin(sim.animPhase * 1.6f)
        val step = (1f - cos(sim.animPhase * 11f)) * .5f
        val settle = accent(p, 0f, .24f, .72f, 1f)
        val itemId = sim.targetItem?.defId.orEmpty()
        return when (sim.state) {
            PetSim.State.WALK -> squash(.965f + .065f * step, lift = .13f * step)
            PetSim.State.SLEEP -> {
                // Сон — это спокойное дыхание всем телом, а не растяжение «резины».
                // В кресле питомец чуть сворачивается и кладёт голову набок.
                val inhale = breath * 0.009f
                val curl = accent(p, 0f, .16f, .84f, 1f)
                if (sim.sleepingInBed) {
                    squash(1f - .075f * curl + inhale, angle = -.075f * sim.dir * curl)
                } else {
                    squash(1f - .04f * curl + inhale, angle = -.035f * sim.dir * curl)
                }
            }
            PetSim.State.SIT -> squash(1f - .085f * settle)
            PetSim.State.STRETCH -> squash(1f + .12f * settle - .035f * accent(p, 0f, .10f, .14f, .26f))
            PetSim.State.HAPPY -> {
                val flight = ((p - .24f) / .48f).coerceIn(0f, 1f)
                val lift = 2.4f * flight * (1f - flight)
                val crouch = accent(p, 0f, .15f, .17f, .24f)
                val landing = accent(p, .72f, .79f, .81f, .96f)
                val launch = accent(p, .19f, .25f, .27f, .45f)
                squash(1f - .13f * crouch + .08f * launch - .11f * landing, lift = lift)
            }
            PetSim.State.ROLL -> {
                val turn = ease((p - 0.16f) / 0.65f)
                Pose(1f + 0.06f * e, 1f - 0.08f * e,
                    turn * 2f * PI.toFloat() * sim.dir, lift = sin(turn * PI.toFloat()) * 0.28f)
            }
            PetSim.State.SWAY -> Pose(angle = sin(p * 4f * PI.toFloat()) * 0.12f * e)
            PetSim.State.LOOK -> Pose(angle = sin(p * 2f * PI.toFloat()) * 0.055f * e)
            PetSim.State.NOTICE -> Pose(1f - 0.045f * e, 1f + 0.07f * e,
                -sim.dir * 0.11f * e, lift = 0.10f * e)
            PetSim.State.INSPECT -> Pose(1f + 0.055f * e, 1f - 0.06f * e,
                sim.dir * 0.13f * e, offset = sim.dir * 0.12f * e)
            PetSim.State.CHASE -> {
                val bounce = (1f - cos(t * 24f)) * .5f * e
                Pose(1f - 0.04f * bounce, 1f + 0.05f * bounce,
                    sim.dir * 0.07f * e, lift = 0.24f * bounce)
            }
            PetSim.State.SATISFIED -> Pose(1f + 0.045f * e, 1f - 0.035f * e,
                sin(t * 3.5f) * 0.04f * e)
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
                val sob = sin(t * 5f) * settle
                squash(1f - .026f * settle + .012f * sob, angle = sin(t * 2.3f) * .024f * settle)
            }
            PetSim.State.INTERACT -> when (itemId) {
                "robot", "top", "ball", "block" ->
                    Pose(1f + 0.06f * e, 1f - 0.09f * e, 0.045f * e * sim.dir)
                "plushie" -> Pose(1f + 0.07f * e, 1f - 0.045f * e,
                    0.025f * sin(t * 2.6f) * e)
                "pizza", "sushi", "donut", "cookie", "coffee", "apple", "icecream" -> {
                    val food = HeldMotion.pose(itemId, p, sim.dir, food = true)
                    squash(1f - .025f * food.chew, (.075f * food.taste + .03f * food.carry) * sim.dir)
                }
                else -> Pose(1f + 0.025f * e, 1f - 0.02f * e, 0.03f * e * sim.dir)
            }
            PetSim.State.PULL -> Pose(1f - 0.04f * e, 1f + 0.06f * e)
            else -> squash(1f + .009f * breath)
        }
    }
}

package com.sting.petwidget

import kotlin.math.PI
import kotlin.math.sin

/** Фазы общего движения предмета, лап и лица. Все смещения в единицах сцены. */
internal object HeldMotion {
    data class Grip(val halfWidth: Float, val height: Float)
    data class Pose(
        val carry: Float,
        val hands: Float,
        val scale: Float,
        val tilt: Float,
        val dx: Float,
        val dy: Float,
        val taste: Float,
        val chew: Float,
        val crumbs: Float
    )

    private val bites = floatArrayOf(0.38f, 0.58f, 0.78f)
    private val sips = floatArrayOf(0.43f, 0.66f)

    fun grip(id: String): Grip = when (id) {
        "balloon" -> Grip(.09f, .15f)
        "flower" -> Grip(.63f, .78f)
        "plushie" -> Grip(.88f, 1.13f)
        "boat" -> Grip(.94f, .54f)
        "rocket" -> Grip(.47f, .95f)
        "dice" -> Grip(.88f, 1.02f)
        "pizza" -> Grip(.60f, 1.55f)
        "sushi" -> Grip(.89f, .60f)
        "donut" -> Grip(1.31f, .98f)
        "cookie" -> Grip(.90f, .77f)
        "coffee" -> Grip(.90f, .82f)
        "apple" -> Grip(.90f, .91f)
        "icecream" -> Grip(.36f, .67f)
        else -> Grip(.55f, .60f)
    }

    fun pose(id: String, p: Float, dir: Int, food: Boolean): Pose {
        val contact = if (food) {
            if (id == "coffee") sips else bites
        } else null
        val carryUp = PetMotion.ease((p - .16f) / .17f)
        val carry = carryUp * (1f - PetMotion.ease((p - .78f) / .16f))
        val hands = PetMotion.ease(p / .16f) * (1f - PetMotion.ease((p - .94f) / .06f))
        val flourish = pulse(p, .34f, .49f, .76f)
        var taste = 0f
        var chew = 0f
        var crumbs = 1f
        var remaining = 1f
        var size = 1f
        if (contact != null) {
            for ((index, point) in contact.withIndex()) {
                taste = maxOf(taste, pulse(p, point - .095f, point, point + .085f))
                val chewing = PetMotion.ease((p - point) / .025f) *
                    (1f - PetMotion.ease((p - point - .075f) / .055f))
                chew = maxOf(chew, chewing * (.65f + .35f * sin((p - point) * 67f)))
                if (id != "coffee") {
                    val bite = PetMotion.ease((p - point) / .055f)
                    size -= bite * if (index == 2) .32f else .14f
                    if (p >= point) crumbs = (p - point) / .11f
                }
            }
            if (id != "coffee") remaining = 1f - PetMotion.ease((p - .80f) / .055f)
        }

        val rock = sin((p - .32f).coerceAtLeast(0f) * PI.toFloat() * 7f) * flourish
        val tilt = when (id) {
            "dice" -> dir * 17f * rock
            "plushie" -> dir * -7f * flourish + 4f * rock
            "balloon" -> dir * 11f * flourish
            "rocket" -> dir * 22f * flourish
            "boat" -> 13f * rock
            "flower" -> dir * -42f * flourish
            "coffee" -> dir * -31f * taste
            "icecream" -> dir * -17f * taste
            else -> dir * -12f * taste
        }
        val dx = when (id) {
            "rocket" -> .45f * flourish
            "boat" -> .37f * rock
            "plushie" -> -.16f * flourish
            "flower" -> 1.20f * flourish
            else -> 0f
        }
        val dy = when (id) {
            "balloon" -> -.28f * flourish
            "rocket" -> -.48f * flourish
            "boat" -> -.14f * flourish
            "dice" -> -.11f * kotlin.math.abs(rock)
            else -> 0f
        }
        return Pose(carry, hands, (size * remaining).coerceAtLeast(0f), tilt, dx * dir, dy, taste, chew, crumbs)
    }

    private fun pulse(p: Float, start: Float, peak: Float, end: Float) =
        PetMotion.ease((p - start) / (peak - start)) * PetMotion.ease((end - p) / (end - peak))
}

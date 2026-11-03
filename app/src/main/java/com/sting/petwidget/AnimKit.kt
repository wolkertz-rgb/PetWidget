package com.sting.petwidget

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val TWO_PI_F = (2.0 * PI).toFloat()

/** Приводит угол в диапазон [-PI, PI). */
fun wrapPi(a: Float): Float {
    val m = ((a + Math.PI.toFloat()) % TWO_PI_F + TWO_PI_F) % TWO_PI_F
    return m - Math.PI.toFloat()
}

/**
 * Пружина 2-го порядка. Устойчива при любом dt (внутри подшаги <= 1/120 с).
 * freq — Гц (скорость отклика), zeta — демпфирование:
 *   ~0.4 живой отскок, ~0.6 лёгкое желе, 1.0 без отскока.
 * Аллокаций нет, держать как поля рендера.
 */
class Spring(var x: Float = 0f, var v: Float = 0f) {
    fun step(target: Float, dt: Float, freq: Float, zeta: Float): Float {
        val w = TWO_PI_F * freq
        var left = dt.coerceIn(0f, 0.25f)
        while (left > 0f) {
            val h = if (left < STEP) left else STEP
            v += (w * w * (target - x) - 2f * zeta * w * v) * h
            x += v * h
            left -= h
        }
        return x
    }

    fun snap(value: Float) { x = value; v = 0f }

    private companion object { const val STEP = 1f / 120f }
}

/**
 * Цикл прыжка-шага. ph ∈ [0,1): 0 = касание земли.
 * Земля: сжатие. Воздух: парабола, растяжение пропорционально вертикальной скорости.
 * Растяжение вверх при взлёте и сжатие при посадке — как ждёт глаз
 * (в текущем коде при шаге наоборот: шире при подъёме, выше при спуске).
 */
object Hop {
    const val GROUND = 0.18f

    /** Высота 0..1 (умножить на r * 0.18f для шага, на r * 1.5f для радостного прыжка). */
    fun height(ph: Float): Float {
        if (ph < GROUND) return 0f
        val t = (ph - GROUND) / (1f - GROUND)
        return 4f * t * (1f - t)
    }

    /** Целевой sy. sx берите как 1/sqrt(sy), чтобы сохранялся объём. */
    fun squashY(ph: Float, squash: Float = 0.12f, stretch: Float = 0.07f): Float {
        if (ph < GROUND) return 1f - squash * sin((PI * ph / GROUND).toFloat())
        val t = (ph - GROUND) / (1f - GROUND)
        return 1f + stretch * abs(1f - 2f * t)
    }
}

/**
 * Замена блока «lastPose + k = dtAnim*9».
 * Старый фильтр лерпом срезал амплитуду (ходьба ~x0.63, дрожь INTERACT/TICKLE ~x0.4-0.5)
 * и сдвигал фазу относительно cy, который не сглаживался.
 * Пружины с высокой собственной частотой держат форму и дают лёгкий отскок на переходах.
 * Состояние живёт в памяти рендера. В prefs его писать НЕ нужно.
 */
class PoseSprings {
    private val sx = Spring(1f)
    private val sy = Spring(1f)
    private val lean = Spring(0f)
    private val dx = Spring(0f)

    var outSx = 1f; private set
    var outSy = 1f; private set
    var outLean = 0f; private set
    var outDx = 0f; private set

    /**
     * tSx/tSy/tLean/tDx — целевые значения этого кадра (как раньше считаются в when(st)).
     * drivenLean = true для ROLL: угол задан напрямую (полный оборот), пружину держим на
     * эквивалентном угле, чтобы после оборота её не раскрутило обратно.
     */
    fun update(
        dt: Float, tSx: Float, tSy: Float, tLean: Float, tDx: Float,
        drivenLean: Boolean = false
    ) {
        outSx = sx.step(tSx, dt, 9f, 0.55f)
        outSy = sy.step(tSy, dt, 9f, 0.55f)
        outDx = dx.step(tDx, dt, 7f, 0.8f)
        if (drivenLean) {
            lean.snap(wrapPi(tLean))
            outLean = tLean
        } else {
            outLean = lean.step(tLean, dt, 6f, 0.6f)
        }
    }

    fun reset() { sx.snap(1f); sy.snap(1f); lean.snap(0f); dx.snap(0f) }
}

/*
 * ===================== КАК ПОДКЛЮЧИТЬ В drawPet =====================
 *
 * поля SceneRenderer:   private val pose = PoseSprings()
 *                       (lastPose / lastPosePhase / pet_pose в prefs — удалить)
 *
 * 1) Шаг (WALK) — цели вместо sin(animPhase*11):
 *      val hopPh = (sim.animPhase * 1.15f) % 1f           // ~1.15 шага/с
 *      tSy = Hop.squashY(hopPh);  tSx = 1f / sqrt(tSy)
 *      hopLift = Hop.height(hopPh) * r * 0.18f             // вместо |sin(animPhase*5.5)|
 *    Радость (HAPPY): присед 150 мс -> Hop.height на остаток времени:
 *      ph = (stateTime/stateDur) маппить в цикл Hop; высота * r * 1.5f.
 *
 * 2) ROLL: убрать ветку из petCy (там +0.3r на ВСЁ время, включая присед, + 0.5r в блоке
 *    позы = до 0.8r). Оставить подъём только внутри фазы оборота.
 *
 * 3) Опора на пол (вместо масштаба вокруг центра):
 *      val footY = cy + r * 0.92f            // cy из petCy, ДО масштабирования
 *      pose.update(dtAnim, tSx, tSy, tRot, tDx, drivenLean = (st == ROLL))
 *      cy = footY - r * 0.92f * pose.outSy   // низ тела всегда на footY
 *      c.save()
 *      c.rotate(pose.outLean * 57.2958f + headTilt * 40f, px + pose.outDx, footY)
 *      ... рисуем как раньше в (px + outDx, cy), радиусы * outSx/outSy ...
 *      c.restore()
 *    Если у вас sx/sy применяются через c.scale — используйте c.scale(sx, sy, px, footY).
 *    SWAY: поворот теперь вокруг ступней, поэтому dx = osc*unit*1.1 уменьшите (иначе удвоится).
 *
 * 4) Цель для сна/покоя та же, пружина сама даёт дыхание без скачков при смене состояния
 *    (правило «каждое состояние с нейтральной позы» больше не нужно).
 *
 * 5) Скорость при шаге (в PetSim, ветка WALK): двигаться только в воздухе, чтобы не скользить:
 *      val move = if (dt > 300) WALK_SPEED          // большие шаги: средняя скорость
 *                 else if ((animPhase * 1.15f) % 1f < Hop.GROUND) 0f
 *                 else WALK_SPEED / (1f - Hop.GROUND)
 */

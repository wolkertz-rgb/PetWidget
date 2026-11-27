package com.sting.petwidget

import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.sin

/** Переходы используют активное время симуляции; незавершённые действия ждём. */
class Outing(private val prefs: SharedPreferences, private val persist: Boolean) {
    var yard = persist && prefs.getBoolean("outing_yard", false); private set
    private var elapsed = prefs.getLong("outing_elapsed", 0L).coerceIn(0L, 120_000L)
    private var phase = if (persist) prefs.getInt("outing_phase",0).coerceIn(0,5) else 0
    private var time = if (persist) prefs.getLong("outing_time",0L).coerceAtLeast(0L) else 0L
    private var swingDue = prefs.getLong("outing_swing_due",25_000L).coerceAtLeast(0L)
    private val roomItems = ArrayList<PetSim.SpawnedItem>()
    private val yardItems = ArrayList<PetSim.SpawnedItem>()
    val doorX get() = 0.16f
    val doorTurn: Float get() = when (phase) {
        2 -> PetMotion.ease(time / 380f)
        3 -> 1f - PetMotion.ease((time - 740f) / 420f)
        else -> 0f
    }
    val doorReach: Float get() = when (phase) {
        2 -> PetMotion.accent(time / 1300f, .19f, .34f, .61f, .80f)
        3 -> PetMotion.accent(time / 1160f, .02f, .22f, .53f, .69f)
        else -> 0f
    }
    val doorOpen: Float get() = when (phase) {
        2 -> PetMotion.ease((time - 442f) / 404f)
        3 -> 1f - PetMotion.ease((time - 256f) / 359f)
        else -> 0f
    }
    val fade: Float get() = when (phase) {
        2 -> PetMotion.ease((time - 825f) / 475f)
        3 -> 1f - PetMotion.ease(time / 339f)
        else -> 0f
    }
    internal fun routeDepth(x: Float): Float = when (phase) {
        1 -> PetMotion.ease(abs(x - doorX) / .19f)
        2 -> 0f
        3 -> PetMotion.ease((time - 740f) / 420f)
        4 -> PetMotion.ease(abs(x - .80f) / .19f)
        5 -> PetMotion.ease((time - 9100f) / 900f)
        else -> 1f
    }

    val swinging get() = phase == 5
    val busy get() = phase != 0
    val mount get() = if (swinging) PetMotion.ease(time / 900f) * PetMotion.ease((10_000f-time)/900f) else 0f
    val swingAngle get() = if (swinging) sin(time / 1000f * 2.1f) * 0.22f *
        PetMotion.accent(time / 10_000f, .09f, .23f, .77f, .91f) else 0f

    fun tick(sim: PetSim, dt: Long): Boolean {
        elapsed += dt
        if (phase == 0) {
            val free = sim.state == PetSim.State.IDLE || sim.state == PetSim.State.SIT ||
                (sim.state == PetSim.State.WALK && sim.walkMode == PetSim.WalkMode.RANDOM)
            if (!free) return false
            if (elapsed >= 120_000L) phase = 1
            else if (yard && elapsed >= swingDue) phase = 4
            else return false
            time = 0; sim.targetItem = null; sim.sleepingInBed = false
        }
        time += dt
        if (phase == 1 || phase == 4) {
            val target = if (phase == 1) doorX else 0.80f
            sim.state = PetSim.State.WALK; sim.walkMode = PetSim.WalkMode.NONE
            sim.dir = if (target > sim.x) 1 else -1
            val step = dt / 1000f * PetSim.WALK_SPEED
            if (abs(target - sim.x) <= step) {
                sim.x = target; phase = if (phase == 1) 2 else 5; time = 0
                sim.state = PetSim.State.SIT; sim.stateTime = 0
            } else sim.x += sim.dir * step
        } else if (phase == 2 && time >= 1300L) {
            val parked = if (yard) yardItems else roomItems
            parked.clear(); parked.addAll(sim.items)
            yard = !yard
            sim.items.clear(); sim.items.addAll(if (yard) yardItems else roomItems)
            sim.x = doorX; phase = 3; time = 0; elapsed = 0; swingDue = 25_000
            sim.state = PetSim.State.IDLE; sim.stateTime = 0
            save()
        } else if (phase == 3 && time >= 1160L || phase == 5 && time >= 10_000L) {
            if (phase == 5) swingDue = elapsed + 40_000L
            phase = 0; time = 0; sim.state = PetSim.State.IDLE
            sim.stateTime = 0; sim.stateDur = 1800L
        }
        return true
    }

    fun save() {
        if (!persist) return
        prefs.edit().putBoolean("outing_yard",yard)
            .putLong("outing_elapsed",elapsed.coerceAtMost(120_000L))
            .putInt("outing_phase",phase).putLong("outing_time",time)
            .putLong("outing_swing_due",swingDue).apply()
    }
}

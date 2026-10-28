package com.sting.petwidget

import android.content.Context
import kotlin.math.abs
import kotlin.random.Random

/**
 * Мозг питомца. Принцип: Blob — не рандом-машинка, а маленький домашний зверь:
 * - у него есть ЛЮБОПЫТСТВО: новые предметы побуждают исследовать;
 * - он ЗНАЕТ ВАС: раз в ~5 минут машет лапой и улыбается (пусть не одиноко);
 * - сон всегда в его кожаном кресле;
 * - все состояния непрерывны во времени: кадры без скачков.
 */
class PetSim(context: Context) {

    enum class State { IDLE, WALK, SIT, SLEEP, INTERACT, HAPPY, ROLL, SWAY, LOOK, STRETCH, WAVE }
    enum class WalkMode { NONE, RANDOM, TO_ITEM, TO_BED }

    /** Предмет в комнате */
    class SpawnedItem(
        val defId: String,
        var x: Float,
        var vx: Float,
        var ageMs: Long,
        val ttlMs: Long,
        var confetti: Boolean = false
    ) {
        val def: ItemDefs.Def get() = ItemDefs.byId[defId]!!
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val rng = Random.Default

    private fun rL(a: Long, b: Long): Long = rng.nextLong(a, b)
    private fun rF(a: Float, b: Float): Float = a + (b - a) * rng.nextFloat()

    var x = 0.5f
    var dir = 1
    var state = State.IDLE
    var stateTime = 0L
    var stateDur = 3000L
    var animPhase = 0f
    var walkMode = WalkMode.NONE
    var targetItem: SpawnedItem? = null
    var reaction = ItemDefs.Reaction.PLAY
    var sleepingInBed = false
    val items = ArrayList<SpawnedItem>()

    private var nextSpawnIn = 6_000L             // первый предмет comes скорее
    private var sleepAfterGap = 0L
    private var saveTimer = 0L
    private var pendingSleepDur = 6_000L

    // --- социальная жизнь
    private var msSinceWave = 45_000L            // первый wамяк через ~4 мин
    private var nextWaveEvery = rL(240_000, 360_000)

    init { load() }

    // ------------------------------------------------------------------ тик

    fun tick(dtRaw: Long) {
        val dt = dtRaw.coerceIn(100, 60_000)
        val dtS = dt / 1000f
        animPhase += dtS
        stateTime += dt

        // физика предметов
        val it = items.iterator()
        while (it.hasNext()) {
            val i = it.next()
            i.ageMs += dt
            if (i.ageMs > i.ttlMs) { it.remove(); continue }
            if (i.ageMs >= 0 && abs(i.vx) > 0.01f) {
                i.x += i.vx * dtS
                i.vx *= (1f - 1.6f * dtS).coerceIn(0f, 0.99f)
                if (i.x < 0.07f) { i.x = 0.07f; i.vx = -i.vx * 0.5f }
                if (i.x > 0.93f) { i.x = 0.93f; i.vx = -i.vx * 0.5f }
            }
        }

        // спавн (упавшие с потолка / подарки)
        nextSpawnIn -= dt
        if (nextSpawnIn <= 0) {
            nextSpawnIn = rL(14_000, 55_000)
            if (items.size < 2 && state != State.SLEEP) spawnItem()
        }

        // "Я ВАС ВИЖУ!" — махание владельцу раз в ~5 минут вежливости
        msSinceWave += dt
        if (msSinceWave >= nextWaveEvery &&
            state != State.SLEEP && state != State.INTERACT &&
            state != State.ROLL && state != State.HAPPY
        ) {
            msSinceWave = 0
            nextWaveEvery = rL(240_000, 360_000)
            state = State.WAVE
            stateTime = 0
            stateDur = 2_400
        }

        // после долгой разлуки сладко досыпает
        if (sleepAfterGap > 0) {
            sleepAfterGap -= dt
            if (state != State.SLEEP) {
                state = State.SLEEP
                stateTime = 0
                stateDur = (sleepAfterGap + 1)
                sleepingInBed = false
            }
            if (sleepAfterGap <= 0) stateDur = stateTime + 1
        }

        when (state) {
            State.WALK -> {
                when (walkMode) {
                    WalkMode.TO_BED -> {
                        val dxb = BED_X - x
                        if (abs(dxb) < 0.04f) {
                            x = BED_X
                            state = State.SLEEP
                            stateTime = 0
                            stateDur = pendingSleepDur
                            sleepingInBed = true
                        } else {
                            dir = if (dxb > 0) 1 else -1
                            x = (x + dir * WALK_SPEED * dtS).coerceIn(X_MIN, X_MAX)
                            if (stateTime >= stateDur) {
                                state = State.SLEEP
                                stateTime = 0
                                stateDur = pendingSleepDur
                                sleepingInBed = false
                            }
                        }
                    }
                    else -> {
                        x += dir * WALK_SPEED * dtS
                        if (x < X_MIN) { x = X_MIN; if (walkMode == WalkMode.RANDOM) dir = 1 else nextAction() }
                        if (x > X_MAX) { x = X_MAX; if (walkMode == WalkMode.RANDOM) dir = -1 else nextAction() }
                        if (walkMode == WalkMode.TO_ITEM) {
                            val ti = targetItem
                            if (ti == null || !items.contains(ti)) {
                                targetItem = null
                                nextAction()
                            } else {
                                val dx = ti.x - x
                                if (abs(dx) < 0.07f) {
                                    beginInteract(ti)
                                } else {
                                    dir = if (dx > 0) 1 else -1
                                }
                            }
                        }
                        if (state == State.WALK && stateTime >= stateDur) nextAction()
                    }
                }
            }

            State.INTERACT -> if (stateTime >= stateDur) finishInteract()

            else -> if (stateTime >= stateDur) nextAction()
        }

        saveTimer += dt
        if (saveTimer >= 20_000) { saveTimer = 0; save() }
    }

    // -------------------------------------------------------------- решения

    private fun nextAction() {
        val prev = state
        val woke = prev == State.SLEEP && sleepAfterGap <= 0

        stateTime = 0
        targetItem = null
        walkMode = WalkMode.NONE
        sleepingInBed = false

        if (sleepAfterGap > 0) { state = State.SLEEP; stateDur = 2_500; return }

        val nearest = items.minByOrNull { abs(it.x - x) }
        val fresh = items.firstOrNull { it.ageMs in 0L..12_000L }
        val r = rng.nextFloat()

        when {
            // только что проснулся: потянуться, а потом пусть имя жизнь продолжается
            woke && (fresh == null || rng.nextFloat() < 0.5f) -> {
                state = State.STRETCH
                stateDur = 1_600
            }

            // ЛЮБОПЫТСТВО: свежий предмет исследуется почти всегда
            fresh != null && rng.nextFloat() < 0.85f -> {
                targetItem = fresh
                walkMode = WalkMode.TO_ITEM
                state = State.WALK
                stateDur = 16_000
                dir = if (fresh.x > x) 1 else -1
            }

            // обычное любопытство к предмету
            nearest != null && r < 0.68f -> {
                targetItem = nearest
                walkMode = WalkMode.TO_ITEM
                state = State.WALK
                stateDur = 14_000
                dir = if (nearest.x > x) 1 else -1
            }

            // трюки
            r < 0.76f -> {
                val rr = rng.nextFloat()
                when {
                    rr < 0.30f -> { state = State.ROLL; stateDur = 1_300 }
                    rr < 0.58f -> { state = State.SWAY; stateDur = 2_100 }
                    rr < 0.84f -> { state = State.LOOK; stateDur = 1_600 }
                    else       -> { state = State.STRETCH; stateDur = 1_500 }
                }
            }

            r < 0.84f -> {
                walkMode = WalkMode.RANDOM
                state = State.WALK
                stateDur = rL(2_500, 7_000)
                dir = if (rng.nextBoolean()) 1 else -1
            }
            r < 0.92f -> { state = State.SIT; stateDur = rL(3_000, 8_000) }
            r < 0.97f -> { state = State.IDLE; stateDur = rL(2_000, 5_000) }
            else -> {
                // сон всегда начинается с похода к своему креслу
                pendingSleepDur = rL(5_000, 16_000)
                walkMode = WalkMode.TO_BED
                state = State.WALK
                stateDur = 9_000
                dir = if (BED_X > x) 1 else -1
            }
        }
    }

    private fun beginInteract(item: SpawnedItem) {
        state = State.INTERACT
        stateTime = 0
        reaction = item.def.reaction
        stateDur = when (reaction) {
            ItemDefs.Reaction.EAT   ->     rL(2_200, 3_200)  // накормили с рук!
            ItemDefs.Reaction.SMELL ->     2_400
            ItemDefs.Reaction.LIFT  ->     2_100
            ItemDefs.Reaction.RUB   ->     2_500
            ItemDefs.Reaction.PUSH  ->     1_100
            ItemDefs.Reaction.OPEN  ->     2_800
            ItemDefs.Reaction.PLAY  ->     rL(1_200, 2_000)
        }
        dir = if (item.x >= x) 1 else -1
    }

    private fun finishInteract() {
        val ti = targetItem
        if (ti != null) {
            when (reaction) {
                ItemDefs.Reaction.EAT, ItemDefs.Reaction.SMELL -> {
                    items.remove(ti)
                    // поел — вскоре помашет владельцу "спасибо"
                    if (rng.nextFloat() < 0.35f) {
                        msSinceWave = nextWaveEvery - 900L
                    }
                }
                ItemDefs.Reaction.PUSH -> ti.vx = dir * rF(0.35f, 0.7f)
                ItemDefs.Reaction.OPEN -> openGift(ti)
                else -> {}
            }
        }
        targetItem = null
        state = State.HAPPY
        stateTime = 0
        stateDur = 700
    }

    private fun openGift(box: SpawnedItem) {
        val others = items.filter { it != box }.map { it.defId }.toSet()
        val pool = ItemDefs.ALL.filter {
            it.kind != ItemDefs.Kind.BOX && it.id !in others
        }
        items.remove(box)
        if (pool.isNotEmpty()) {
            val def = pool.random(rng)
            items.add(
                SpawnedItem(
                    def.id,
                    x = box.x.coerceIn(X_MIN, X_MAX),
                    vx = 0f,
                    ageMs = 0,
                    ttlMs = rL(90_000, 210_000),
                    confetti = true
                )
            )
        }
    }

    fun tap() {
        if (state == State.SLEEP) return
        val r = rng.nextFloat()
        stateState(r)
    }

    private fun stateState(r: Float) {
        when {
            r < 0.25f && state != State.INTERACT -> { state = State.WAVE; stateTime = 0; stateDur = 2_400 }
            r < 0.55f && state != State.INTERACT -> { state = State.ROLL; stateTime = 0; stateDur = 1_300 }
            r < 0.80f && state != State.INTERACT -> { state = State.SWAY; stateTime = 0; stateDur = 2_100 }
            else -> {
                state = State.HAPPY
                stateTime = 0
                stateDur = 900
            }
        }
        targetItem = null
        walkMode = WalkMode.NONE
        sleepingInBed = false
    }

    fun onResume(gapMs: Long) {
        if (gapMs > 2 * 60_000) {
            items.removeAll { it.ageMs > it.ttlMs }
        }
        if (gapMs > 4 * 60 * 60_000) {
            sleepAfterGap = rL(8_000, 18_000)
        }
        stateDur = stateTime + 1
        save()
    }

    private fun spawnItem() {
        val haveBox = items.any { it.def.kind == ItemDefs.Kind.BOX }
        if (!haveBox && rng.nextFloat() < 0.22f) {
            items.add(newFalling("box"))
            return
        }
        val present = items.map { it.defId }.toSet()
        val pool = ItemDefs.ALL.filter { it.kind != ItemDefs.Kind.BOX && it.id !in present }
        if (pool.isEmpty()) return
        val def = pool.random(rng)
        items.add(newFalling(def.id))
    }

    private fun newFalling(defId: String): SpawnedItem = SpawnedItem(
        defId,
        x = rF(0.10f, 0.66f), // левее кресла — кресло занимает правый угол
        vx = 0f,
        ageMs = -1500L,
        ttlMs = rL(90_000, 210_000)
    )

    // ---------------------------------------------------------- сохранение

    fun save() {
        val ed = prefs.edit()
        ed.putFloat(KEY_X, x)
        ed.putInt(KEY_DIR, dir)
        ed.putString(KEY_STATE, state.name)
        ed.putString(KEY_MODE, walkMode.name)
        ed.putFloat(KEY_PHASE, animPhase)
        ed.putLong(KEY_STIME, stateTime)
        ed.putLong(KEY_SDUR, stateDur)
        ed.putString(KEY_REACTION, reaction.name)
        ed.putBoolean(KEY_INBED, sleepingInBed)
        if (state == State.INTERACT && targetItem != null) {
            ed.putString(KEY_TARGET, "${targetItem!!.defId},${targetItem!!.x}")
        } else {
            ed.remove(KEY_TARGET)
        }
        ed.putString(
            KEY_ITEMS,
            items.joinToString("|") { "${it.defId},${it.x},${it.vx},${it.ageMs},${it.ttlMs}" }
        )
        ed.apply()
    }

    fun load() {
        x = prefs.getFloat(KEY_X, 0.5f).coerceIn(X_MIN, X_MAX)
        dir = prefs.getInt(KEY_DIR, 1)
        state = try { PetSim.State.valueOf(prefs.getString(KEY_STATE, "IDLE")!!) } catch (_: Exception) { State.IDLE }
        walkMode = try { WalkMode.valueOf(prefs.getString(KEY_MODE, "NONE")!!) } catch (_: Exception) { WalkMode.NONE }
        animPhase = prefs.getFloat(KEY_PHASE, animPhase)
        stateTime = prefs.getLong(KEY_STIME, 0L)
        stateDur = prefs.getLong(KEY_SDUR, 2_000L)
        reaction = try {
            ItemDefs.Reaction.valueOf(prefs.getString(KEY_REACTION, "PLAY")!!)
        } catch (_: Exception) { ItemDefs.Reaction.PLAY }
        sleepingInBed = prefs.getBoolean(KEY_INBED, false)
        targetItem = null
        items.clear()
        prefs.getString(KEY_ITEMS, null)?.takeIf { it.isNotBlank() }?.split("|")?.forEach { s ->
            val p = s.split(",")
            if (p.size == 5) {
                val d = ItemDefs.byId[p[0]] ?: return@forEach
                items.add(
                    SpawnedItem(
                        d.id,
                        p[1].toFloatOrNull() ?: 0.5f,
                        p[2].toFloatOrNull() ?: 0f,
                        p[3].toLongOrNull() ?: 0L,
                        p[4].toLongOrNull() ?: 120_000L
                    )
                )
            }
        }
        if (state == State.INTERACT) {
            val tk = prefs.getString(KEY_TARGET, null)?.split(",")
            if (tk != null && tk.size == 2) {
                val tx = tk[1].toFloatOrNull()
                targetItem = items.firstOrNull {
                    it.defId == tk[0] && tx != null && abs(it.x - tx) < 0.15f
                }
            }
        }
    }

    companion object {
        private const val PREFS = "pet_state"
        private const val KEY_X = "x"
        private const val KEY_DIR = "dir"
        private const val KEY_STATE = "state"
        private const val KEY_MODE = "mode"
        private const val KEY_PHASE = "phase"
        private const val KEY_ITEMS = "items"
        private const val KEY_STIME = "stime"
        private const val KEY_SDUR = "sdur"
        private const val KEY_REACTION = "reaction"
        private const val KEY_INBED = "inbed"
        private const val KEY_TARGET = "target"

        const val WALK_SPEED = 0.075f
        const val X_MIN = 0.09f
        const val X_MAX = 0.91f
        const val BED_X = 0.80f

        const val HOLD_AT = 600L     // сколько мс тянуться/брать
        const val RELEASE_MS = 250L  // плавно убрать руки
    }
}

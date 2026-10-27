package com.sting.petwidget

import android.content.Context
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/**
 * Автономная жизнь питомца-блоба.
 * Координаты нормализованы: x в пределах 0..1 внутри комнаты,
 * "пол" всегда внизу — границы виджета и есть стены.
 *
 * Симуляция тикает ТОЛЬКО когда экран включён (тик присылает PetService).
 * Всё состояние сохраняется в SharedPreferences, поэтому питомец
 * "помнит" свою жизнь между выключениями телефона.
 */
class PetSim(context: Context) {

    enum class State { IDLE, WALK, SIT, SLEEP, INTERACT, HAPPY }
    enum class WalkMode { NONE, RANDOM, TO_ITEM }

    /** Предмет, который сейчас лежит в комнате */
    class SpawnedItem(
        val defId: String,
        var x: Float,
        var vx: Float,
        var ageMs: Long, // может быть отрицательным: "дорастает" при появлении
        val ttlMs: Long
    ) {
        val def: ItemDefs.Def get() = ItemDefs.byId[defId]!!
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val rng = Random.Default

    var x = 0.5f                 // позиция питомца
    var dir = 1                  // куда смотрит: 1 вправо, -1 влево
    var state = State.IDLE
    var stateTime = 0L           // сколько мс уже в текущем состоянии
    var stateDur = 3000L         // сколько мс просидит в нём
    var animPhase = 0f           // общая "секунда" анимации (для покачивания)
    var walkMode = WalkMode.NONE
    var targetItem: SpawnedItem? = null
    var reaction = ItemDefs.Reaction.PLAY
    val items = ArrayList<SpawnedItem>()

    private var nextSpawnIn = 8_000L
    private var sleepAfterGap = 0L
    private var saveTimer = 0L

    init { load() }

    // ------------------------------------------------------------------ тик

    fun tick(dtRaw: Long) {
        val dt = dtRaw.coerceIn(100, 60_000)
        val dtS = dt / 1000f
        animPhase += dtS
        stateTime += dt

        // физика предметов: остановка после толчка, границы комнаты
        val it = items.iterator()
        while (it.hasNext()) {
            val i = it.next()
            i.ageMs += dt
            if (i.ageMs > i.ttlMs) { it.remove(); continue }
            if (abs(i.vx) > 0.01f) {
                i.x += i.vx * dtS
                i.vx *= (1f - 1.6f * dtS).coerceIn(0f, 0.99f)
                if (i.x < 0.07f) { i.x = 0.07f; i.vx = -i.vx * 0.5f }
                if (i.x > 0.93f) { i.x = 0.93f; i.vx = -i.vx * 0.5f }
            }
        }

        // случайные появления новых предметов
        nextSpawnIn -= dt
        if (nextSpawnIn <= 0) {
            nextSpawnIn = (14_000..55_000).random(rng)
            if (items.size < 2 && state != State.SLEEP) spawnItem()
        }

        // после долгого отсутствия человека питомец сладко спит какое-то время
        if (sleepAfterGap > 0) {
            sleepAfterGap -= dt
            if (state != State.SLEEP) {
                state = State.SLEEP
                stateTime = 0
                stateDur = (sleepAfterGap + 1)
            }
            if (sleepAfterGap <= 0) stateDur = stateTime + 1 // проснуться
        }

        when (state) {
            State.WALK -> {
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

            State.INTERACT -> {
                if (stateTime >= stateDur) {
                    val ti = targetItem
                    if (ti != null) {
                        if (reaction == ItemDefs.Reaction.EAT || reaction == ItemDefs.Reaction.SMELL) {
                            items.remove(ti)          // съел
                        } else {
                            ti.vx = dir * (0.35f..0.7f).random(rng) // толкнул/пинал
                        }
                    }
                    targetItem = null
                    state = State.HAPPY
                    stateTime = 0
                    stateDur = 700
                }
            }

            else -> if (stateTime >= stateDur) nextAction()
        }

        saveTimer += dt
        if (saveTimer >= 20_000) { saveTimer = 0; save() }
    }

    // -------------------------------------------------------------- решения

    private fun nextAction() {
        stateTime = 0
        targetItem = null
        walkMode = WalkMode.NONE

        if (sleepAfterGap > 0) { state = State.SLEEP; stateDur = 2_500; return }

        val nearest = items.minByOrNull { abs(it.x - x) }
        val r = rng.nextFloat()
        when {
            nearest != null && r < 0.7f -> {
                targetItem = nearest
                walkMode = WalkMode.TO_ITEM
                state = State.WALK
                stateDur = 14_000
                dir = if (nearest.x > x) 1 else -1
            }
            r < 0.55f -> {
                walkMode = WalkMode.RANDOM
                state = State.WALK
                stateDur = (2_500..7_000).random(rng)
                dir = if (rng.nextBoolean()) 1 else -1
            }
            r < 0.78f -> {
                state = State.SIT
                stateDur = (3_000..8_000).random(rng)
            }
            r < 0.93f -> {
                state = State.IDLE
                stateDur = (2_000..6_000).random(rng)
            }
            else -> {
                state = State.SLEEP
                stateDur = (5_000..16_000).random(rng)
            }
        }
    }

    private fun beginInteract(item: SpawnedItem) {
        state = State.INTERACT
        stateTime = 0
        reaction = item.def.reaction
        stateDur = when (reaction) {
            ItemDefs.Reaction.EAT   -> (1_500..2_500).random(rng)
            ItemDefs.Reaction.SMELL -> 1_300
            ItemDefs.Reaction.PLAY  -> (1_200..2_000).random(rng)
            ItemDefs.Reaction.PUSH  -> 900
        }
        dir = if (item.x >= x) 1 else -1
    }

    /** Пользователь ткнул в виджет — питомец радуется */
    fun tap() {
        if (state == State.SLEEP) return // не будим его, пусть спит :)
        state = State.HAPPY
        stateTime = 0
        stateDur = 900
        targetItem = null
        walkMode = WalkMode.NONE
    }

    /** Экран снова включился. gapMs — сколько питомец жил без свидетелей */
    fun onResume(gapMs: Long) {
        if (gapMs > 2 * 60_000) {
            // предметы за это время могли "устареть" — жизни цикл честный
            items.removeAll { it.ageMs > it.ttlMs }
        }
        if (gapMs > 4 * 60 * 60_000) {
            sleepAfterGap = (8_000..18_000).random(rng)
        }
        stateDur = stateTime + 1 // чтобы сразу выбрать новое действие
        save()
    }

    private fun spawnItem() {
        val present = items.map { it.defId }.toSet()
        val pool = ItemDefs.ALL.filter { it.id !in present }
        if (pool.isEmpty()) return
        val def = pool.random(rng)
        items.add(
            SpawnedItem(
                def.id,
                x = (0.15f..0.85f).random(rng),
                vx = 0f,
                ageMs = -900L, // эффект "появился из воздуха"
                ttlMs = (90_000..210_000).random(rng)
            )
        )
    }

    // ---------------------------------------------------------- сохранение

    fun save() {
        prefs.edit()
            .putFloat(KEY_X, x)
            .putInt(KEY_DIR, dir)
            .putString(KEY_STATE, state.name)
            .putString(KEY_MODE, walkMode.name)
            .putString(
                KEY_ITEMS,
                items.joinToString("|") { "${it.defId},${it.x},${it.vx},${it.ageMs},${it.ttlMs}" }
            )
            .apply()
    }

    private fun load() {
        x = prefs.getFloat(KEY_X, 0.5f).coerceIn(X_MIN, X_MAX)
        dir = prefs.getInt(KEY_DIR, 1)
        state = try { PetSim.State.valueOf(prefs.getString(KEY_STATE, "IDLE")!!) } catch (_: Exception) { State.IDLE }
        walkMode = try { WalkMode.valueOf(prefs.getString(KEY_MODE, "NONE")!!) } catch (_: Exception) { WalkMode.NONE }
        stateTime = 0
        stateDur = 2_000
        targetItem = null
        items.clear()
        prefs.getString(KEY_ITEMS, null)?.takeIf { it.isNotBlank() }?.split("|")?.forEach { s ->
            val p = s.split(",")
            if (p.size == 5) {
                val d = ItemDefs.byId[p[0]] ?: return@forEach
                items.add(SpawnedItem(d.id, p[1].toFloatOrNull() ?: 0.5f, p[2].toFloatOrNull() ?: 0f, p[3].toLongOrNull() ?: 0L, p[4].toLongOrNull() ?: 120_000L))
            }
        }
    }

    companion object {
        private const val PREFS = "pet_state"
        private const val KEY_X = "x"
        private const val KEY_DIR = "dir"
        private const val KEY_STATE = "state"
        private const val KEY_MODE = "mode"
        private const val KEY_ITEMS = "items"

        const val WALK_SPEED = 0.075f
        const val X_MIN = 0.09f
        const val X_MAX = 0.91f
    }
}

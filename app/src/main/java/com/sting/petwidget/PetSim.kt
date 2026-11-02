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

    enum class State { IDLE, WALK, SIT, SLEEP, INTERACT, HAPPY, ROLL, SWAY, LOOK, STRETCH, WAVE, SMUG, BLUSH, SQUEEZE, TICKLE, PULL, HUNGRY }
    enum class WalkMode { NONE, RANDOM, TO_ITEM, TO_BED, TO_SWITCH }

    /** Предмет в комнате */
    class SpawnedItem(
        val defId: String,
        var x: Float,
        var vx: Float,
        var ageMs: Long,
        val ttlMs: Long,
        var confetti: Boolean = false,
        var used: Boolean = false // питомец уже поиграл с ним — интерес пропал
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
    var petStyle = 0              // вариант реакции на поглаживание
    var hunger = 30f              // 0..100: растёт со временем, снимается едой
    val items = ArrayList<SpawnedItem>()
    var tickCount = 0L           // для диагностики (бейдж)

    private var nextSpawnIn = 6_000L             // первый предмет comes скорее
    private var sleepAfterGap = 0L
    private var saveTimer = 0L
    private var pendingSleepDur = 6_000L
    private var interactShown = false   // в режиме "ходов": середину уже показали?

    // --- социальная жизнь
    private var msSinceWave = 45_000L            // первый wамяк через ~4 мин
    private var nextWaveEvery = rL(240_000, 360_000)
    private var nextAutoInteract = 35f           // первая самостоятельная встреча с предметом (~35 сек)
    private var nextTrickAt = 15f                // кулдаун между самостоятельными трюками (30 сек)
    private var lastPullAt = 0f                  // ЖЁСТКОЕ ПРАВИЛО: тяга раз в 60 сек (animPhase)
    private var switchPartyLeft = 0              // «детская игра со светом» — сколько тяг осталось
    private var pendingNextPull = false
    private var pullDone = false                 // свет в текущей тяге уже переключён?
    private var nextHungryAt = 90f               // таймер «голодной побудки»

    init { load() }

    // ------------------------------------------------------------------ тик

    fun tick(dtRaw: Long) {
        tickCount++
        val dt = dtRaw.coerceIn(100, 60_000)
        val dtS = dt / 1000f
        animPhase += dtS
        stateTime += dt

        // физика предметов: НОГОВОЙ-совместимая — шаг и трение ограничены,
        // иначе за один тик предмет телепортировался и скорость умирала мгновенно
        val it = items.iterator()
        while (it.hasNext()) {
            val i = it.next()
            i.ageMs += dt
            if (i.ageMs > i.ttlMs) { it.remove(); continue }
            if (i.ageMs >= 0 && abs(i.vx) > 0.01f) {
                val moveS = (dt / 1000f).coerceAtMost(0.55f)   // шаг ≤ 0.55 комнаты за тик
                val decayS = (dt / 1000f).coerceAtMost(0.5f)   // трение как в ~0.5с кадре
                i.x += i.vx * moveS
                i.vx *= (1f - 1.6f * decayS).coerceIn(0f, 0.99f)
                if (i.x < 0.07f) { i.x = 0.07f; i.vx = -i.vx * 0.5f }
                if (i.x > 0.93f) { i.x = 0.93f; i.vx = -i.vx * 0.5f }
            }
        }

        // спавн (упавшие с потолка / подарки) — ЛИМИТ: максимум 1 предмет
        nextSpawnIn -= dt
        if (nextSpawnIn <= 0) {
            nextSpawnIn = rL(14_000, 55_000)
            if (items.isEmpty() && state != State.SLEEP && state != State.PULL && state != State.WAVE) spawnItem()
        }
        // "Я ВАС ВИЖУ!" — махание владельцу раз в ~5 минут вежливости
        msSinceWave += dt
        if (msSinceWave >= nextWaveEvery &&
            state != State.SLEEP && state != State.INTERACT && state != State.PULL &&
            state != State.ROLL && state != State.HAPPY &&
            walkMode != WalkMode.TO_SWITCH && !pendingNextPull
        ) {
            msSinceWave = 0
            nextWaveEvery = rL(240_000, 360_000)
            state = State.WAVE
            stateTime = 0
            stateDur = 2_400
        }

        // ЖЁСТКОЕ ПРАВИЛО: раз в минуту питомец ОБЯЗАН дёрнуть выключатель.
        // Не рандом и без бегов — будит даже спящего, перебивает остальные дела.
        // Пока он в процессе тяги/похода/серии — правило честно ждёт окончания.
        val switchDue = animPhase - lastPullAt >= 60f
        val switchBusy = state == State.PULL || walkMode == WalkMode.TO_SWITCH || pendingNextPull
        if (switchDue && !switchBusy) {
            lastPullAt = animPhase
            walkMode = WalkMode.TO_SWITCH
            state = State.WALK
            stateTime = 0
            stateDur = 12_000
            dir = if (SWITCH_X > x) 1 else -1
            sleepingInBed = false
            targetItem = null
        }

        // ГАРАНТИРОВАННОЕ ЖЕЛАНИЕ: давно не трогал предметы и есть НЕТРОНУТЫЙ —
        // начинает взаимодействие (тот же путь, что и тап-тест — он работает всегда)
        // голоден сильнее меры — еды в приоритете и ждать недолго
        if (animPhase >= nextAutoInteract &&
            state != State.INTERACT && state != State.SLEEP &&
            state != State.WAVE && state != State.HAPPY &&
            walkMode != WalkMode.TO_SWITCH && !pendingNextPull
        ) {
            val unused = items.filter { !it.used }
            val candidates = if (hunger > 55f) {
                (unused.filter { it.def.kind == ItemDefs.Kind.FOOD } + unused).distinctBy { it.x }
            } else unused
            val nearest = candidates.minByOrNull { abs(it.x - x) }
            if (nearest != null) {
                nextAutoInteract = animPhase + if (hunger > 55f) rF(45f, 75f) else rF(90f, 150f)
                if (abs(nearest.x - x) < 0.15f) {
                    targetItem = nearest
                    beginInteract(nearest)
                } else {
                    // далекий предмет: честно идём к нему (ход дойдёт благодаря clamp)
                    targetItem = nearest
                    walkMode = WalkMode.TO_ITEM
                    state = State.WALK
                    stateTime = 0
                    stateDur = 16_000
                    dir = if (nearest.x > x) 1 else -1
                }
            } else {
                // нетронутых предметов нет — интерес утих, ждём нового
                nextAutoInteract = animPhase + rF(60f, 120f)
            }
        }

        // ГОЛОД: растёт со временем жизни на экране
        hunger = (hunger + dt * 100f / HUNGER_FULL_MS).coerceIn(0f, 100f)
        // если голоден сильнее меры и еды в комнате нет — голодная побудка-жест
        if (hunger > HUNGER_LEVEL && animPhase >= nextHungryAt &&
            (state == State.IDLE || state == State.SIT ||
                (state == State.WALK && walkMode == WalkMode.RANDOM))
        ) {
            nextHungryAt = animPhase + 45f
            state = State.HUNGRY
            stateTime = 0
            stateDur = 2_800
        }

        // после долгой разлуки сладко досыпает (не посреди похода к выключателю)
        if (sleepAfterGap > 0 && walkMode != WalkMode.TO_SWITCH && state != State.PULL) {
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
                        if (walkMode == WalkMode.TO_ITEM) {
                            val ti = targetItem
                            if (ti == null || !items.contains(ti)) {
                                targetItem = null
                                nextAction()
                            } else {
                                val dx = ti.x - x
                                val dist = abs(dx)
                                if (dist < 0.07f) {
                                    // ДОШЁЛ — начинается взаимодействие
                                    beginInteract(ti)
                                } else {
                                    dir = if (dx > 0) 1 else -1
                                    // шаг строго ограничен дистанцией: питомец
                                    // НЕ может перепрыгнуть предмет ни при каком dt
                                    val step = WALK_SPEED * dtS
                                    val move = if (dist - 0.05f < step) dist - 0.05f else step
                                    x = (x + dir * move).coerceIn(X_MIN, X_MAX)
                                }
                                // цель важнее таймера: цель не отменяется по времени,
                                // пока питомец реально ни дойдёт
                            }
                        } else if (walkMode == WalkMode.TO_SWITCH) {
                            val dxs = SWITCH_X - x
                            if (abs(dxs) < 0.05f) {
                                x = SWITCH_X
                                beginPull()
                            } else {
                                dir = if (dxs > 0) 1 else -1
                                val step = WALK_SPEED * dtS
                                val move = if (abs(dxs) - 0.04f < step) abs(dxs) - 0.04f else step
                                x = (x + dir * move).coerceIn(X_MIN, X_MAX)
                                if (stateTime >= stateDur) {
                                    // не дошёл — отложим это на потом
                                    state = State.IDLE
                                    stateTime = 0
                                    stateDur = 2_000
                                }
                            }
                        } else {
                            // случайная прогулка: мягкий отскок от стен
                            x += dir * WALK_SPEED * dtS
                            if (x < X_MIN) { x = X_MIN; dir = 1 }
                            if (x > X_MAX) { x = X_MAX; dir = -1 }
                        }
                        if (state == State.WALK && stateTime >= stateDur && walkMode != WalkMode.TO_ITEM) nextAction()
                    }
                }
            }

            State.INTERACT -> {
                if (stateTime >= stateDur) {
                    if (dt > 1500 && !interactShown) {
                        // большой шаг времени (режим "ходов"): показать СЕРЕДИНУ
                        // действия в этом кадре (руки держат предмет), завершить в следующий
                        interactShown = true
                        stateTime = stateDur / 2
                    } else {
                        interactShown = false
                        finishInteract()
                    }
                }
            }

            State.PULL -> {
                // щелчок света в середине тяги
                if (!pullDone && stateTime >= PULL_TOGGLE_MS) {
                    pullDone = true
                    toggleLight()
                }
                if (stateTime >= stateDur) {
                    if (dt > 1500 && !interactShown) {
                        // ход-режим: показать момент тяги в кадре
                        interactShown = true
                        stateTime = PULL_TOGGLE_MS + 60
                    } else {
                        interactShown = false
                        finishPull()
                    }
                }
            }

            else -> if (stateTime >= stateDur) {
                if (pendingNextPull && state == State.HAPPY) {
                    pendingNextPull = false
                    beginPull()
                } else {
                    nextAction()
                }
            }
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

        val nearest = items.filter { !it.used }.minByOrNull { abs(it.x - x) }
        val fresh = nearest?.takeIf { it.ageMs in 0L..12_000L }
        val r = rng.nextFloat()

        when {
            // только что проснулся: потянуться, а потом пусть имя жизнь продолжается
            woke && (nearest == null || rng.nextFloat() < 0.5f) -> {
                state = State.STRETCH
                stateDur = 1_600
            }

            // ЛЮБОПЫТСТВО к НОВОМУ предмету (сила — только пока он нетронутый)
            fresh != null && rng.nextFloat() < 0.85f -> {
                targetItem = fresh
                walkMode = WalkMode.TO_ITEM
                state = State.WALK
                stateDur = 16_000
                dir = if (fresh.x > x) 1 else -1
            }

            // обычное любопытство: только к нетронутым предметам
            nearest != null && r < 0.62f -> {
                targetItem = nearest
                walkMode = WalkMode.TO_ITEM
                state = State.WALK
                stateDur = 14_000
                dir = if (nearest.x > x) 1 else -1
            }

            // РЕДКИЕ выходки-трюки: не чаще, чем раз в 30 секунд
            r < 0.70f && animPhase >= nextTrickAt -> {
                nextTrickAt = animPhase + 30f
                val rr = rng.nextFloat()
                when {
                    rr < 0.25f -> { state = State.ROLL; stateDur = 1_300 }
                    rr < 0.50f -> { state = State.SWAY; stateDur = 2_100 }
                    rr < 0.80f -> { state = State.LOOK; stateDur = 1_600 }
                    else       -> { state = State.STRETCH; stateDur = 1_500 }
                }
            }

            // в кулдауне трюков — просто гуляет заметно больше
            r < 0.88f -> {
                walkMode = WalkMode.RANDOM
                state = State.WALK
                stateDur = rL(4_000, 9_000)
                dir = if (rng.nextBoolean()) 1 else -1
            }
            r < 0.945f -> { state = State.SIT; stateDur = rL(3_000, 8_000) }
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
        interactShown = false
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
                    // поел — голод отступает
                    hunger = (hunger - HUNGER_EAT_RELIEF).coerceAtLeast(0f)
                    // поел — вскоре помашет владельцу "спасибо"
                    if (rng.nextFloat() < 0.35f) {
                        msSinceWave = nextWaveEvery - 900L
                    }
                }
                ItemDefs.Reaction.PUSH -> ti.vx = dir * rF(0.35f, 0.7f)
                ItemDefs.Reaction.OPEN -> openGift(ti)
                else -> {}
            }
            // ОДНА ВСТРЕЧА — ОДНУ ВЫСТАВКУ: после контакта интерес исчезает
            if (items.contains(ti)) ti.used = true
        }
        targetItem = null
        state = State.HAPPY
        stateTime = 0
        stateDur = 700
    }

    private fun openGift(box: SpawnedItem) {
        val others = items.filter { it != box }.map { it.defId }.toSet()
        //нынутри подарка — предмет согласно весам (голоден → еда чаще)
        var kind = pickKind()
        var pool = ItemDefs.ALL.filter {
            it.kind == kind && it.kind != ItemDefs.Kind.BOX && it.id !in others
        }
        if (pool.isEmpty()) {
            kind = if (kind == ItemDefs.Kind.FOOD) ItemDefs.Kind.TOY else ItemDefs.Kind.FOOD
            pool = ItemDefs.ALL.filter {
                it.kind == kind && it.kind != ItemDefs.Kind.BOX && it.id !in others
            }
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

    /** Начало тяги за шнур: питомец встал под выключатель */
    fun beginPull() {
        x = SWITCH_X
        dir = 1
        walkMode = WalkMode.NONE
        targetItem = null
        state = State.PULL
        stateTime = 0
        stateDur = PULL_DUR
        pullDone = false
        lastPullAt = animPhase
    }

    private fun finishPull() {
        // «детская игра»: иногда после тяги — серия весёлых включений-выключений
        if (switchPartyLeft == 0 && !pendingNextPull && rng.nextFloat() < 0.30f) {
            switchPartyLeft = (2..4).random(rng)
        }
        if (switchPartyLeft > 0) {
            switchPartyLeft--
            pendingNextPull = true
            petStyle = 0
            state = State.HAPPY
            stateTime = 0
            stateDur = 650
        } else {
            // финал: довольный смешок. Свет вернётся сам — тяга по жёсткому
            // правилу случится опять в течение минуты
            pendingNextPull = false
            state = State.TICKLE
            stateTime = 0
            stateDur = 1_200
        }
    }

    private fun toggleLight() {
        prefs.edit()
            .putBoolean("light_on", !prefs.getBoolean("light_on", true))
            .apply()
    }

    /** Тап по питомцу = ПОГЛАЖИВАНИЕ: случайная реакция */
    fun tap() {
        if (state == State.SLEEP) return
        petStyle = rng.nextInt(5)
        targetItem = null
        walkMode = WalkMode.NONE
        stateTime = 0
        when (petStyle) {
            0 -> { state = State.HAPPY;  stateDur = 1_000 }  // обрадовался + сердечки
            1 -> { state = State.SMUG;   stateDur = 1_500 }  // ухмыльнулся
            2 -> { state = State.BLUSH;  stateDur = 1_600 }  // покраснел
            3 -> { state = State.SQUEEZE; stateDur = 1_300 } // зажмурился от удовольствия
            else -> { state = State.TICKLE; stateDur = 1_300 } // подпрыгнул от щекотки
        }
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
        val spawned: SpawnedItem
        if (!haveBox && rng.nextFloat() < 0.22f) {
            spawned = newFalling("box")
        } else {
            // взвешенный выбор типа: голоден → еда чаще; сыт → игрушки чаще
            var kind = pickKind()
            var pool = ItemDefs.ALL.filter {
                it.kind == kind && items.none { i -> i.defId == it.id }
            }
            if (pool.isEmpty()) {
                kind = if (kind == ItemDefs.Kind.FOOD) ItemDefs.Kind.TOY else ItemDefs.Kind.FOOD
                pool = ItemDefs.ALL.filter {
                    it.kind == kind && items.none { i -> i.defId == it.id }
                }
            }
            if (pool.isEmpty()) return
            spawned = newFalling(pool.random(rng).id)
        }
        items.add(spawned)

        // МГНОВЕННОЕ ЛЮБОПЫТСТВО: каждый новый предмет — сразу цель похода
        if (walkMode != WalkMode.TO_SWITCH && state != State.SLEEP &&
            state != State.PULL && state != State.INTERACT
        ) {
            targetItem = spawned
            walkMode = WalkMode.TO_ITEM
            state = State.WALK
            stateTime = 0
            stateDur = 16_000
            dir = if (spawned.x > x) 1 else -1
        }
    }

    /**
     * ВЕСЫ СПАВНА: голоден — еда появляется чаще (75/25),
     * сыт — наоборот, игрушки чаще (75/25)
     */
    private fun pickKind(): ItemDefs.Kind {
        val foodBias = if (hunger > 55f) 0.75f else 0.25f
        return if (rng.nextFloat() < foodBias) ItemDefs.Kind.FOOD else ItemDefs.Kind.TOY
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
        ed.putBoolean(KEY_PULLDONE, pullDone)
        ed.putFloat(KEY_HUNGER, hunger)
        // ЦЕЛЬ ХРАНИТСЯ НЕ ТОЛЬКО ДЛЯ INTERACT — иначе поход к предмету
        // теряет цель при пересоздании симуляции (амнезия ходьбы)
        if (targetItem != null &&
            (state == State.INTERACT || (state == State.WALK && walkMode == WalkMode.TO_ITEM))
        ) {
            ed.putString(KEY_TARGET, "${targetItem!!.defId},${targetItem!!.x}")
        } else {
            ed.remove(KEY_TARGET)
        }
        ed.putString(
            KEY_ITEMS,
            items.joinToString("|") { "${it.defId},${it.x},${it.vx},${it.ageMs},${it.ttlMs},${if (it.used) 1 else 0}" }
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
        pullDone = prefs.getBoolean(KEY_PULLDONE, false)
        hunger = prefs.getFloat(KEY_HUNGER, 30f).coerceIn(0f, 100f)
        targetItem = null
        items.clear()
        prefs.getString(KEY_ITEMS, null)?.takeIf { it.isNotBlank() }?.split("|")?.forEach { s ->
            val p = s.split(",")
            if (p.size == 5 || p.size == 6) {
                val d = ItemDefs.byId[p[0]] ?: return@forEach
                items.add(
                    SpawnedItem(
                        d.id,
                        p[1].toFloatOrNull() ?: 0.5f,
                        p[2].toFloatOrNull() ?: 0f,
                        p[3].toLongOrNull() ?: 0L,
                        p[4].toLongOrNull() ?: 120_000L,
                        false,
                        p.size == 6 && p[5] == "1"
                    )
                )
            }
        }
        // восстановить цель: для взаимодействия И для похода к предмету
        if (state == State.INTERACT || (state == State.WALK && walkMode == WalkMode.TO_ITEM)) {
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
        private const val KEY_PULLDONE = "pulldone"
        private const val KEY_HUNGER = "hunger"
        private const val KEY_TARGET = "target"

        const val WALK_SPEED = 0.075f
        const val X_MIN = 0.09f
        const val X_MAX = 0.91f
        const val BED_X = 0.80f
        const val SWITCH_X = 0.40f          // где висит шнур выключателя

        const val PULL_TOGGLE_MS = 700L     // момент в тяге, когда свет щёлкает
        const val PULL_DUR = 1400L          // полная длительность тяги

        const val HUNGER_FULL_MS = 600_000L // голод растёт до 100 за ~10 минут жизни
        const val HUNGER_LEVEL = 75f        // порог, при котором морда грустит
        const val HUNGER_EAT_RELIEF = 65f   // сколько снимает одна еда

        const val HOLD_AT = 600L     // сколько мс тянуться/брать
        const val RELEASE_MS = 250L  // плавно убрать руки
    }
}

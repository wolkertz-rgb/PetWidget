package com.sting.petwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Художник сцены: градиентная комната, кожаное мини-кресло, предметы
 * с появлением "с потолка", резиновые ручки блоба, трюки, приветствие.
 * Всё — функции непрерывного времени: без "слайдов".
 */
class SceneRenderer {

    private var bmp: Bitmap? = null
    private var w = 0
    private var h = 0

    private val pF = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pS = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pZ = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF75808B.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val path = Path()

    fun render(sim: PetSim, newW: Int, newH: Int): Bitmap? {
        if (newW <= 0 || newH <= 0) return null
        if (bmp == null || w != newW || h != newH) {
            try { bmp = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) { return null }
            w = newW; h = newH
        }
        val c = Canvas(bmp!!)
        try { paintScene(c, sim) } catch (t: Throwable) { logErr("scene", t) }
        return bmp
    }

    private fun logErr(section: String, t: Throwable) {
        android.util.Log.e("PetRender", "[$section] ${t.javaClass.simpleName}: ${t.message}", t)
    }

    /** контекст для аварийного журнала (задаётся при создании симуляции) */
    var lastPrefs: android.content.SharedPreferences? = null

    /** АКТУАЛЬНОЕ состояние света — читается прямо при рисовании,
     *  чтобы щелчок тумблера внутри тика не терялся ("single source of truth") */
    private fun lightNow(): Boolean {
        return try {
            lastPrefs?.getBoolean("light_on", true) ?: true
        } catch (_: Throwable) {
            true
        }
    }

    /** строковый кастом-токен внешности (варианты форм) */
    private fun look(key: String, def: String): String {
        return try {
            lastPrefs?.getString(key, def) ?: def
        } catch (_: Throwable) {
            def
        }
    }

    /** плотность экрана (px на dp) — для позиции лампочки */
    var density = 3f

    /** читатель темы: значения можно переопределить из «ателье» (prefs) */
    private fun th(key: String, def: Int): Int {
        return try {
            lastPrefs?.getInt(key, def) ?: def
        } catch (_: Throwable) {
            def
        }
    }

    // ---------------------------------------------------------------- сцена

    private fun paintScene(c: Canvas, sim: PetSim) {
        val unit = (w / 24f).coerceAtLeast(8f)
        val pad = unit * 0.7f
        val floorY = h - h * 0.115f
        val itemX: (Float) -> Float = { pad + it.coerceIn(0f, 1f) * (w - 2 * pad) }

        // фон (тема-токены)
        val bg = Paint().apply {
            shader = LinearGradient(0f, pad, 0f, floorY, th("room_bg_top", 0xFFF2F2EF.toInt()), th("room_bg_bottom", 0xFFE8E8E4.toInt()), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), floorY + 1f, bg)
        c.drawRect(0f, floorY, w.toFloat(), h.toFloat(), bg)

        // время суток
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour >= 22 || hour <= 5 -> c.drawColor(0x242E3A55)
            hour >= 20 -> c.drawColor(0x18FFBE86)
            hour <= 8 -> c.drawColor(0x12FFE9C4)
            else -> {}
        }

        // стены/потолок
        val wallL = Paint().apply { shader = LinearGradient(0f, 0f, pad * 2.4f, 0f, 0x1E555550, 0x00555550, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, pad * 2.4f, h.toFloat(), wallL)
        val wallR = Paint().apply { shader = LinearGradient(w.toFloat(), 0f, w - pad * 2.4f, 0f, 0x1E555550, 0x00555550, Shader.TileMode.CLAMP) }
        c.drawRect(w - pad * 2.4f, 0f, w.toFloat(), h.toFloat(), wallR)
        val ceil = Paint().apply { shader = LinearGradient(0f, 0f, 0f, pad * 1.6f, 0x16606058, 0x00606058, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), pad * 1.6f, ceil)

        // узор обоев (читается из кастомизации)
        drawWallPattern(c, unit, pad, floorY)
        pF.color = 0xFF555550.toInt()
        pF.alpha = 14
        val grainStep = unit * 1.5f
        var gy = pad + grainStep
        while (gy < floorY - unit * 2.1f) {
            var gx = pad + grainStep
            val shift = if ((gy / grainStep).toInt() % 2 == 1) grainStep / 2f else 0f
            while (gx < w - pad) {
                c.drawCircle(gx + shift, gy, unit * 0.05f, pF)
                gx += grainStep
            }
            gy += grainStep
        }
        pF.alpha = 255

        // панель нижней части стены + линия кромки
        pF.color = th("room_panel", 0xFFE5E5E0.toInt())
        rect.set(pad, floorY - unit * 2.0f, w - pad, floorY - unit * 0.2f)
        c.drawRect(rect, pF)
        pS.color = 0x2ECFCFC9.toInt()
        pS.strokeWidth = unit * 0.08f
        c.drawLine(pad, floorY - unit * 2.0f, w - pad, floorY - unit * 2.0f, pS)

        // пол + плинтус (тема-токены)
        val floor = Paint().apply { shader = LinearGradient(0f, floorY, 0f, h.toFloat(), th("floor_top", 0xFFE1E1DC.toInt()), th("floor_bottom", 0xFFD3D3CE.toInt()), Shader.TileMode.CLAMP) }
        rect.set(pad, floorY, w - pad, h - pad)
        c.drawRect(rect, floor)
        pF.color = th("room_skirt", 0xFFDCDCD6.toInt())
        rect.set(pad, floorY - unit * 0.22f, w - pad, floorY)
        c.drawRect(rect, pF)
        pS.color = 0xFFB9B9B2.toInt()
        pS.strokeWidth = unit * 0.1f
        c.drawLine(pad + 1, floorY, w - pad - 1, floorY, pS)
        pS.color = 0x40FFFFFF
        c.drawLine(pad + 1, floorY + 1, w - pad - 1, floorY + 1, pS)

        // рамка
        pS.color = 0x66C9C9C3.toInt()
        pS.strokeWidth = unit * 0.12f
        c.drawRect(pad, pad, w - pad, h - pad, pS)

        // кожаное мини-кресло: задняя часть (спинка+сидушка) — ФОН
        drawChairBack(c, unit, floorY)

        // лампочка на шнурке (зона тапа — отдельный View сверху по центру)
        drawBulb(c, unit, floorY)

        // шнур-выключатель с бусиной
        drawPullCord(c, sim, unit, floorY)

        // когда питомец НЕ спит в кресле — подлокотники тоже позади него
        val sleepingInChair = sim.state == PetSim.State.SLEEP && sim.sleepingInBed
        if (!sleepingInChair) drawChairFront(c, unit, floorY)

        val inter = readInteract(sim, itemX, r = unit * 1.55f)

        // питомец
        try { drawPet(c, sim, itemX(sim.x), floorY, unit, inter) } catch (t: Throwable) { logErr("pet", t) }

        // предметы ПОВЕРХ питомца (стоят перед ним)
        for (item in sim.items) {
            if (inter.holding && item === inter.target) continue // этот в руках
            try { drawFloorItem(c, sim, item, inter, itemX(item.x), floorY, unit) } catch (t: Throwable) { logErr("item", t) }
        }

        // крышка подарка
        if (inter.lidProg > 0f && inter.target != null) try { drawLidPop(c, inter, unit, floorY) } catch (t: Throwable) { logErr("lid", t) }

        // предмет в руках
        if (inter.holding && inter.target != null && inter.prog > 0f) {
            try { drawHeldItem(c, sim, inter, unit, floorY) } catch (t: Throwable) { logErr("held", t) }
        }

        // ручки
        try { drawArms(c, sim, inter, unit, floorY, itemX) } catch (t: Throwable) { logErr("arms", t) }

        // сердечки при приветствии
        if (sim.state == PetSim.State.WAVE) drawHearts(c, sim, itemX(sim.x), floorY, unit)

        // конфетти
        for (item in sim.items) {
            if (item.confetti && item.ageMs in 0L..420L) {
                drawConfetti(c, itemX(item.x), floorY, unit, item.ageMs / 420f)
            }
        }

        // спит в кресле — подлокотники обнимают его ПОВЕРХ
        if (sleepingInChair) drawChairFront(c, unit, floorY)

        // свет лампочки: тьма или тёплое сияние — поверх всей сцены
        applyLightOverlay(c, unit, floorY)

        // лампу и шнур видно и в темноте (питомцу нужно видеть, куда тянуть)
        drawBulb(c, unit, floorY)
        drawPullCord(c, sim, unit, floorY)

        // god-rays: мягкие размытые световые конусы из лампы (без жёстких краёв)
        if (lightNow()) {
            val rayBx = w / 2f
            val rayBy = 26f * density
            val drift = sin(sim.animPhase * 0.15f) * 8f
            val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    0f, 0f, w * 0.55f,
                    intArrayOf(0x30FFD98E.toInt(), 0x00FFD98E.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
            for (dirDeg in floatArrayOf(-30f, 30f)) {
                c.save()
                c.translate(rayBx, rayBy)
                c.rotate(dirDeg + drift)
                c.scale(0.42f, 1.5f)
                c.drawCircle(0f, w * 0.38f, w * 0.5f, beamPaint)
                c.restore()
            }
        }
    }

    // ------------------------------------------------------- свет и лампа

    private fun drawBulb(c: Canvas, unit: Float, floorY: Float) {
        val bx = w / 2f
        val by = 26f * density          // фиксируем в dp, чтобы совпадало с зоной тапа
        val glass = unit * 0.62f

        // шнур
        pS.color = 0x55707070.toInt()
        pS.strokeWidth = unit * 0.1f
        c.drawLine(bx, 0f, bx, by - glass - unit * 0.1f, pS)

        // цоколь
        pF.color = 0xFF9B9B93.toInt()
        rect.set(bx - unit * 0.26f, by - glass - unit * 0.12f, bx + unit * 0.26f, by - glass * 0.25f)
        c.drawRoundRect(rect, unit * 0.06f, unit * 0.06f, pF)

        // колба
        pF.color = if (lightNow()) 0xFFFFE9A8.toInt() else 0xFFDCD8CA.toInt()
        c.drawCircle(bx, by, glass, pF)
        // контур колбы (виден и в темноте — чтобы найти тапом)
        pS.color = if (lightNow()) 0x66C9A54A.toInt() else 0x5A6B6B66.toInt()
        pS.strokeWidth = unit * 0.1f
        c.drawCircle(bx, by, glass, pS)

        // нить накала
        pS.color = if (lightNow()) 0xFFFFB84D.toInt() else 0xFF8E8E82.toInt()
        pS.strokeWidth = unit * 0.09f
        c.drawCircle(bx, by + glass * 0.12f, unit * 0.17f, pS)

        // блик
        pF.color = 0x66FFFFFF.toInt()
        c.drawCircle(bx - glass * 0.35f, by - glass * 0.35f, glass * 0.22f, pF)
    }

    /** координаты кончика шнура-выключателя (с учётом тяги) */
    private fun pullCordPos(sim: PetSim, unit: Float, floorY: Float): Pair<Float, Float> {
        val sx = floorXfor(PetSim.SWITCH_X, unit)
        var pullOffset = 0f
        if (sim.state == PetSim.State.PULL) {
            val t = sim.stateTime
            pullOffset = when {
                t < 700L -> -unit * 0.85f * smooth(t.toFloat() / 700f)
                t < 1000L -> -unit * 0.85f
                else -> -unit * 0.85f * (1f - smooth(((t - 1000f) / 400f).coerceIn(0f, 1f)))
            }
        }
        val sway = sin(sim.animPhase * 2.2f) * unit * 0.15f
        return sx to (floorY - unit * 4.1f + pullOffset + sway)
    }

    private fun drawPullCord(c: Canvas, sim: PetSim, unit: Float, floorY: Float) {
        val (sx, tipY) = pullCordPos(sim, unit, floorY)
        pS.color = 0xFF8C8C86.toInt()
        pS.strokeWidth = unit * 0.09f
        c.drawLine(sx, 0f, sx, tipY, pS)
        // бусина-хвостик, за которую дёргают
        pF.color = if (lightNow()) 0xFFD8B368.toInt() else 0xFF948A78.toInt()
        c.drawCircle(sx, tipY, unit * 0.18f, pF)
        pF.color = 0x49FFFFFF.toInt()
        c.drawCircle(sx - unit * 0.05f, tipY - unit * 0.05f, unit * 0.07f, pF)
    }

    /** узор обоев: 0 нет(только зерно), 1 точки, 2 полоски, 3 зигзаг, 4 клетка, 5 звёзды */
    private fun drawWallPattern(c: Canvas, unit: Float, pad: Float, floorY: Float) {
        val idx = th("wall_pattern", 0)
        if (idx == 0) return
        val col = th("wall_pattern_color", 0x1A55604F.toInt())
        val top = pad * 1.6f
        val bottom = floorY - unit * 2.1f
        val left = pad
        val right = w - pad
        rect.set(left, top, right, bottom)
        c.save()
        c.clipRect(rect)
        when (idx) {
            1 -> { // крупные точки
                pF.color = col
                val st = unit * 1.8f
                var y = top + st
                while (y < bottom) {
                    var x = left + st
                    val sh = if (((y / st).toInt() % 2) == 1) st / 2f else 0f
                    while (x < right) {
                        c.drawCircle(x + sh, y, unit * 0.2f, pF)
                        x += st
                    }
                    y += st
                }
            }
            2 -> { // полоски
                pF.color = col
                var x = left + unit
                while (x < right) {
                    rect.set(x, top, x + unit * 0.5f, bottom)
                    c.drawRect(rect, pF)
                    x += unit * 2.2f
                }
            }
            3 -> { // зигзаг
                pS.color = col
                pS.strokeWidth = unit * 0.16f
                val amp = unit * 0.32f
                var y = top + unit * 1.2f
                while (y < bottom) {
                    path.reset()
                    var x = left
                    var up = true
                    path.moveTo(x, y + amp / 2f)
                    while (x < right) {
                        x += unit * 0.8f
                        path.lineTo(x, y + (if (up) -amp else amp))
                        up = !up
                    }
                    c.drawPath(path, pS)
                    y += unit * 1.5f
                }
            }
            4 -> { // шахматная клетка
                pF.color = col
                val st = unit * 1.1f
                var row = 0
                var yy = top
                while (yy < bottom) {
                    var ix = 0
                    var xx = left
                    while (xx < right) {
                        if ((ix + row) % 2 == 0) {
                            rect.set(xx, yy, xx + st, yy + st)
                            c.drawRect(rect, pF)
                        }
                        xx += st
                        ix++
                    }
                    yy += st
                    row++
                }
            }
            5 -> { // звёздочки-искры
                val st = unit * 2.0f
                var row = 0
                var yy = top + st
                while (yy < bottom) {
                    var xx = left + st
                    val sh = if (row % 2 == 1) st / 2f else 0f
                    while (xx < right) {
                        drawSpark(c, xx + sh, yy, unit * 0.34f, col, 190)
                        xx += st
                    }
                    yy += st
                    row++
                }
                pS.strokeWidth = unit * 0.16f
            }
        }
        c.restore()
    }

    private fun applyLightOverlay(c: Canvas, unit: Float, floorY: Float) {
        val bx = w / 2f
        val by = 26f * density
        if (!lightNow()) {
            // ПОЛУМРАК: сцена читается, но без освещения (верх темнее, низ светлее)
            val dark = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    0xC0202838.toInt(), 0x66202838.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), dark)
            // лунный отсвет на полу под лампочкой (намёк)
            pF.color = 0x14202C48.toInt()
            rect.set(bx - w * 0.22f, floorY - unit * 0.3f, bx + w * 0.22f, floorY + unit * 0.6f)
            c.drawOval(rect, pF)
            return
        }
        // ТЁПЛЫЙ СВЕТ: радиальное сияние вокруг лампы
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                bx, by, w * 0.95f,
                intArrayOf(0x54FFD98E.toInt(), 0x2AFFD98E.toInt(), 0x00FFD98E.toInt()),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow)
        // тёплый тон всей комнате
        c.drawColor(0x14FFC97A.toInt())
        // световое пятно на полу
        pF.color = 0x2EFFE2A0.toInt()
        rect.set(bx - w * 0.30f, floorY - unit * 0.5f, bx + w * 0.30f, floorY + unit * 0.7f)
        c.drawOval(rect, pF)
        // пульс-дыхание света
        val pulse = 0x10 + ((sin(System.currentTimeMillis() / 900.0) + 1f) * 0.5f * 0x14).toInt()
        c.drawColor((pulse shl 24) or 0x00FFD98E)
    }

    // ------------------------------------------------------------- захват

    private class InteractInfo {
        var holding = false
        var target: PetSim.SpawnedItem? = null
        var prog = 0f          // 0..1 — плавный ход "от пола к рукам"
        var armReach = 0f      // насколько вылезли руки
        var fromX = 0f         // px предмета на полу (для крышки)
        var holdX = 0f         // ТОЧКА ХВАТА у питомца: предмет всегда здесь
        var liftPx = 0f        // подъём
        var lidProg = 0f
        var rubPhase = 0f
        var eatFade = 0f       // 0..1 — съедание (затухание предмета)
        var eatProgress = 0f   // 0..1 — сколько уже "съедено" (	Schema поедания)
        var holdYExtra = 0f    // индивидуальный "боб" предмета (лижет, хрустит)
    }

    private fun readInteract(sim: PetSim, itemX: (Float) -> Float, r: Float): InteractInfo {
        val info = InteractInfo()
        if (sim.state != PetSim.State.INTERACT) return info
        val ti = sim.targetItem ?: return info
        if (!sim.items.contains(ti)) return info

        val t = sim.stateTime
        val dur = sim.stateDur
        info.target = ti
        info.rubPhase = sim.animPhase * 6f

        val reach = (t.toFloat() / 700f).coerceIn(0f, 1f)
        val rel = if (t >= dur - PetSim.RELEASE_MS)
            ((dur - t).toFloat() / PetSim.RELEASE_MS).coerceIn(0f, 1f) else 1f
        info.prog = (smooth(reach) * rel).coerceIn(0f, 1f)
        info.armReach = (smooth((t.toFloat() / 380f).coerceIn(0f, 1f)) * rel).coerceIn(0f, 1f)

        info.fromX = itemX(ti.x)
        // ТОЧКА ХВАТА: всегда прямо перед питомцем — предмет "приклеен" к рукам
        val cx = itemX(sim.x)
        info.holdX = cx + sim.dir * r * 1.35f

        when (sim.reaction) {
            ItemDefs.Reaction.SMELL -> { info.holding = true; info.liftPx = -r * 1.72f }
            ItemDefs.Reaction.LIFT  -> {
                info.holding = true; info.liftPx = -r * 1.05f
                // прогресс «в руках» — по нему живут игрушечные трюки
                info.eatProgress = ((t - 600f) / (dur - 1000f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            ItemDefs.Reaction.EAT   -> {
                info.holding = true
                info.liftPx = -r * 1.15f
                info.eatFade = ((t - (dur - 400f)).toFloat() / 400f).coerceIn(0f, 1f)
                // прогресс поедания от захвата до конца-минус-финал
                info.eatProgress = ((t - 600f) / (dur - 1000f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            ItemDefs.Reaction.OPEN  -> {
                info.lidProg = ((t - (dur - 800f)).toFloat() / 800f).coerceIn(0f, 1f)
            }
            else -> {} // RUB/PUSH/PLAY: предмет на полу, руки сами
        }
        return info
    }

    // ------------------------------------------------------ предмет на полу

    private fun drawFloorItem(
        c: Canvas, sim: PetSim, item: PetSim.SpawnedItem, inter: InteractInfo,
        ix: Float, floorY: Float, unit: Float
    ) {
        val def = item.def
        if (item.ageMs < 0) {
            val a = item.ageMs
            if (a < -1000) {
                val tp = ((a + 1500f) / 500f).coerceIn(0f, 1f)
                pF.color = 0xFF000000.toInt()
                pF.alpha = (tp * 46).toInt().coerceIn(0, 255)
                val rx = def.w * unit * 0.42f * (0.55f + 0.45f * tp)
                rect.set(ix - rx, floorY - unit * 0.18f, ix + rx, floorY + unit * 0.10f)
                c.drawOval(rect, pF)
                pF.alpha = 255
                return
            }
            val p = ((a + 1000f) / 1000f).coerceIn(0f, 1f)
            val yOff: Float
            var squash = 1f
            when {
                p < 0.62f -> { val q = p / 0.62f; yOff = -(1f - q * q) * unit * 9f }
                p < 0.82f -> {
                    val q = (p - 0.62f) / 0.20f
                    yOff = -sin((q * PI).toFloat()) * unit * 1.15f
                    if (q < 0.25f) squash = 1f - 0.3f * (1f - q * 4f)
                }
                else -> {
                    val q = (p - 0.82f) / 0.18f
                    yOff = -sin((q * PI).toFloat()) * unit * 0.35f
                    if (q < 0.25f) squash = 1f - 0.2f * (1f - q * 4f)
                }
            }
            if (p > 0.60f && p < 0.78f) spawnDust(c, ix, floorY, unit * 0.9f, (p - 0.60f) / 0.18f)
            if (p > 0.80f && p < 0.98f) spawnDust(c, ix, floorY, unit * 0.5f, (p - 0.80f) / 0.18f)

            c.save()
            c.translate(0f, yOff)
            c.translate(ix, floorY)
            c.scale(1f + (1f - squash) * 0.6f, squash, ix, floorY)
            c.translate(-ix, -floorY)
            def.draw(c, ix, floorY, unit, sim.animPhase)
            c.restore()
            return
        }

        val pop = if (item.confetti) 1f else (item.ageMs / 900f).coerceIn(0f, 1f)
        val sc = if (pop >= 1f) 1f else 0.55f + 0.45f * pop
        val fade = 1f - ((item.ageMs - item.ttlMs + 1500f) / 1500f).coerceIn(0f, 1f)
        if (fade <= 0f) return
        val alpha = (fade * 255).toInt()

        // ---- детская повадка каждой игрушки
        val isTarget = inter.target === item && sim.state == PetSim.State.INTERACT
        var toyRot = 0f
        var toyScale = 1f
        val rolling = abs(item.vx) > 0.02f
        when (def.id) {
            "ball" -> if (rolling) toyRot = sim.animPhase * 240f * (if (item.vx > 0) 1f else -1f)
            "block" -> if (rolling) toyRot = sin(sim.animPhase * 8f) * 8f
            "top" -> if (rolling) toyRot = sim.animPhase * 260f * (if (item.vx > 0) 1f else -1f)
            "robot" -> if (isTarget && sim.reaction == ItemDefs.Reaction.PLAY) {
                toyRot = sin(sim.stateTime / 55f) * 4f        // вибрирует «бип-бип»
            }
            "plushie" -> if (isTarget && sim.reaction == ItemDefs.Reaction.RUB) {
                toyScale = 1f + 0.06f * sin(sim.stateTime / 120f)  // мягко пружинит
            }
            "flower" -> if (isTarget && sim.reaction == ItemDefs.Reaction.SMELL) {
                toyRot = sin(sim.stateTime / 150f) * 7f           // кланяется навстречу
            }
        }

        c.save()
        c.scale(sc * toyScale, sc * toyScale, ix, floorY)
        c.rotate(toyRot, ix, floorY - unit * 0.9f)
        pF.alpha = alpha; pS.alpha = alpha; pZ.alpha = alpha
        def.draw(c, ix, floorY, unit, sim.animPhase)
        c.restore()

        // ---- спецэффекты поверх игрушек
        if (def.id == "robot" && isTarget && sim.reaction == ItemDefs.Reaction.PLAY) {
            drawSpark(c, ix, floorY - unit * 3.5f, unit * 0.35f, 0xFFFFD54D.toInt(),
                ((sin(sim.stateTime / 90f) * 0.5f + 0.5f) * 200f).toInt().coerceIn(0, 255))
        }
        if (def.id == "plushie" && isTarget && sim.reaction == ItemDefs.Reaction.RUB) {
            val hp = (sin(sim.stateTime / 200f) * 0.5f + 0.5f)
            drawMiniHeart(c, ix + unit * 1.1f, floorY - unit * (2.2f + hp * 0.4f),
                unit * 0.16f, 0xFFF26D7E.toInt(), (hp * 200).toInt().coerceIn(0, 255))
        }
        if (def.id == "flower" && isTarget && sim.reaction == ItemDefs.Reaction.SMELL) {
            drawSpark(c, ix - unit * 0.5f, floorY - unit * 3.0f, unit * 0.22f, 0xFF9FE8C4.toInt(),
                (abs(sin(sim.stateTime / 150f)) * 190f).toInt().coerceIn(0, 255))
            drawSpark(c, ix + unit * 0.55f, floorY - unit * 2.6f, unit * 0.18f, 0xFFFFF3B0.toInt(),
                (abs(cos(sim.stateTime / 170f)) * 190f).toInt().coerceIn(0, 255))
        }
        pF.alpha = 255; pS.alpha = 255; pZ.alpha = 255
    }

    /** искра-блеск: 4 лучика */
    private fun drawSpark(c: Canvas, x: Float, y: Float, s: Float, col: Int, alpha: Int) {
        pS.color = col
        pS.strokeWidth = s * 0.22f
        c.drawLine(x - s, y, x + s, y, pS)
        c.drawLine(x, y - s, x, y + s, pS)
        val d = s * 0.55f
        c.drawLine(x - d, y - d, x + d, y + d, pS)
        c.drawLine(x - d, y + d, x + d, y - d, pS)
    }

    /** мини-сердечко (для мишки и компании) */
    private fun drawMiniHeart(c: Canvas, cx: Float, cy: Float, s: Float, col: Int, alpha: Int) {
        path.reset()
        path.moveTo(cx, cy + s)
        path.quadTo(cx - s, cy, cx - s * 0.4f, cy - s * 0.5f)
        path.quadTo(cx, cy - s, cx, cy - s * 0.3f)
        path.quadTo(cx, cy - s, cx + s * 0.4f, cy - s * 0.5f)
        path.quadTo(cx + s, cy, cx, cy + s)
        pF.color = col
        pF.alpha = alpha
        c.drawPath(path, pF)
        pF.alpha = 255
    }

    private fun spawnDust(c: Canvas, x: Float, floorY: Float, r: Float, p: Float) {
        val n = 5
        for (i in 0 until n) {
            val ang = (i.toFloat() / n) * PI.toFloat() * 2f + 0.4f
            val d = p * r * 1.6f
            val dx = cos(ang) * d
            val dy = -abs(sin(ang)) * d * 0.5f + r * 0.2f
            pF.color = 0xFF000000.toInt()
            pF.alpha = ((1f - p) * 60f).toInt().coerceIn(0, 255)
            c.drawCircle(x + dx, floorY + dy, r * (1f - p * 0.6f) * 0.22f, pF)
        }
        pF.alpha = 255
    }

    private fun drawConfetti(c: Canvas, x: Float, floorY: Float, unit: Float, p: Float) {
        for (i in 0 until 9) {
            val ang = i * (PI.toFloat() * 2f / 9f)
            val spread = 0.5f + p * 1.6f
            val dx = cos(ang) * unit * spread
            val dy = sin(ang) * unit * spread * 0.7f - p * unit * 0.8f
            pF.color = when (i % 3) { 0 -> 0xFFE05B5B.toInt(); 1 -> 0xFFF4C95D.toInt(); else -> 0xFF7CC8A2.toInt() }
            pF.alpha = ((1f - p) * 255f).toInt().coerceIn(0, 255)
            c.save()
            c.translate(x + dx, floorY + dy)
            c.rotate((ang * 57f) + p * 160f)
            val s = unit * 0.12f * (1f - p * 0.5f)
            c.drawRect(-s, -s * 0.6f, s, s * 0.6f, pF)
            c.restore()
        }
        pF.alpha = 255
    }

    // ------------------------------------------------------------- крышка

    private fun drawLidPop(c: Canvas, inter: InteractInfo, unit: Float, floorY: Float) {
        val ti = inter.target ?: return
        val p = inter.lidProg
        if (p <= 0f || p >= 1f) return
        val topY = floorY - unit * 1.05f - unit * 0.3f
        c.save()
        c.translate(inter.fromX, topY - p * unit * 2.4f)
        c.rotate(-p * 130f)
        val lw = unit * 1.15f * 1.08f
        pF.color = 0xFFD8945C.toInt()
        pF.alpha = (255f * (1f - p * 0.8f)).toInt().coerceIn(0, 255)
        rect.set(-lw, -unit * 0.21f, lw, unit * 0.21f)
        c.drawRoundRect(rect, unit * 0.09f, unit * 0.09f, pF)
        rect.set(-unit * 0.18f, -unit * 0.21f, unit * 0.18f, unit * 0.21f)
        pF.color = 0xFFE05B5B.toInt()
        c.drawRect(rect, pF)
        pF.alpha = 255
        c.restore()
    }

    // ------------------------------------------------------------- питомец

    private fun petCy(sim: PetSim, floorY: Float, unit: Float, r: Float): Float {
        val onBed = sim.state == PetSim.State.SLEEP && sim.sleepingInBed
        var cy = floorY - r * 0.92f - if (onBed) unit * 0.62f else 0f
        if (sim.state == PetSim.State.HAPPY) {
            val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
            cy -= sin((p * PI).toFloat()) * (r * 1.5f)
        }
        if (sim.state == PetSim.State.WALK) {
            cy -= abs(sin(sim.animPhase * 5.5f)) * r * 0.14f
        }
        if (sim.state == PetSim.State.ROLL) {
            val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
            cy -= sin((p * PI).toFloat()) * r * 0.3f
        }
        if (sim.state == PetSim.State.WAVE) {
            cy -= abs(sin(sim.stateTime / 280f)) * r * 0.12f
        }
        if (sim.state == PetSim.State.PULL) {
            // подпрыгивает, чтобы схватить шнур
            val jp = (sim.stateTime.toFloat() / 500f).coerceIn(0f, 1f)
            cy -= sin((jp * PI).toFloat()) * r * 0.85f
        }
        return cy
    }

    /** пол по последней позе (для плавных переходов), [sx, sy, rot, dx] */
    private var lastPose = floatArrayOf(1f, 1f, 0f, 0f)
    private var lastPosePhase = -1f
    private var lastPoseWrite = 0f      // pet_pose пишется на диск НЕ каждый кадр, а раз в секунду
    private var lastStName = ""
    private var facePuff = 0f

    private fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k

    private fun drawPet(
        c: Canvas, sim: PetSim, cx: Float, floorY: Float, unit: Float, inter: InteractInfo
    ) {
        val r = unit * 1.55f
        val onBed = sim.state == PetSim.State.SLEEP && sim.sleepingInBed
        var cy = petCy(sim, floorY, unit, r)
        var dx = 0f
        var sx = 1f
        var sy = 1f
        var rot = 0f
        var headTilt = 0f
        val st = sim.state

        // ---- ПЛАВНЫЕ ПЕРЕХОДЫ: поза тянется к новой, никаких телепортов
        if (lastPosePhase < 0f) {
            lastPrefs?.getString("pet_pose", "1,1,0,0,0")?.let { s ->
                val pArr = s.split(",").mapNotNull { it.toFloatOrNull() }
                if (pArr.size == 5) {
                    lastPose = floatArrayOf(pArr[0], pArr[1], pArr[2], pArr[3])
                    lastPosePhase = sim.animPhase - 0.05f
                }
            }
            if (lastPosePhase < 0f) lastPosePhase = sim.animPhase
        }
        val dtAnim = (sim.animPhase - lastPosePhase).coerceIn(0f, 3f)
        val k = (dtAnim * 9f).coerceIn(0.18f, 1f)

        // смена действия = мягкая «вспышка-облако» вместо резкого скачка лица
        if (lastStName != st.name) {
            facePuff = 1f
            lastStName = st.name
        }
        facePuff = (facePuff - dtAnim * 6f).coerceIn(0f, 1f)

        // тень (в кресле чуть меньше)
        pF.color = if (onBed) 0x0A000000 else 0x0F000000
        val shadowR = if (onBed) 1.15f else 1.45f
        rect.set(cx - r * shadowR, floorY - unit * 0.30f, cx + r * shadowR, floorY + unit * 0.18f)
        c.drawOval(rect, pF)
        pF.color = if (onBed) 0x0E000000 else 0x17000000
        rect.set(cx - r * (if (onBed) 0.85f else 1.0f), floorY - unit * 0.20f, cx + r * (if (onBed) 0.85f else 1.0f), floorY + unit * 0.12f)
        c.drawOval(rect, pF)

        val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
        when (st) {
            PetSim.State.WALK -> {
                sx = 1f + 0.05f * sin(sim.animPhase * 11f)
                sy = 1f - 0.035f * sin(sim.animPhase * 11f)
            }
            PetSim.State.SLEEP -> {
                // лежит в кресле: расплющен (шире и ниже), мелко дышит —
                // раньше «стоял столбиком» и выглядел неестественно
                sx = 1.16f + 0.022f * sin(sim.animPhase * 1.2f)
                sy = 0.70f - 0.018f * sin(sim.animPhase * 1.2f)
            }
            PetSim.State.INTERACT -> sx = 1f + 0.05f * sin(sim.stateTime / 50f)
            PetSim.State.ROLL -> {
                // антиципация: приседает (0..0.14) → кувырок → приземление с отскоком
                if (p < 0.14f) {
                    sy = 1f - 0.16f * smooth(p / 0.14f)
                } else if (p < 0.86f) {
                    val rp = smooth((p - 0.14f) / 0.72f)
                    rot = rp * 2f * PI.toFloat() * sim.dir
                    cy -= sin((rp * PI).toFloat()) * r * 0.5f
                } else {
                    sy = 1f - 0.14f * sin((smooth((p - 0.86f) / 0.14f) * PI).toFloat())
                }
            }
            PetSim.State.SWAY -> {
                val osc = sin(p * PI.toFloat() * 4f)   // два полных покачивания
                rot = osc * 0.30f
                dx = osc * unit * 1.1f
            }
            PetSim.State.LOOK -> {
                headTilt = sin(p * PI.toFloat() * 1.2f) * 0.10f
            }
            PetSim.State.WAVE -> {
                sx = 1f + 0.04f * sin(sim.stateTime / 240f)
                sy = 1f + 0.03f * sin(sim.stateTime / 240f)
            }
            PetSim.State.SMUG -> {
                // хитро наклоняется и чуть надувается (плавный въезд)
                sx = 1f + 0.05f * sin(sim.stateTime / 300f)
                dx = unit * 0.15f * sim.dir * smooth((sim.stateTime / 300f).coerceIn(0f, 1f))
            }
            PetSim.State.BLUSH -> {
                // стесняется: слегка съёживается
                sx = 1f + 0.04f * sin(sim.stateTime / 250f)
                sy = 1f - 0.04f * sin(sim.stateTime / 250f)
            }
            PetSim.State.SQUEEZE -> {
                // блаженное сжатие-расжатие
                sx = 1f - 0.07f * sin(sim.stateTime / 130f)
                sy = 1f + 0.07f * sin(sim.stateTime / 130f)
            }
            PetSim.State.TICKLE -> {
                // пружинистые подпрыгивания от щекотки
                cy -= abs(sin(sim.stateTime / 120f)) * r * 0.9f
                sx = 1f + 0.08f * sin(sim.stateTime / 60f)
            }
            PetSim.State.HUNGRY -> {
                // хнычет и потирает животик: лёгкая дрожь
                sx = 1f + 0.04f * sin(sim.stateTime / 90f)
                sy = 1f - 0.03f * sin(sim.stateTime / 90f)
            }
            else -> {
                sx = 1f + 0.02f * sin(sim.animPhase * 1.5f)
                sy = 1f - 0.015f * sin(sim.animPhase * 1.5f)
            }
        }

        // ---- сглаживание: поза тянется к целевой (кратчайшая дуга по углу)
        val tl = lastPose
        dx = tl[3] + (dx - tl[3]) * k
        sx = lerp(tl[0], sx, k)
        sy = lerp(tl[1], sy, k)
        val TWO_PI = (2.0 * PI).toFloat()
        var dRot = rot - tl[2]
        dRot = ((dRot % TWO_PI) + TWO_PI) % TWO_PI
        if (dRot > PI.toFloat()) dRot -= TWO_PI
        rot = tl[2] + dRot * k
        lastPose = floatArrayOf(sx, sy, rot, dx)
        lastPosePhase = sim.animPhase
        // pet_pose: в кадре пружинная поза живёт в lastPose (память); на диск — раз в
        // секунду, чтобы «ходы» после смерти процесса восстановили БЛИЗКУЮ позу
        // (дальше пружина дотянет) — было 20 записей prefs в секунду
        if (sim.animPhase - lastPoseWrite >= 1f) {
            lastPoseWrite = sim.animPhase
            try {
                lastPrefs?.edit()?.putString(
                    "pet_pose", "${sx},${sy},${rot},${dx},${sim.animPhase}"
                )?.apply()
            } catch (_: Throwable) {}
        }

        val px = cx + dx
        c.save()
        c.rotate(rot * 57.2958f + headTilt * 40f, px, cy)

        // антеннка: варианты по кастомизации (шарик / цветочек / нить / ничего)
        when (look("look_antenna", "ball")) {
            "ball" -> {
                val antBob = sin(sim.animPhase * 2.2f) * unit * 0.12f
                val antTopX = px + r * 0.1f + antBob * 0.5f
                val antTopY = cy - r * sy - unit * 0.55f + antBob
                pS.color = 0xFF4E8F6E.toInt()
                pS.strokeWidth = unit * 0.1f
                c.drawLine(px + r * 0.02f, cy - r * sy * 0.92f, antTopX, antTopY, pS)
                pF.color = th("look_ante", 0xFFF4C95D.toInt())
                c.drawCircle(antTopX, antTopY, unit * 0.14f, pF)
                pS.color = 0x66B98A2B.toInt()
                pS.strokeWidth = unit * 0.06f
                c.drawCircle(antTopX, antTopY, unit * 0.14f, pS)
            }
            "flower" -> {
                val antTopX = px + r * 0.12f
                val antTopY = cy - r * sy - unit * 0.5f
                pS.color = 0xFF4E8F6E.toInt()
                pS.strokeWidth = unit * 0.1f
                c.drawLine(px + r * 0.02f, cy - r * sy * 0.92f, antTopX, antTopY, pS)
                for (i in 0..4) {
                    val ang = i * 1.257f + (sim.animPhase * 0.6f)
                    circleAt(c, antTopX + cos(ang) * unit * 0.14f, antTopY + sin(ang) * unit * 0.14f,
                        unit * 0.1f, th("look_ante", 0xFFF472B6.toInt()))
                }
                pF.color = 0xFFFDE047.toInt()
                c.drawCircle(antTopX, antTopY, unit * 0.09f, pF)
            }
            "string" -> {
                val antTipY = cy - r * sy - unit * 0.75f + sin(sim.animPhase * 2.5f) * unit * 0.06f
                pS.color = 0xFF4E8F6E.toInt()
                pS.strokeWidth = unit * 0.07f
                c.drawLine(px + r * 0.02f, cy - r * sy * 0.92f, px + r * 0.06f, antTipY, pS)
                pF.color = 0xFF7C9B85.toInt()
                c.drawCircle(px + r * 0.06f, antTipY, unit * 0.06f, pF)
            }
            else -> { /* без антенны */ }
        }

        // тело: палитра из темы (двухцветный градиент топ→низ, серединка выводится автоматически)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                px - r * 0.4f, cy - r * sy, px + r * 0.2f, cy + r * sy,
                intArrayOf(th("pet_top", 0xFFA8E6C3.toInt()), th("pet_bottom", 0xFF4FA379.toInt())),
                null, Shader.TileMode.CLAMP
            )
        }
        rect.set(px - r * sx, cy - r * sy, px + r * sx, cy + r * sy)
        c.drawOval(rect, body)
        pS.color = th("pet_outline", 0x7A35684E.toInt())
        pS.strokeWidth = unit * 0.11f
        c.drawOval(rect, pS)

        // обводка-кромка слоя света (rim-light)
        pS.color = 0x30FFFFFF.toInt()
        pS.strokeWidth = unit * 0.07f
        rect.set(px - r * sx - unit * 0.07f, cy - r * sy - unit * 0.07f, px + r * sx + unit * 0.07f, cy + r * sy + unit * 0.07f)
        c.drawOval(rect, pS)

        // «пятки-няшки» убраны по решению: чистый силуэт выглядит круче


        // двойной блик (стеклянность)
        pF.color = 0x59FFFFFF.toInt()
        rect.set(px - r * 0.66f * sx, cy - r * sy * 0.98f, px + r * 0.10f * sx, cy - r * sy * 0.42f)
        c.drawOval(rect, pF)
        pF.color = 0x30FFFFFF.toInt()
        rect.set(px + r * 0.16f * sx, cy - r * sy * 0.80f, px + r * 0.42f * sx, cy - r * sy * 0.52f)
        c.drawOval(rect, pF)

        // животик
        pF.color = th("pet_belly", 0xFFBDEBCF.toInt())
        rect.set(px - r * 0.55f * sx, cy + r * sy * 0.02f, px + r * 0.55f * sx, cy + r * sy * 0.84f)
        c.drawOval(rect, pF)

        // лицо
        val eyeY = cy - r * sy * 0.30f
        val eyeDx = r * 0.40f * sx
        val sleeping = st == PetSim.State.SLEEP
        val stretching = st == PetSim.State.STRETCH
        val waving = st == PetSim.State.WAVE
        val blinking = !sleeping && !stretching && !waving && (sim.animPhase % 4.4f) < 0.14f
        val happy = st == PetSim.State.HAPPY || st == PetSim.State.INTERACT || st == PetSim.State.ROLL

        var lookX = sim.dir * 0.18f
        var lookY = 0f
        if (st == PetSim.State.LOOK) {
            lookX = sin(sim.stateTime / 1000f * PI * 1.4f).toFloat() * 0.25f
        } else if (!waving) {
            sim.targetItem?.let { ti ->
                val dxT = ti.x - sim.x
                val dyT = -(r * sy * 0.3f / r)
                val len = kotlin.math.sqrt(dxT * dxT + dyT * dyT).coerceAtLeast(0.001f)
                lookX = dxT / len * 0.2f
                lookY = dyT / len * 0.12f
            }
        }

        when {
            sleeping -> {
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.16f
                rect.set(px - eyeDx - r * 0.18f, eyeY - r * 0.10f, px - eyeDx + r * 0.18f, eyeY + r * 0.10f)
                c.drawArc(rect, 20f, 140f, false, pS)
                rect.set(px + eyeDx - r * 0.18f, eyeY - r * 0.10f, px + eyeDx + r * 0.18f, eyeY + r * 0.10f)
                c.drawArc(rect, 20f, 140f, false, pS)
                pF.color = 0xFF2E3B34.toInt()
                c.drawCircle(px, eyeY + r * sy * 0.36f, unit * 0.11f, pF)
                for (i in 0..2) {
                    val t = sim.animPhase + i * 0.4f
                    val zy = cy - r * sy - unit * (0.9f + i * 0.50f + sin(t * 1.3f) * 0.15f)
                    val zx = px + r * 0.6f + i * unit * 0.5f + sin(t * 0.9f) * unit * 0.08f
                    pZ.textSize = unit * (0.70f + i * 0.16f)
                    pZ.alpha = (170 - i * 45).coerceIn(0, 255)
                    c.drawText("Z", zx, zy, pZ)
                }
                pZ.alpha = 255
            }
            stretching || waving || st == PetSim.State.SQUEEZE || st == PetSim.State.TICKLE || st == PetSim.State.HAPPY -> {
                // РАДОСТЬ: дужки-глаза — читается мгновенно
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.16f
                rect.set(px - eyeDx - r * 0.17f, eyeY - r * 0.14f, px - eyeDx + r * 0.17f, eyeY + r * 0.07f)
                c.drawArc(rect, 200f, 140f, false, pS)
                rect.set(px + eyeDx - r * 0.17f, eyeY - r * 0.14f, px + eyeDx + r * 0.17f, eyeY + r * 0.07f)
                c.drawArc(rect, 200f, 140f, false, pS)
                if (st == PetSim.State.TICKLE) {
                    // слеза смеха — золотая искорка
                    drawSpark(c, px + eyeDx + r * 0.16f, eyeY - r * 0.14f, unit * 0.14f, 0xFFFFD54D.toInt(), 200)
                } else if (st == PetSim.State.HAPPY) {
                    // pockets сердечки создаются в drawHearts — здесь только глаза
                }
            }
            st == PetSim.State.BLUSH -> {
                // СТЕСНЯЕТСЯ: глазки-полоски + украдкой смотрит вбок
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.16f
                c.drawLine(px - eyeDx - r * 0.14f, eyeY + r * 0.02f, px - eyeDx + r * 0.14f, eyeY + r * 0.02f, pS)
                c.drawLine(px + eyeDx - r * 0.14f, eyeY + r * 0.02f, px + eyeDx + r * 0.14f, eyeY + r * 0.02f, pS)
                pF.color = 0xFF2E3B34.toInt()
                pF.alpha = 130
                c.drawCircle(px - eyeDx - sim.dir * r * 0.06f, eyeY + r * 0.02f, r * 0.07f, pF)
                c.drawCircle(px + eyeDx - sim.dir * r * 0.06f, eyeY + r * 0.02f, r * 0.07f, pF)
                pF.alpha = 255
            }
            // моргание отключено (по решению владельца): питомец не моргает
            else -> {
                val er = r * 0.24f
                val eyeCol = th("look_eyecolor", 0xFF22333B.toInt())
                when (look("look_eyes", "classic")) {
                    "shine" -> {
                        // большой блеск: глаз-блик + хвостик-искра
                        circleAt(c, px - eyeDx, eyeY, er, eyeCol)
                        circleAt(c, px + eyeDx, eyeY, er, eyeCol)
                        circleAt(c, px - eyeDx + lookX * er, eyeY + lookY * er - er * 0.3f, er * 0.52f, 0xFFFFFFFF.toInt())
                        circleAt(c, px + eyeDx + lookX * er, eyeY + lookY * er - er * 0.3f, er * 0.52f, 0xFFFFFFFF.toInt())
                        circleAt(c, px - eyeDx + lookX * er * 0.9f, eyeY + er * 0.3f, er * 0.14f, 0x50FFFFFF.toInt())
                        circleAt(c, px + eyeDx + lookX * er * 0.9f, eyeY + er * 0.3f, er * 0.14f, 0x50FFFFFF.toInt())
                    }
                    "sleepy" -> {
                        // сонные: полузакрытые веки — «крышка» цветом тела (не прямоугольник!)
                        val eyeCol2 = eyeCol
                        c.save()
                        rect.set(px - eyeDx - er - 1f, eyeY + er * 0.25f - er * 0.8f, px - eyeDx + er + 1f, eyeY + er * 0.25f + er * 0.8f)
                        c.clipPath(android.graphics.Path().apply {
                            addOval(rect, android.graphics.Path.Direction.CW)
                        })
                        circleAt(c, px - eyeDx, eyeY + er * 0.25f, er * 0.8f, eyeCol2)
                        c.restore()
                        c.save()
                        rect.set(px + eyeDx - er - 1f, eyeY + er * 0.25f - er * 0.8f, px + eyeDx + er + 1f, eyeY + er * 0.25f + er * 0.8f)
                        c.clipPath(android.graphics.Path().apply {
                            addOval(rect, android.graphics.Path.Direction.CW)
                        })
                        circleAt(c, px + eyeDx, eyeY + er * 0.25f, er * 0.8f, eyeCol2)
                        c.restore()
                        // веко: полукруг цвета тела, сглаженный по краям
                        pF.color = th("pet_body_lid", th("pet_top", 0xFFA8E6C3.toInt()))
                        rect.set(px - eyeDx - er - unit * 0.06f, eyeY - er * 1.15f, px - eyeDx + er + unit * 0.06f, eyeY + er * 0.02f)
                        c.drawOval(rect, pF)
                        rect.set(px + eyeDx - er - unit * 0.06f, eyeY - er * 1.15f, px + eyeDx + er + unit * 0.06f, eyeY + er * 0.02f)
                        c.drawOval(rect, pF)
                        pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.08f
                        c.drawLine(px - eyeDx - er, eyeY, px - eyeDx + er, eyeY, pS)
                        c.drawLine(px + eyeDx - er, eyeY, px + eyeDx + er, eyeY, pS)
                    }
                    "round" -> {
                        // круглые: умиротворённые
                        circleAt(c, px - eyeDx, eyeY, er * 1.08f, eyeCol)
                        circleAt(c, px + eyeDx, eyeY, er * 1.08f, eyeCol)
                        circleAt(c, px - eyeDx + lookX * er, eyeY - er * 0.22f, er * 0.3f, 0xFFFFFFFF.toInt())
                        circleAt(c, px + eyeDx + lookX * er, eyeY - er * 0.22f, er * 0.3f, 0xFFFFFFFF.toInt())
                    }
                    else -> {
                        circleAt(c, px - eyeDx, eyeY, er, eyeCol)
                        circleAt(c, px + eyeDx, eyeY, er, eyeCol)
                        circleAt(c, px - eyeDx + lookX * er, eyeY + lookY * er - er * 0.28f, er * 0.42f, 0xFFFFFFFF.toInt())
                        circleAt(c, px + eyeDx + lookX * er, eyeY + lookY * er - er * 0.25f, er * 0.40f, 0xFFFFFFFF.toInt())
                        circleAt(c, px - eyeDx + lookX * er * 0.6f, eyeY + er * 0.38f, er * 0.16f, 0x30FFFFFF.toInt())
                        circleAt(c, px + eyeDx + lookX * er * 0.6f, eyeY + er * 0.38f, er * 0.16f, 0x30FFFFFF.toInt())
                    }
                }
            }
        }

        // щёчки: формы по кастомизации
        val blushBase = th("pet_blush", 0xFFF3A9A0.toInt())
        val blushCol = if (st == PetSim.State.BLUSH) (0xB4 shl 24) or (blushBase and 0x00FFFFFF)
                       else (0x52 shl 24) or (blushBase and 0x00FFFFFF)
        val cheekY = eyeY + r * 0.24f
        val cheekLX = px - eyeDx - r * 0.3f
        val cheekRX = px + eyeDx + r * 0.3f
        val grow = if (st == PetSim.State.BLUSH) 1.35f else 1f
        when (look("look_blush", "circles")) {
            "stripes" -> {
                pF.color = blushCol
                for (i in 0..1) {
                    val off = i * r * 0.18f
                    rect.set(cheekLX - r * 0.28f + off, cheekY - r * 0.15f * grow, cheekLX - r * 0.28f + off + r * 0.1f, cheekY + r * 0.15f * grow)
                    c.drawRoundRect(rect, r * 0.05f, r * 0.05f, pF)
                    rect.set(cheekRX + r * 0.08f + off, cheekY - r * 0.15f * grow, cheekRX + r * 0.08f + off + r * 0.1f, cheekY + r * 0.15f * grow)
                    c.drawRoundRect(rect, r * 0.05f, r * 0.05f, pF)
                }
            }
            "hearts" -> {
                pF.color = blushCol
                drawMiniHeart(c, cheekLX, cheekY + r * 0.04f, r * 0.17f * grow, blushCol and 0x00FFFFFF, (blushCol ushr 24))
                drawMiniHeart(c, cheekRX, cheekY + r * 0.04f, r * 0.17f * grow, blushCol and 0x00FFFFFF, (blushCol ushr 24))
            }
            else -> {
                pF.color = blushCol
                rect.set(cheekLX - r * 0.13f * grow, cheekY - r * 0.08f * grow, cheekLX + r * 0.13f * grow, cheekY + r * 0.12f * grow)
                c.drawOval(rect, pF)
                rect.set(cheekRX - r * 0.13f * grow, cheekY - r * 0.08f * grow, cheekRX + r * 0.13f * grow, cheekY + r * 0.12f * grow)
                c.drawOval(rect, pF)
            }
        }

        // рот
        val mouthY = eyeY + r * sy * 0.36f
        pS.strokeWidth = unit * 0.16f
        // хитрец-прищур (поверх глаз) + голодные бровки
        when {
            st == PetSim.State.SMUG -> {
                // прищур хитреца: полукруглые «веки» цвета тела (не прямоугольники!)
                pF.color = th("pet_top", 0xFFA8E6C3.toInt())
                rect.set(px - eyeDx - r * 0.1f, eyeY - r * 0.26f, px - eyeDx + r * 0.1f, eyeY - r * 0.08f)
                c.drawOval(rect, pF)
                rect.set(px + eyeDx - r * 0.1f, eyeY - r * 0.26f, px + eyeDx + r * 0.1f, eyeY - r * 0.08f)
                c.drawOval(rect, pF)
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.11f
                c.drawLine(px - eyeDx - r * 0.16f, eyeY - r * 0.07f, px - eyeDx + r * 0.16f, eyeY - r * 0.07f, pS)
                c.drawLine(px + eyeDx - r * 0.16f, eyeY - r * 0.07f, px + eyeDx + r * 0.16f, eyeY - r * 0.07f, pS)
                pS.strokeWidth = unit * 0.16f
            }
            sim.hunger > 70f && !sleeping && !stretching && !waving -> {
                // голоден: бровки «домиком»
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.10f; pS.alpha = 185
                c.drawLine(px - eyeDx - r * 0.15f, eyeY - r * 0.44f, px - eyeDx - r * 0.02f, eyeY - r * 0.31f, pS)
                c.drawLine(px + eyeDx + r * 0.15f, eyeY - r * 0.44f, px + eyeDx + r * 0.02f, eyeY - r * 0.31f, pS)
                pS.alpha = 255; pS.strokeWidth = unit * 0.16f
            }
            else -> {}
        }
        when {
            sleeping -> { /* нарисовано */ }
            waving -> {
                // широкая улыбка хозяину с открытым ротиком
                pF.color = 0xFF2E3B34.toInt()
                rect.set(px - r * 0.26f, mouthY - r * 0.24f, px + r * 0.26f, mouthY + r * 0.10f)
                c.drawArc(rect, 0f, 180f, true, pF)
                pF.color = 0xFFE58C8C.toInt()
                rect.set(px - r * 0.10f, mouthY + r * 0.02f, px + r * 0.10f, mouthY + r * 0.10f)
                c.drawOval(rect, pF) // язычок
            }
            st == PetSim.State.SMUG -> {
                // хитрая ухмылка: дуга со смещением
                pS.color = 0xFF2E3B34.toInt()
                c.save()
                c.rotate(sim.dir * 8f, px, mouthY)
                rect.set(px - r * 0.20f, mouthY - r * 0.16f, px + r * 0.24f, mouthY + r * 0.06f)
                c.drawArc(rect, 10f, 145f, false, pS)
                c.restore()
                // приподнятая бровка
                c.drawLine(px + eyeDx - r * 0.1f, eyeY - r * 0.45f, px + eyeDx + r * 0.16f, eyeY - r * 0.55f, pS)
            }
            st == PetSim.State.BLUSH -> {
                // маленькая довольная улыбочка
                pS.color = 0xFF2E3B34.toInt()
                rect.set(px - r * 0.16f, mouthY - r * 0.12f, px + r * 0.16f, mouthY + r * 0.05f)
                c.drawArc(rect, 20f, 140f, false, pS)
            }
            st == PetSim.State.SQUEEZE -> {
                // блаженный "ооо"
                pF.color = 0xFF2E3B34.toInt()
                c.drawCircle(px, mouthY + r * 0.02f, r * 0.09f, pF)
            }
            st == PetSim.State.TICKLE -> {
                // хохочет от щекотки
                pF.color = 0xFF2E3B34.toInt()
                rect.set(px - r * 0.22f, mouthY - r * 0.22f, px + r * 0.22f, mouthY + r * 0.08f)
                c.drawArc(rect, 0f, 180f, true, pF)
            }
            st == PetSim.State.PULL -> {
                if (sim.stateTime < PetSim.PULL_TOGGLE_MS) {
                    // сосредоточенная гримаса: полуприкрытые глаза
                    pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.14f
                    c.drawLine(px - eyeDx - r * 0.12f, eyeY - r * 0.02f, px - eyeDx + r * 0.12f, eyeY - r * 0.02f, pS)
                    c.drawLine(px + eyeDx - r * 0.12f, eyeY - r * 0.02f, px + eyeDx + r * 0.12f, eyeY - r * 0.02f, pS)
                    pS.color = 0xFF2E3B34.toInt()
                    c.drawLine(px - r * 0.10f, mouthY, px + r * 0.10f, mouthY, pS)
                } else {
                    // щёлк! — довольный
                    pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.16f
                    rect.set(px - eyeDx - r * 0.16f, eyeY - r * 0.12f, px - eyeDx + r * 0.16f, eyeY + r * 0.06f)
                    c.drawArc(rect, 200f, 140f, false, pS)
                    rect.set(px + eyeDx - r * 0.16f, eyeY - r * 0.12f, px + eyeDx + r * 0.16f, eyeY + r * 0.06f)
                    c.drawArc(rect, 200f, 140f, false, pS)
                    pS.color = 0xFF2E3B34.toInt()
                    rect.set(px - r * 0.24f, mouthY - r * 0.18f, px + r * 0.24f, mouthY + r * 0.06f)
                    c.drawArc(rect, 15f, 150f, false, pS)
                }
            }
            st == PetSim.State.HUNGRY -> {
                // голодная жалоба
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.14f
                // грустные глаза с вверх бегающей слезинкой
                c.drawLine(px - eyeDx - r * 0.13f, eyeY + r * 0.02f, px - eyeDx + r * 0.13f, eyeY + r * 0.02f, pS)
                c.drawLine(px + eyeDx - r * 0.13f, eyeY + r * 0.02f, px + eyeDx + r * 0.13f, eyeY + r * 0.02f, pS)
                val tearP = (sim.stateTime % 900f) / 900f
                pF.color = 0xFF7EB7F2.toInt()
                pF.alpha = ((1f - tearP) * 180f).toInt().coerceIn(0, 255)
                c.drawCircle(px - eyeDx - r * 0.18f, eyeY + r * 0.10f + tearP * r * 0.5f, unit * 0.07f, pF)
                pF.alpha = 255
                // волнистый ротик «жалуется»
                rect.set(px - r * 0.18f, mouthY - r * 0.04f, px + r * 0.18f, mouthY + r * 0.08f)
                c.drawArc(rect, 200f, 140f, false, pS)
                // живо-бурчащий "буль" у животика
                pS.color = 0x80D98C5A.toInt()
                pS.strokeWidth = unit * 0.07f
                val bx2 = px - sim.dir * r * 0.75f
                val byy = cy + r * sy * 0.25f
                for (i in 0..1) {
                    val ph2 = sim.stateTime / 260f + i * 1.8f
                    val zz = sin(ph2 * 8f) * unit * 0.16f
                    val zy2 = byy + sin(ph2) * unit * 0.1f
                    c.drawLine(bx2 - unit * 0.3f, zy2, bx2 + unit * 0.3f, zy2 + zz * 0.6f, pS)
                }
                pS.strokeWidth = unit * 0.16f
            }
            happy && (sim.reaction == ItemDefs.Reaction.EAT || sim.reaction == ItemDefs.Reaction.SMELL) && st == PetSim.State.INTERACT -> {
                pF.color = 0xFF2E3B34.toInt()
                c.drawCircle(px, mouthY + r * 0.02f, r * 0.11f, pF)
                if (sim.reaction == ItemDefs.Reaction.SMELL) {
                    pS.color = 0x50FFFFFF
                    pS.strokeWidth = unit * 0.07f
                    c.drawLine(px - r * 0.06f, mouthY - r * 0.14f, px - r * 0.06f, mouthY - r * 0.26f, pS)
                    c.drawLine(px + r * 0.06f, mouthY - r * 0.14f, px + r * 0.06f, mouthY - r * 0.26f, pS)
                }
            }
            happy -> {
                // форма «счастливого» рта — по кастомизации
                pS.color = 0xFF2E3B34.toInt()
                when (look("look_grin", "smile")) {
                    "open" -> {
                        pF.color = 0xFF2E3B34.toInt()
                        rect.set(px - r * 0.26f, mouthY - r * 0.24f, px + r * 0.26f, mouthY + r * 0.10f)
                        c.drawArc(rect, 0f, 180f, true, pF)
                        pF.color = 0xFFE58C8C.toInt()
                        rect.set(px - r * 0.10f, mouthY + r * 0.02f, px + r * 0.10f, mouthY + r * 0.10f)
                        c.drawOval(rect, pF)
                    }
                    "neutral" -> {
                        c.drawLine(px - r * 0.14f, mouthY + r * 0.02f, px + r * 0.14f, mouthY + r * 0.02f, pS)
                    }
                    "ooh" -> {
                        pF.color = 0xFF2E3B34.toInt()
                        c.drawCircle(px, mouthY + r * 0.02f, r * 0.1f, pF)
                    }
                    else -> {
                        rect.set(px - r * 0.28f, mouthY - r * 0.20f, px + r * 0.28f, mouthY + r * 0.08f)
                        c.drawArc(rect, 15f, 150f, false, pS)
                    }
                }
            }
            else -> {
                pS.color = 0xFF2E3B34.toInt()
                if (sim.hunger > 70f) {
                    // сильно голоден — спокойная грустинка вместо точки-рта
                    rect.set(px - r * 0.13f, mouthY - r * 0.05f, px + r * 0.13f, mouthY + r * 0.06f)
                    c.drawArc(rect, 195f, 150f, false, pS)
                } else when (look("look_mouth", "smile")) {
                    "wide" -> {
                        rect.set(px - r * 0.20f, mouthY - r * 0.16f, px + r * 0.20f, mouthY + r * 0.06f)
                        c.drawArc(rect, 25f, 130f, false, pS)
                    }
                    "dimples" -> {
                        rect.set(px - r * 0.16f, mouthY - r * 0.12f, px + r * 0.16f, mouthY + r * 0.05f)
                        c.drawArc(rect, 25f, 130f, false, pS)
                        pF.color = 0xFF2E3B34.toInt()
                        c.drawCircle(px - r * 0.24f, mouthY - r * 0.02f, unit * 0.04f, pF)
                        c.drawCircle(px + r * 0.24f, mouthY - r * 0.02f, unit * 0.04f, pF)
                    }
                    "tooth" -> {
                        rect.set(px - r * 0.18f, mouthY - r * 0.14f, px + r * 0.18f, mouthY + r * 0.05f)
                        c.drawArc(rect, 15f, 150f, false, pS)
                        pF.color = 0xFFFFFFFF.toInt()
                        rect.set(px - unit * 0.04f, mouthY - r * 0.02f, px + unit * 0.04f, mouthY + r * 0.05f)
                        c.drawRect(rect, pF)
                    }
                    "small" -> {
                        rect.set(px - r * 0.12f, mouthY - r * 0.08f, px + r * 0.12f, mouthY + r * 0.03f)
                        c.drawArc(rect, 30f, 120f, false, pS)
                    }
                    else -> c.drawLine(px - r * 0.13f, mouthY, px + r * 0.13f, mouthY, pS)
                }
            }
        }

        // «пуф-облако» при смене действия: мягкий мультипликационный переход
        if (facePuff > 0.02f) {
            val puff = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    px, eyeY - r * sy * 0.10f, r * 1.35f,
                    intArrayOf(((0x26 * facePuff).toInt() shl 24) or 0x00FFFFFF, 0x00FFFFFF),
                    null, Shader.TileMode.CLAMP
                )
            }
            c.drawCircle(px, eyeY - r * sy * 0.05f, r * 1.3f, puff)
        }
        c.restore()
    }

    // ------------------------------------------------- предмет "в руках"

    private fun drawHeldItem(c: Canvas, sim: PetSim, inter: InteractInfo, unit: Float, floorY: Float) {
        val ti = inter.target ?: return
        val prog = inter.prog
        val x = inter.holdX                                   // всегда у рук
        val y = floorY + inter.liftPx * prog

        // ---- Индивидуальность еды: масштаб/наклон/боб/крошки по типу
        val ep = inter.eatProgress
        var sc = 1f
        var tilt = 0f
        var bob = 0f
        when (ti.def.id) {
            "pizza" -> { sc = 1f - 0.32f * ep; bob = sin(sim.animPhase * 20f) * unit * 0.05f * ep }
            "cookie" -> { sc = 1f - 0.38f * ep; bob = sin(sim.animPhase * 26f) * unit * 0.10f * ep }
            "donut" -> { sc = 1f - 0.30f * ep; tilt = ep * 160f }
            "apple" -> { sc = 1f - 0.55f * ep; tilt = ep * 12f }
            "icecream" -> { bob = sin(sim.animPhase / 180f) * unit * 0.22f; tilt = ep * 16f }
            "coffee" -> { tilt = ep * 34f }
            "sushi" -> { bob = sin(sim.animPhase * 22f) * unit * 0.07f * ep }
            // ---- игрушки в руках (LIFT-игры)
            "dice" -> tilt = sin(sim.animPhase * 18f) * 18f * (0.6f + 0.4f * ep)   // трясёт кости
            "balloon" -> {                                                        // шарик рвётся в небо
                bob = -unit * 0.45f * ep + sin(sim.animPhase * 3f) * unit * 0.12f
                tilt = sin(sim.animPhase * 2f) * 6f
            }
            "rocket" -> {                                                          // пытается улететь
                bob = -unit * 0.5f * ep
                tilt = sin(sim.animPhase * 5f) * 4f
            }
            "boat" -> {                                                            // качается на волнах
                bob = sin(sim.animPhase * 3.4f) * unit * 0.09f
                tilt = sin(sim.animPhase * 2.6f) * 7f
            }
        }
        val yb = y + bob

        c.save()
        c.rotate(sin(sim.animPhase * 3f) * 4f * prog, x, yb)
        c.rotate(tilt, x, yb)
        c.scale(sc, sc, x, yb)
        val alpha = ((1f - inter.eatFade) * 255f).toInt().coerceIn(0, 255)
        pF.alpha = alpha; pS.alpha = alpha; pZ.alpha = alpha
        ti.def.draw(c, x, yb, unit, sim.animPhase)
        c.restore()

        // ---- крошки/капли по типу еды
        if (ti.def.id == "rocket" && inter.prog > 0.3f) {
            // пламя ракеты — при попытке улететь
            val fl = sin(sim.animPhase * 30f) * 0.5f + 0.5f
            path.reset()
            path.moveTo(x - unit * 0.30f, yb + unit * 1.0f)
            path.quadTo(x, yb + unit * (1.6f + fl * 0.5f), x + unit * 0.30f, yb + unit * 1.0f)
            path.close()
            pF.color = 0xFFFFB84D.toInt()
            pF.alpha = ((1f - inter.eatFade) * 235f).toInt().coerceIn(0, 255)
            c.drawPath(path, pF)
            pF.color = 0xFFFFD54D.toInt()
            path.reset()
            path.moveTo(x - unit * 0.16f, yb + unit * 1.0f)
            path.quadTo(x, yb + unit * (1.35f + fl * 0.3f), x + unit * 0.16f, yb + unit * 1.0f)
            path.close()
            c.drawPath(path, pF)
        }
        if (inter.eatProgress > 0.08f) {
            val crumbCol = when (ti.def.id) {
                "pizza", "cookie" -> 0xFFC88A4A
                "donut" -> 0xFFF6A8C0
                "apple" -> 0xFFF3F1E4
                "icecream" -> 0xFFFDE9F1
                "sushi" -> 0xFFF4F8FB
                else -> 0xFF9CA3AF
            }
            drawCrumbs(c, x, yb + inter.liftPx * 0.2f, unit, inter.eatProgress, crumbCol.toInt())
        }
        if (ti.def.id == "coffee" && inter.eatProgress > 0.05f) {
            // глотки: капли бегут к морде
            for (i in 0..2) {
                val dp = (inter.eatProgress * 3f - i * 0.33f).coerceIn(0f, 1f)
                if (dp <= 0f) continue
                pF.color = 0xFFB98B5A.toInt()
                pF.alpha = (dp * 200).toInt().coerceIn(0, 255)
                c.drawCircle(x + unit * 0.1f * i, yb - unit * (0.2f + dp * 0.5f), unit * 0.05f, pF)
            }
            pF.alpha = 255
        }
        pF.alpha = 255; pS.alpha = 255; pZ.alpha = 255
        inter.holdYExtra = bob
    }

    /** живые крошки у рта */
    private fun drawCrumbs(c: Canvas, x: Float, y: Float, unit: Float, ep: Float, col: Int) {
        for (i in 0..4) {
            val ang = i * 1.9f + ep * 4f
            val dx = cos(ang) * unit * (0.25f + 0.55f * ep)
            val dy = abs(sin(ang)) * unit * 0.45f + ep * unit * 0.55f
            pF.color = col
            pF.alpha = ((1f - ep * 0.8f) * 210).toInt().coerceIn(0, 255)
            c.drawCircle(x + dx, y + dy, unit * 0.06f, pF)
        }
        pF.alpha = 255
    }

    // --------------------------------------------------------------- руки

    private fun drawArms(
        c: Canvas, sim: PetSim, inter: InteractInfo, unit: Float, floorY: Float, itemX: (Float) -> Float
    ) {
        val r = unit * 1.55f
        val cx = itemX(sim.x)
        val cy = petCy(sim, floorY, unit, r)
        val st = sim.state

        // руки красятся токенами тела (утолщённый контур + светлые ладошки)
        val armColor = th("pet_bottom", 0xFF5CA985.toInt())
        val palmColor = th("pet_belly", 0xFF9ADDB8.toInt())
        val grip = unit * 0.24f

        fun arm(ax: Float, ay: Float, hxRaw: Float, hyRaw: Float, up: Boolean) {
            // руки не резиновые до бесконечности: максимум ~2 радиуса тела
            var hx = hxRaw
            var hy = hyRaw
            val vx = hx - cx
            val vy = hy - cy
            val d = kotlin.math.sqrt(vx * vx + vy * vy)
            val maxD = r * 2.15f
            if (d > maxD) {
                hx = cx + vx / d * maxD
                hy = cy + vy / d * maxD
            }
            val mx = (ax + hx) / 2f
            val my = (ay + hy) / 2f + (if (up) -unit * 0.30f else unit * 0.45f)
            path.reset()
            path.moveTo(ax, ay)
            path.quadTo(mx, my, hx, hy)
            // мягкое свечение под рукой
            pS.color = (0x2E shl 24) or (palmColor and 0x00FFFFFF)
            pS.strokeWidth = unit * 0.40f
            c.drawPath(path, pS)
            // основная рука
            pS.color = armColor
            pS.strokeWidth = unit * 0.22f
            c.drawPath(path, pS)
            circleAt(c, hx, hy, grip, palmColor)
            pS.color = th("pet_outline", 0x6E3C7A63.toInt())
            pS.strokeWidth = unit * 0.08f
            c.drawCircle(hx, hy, grip, pS)
        }

        when (st) {
            PetSim.State.STRETCH -> {
                val sp = sin(((sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)) * PI.toFloat())
                val up = sp * r * 1.35f
                arm(cx - r * 0.30f, cy - r * 0.85f, cx - r * 0.55f * sp, cy - r * 0.85f - up, true)
                arm(cx + r * 0.30f, cy - r * 0.85f, cx + r * 0.55f * sp, cy - r * 0.85f - up, true)
            }

            PetSim.State.WAVE -> {
                val sw = sin(sim.stateTime / 230f) * unit * 0.6f
                val reach = (sim.stateTime / 260f).coerceIn(0f, 1f)
                arm(
                    cx + r * 0.5f, cy - r * 0.35f,
                    cx + r * 1.1f + sw, cy - r * 1.9f - unit * 0.2f * reach,
                    true
                )
            }

            PetSim.State.PULL -> {
                // рука держит бусину шнура
                val (tipX, tipY) = pullCordPos(sim, unit, floorY)
                arm(cx + r * 0.35f, cy - r * 0.30f, tipX, tipY + unit * 0.1f, true)
            }

            PetSim.State.INTERACT -> {
                val ti = inter.target ?: return
                val prog = inter.armReach
                if (prog <= 0.01f) return
                val axL = cx - r * 0.55f; val axR = cx + r * 0.55f
                val ay = cy + r * 0.18f

                when (sim.reaction) {
                    // держит предмет двумя руками (в воздухе) — у точки хвата
                    ItemDefs.Reaction.SMELL, ItemDefs.Reaction.LIFT, ItemDefs.Reaction.EAT -> {
                        val hy = floorY + inter.liftPx * inter.prog + inter.holdYExtra
                        arm(axL, ay, inter.holdX - unit * 0.3f, hy + unit * 0.22f, false)
                        arm(axR, ay, inter.holdX + unit * 0.3f, hy + unit * 0.22f, false)
                    }
                    // гладит: ручки кружат над предметом у себя перед мордой
                    ItemDefs.Reaction.RUB -> {
                        val a1 = inter.rubPhase
                        val a2 = inter.rubPhase + PI.toFloat()
                        val ccx = cx + sim.dir * r * 1.15f
                        arm(axL, ay, ccx + cos(a1) * unit * 0.42f, floorY - unit * 0.55f + sin(a1) * unit * 0.18f, false)
                        arm(axR, ay, ccx + cos(a2) * unit * 0.42f, floorY - unit * 0.55f + sin(a2) * unit * 0.18f, false)
                    }
                    // открывает крышку перед собой
                    ItemDefs.Reaction.OPEN -> {
                        val lift = inter.lidProg
                        arm(axL, ay, cx + sim.dir * r * 1.05f, floorY - unit * 1.55f - unit * 1.15f * lift, false)
                        arm(axR, ay, cx + sim.dir * r * 1.05f, floorY - unit * 1.55f - unit * 1.15f * lift, false)
                    }
                    // упирается обеими и толкает перед собой
                    else -> {
                        arm(axL, ay, cx + sim.dir * r * 1.3f, floorY - unit * 0.42f, false)
                        arm(axR, ay, cx + sim.dir * r * 1.3f, floorY - unit * 0.42f, false)
                    }
                }
            }
            else -> {}
        }
    }

    // сердечки при волне и при радости от поглаживания
    private fun drawHearts(c: Canvas, sim: PetSim, cx: Float, floorY: Float, unit: Float) {
        val show = sim.state == PetSim.State.WAVE ||
            (sim.state == PetSim.State.HAPPY && sim.petStyle == 0)
        if (!show) return
        if (sim.state == PetSim.State.WAVE) drawHeartsWave(c, sim, cx, floorY, unit)
        else drawHeartsPet(c, sim, cx, floorY, unit)
    }

    private fun drawHeartsWave(c: Canvas, sim: PetSim, cx: Float, floorY: Float, unit: Float) {
        val r = unit * 1.55f
        val cy = petCy(sim, floorY, unit, r)
        val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
        for (i in 0..2) {
            val hp = (p - 0.15f - i * 0.18f)
            if (hp <= 0f || hp >= 1f) continue
            val alpha = ((1f - hp) * 220f).toInt().coerceIn(0, 255)
            pF.color = 0xFFF26D7E.toInt()
            pF.alpha = alpha
            val hx = cx + r * 1.1f + i * unit * 0.55f
            val hy = cy - r * 1.9f - hp * unit * 1.6f
            val s = unit * 0.16f
            path.reset()
            path.moveTo(hx, hy + s)
            path.quadTo(hx - s, hy, hx - s * 0.4f, hy - s * 0.5f)
            path.quadTo(hx, hy - s, hx, hy - s * 0.3f)
            path.quadTo(hx, hy - s, hx + s * 0.4f, hy - s * 0.5f)
            path.quadTo(hx + s, hy, hx, hy + s)
            c.drawPath(path, pF)
        }
        pF.alpha = 255
    }

    /** сердечки после поглаживания — столбик над головой */
    private fun drawHeartsPet(c: Canvas, sim: PetSim, cx: Float, floorY: Float, unit: Float) {
        val r = unit * 1.55f
        val cy = petCy(sim, floorY, unit, r)
        val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
        for (i in 0..1) {
            val hp = p * 1.6f - i * 0.5f
            if (hp <= 0f || hp >= 1f) continue
            pF.color = 0xFFF26D7E.toInt()
            pF.alpha = ((1f - hp) * 230f).toInt().coerceIn(0, 255)
            val hxx = cx + sin(hp * 6f) * unit * 0.2f
            val hyy = cy - r * 1.8f - hp * unit * 1.4f
            val s = unit * (0.20f - hp * 0.06f)
            path.reset()
            path.moveTo(hxx, hyy + s)
            path.quadTo(hxx - s, hyy, hxx - s * 0.4f, hyy - s * 0.5f)
            path.quadTo(hxx, hyy - s, hxx, hyy - s * 0.3f)
            path.quadTo(hxx, hyy - s, hxx + s * 0.4f, hyy - s * 0.5f)
            path.quadTo(hxx + s, hyy, hxx, hyy + s)
            c.drawPath(path, pF)
        }
        pF.alpha = 255
    }

    // ------------------------------------------------------ кожаное кресло

    private fun drawChairBack(c: Canvas, unit: Float, floorY: Float) {
        val bx = floorXfor(PetSim.BED_X, unit)

        // тень кресла
        pF.color = 0x14000000
        rect.set(bx - unit * 2.0f, floorY - unit * 0.16f, bx + unit * 2.0f, floorY + unit * 0.14f)
        c.drawOval(rect, pF)

        // спинка с кожаным градиентом
        val back = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bx - unit, floorY - unit * 3.0f, bx + unit * 0.6f, floorY - unit * 0.5f,
                th("chair_back1", 0xFFAB7B44.toInt()), th("chair_back2", 0xFF6D4523.toInt()), Shader.TileMode.CLAMP)
        }
        rect.set(bx - unit * 1.05f, floorY - unit * 3.0f, bx + unit * 1.05f, floorY - unit * 0.4f)
        c.drawRoundRect(rect, unit * 0.52f, unit * 0.52f, back)
        // крылья спинки (ушейки)
        rect.set(bx - unit * 1.30f, floorY - unit * 2.35f, bx - unit * 0.75f, floorY - unit * 0.55f)
        c.drawRoundRect(rect, unit * 0.4f, unit * 0.4f, back)
        rect.set(bx + unit * 0.75f, floorY - unit * 2.35f, bx + unit * 1.30f, floorY - unit * 0.55f)
        c.drawRoundRect(rect, unit * 0.4f, unit * 0.4f, back)

        // сиденье-подушка
        val seat = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, floorY - unit * 0.95f, 0f, floorY,
                th("chair_seat1", 0xFFC08A57.toInt()), th("chair_seat2", 0xFF94613A.toInt()), Shader.TileMode.CLAMP)
        }
        rect.set(bx - unit * 1.35f, floorY - unit * 0.95f, bx + unit * 1.35f, floorY - unit * 0.05f)
        c.drawRoundRect(rect, unit * 0.4f, unit * 0.4f, seat)
        // блик на подушке
        pF.color = 0x2EFFFFFF.toInt()
        rect.set(bx - unit * 1.2f, floorY - unit * 0.92f, bx + unit * 0.6f, floorY - unit * 0.55f)
        c.drawOval(rect, pF)
    }

    private fun drawChairFront(c: Canvas, unit: Float, floorY: Float) {
        val bx = floorXfor(PetSim.BED_X, unit)

        val roll = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, floorY - unit * 2.0f, 0f, floorY,
                th("chair_roll1", 0xFFB98350.toInt()), th("chair_roll2", 0xFF8A5A2E.toInt()), Shader.TileMode.CLAMP)
        }
        // подлокотники (передний план)
        rect.set(bx - unit * 1.75f, floorY - unit * 1.85f, bx - unit * 1.05f, floorY - unit * 0.05f)
        c.drawRoundRect(rect, unit * 0.34f, unit * 0.34f, roll)
        rect.set(bx + unit * 1.05f, floorY - unit * 1.85f, bx + unit * 1.75f, floorY - unit * 0.05f)
        c.drawRoundRect(rect, unit * 0.34f, unit * 0.34f, roll)
        // заглушки-капельки на подлокотниках
        pF.color = 0xFFC99563.toInt()
        rect.set(bx - unit * 1.72f, floorY - unit * 1.96f, bx - unit * 1.08f, floorY - unit * 1.64f)
        c.drawOval(rect, pF)
        rect.set(bx + unit * 1.08f, floorY - unit * 1.96f, bx + unit * 1.72f, floorY - unit * 1.64f)
        c.drawOval(rect, pF)
        // строчки
        pS.color = 0x886B4423.toInt()
        pS.strokeWidth = unit * 0.07f
        c.drawLine(bx - unit * 1.42f, floorY - unit * 1.6f, bx - unit * 1.38f, floorY - unit * 0.25f, pS)
        c.drawLine(bx + unit * 1.38f, floorY - unit * 1.6f, bx + unit * 1.42f, floorY - unit * 0.25f, pS)
        // ножки
        pF.color = 0xFF5B3A1F.toInt()
        rect.set(bx - unit * 1.15f, floorY - unit * 0.12f, bx - unit * 0.85f, floorY - unit * 0.0f)
        c.drawRect(rect, pF)
        rect.set(bx + unit * 0.85f, floorY - unit * 0.12f, bx + unit * 1.15f, floorY - unit * 0.0f)
        c.drawRect(rect, pF)
    }

    // Helpers -----------------------------------------------------------

    private fun floorXfor(xN: Float, unit: Float): Float {
        val pad = unit * 0.7f
        return pad + xN.coerceIn(0f, 1f) * (w - 2 * pad)
    }

    private fun smooth(p: Float): Float = p * p * (3f - 2f * p)

    private fun circleAt(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        pF.color = col
        c.drawCircle(x, y, r, pF)
    }
}

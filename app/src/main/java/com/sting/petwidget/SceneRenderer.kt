package com.sting.petwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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
    private val furniture = RoomFurniture()
    private val yardScene = YardScene()
    private val gradientPaints = HashMap<String, Paint>()
    private fun gradientPaint(name: String): Paint = gradientPaints.getOrPut(name) { Paint(Paint.ANTI_ALIAS_FLAG) }
    /** Превью не должно записывать общую позу живого питомца. */
    var persistPose = true

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

    /** шрифт подписей (Rubik задаётся сервисом/атelier; null → системный) */
    var font: android.graphics.Typeface? = null

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
        if (sim.outing.yard) {
            drawYard(c, sim, unit, floorY, itemX)
            return
        }

        // фон (тема-токены)
        val bg = gradientPaint("bg").apply {
            shader = LinearGradient(0f, pad, 0f, floorY, th("room_bg_top", 0xFFF2F2EF.toInt()), th("room_bg_bottom", 0xFFE8E8E4.toInt()), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), floorY + 1f, bg)
        c.drawRect(0f, floorY, w.toFloat(), h.toFloat(), bg)

        // время суток
        val worldTime = System.currentTimeMillis()
        val night = WindowScene.night(worldTime)

        // стены/потолок
        val wallL = gradientPaint("wallL").apply { shader = LinearGradient(0f, 0f, pad * 2.4f, 0f, 0x1E555550, 0x00555550, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, pad * 2.4f, h.toFloat(), wallL)
        val wallR = gradientPaint("wallR").apply { shader = LinearGradient(w.toFloat(), 0f, w - pad * 2.4f, 0f, 0x1E555550, 0x00555550, Shader.TileMode.CLAMP) }
        c.drawRect(w - pad * 2.4f, 0f, w.toFloat(), h.toFloat(), wallR)
        val ceil = gradientPaint("ceil").apply { shader = LinearGradient(0f, 0f, 0f, pad * 1.6f, 0x16606058, 0x00606058, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), pad * 1.6f, ceil)

        // узор обоев (читается из кастомизации)
        drawWallPattern(c, unit, pad, floorY)
        pF.color = 0xFF555550.toInt()
        pF.alpha = 5
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
        val floor = gradientPaint("floor").apply { shader = LinearGradient(0f, floorY, 0f, h.toFloat(), th("floor_top", 0xFFE1E1DC.toInt()), th("floor_bottom", 0xFFD3D3CE.toInt()), Shader.TileMode.CLAMP) }
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
        pS.color = 0x24C9C9C3.toInt()
        pS.strokeWidth = unit * 0.12f
        c.drawRoundRect(pad, pad, w - pad, h - pad, unit * 0.65f, unit * 0.65f, pS)

        // Мягкая смена времени суток и окно в свободной левой части стены.
        c.drawColor(((night * 30).toInt() shl 24) or 0x002E3A55)
        WindowScene.draw(c, unit, floorY, worldTime)
        val curtainStyle = th("room_curtain", -1)
        if (curtainStyle >= 0) furniture.curtains(c, unit, floorY, curtainStyle,
            th("curtain_closed", 0) == 1, th("curtain_color", 0xFF8AAFA3.toInt()))
        furniture.decor(c, unit, floorY, th("decor_rug", 0) == 1,
            th("decor_tv", 0) == 1, th("decor_plant", 0) == 1,
            th("rug_color", 0xFF7FA89D.toInt()), th("decor_accent", 0xFFD18F6C.toInt()))

        // кожаное мини-кресло: задняя часть (спинка+сидушка) — ФОН
        drawChairBack(c, unit, floorY)

        // лампочка на шнурке (зона тапа — отдельный View сверху по центру)
        drawBulb(c, unit, floorY)

        // шнур-выключатель с бусиной
        drawPullCord(c, sim, unit, floorY)

        yardScene.door(c, itemX(sim.outing.doorX), floorY, unit, sim.outing.doorOpen)

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

        // метка предмета при появлении — чтобы не угадывать, что упало
        for (item in sim.items) {
            if (item.ageMs in 0L..1_700L) {
                try { drawSpawnLabel(c, item.def.name, itemX(item.x), floorY, unit, item.ageMs) }
                catch (t: Throwable) { logErr("label", t) }
            }
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
        // Настенная клавиша рисуется только в слое стены, до питомца.
        if (th("room_lamp", 0) == 0) drawPullCord(c, sim, unit, floorY)

        drawLocationFade(c, sim)

    }

    private fun drawLocationFade(c: Canvas, sim: PetSim) {
        val alpha = (sim.outing.fade * 255).toInt().coerceIn(0,255)
        if (alpha > 0) c.drawColor((alpha shl 24) or 0x001D2B29)
    }

    private fun drawYard(c: Canvas, sim: PetSim, unit: Float, floor: Float, itemX: (Float) -> Float) {
        yardScene.draw(c, w.toFloat(), h.toFloat(), floor, unit, System.currentTimeMillis(), sim.outing)
        val inter = readInteract(sim, itemX, r = unit * 1.55f)
        val a = sim.outing.swingAngle
        c.save()
        c.translate(sin(a)*unit*3.4f, -unit*.65f*sim.outing.mount-(1f-cos(a))*unit*3.4f)
        drawPet(c, sim, itemX(sim.x), floor, unit, inter)
        drawArms(c, sim, inter, unit, floor, itemX)
        c.restore()
        for (item in sim.items) {
            if (!(inter.holding && item === inter.target)) drawFloorItem(c, sim, item, inter, itemX(item.x), floor, unit)
            if (item.ageMs in 0L..1700L) drawSpawnLabel(c, item.def.name, itemX(item.x), floor, unit, item.ageMs)
            if (item.confetti && item.ageMs in 0L..420L) drawConfetti(c,itemX(item.x),floor,unit,item.ageMs/420f)
        }
        if (inter.lidProg > 0f && inter.target != null) drawLidPop(c,inter,unit,floor)
        if (inter.holding && inter.target != null && inter.prog > 0f) {
            drawHeldItem(c,sim,inter,unit,floor)
            drawArms(c,sim,inter,unit,floor,itemX)
        }
        if (sim.state == PetSim.State.WAVE) drawHearts(c,sim,itemX(sim.x),floor,unit)
        drawLocationFade(c,sim)
    }

    // ------------------------------------------------------- свет и лампа

    private fun drawBulb(c: Canvas, unit: Float, floorY: Float) {
        val style = th("room_lamp", 0)
        if (style != 0) {
            furniture.lamp(c, w.toFloat(), floorY, unit, 26f * density, style, lightNow())
            return
        }
        val bx = w / 2f
        val by = 26f * density          // фиксируем в dp, чтобы совпадало с зоной тапа
        val on = lightNow()

        // потолочная чашка и тканевый кабель
        pF.color = 0xFF33443E.toInt()
        rect.set(bx - unit * 0.5f, -unit * 0.08f, bx + unit * 0.5f, unit * 0.22f)
        c.drawRoundRect(rect, unit * 0.12f, unit * 0.12f, pF)
        pS.color = 0xFF53635D.toInt()
        pS.strokeWidth = unit * 0.11f
        c.drawLine(bx, unit * 0.18f, bx, by - unit * 0.72f, pS)

        // современный колоколообразный плафон
        val shade = gradientPaint("shade").apply {
            shader = LinearGradient(bx - unit, by - unit * 0.7f, bx + unit, by + unit * 0.4f,
                0xFF587067.toInt(), 0xFF263A34.toInt(), Shader.TileMode.CLAMP)
        }
        path.reset()
        path.moveTo(bx - unit * 0.34f, by - unit * 0.72f)
        path.quadTo(bx - unit * 0.78f, by - unit * 0.25f, bx - unit * 1.03f, by + unit * 0.34f)
        path.quadTo(bx, by + unit * 0.62f, bx + unit * 1.03f, by + unit * 0.34f)
        path.quadTo(bx + unit * 0.78f, by - unit * 0.25f, bx + unit * 0.34f, by - unit * 0.72f)
        path.close()
        c.drawPath(path, shade)
        pS.color = 0xFF20312C.toInt(); pS.strokeWidth = unit * 0.09f; c.drawPath(path, pS)

        // тёплый матовый рассеиватель, видимый и при выключенном свете
        pF.color = if (on) 0xFFFFE7AE.toInt() else 0xFFC8C5B8.toInt()
        rect.set(bx - unit * 0.88f, by + unit * 0.20f, bx + unit * 0.88f, by + unit * 0.58f)
        c.drawOval(rect, pF)
        pF.color = if (on) 0x8CFFFFFF.toInt() else 0x28FFFFFF
        rect.set(bx - unit * 0.55f, by + unit * 0.24f, bx + unit * 0.18f, by + unit * 0.39f)
        c.drawOval(rect, pF)
    }

    /** координаты кончика шнура-выключателя (с учётом тяги) */
    private fun pullCordPos(sim: PetSim, unit: Float, floorY: Float): Pair<Float, Float> {
        val sx = floorXfor(sim.switchX, unit)
        if (th("room_lamp", 0) != 0) return (sx + unit * 0.8f) to (floorY - unit * 2.5f)
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
        if (th("room_lamp", 0) != 0) {
            pF.color = 0xFFF2E7D5.toInt()
            rect.set(sx - unit * 0.32f, tipY - unit * 0.42f, sx + unit * 0.32f, tipY + unit * 0.42f)
            c.drawRoundRect(rect, unit * 0.1f, unit * 0.1f, pF)
            pS.color = 0xFF4A4038.toInt(); pS.strokeWidth = unit * 0.08f
            c.drawRoundRect(rect, unit * 0.1f, unit * 0.1f, pS)
            pF.color = if (lightNow()) 0xFF7FAE87.toInt() else 0xFF8A9290.toInt()
            val press = buttonPress(sim)
            rect.inset(unit * (0.14f + press * 0.025f), unit * 0.15f)
            rect.offset(0f, unit * 0.06f * press)
            c.drawRoundRect(rect, unit * 0.04f, unit * 0.04f, pF)
            pS.color = 0x77524C43; pS.strokeWidth = unit * 0.045f
            c.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, pS)
            return
        }
        pS.color = 0xFF52635C.toInt()
        pS.strokeWidth = unit * 0.075f
        c.drawLine(sx, 0f, sx, tipY, pS)
        // компактная латунная ручка вместо старой бусины
        pF.color = if (lightNow()) 0xFFD3A85D.toInt() else 0xFF857966.toInt()
        rect.set(sx - unit * 0.16f, tipY - unit * 0.24f, sx + unit * 0.16f, tipY + unit * 0.24f)
        c.drawRoundRect(rect, unit * 0.15f, unit * 0.15f, pF)
        pF.color = 0x55FFFFFF
        rect.set(sx - unit * 0.07f, tipY - unit * 0.16f, sx, tipY + unit * 0.07f)
        c.drawRoundRect(rect, unit * 0.04f, unit * 0.04f, pF)
    }

    /** Узоры: чисто, точки, полосы, зигзаг, клетка, звёзды, сердца, облака, листья. */
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
            5 -> { // замкнутые пятиконечные звёзды
                val st = unit * 2.0f
                var row = 0
                var yy = top + st
                while (yy < bottom) {
                    var xx = left + st
                    val sh = if (row % 2 == 1) st / 2f else 0f
                    while (xx < right) {
                        path.reset()
                        for (point in 0..9) {
                            val angle = -PI.toFloat() / 2f + point * PI.toFloat() / 5f
                            val radius = unit * (if (point % 2 == 0) 0.37f else 0.17f)
                            val sx = xx + sh + cos(angle) * radius
                            val sy = yy + sin(angle) * radius
                            if (point == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                        }
                        path.close(); pF.color = col; c.drawPath(path, pF)
                        xx += st
                    }
                    yy += st
                    row++
                }
                pS.strokeWidth = unit * 0.16f
            }
            6 -> { // маленькие сердца
                val st = unit * 2.05f
                var row = 0
                var yy = top + st
                while (yy < bottom) {
                    var xx = left + st * 0.7f
                    val shift = if (row % 2 == 1) st / 2f else 0f
                    while (xx < right) {
                        drawMiniHeart(c, xx + shift, yy, unit * 0.3f, col and 0x00FFFFFF, (col ushr 24).coerceAtLeast(36))
                        xx += st
                    }
                    yy += st * 0.9f; row++
                }
            }
            7 -> { // мягкие облака
                pF.color = col
                val st = unit * 2.35f
                var row = 0
                var yy = top + st
                while (yy < bottom) {
                    var xx = left + st
                    val shift = if (row % 2 == 1) st / 2f else 0f
                    while (xx < right) {
                        val x = xx + shift
                        // Один замкнутый силуэт, без наложения полупрозрачных кругов.
                        path.reset(); path.moveTo(x - unit * 0.4f, yy + unit * 0.2f)
                        path.cubicTo(x - unit * 0.78f, yy + unit * 0.18f, x - unit * 0.65f, yy - unit * 0.3f, x - unit * 0.3f, yy - unit * 0.22f)
                        path.cubicTo(x - unit * 0.24f, yy - unit * 0.72f, x + unit * 0.48f, yy - unit * 0.65f, x + unit * 0.43f, yy - unit * 0.16f)
                        path.cubicTo(x + unit * 0.86f, yy - unit * 0.2f, x + unit * 0.85f, yy + unit * 0.2f, x + unit * 0.5f, yy + unit * 0.2f)
                        path.close(); c.drawPath(path, pF)
                        xx += st
                    }
                    yy += st; row++
                }
            }
            8 -> { // веточки с листьями
                pS.color = col; pS.strokeWidth = unit * 0.08f
                pF.color = col
                val st = unit * 2.15f
                var row = 0
                var yy = top + st
                while (yy < bottom) {
                    var xx = left + st
                    val shift = if (row % 2 == 1) st / 2f else 0f
                    while (xx < right) {
                        val x = xx + shift
                        path.reset(); path.moveTo(x - unit * 0.36f, yy + unit * 0.42f)
                        path.cubicTo(x - unit * 0.7f, yy - unit * 0.1f, x - unit * 0.1f, yy - unit * 0.54f, x + unit * 0.48f, yy - unit * 0.42f)
                        path.cubicTo(x + unit * 0.58f, yy + unit * 0.12f, x + unit * 0.08f, yy + unit * 0.58f, x - unit * 0.36f, yy + unit * 0.42f)
                        path.close(); c.drawPath(path, pS)
                        c.drawLine(x - unit * 0.43f, yy + unit * 0.5f, x + unit * 0.32f, yy - unit * 0.27f, pS)
                        c.drawLine(x - unit * 0.12f, yy + unit * 0.19f, x - unit * 0.3f, yy - unit * 0.04f, pS)
                        c.drawLine(x + unit * 0.07f, yy, x + unit * 0.3f, yy + unit * 0.12f, pS)
                        xx += st
                    }
                    yy += st; row++
                }
            }
        }
        c.restore()
    }

    private var windowGlow = 0f
    private fun buttonPress(sim: PetSim): Float {
        if (sim.state != PetSim.State.PULL) return 0f
        return SwitchMotion.pose(sim.stateTime).press
    }
    private var lastLightFrame = 0L

    private fun applyLightOverlay(c: Canvas, unit: Float, floorY: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val dt = if (lastLightFrame == 0L) 0f else ((now - lastLightFrame) / 1000f).coerceIn(0f, 1f)
        lastLightFrame = now
        val on = lightNow()
        val curtainsBlockLight = th("room_curtain", -1) >= 0 && th("curtain_closed", 0) == 1
        val daylight = if (curtainsBlockLight) 0f else WindowScene.daylight(System.currentTimeMillis())
        val target = if (on) 0f else daylight
        windowGlow += (target - windowGlow) * (1f - kotlin.math.exp(-dt * 1.4f))
        SceneLighting.draw(c, w.toFloat(), h.toFloat(), floorY,
            26f * density, on, windowGlow.coerceAtMost(daylight), w / 2f)
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

        val reach = (t.toFloat() / 700f).coerceIn(0f, 1f)
        val release = if (t >= dur - PetSim.RELEASE_MS)
            smooth(((dur - t).toFloat() / PetSim.RELEASE_MS).coerceIn(0f, 1f)) else 1f
        val itemRelease = if (sim.reaction == ItemDefs.Reaction.EAT) 1f else release
        info.prog = (smooth(reach) * itemRelease).coerceIn(0f, 1f)
        info.armReach = (smooth((t.toFloat() / 380f).coerceIn(0f, 1f)) * release).coerceIn(0f, 1f)

        info.fromX = itemX(ti.x)
        // ТОЧКА ХВАТА: всегда прямо перед питомцем — предмет "приклеен" к рукам
        val cx = itemX(sim.x)
        info.holdX = cx + sim.dir * r * when (ti.def.id) {
            "plushie" -> 0.82f
            "flower" -> 0.92f
            "pizza", "sushi", "donut", "cookie", "coffee", "apple", "icecream" -> 0.90f
            else -> 1.08f
        }

        when (sim.reaction) {
            ItemDefs.Reaction.SMELL -> { info.holding = true; info.liftPx = -r * 1.28f }
            ItemDefs.Reaction.LIFT  -> {
                info.holding = true; info.liftPx = -r * 1.12f
                // прогресс «в руках» — по нему живут игрушечные трюки
                info.eatProgress = ((t - 600f) / (dur - 1000f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            ItemDefs.Reaction.EAT   -> {
                info.holding = true
                info.liftPx = -r * 1.20f
                info.eatFade = ((t - (dur - 400f)).toFloat() / 400f).coerceIn(0f, 1f)
                // прогресс поедания от захвата до конца-минус-финал
                info.eatProgress = ((t - 600f) / (dur - 1000f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            ItemDefs.Reaction.OPEN  -> {
                info.lidProg = ((t - (dur - 800f)).toFloat() / 800f).coerceIn(0f, 1f)
            }
            ItemDefs.Reaction.RUB   -> {
                // гладим мишку В ЛАПКАХ у морды (раньше предмет оставался на полу
                // и руки кружили в воздухе — рассинхрон)
                info.holding = true
                info.liftPx = -r * 1.00f
            }
            else -> {} // PUSH/PLAY: предмет на полу, руки сами
        }
        // Единая дуга предмета и ладоней: захват начинается на полу.
        val u = r / 1.55f
        val active = PetMotion.envelope((t.toFloat() / dur.coerceAtLeast(1)).coerceIn(0f, 1f))
        info.holdX = lerp(info.fromX, info.holdX, info.prog)
        info.holdYExtra = -sin(info.prog * PI.toFloat()) * u * 0.35f
        info.holdYExtra += HeldMotion.pose(ti.def.id, t / 1000f, info.eatProgress, active).lift * u
        return info
    }

    // ------------------------------------------------------ предмет на полу

    private fun drawFloorItem(
        c: Canvas, sim: PetSim, item: PetSim.SpawnedItem, inter: InteractInfo,
        ix: Float, floorY: Float, unit: Float
    ) {
        val def = item.def
        if (item.ageMs < 0) {
            val pose = SpawnMotion.pose(item.ageMs, item.defId.hashCode())
            val proximity = (1f - (-pose.y / 10.5f)).coerceIn(0f, 1f)
            pF.color = 0xFF000000.toInt()
            pF.alpha = (proximity * 40f).toInt()
            val shadowW = def.w * unit * (0.24f + proximity * 0.18f)
            rect.set(ix - shadowW, floorY - unit * 0.10f, ix + shadowW, floorY + unit * 0.10f)
            c.drawOval(rect, pF)
            pF.alpha = 255
            if (pose.impact > 0.02f) spawnDust(c, ix, floorY, unit * 0.72f, 1f - pose.impact)
            c.save()
            // Локальная точка (0,0) — контакт с полом. Масштабирование вокруг неё
            // физически не может протолкнуть предмет ниже floorY.
            c.translate(ix, floorY + pose.y * unit)
            c.rotate(pose.rotation, 0f, -unit)
            c.scale(pose.scaleX, pose.scaleY, 0f, 0f)
            def.draw(c, 0f, 0f, unit, sim.animPhase)
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
        val interactionPhase = if (isTarget)
            (sim.stateTime.toFloat() / sim.stateDur.coerceAtLeast(1)).coerceIn(0f, 1f) else 0f
        val floorPose = if (isTarget) InteractionMotion.floorPose(def.id, interactionPhase, sim.dir)
            else InteractionMotion.FloorPose()
        val renderX = ix + floorPose.dx * unit
        val renderY = floorY + floorPose.lift * unit
        var toyRot = 0f
        var toyScale = 1f
        val rolling = abs(item.vx) > 0.02f
        when (def.id) {
            "ball" -> if (rolling) toyRot = sim.animPhase * 240f * (if (item.vx > 0) 1f else -1f)
            "block" -> if (rolling) toyRot = sin(sim.animPhase * 8f) * 8f
            "top" -> if (rolling) toyRot = sim.animPhase * 260f * (if (item.vx > 0) 1f else -1f)
            "plushie" -> if (rolling) toyRot = sin(sim.animPhase * 8f) * 6f
            "flower" -> if (rolling) toyRot = sin(sim.animPhase * 8f) * 6f
        }

        c.save()
        c.scale(sc * toyScale * floorPose.scaleX, sc * toyScale * floorPose.scaleY, renderX, renderY)
        c.rotate(toyRot + floorPose.rotation, renderX, renderY - unit * 0.9f)
        pF.alpha = alpha; pS.alpha = alpha; pZ.alpha = alpha
        def.draw(c, renderX, renderY, unit, sim.animPhase)
        c.restore()

        // ---- спецэффекты поверх игрушек
        if (def.id == "robot" && isTarget && sim.reaction == ItemDefs.Reaction.PLAY) {
            val blink = if ((sim.stateTime / 260L) % 2L == 0L) 220 else 50
            drawSpark(c, renderX, renderY - unit * 3.5f, unit * 0.30f,
                0xFFFFD54D.toInt(), blink)
        }
        // (мишка и цветок теперь в лапках — их эффекты переехали в drawHeldItem)
        pF.alpha = 255; pS.alpha = 255; pZ.alpha = 255
    }

    /** искра-блеск: 4 лучика */
    private fun drawSpark(c: Canvas, x: Float, y: Float, s: Float, col: Int, alpha: Int) {
        pS.color = col
        pS.alpha = alpha.coerceIn(0, 255)
        pS.strokeWidth = s * 0.22f
        c.drawLine(x - s, y, x + s, y, pS)
        c.drawLine(x, y - s, x, y + s, pS)
        val d = s * 0.55f
        c.drawLine(x - d, y - d, x + d, y + d, pS)
        c.drawLine(x - d, y + d, x + d, y - d, pS)
        pS.alpha = 255
    }

    /** мини-сердечко (для мишки и компании) */
    private fun drawMiniHeart(c: Canvas, cx: Float, cy: Float, s: Float, col: Int, alpha: Int) {
        path.reset()
        path.moveTo(cx, cy + s * 0.85f)
        path.cubicTo(cx - s * 0.4f, cy + s * 0.45f, cx - s, cy, cx - s * 0.82f, cy - s * 0.46f)
        path.cubicTo(cx - s * 0.64f, cy - s * 0.95f, cx - s * 0.15f, cy - s * 0.86f, cx, cy - s * 0.4f)
        path.cubicTo(cx + s * 0.15f, cy - s * 0.86f, cx + s * 0.64f, cy - s * 0.95f, cx + s * 0.82f, cy - s * 0.46f)
        path.cubicTo(cx + s, cy, cx + s * 0.4f, cy + s * 0.45f, cx, cy + s * 0.85f)
        path.close()
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
        val bedLift = if (th("room_bed", 0) == 1) 0.02f else 0.62f
        var cy = floorY - r * 0.92f - if (onBed) unit * bedLift else 0f
        if (sim.state != PetSim.State.WALK) cy -= PetMotion.pose(sim).lift * r
        if (sim.state == PetSim.State.WALK) {
            cy -= abs(sin(sim.animPhase * 5.5f)) * r * 0.14f
        }
        if (sim.state == PetSim.State.PULL && th("room_lamp", 0) == 0) {
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
        val k = (1f - kotlin.math.exp(-12f * dtAnim)).coerceIn(0f, 1f)


        // тень (в кресле чуть меньше)
        pF.color = if (onBed) 0x0A000000 else 0x0F000000
        val shadowR = if (onBed) 1.15f else 1.45f
        rect.set(cx - r * shadowR, floorY - unit * 0.30f, cx + r * shadowR, floorY + unit * 0.18f)
        c.drawOval(rect, pF)
        pF.color = if (onBed) 0x0E000000 else 0x17000000
        rect.set(cx - r * (if (onBed) 0.85f else 1.0f), floorY - unit * 0.20f, cx + r * (if (onBed) 0.85f else 1.0f), floorY + unit * 0.12f)
        c.drawOval(rect, pF)

        val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
        if (st == PetSim.State.WALK) {
            sx = 1f + 0.05f * sin(sim.animPhase * 11f)
            sy = 1f - 0.035f * sin(sim.animPhase * 11f)
        } else {
            val pose = PetMotion.pose(sim)
            sx = pose.sx
            sy = pose.sy
            rot = pose.angle
            dx = pose.offset * unit
            if (st == PetSim.State.PULL && th("room_lamp", 0) != 0) {
                sx = 1f; sy = 1f
                rot = buttonPress(sim) * 0.035f
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
        lastPose[0] = sx; lastPose[1] = sy; lastPose[2] = rot; lastPose[3] = dx
        lastPosePhase = sim.animPhase
        // pet_pose: в кадре пружинная поза живёт в lastPose (память); на диск — раз в
        // секунду, чтобы «ходы» после смерти процесса восстановили БЛИЗКУЮ позу
        // (дальше пружина дотянет) — было 20 записей prefs в секунду
        if (persistPose && sim.animPhase - lastPoseWrite >= 1f) {
            lastPoseWrite = sim.animPhase
            try {
                lastPrefs?.edit()?.putString(
                    "pet_pose", "${sx},${sy},${rot},${dx},${sim.animPhase}"
                )?.apply()
            } catch (_: Throwable) {}
        }

        val px = cx + dx
        c.save()
        c.rotate(rot * 57.2958f, px, cy)

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
            "leaf" -> {
                val antTopX = px + r * 0.08f
                val antTopY = cy - r * sy - unit * 0.58f
                pS.color = 0xFF4E8F6E.toInt(); pS.strokeWidth = unit * 0.09f
                c.drawLine(px + r * 0.02f, cy - r * sy * 0.92f, antTopX, antTopY, pS)
                pF.color = th("look_ante", 0xFF7FC9A6.toInt())
                rect.set(antTopX - unit * 0.34f, antTopY - unit * 0.13f, antTopX, antTopY + unit * 0.16f)
                c.drawOval(rect, pF)
                rect.set(antTopX, antTopY - unit * 0.28f, antTopX + unit * 0.34f, antTopY + unit * 0.04f)
                c.drawOval(rect, pF)
            }
            "star" -> {
                val antTopX = px + r * 0.08f
                val antTopY = cy - r * sy - unit * 0.62f + sin(sim.animPhase * 2f) * unit * 0.05f
                pS.color = 0xFF4E8F6E.toInt(); pS.strokeWidth = unit * 0.09f
                c.drawLine(px + r * 0.02f, cy - r * sy * 0.92f, antTopX, antTopY, pS)
                drawSpark(c, antTopX, antTopY, unit * 0.27f, th("look_ante", 0xFFF4C95D.toInt()), 255)
            }
            else -> { /* без антенны */ }
        }

        // тело: палитра из темы (двухцветный градиент топ→низ, серединка выводится автоматически)
        val body = gradientPaint("body").apply {
            shader = LinearGradient(
                px - r * 0.4f, cy - r * sy, px + r * 0.2f, cy + r * sy,
                intArrayOf(th("pet_top", 0xFFA8E6C3.toInt()), th("pet_mid", 0xFF6FBE97.toInt()), th("pet_bottom", 0xFF4FA379.toInt())),
                null, Shader.TileMode.CLAMP
            )
        }
        rect.set(px - r * sx, cy - r * sy, px + r * sx, cy + r * sy)
        c.drawOval(rect, body)
        pS.color = th("pet_outline", 0xFF4A4038.toInt())
        pS.strokeWidth = unit * 0.12f
        c.drawOval(rect, pS)

        // обводка-кромка слоя света (rim-light)
        pS.color = 0x20FFFFFF.toInt()
        pS.strokeWidth = unit * 0.07f
        rect.set(px - r * sx - unit * 0.07f, cy - r * sy - unit * 0.07f, px + r * sx + unit * 0.07f, cy + r * sy + unit * 0.07f)
        c.drawArc(rect, 205f, 115f, false, pS)

        // «пятки-няшки» убраны по решению: чистый силуэт выглядит круче


        // двойной блик (стеклянность)
        pF.color = 0x32FFFFFF.toInt()
        rect.set(px - r * 0.66f * sx, cy - r * sy * 0.98f, px + r * 0.10f * sx, cy - r * sy * 0.42f)
        c.drawOval(rect, pF)
        pF.color = 0x20FFFFFF.toInt()
        rect.set(px + r * 0.16f * sx, cy - r * sy * 0.80f, px + r * 0.42f * sx, cy - r * sy * 0.52f)
        c.drawOval(rect, pF)

        // животик
        pF.color = th("pet_belly", 0xFFBDEBCF.toInt())
        rect.set(px - r * 0.55f * sx, cy + r * sy * 0.02f, px + r * 0.55f * sx, cy + r * sy * 0.84f)
        c.drawOval(rect, pF)

        // лицо
        val eyeY = cy - r * sy * 0.30f
        val eyeDx = r * 0.42f * sx
        val sleeping = st == PetSim.State.SLEEP
        val stretching = st == PetSim.State.STRETCH
        val waving = st == PetSim.State.WAVE
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
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.20f
                rect.set(px - eyeDx - r * 0.18f, eyeY - r * 0.10f, px - eyeDx + r * 0.18f, eyeY + r * 0.10f)
                c.drawArc(rect, 20f, 140f, false, pS)
                rect.set(px + eyeDx - r * 0.18f, eyeY - r * 0.10f, px + eyeDx + r * 0.18f, eyeY + r * 0.10f)
                c.drawArc(rect, 20f, 140f, false, pS)
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.11f
                val sleepMouthY = eyeY + r * sy * 0.36f
                rect.set(px - r * 0.1f, sleepMouthY - r * 0.05f, px + r * 0.1f, sleepMouthY + r * 0.05f)
                c.drawArc(rect, 20f, 140f, false, pS)
                for (i in 0..1) {
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
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.21f
                rect.set(px - eyeDx - r * 0.18f, eyeY - r * 0.14f, px - eyeDx + r * 0.18f, eyeY + r * 0.07f)
                c.drawArc(rect, 200f, 140f, false, pS)
                rect.set(px + eyeDx - r * 0.18f, eyeY - r * 0.14f, px + eyeDx + r * 0.18f, eyeY + r * 0.07f)
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
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.19f
                c.drawLine(px - eyeDx - r * 0.14f, eyeY + r * 0.02f, px - eyeDx + r * 0.14f, eyeY + r * 0.02f, pS)
                c.drawLine(px + eyeDx - r * 0.14f, eyeY + r * 0.02f, px + eyeDx + r * 0.14f, eyeY + r * 0.02f, pS)
                pF.color = 0xFF2E3B34.toInt()
                pF.alpha = 150
                c.drawCircle(px - eyeDx - sim.dir * r * 0.06f, eyeY + r * 0.02f, r * 0.085f, pF)
                c.drawCircle(px + eyeDx - sim.dir * r * 0.06f, eyeY + r * 0.02f, r * 0.085f, pF)
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
                        // Полуприкрытый глаз рисуется собственной формой без цветной маски.
                        for (ex in floatArrayOf(px - eyeDx, px + eyeDx)) {
                            path.reset(); path.moveTo(ex - er, eyeY)
                            path.lineTo(ex + er, eyeY)
                            path.cubicTo(ex + er, eyeY + er * 1.05f, ex - er, eyeY + er * 1.05f, ex - er, eyeY)
                            path.close(); pF.color = eyeCol; c.drawPath(path, pF)
                            circleAt(c, ex - er * 0.24f, eyeY + er * 0.27f, er * 0.18f, 0xFFFFFFFF.toInt())
                            pS.color = eyeCol; pS.strokeWidth = unit * 0.09f
                            c.drawLine(ex - er * 1.05f, eyeY, ex + er * 1.05f, eyeY, pS)
                        }
                    }
                    "round" -> {
                        // круглые: умиротворённые
                        circleAt(c, px - eyeDx, eyeY, er * 1.08f, eyeCol)
                        circleAt(c, px + eyeDx, eyeY, er * 1.08f, eyeCol)
                        circleAt(c, px - eyeDx + lookX * er, eyeY - er * 0.22f, er * 0.3f, 0xFFFFFFFF.toInt())
                        circleAt(c, px + eyeDx + lookX * er, eyeY - er * 0.22f, er * 0.3f, 0xFFFFFFFF.toInt())
                    }
                    "sparkle" -> {
                        circleAt(c, px - eyeDx, eyeY, er * 1.04f, eyeCol)
                        circleAt(c, px + eyeDx, eyeY, er * 1.04f, eyeCol)
                        drawSpark(c, px - eyeDx - er * 0.12f, eyeY - er * 0.18f, er * 0.48f, 0xFFFFFFFF.toInt(), 255)
                        drawSpark(c, px + eyeDx - er * 0.12f, eyeY - er * 0.18f, er * 0.48f, 0xFFFFFFFF.toInt(), 255)
                    }
                    "soft" -> {
                        pF.color = eyeCol
                        rect.set(px - eyeDx - er * 1.05f, eyeY - er * 0.68f, px - eyeDx + er * 1.05f, eyeY + er * 0.68f)
                        c.drawOval(rect, pF)
                        rect.set(px + eyeDx - er * 1.05f, eyeY - er * 0.68f, px + eyeDx + er * 1.05f, eyeY + er * 0.68f)
                        c.drawOval(rect, pF)
                        circleAt(c, px - eyeDx - er * 0.2f, eyeY - er * 0.2f, er * 0.26f, 0xFFFFFFFF.toInt())
                        circleAt(c, px + eyeDx - er * 0.2f, eyeY - er * 0.2f, er * 0.26f, 0xFFFFFFFF.toInt())
                    }
                    else -> {
                        circleAt(c, px - eyeDx, eyeY, er, eyeCol)
                        circleAt(c, px + eyeDx, eyeY, er, eyeCol)
                        circleAt(c, px - eyeDx + lookX * er, eyeY + lookY * er - er * 0.28f, er * 0.42f, 0xFFFFFFFF.toInt())
                        circleAt(c, px + eyeDx + lookX * er, eyeY + lookY * er - er * 0.25f, er * 0.40f, 0xFFFFFFFF.toInt())
                        circleAt(c, px - eyeDx + lookX * er * 0.6f, eyeY + er * 0.38f, er * 0.16f, 0x20FFFFFF.toInt())
                        circleAt(c, px + eyeDx + lookX * er * 0.6f, eyeY + er * 0.38f, er * 0.16f, 0x20FFFFFF.toInt())
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
        val grow = if (st == PetSim.State.BLUSH) 1.45f else 1f
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
            "freckles" -> {
                pF.color = blushCol
                for (i in -1..1) {
                    c.drawCircle(cheekLX + i * r * 0.11f, cheekY + abs(i) * r * 0.04f, r * 0.045f * grow, pF)
                    c.drawCircle(cheekRX + i * r * 0.11f, cheekY + abs(i) * r * 0.04f, r * 0.045f * grow, pF)
                }
            }
            "stars" -> {
                drawSpark(c, cheekLX, cheekY, r * 0.15f * grow, blushCol and 0x00FFFFFF, blushCol ushr 24)
                drawSpark(c, cheekRX, cheekY, r * 0.15f * grow, blushCol and 0x00FFFFFF, blushCol ushr 24)
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
                // Одна поднятая бровь располагается над глазом, не перекрывая его.
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.14f
                path.reset(); path.moveTo(px - eyeDx - r * 0.18f, eyeY - r * 0.37f)
                path.quadTo(px - eyeDx, eyeY - r * 0.56f, px - eyeDx + r * 0.18f, eyeY - r * 0.44f)
                c.drawPath(path, pS)
                pS.strokeWidth = unit * 0.16f
            }
            sim.hunger > 70f && !sleeping && !stretching && !waving -> {
                // голоден: бровки «домиком»
                pS.color = 0xFF2E3B34.toInt(); pS.strokeWidth = unit * 0.14f; pS.alpha = 215
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
                // блаженный «ооо»
                pF.color = 0xFF2E3B34.toInt()
                c.drawCircle(px, mouthY + r * 0.02f, r * 0.11f, pF)
            }
            st == PetSim.State.TICKLE -> {
                // хохочет от щекотки
                pF.color = 0xFF2E3B34.toInt()
                rect.set(px - r * 0.26f, mouthY - r * 0.24f, px + r * 0.26f, mouthY + r * 0.08f)
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
                if (sim.reaction == ItemDefs.Reaction.EAT) {
                    // жуёт: рот открывается-закрывается ~1.5 Гц (закон SPEC: не выше 2 Гц)
                    val chew = 0.55f + 0.45f * abs(sin(sim.stateTime / 170f))
                    c.drawCircle(px, mouthY + r * 0.02f, r * 0.13f * chew, pF)
                } else {
                    c.drawCircle(px, mouthY + r * 0.02f, r * 0.1f, pF)
                }
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
                    "tongue" -> {
                        pF.color = 0xFF2E3B34.toInt()
                        rect.set(px - r * 0.23f, mouthY - r * 0.2f, px + r * 0.23f, mouthY + r * 0.12f)
                        c.drawArc(rect, 0f, 180f, true, pF)
                        pF.color = 0xFFF08F9C.toInt()
                        rect.set(px - r * 0.1f, mouthY + r * 0.01f, px + r * 0.1f, mouthY + r * 0.17f)
                        c.drawOval(rect, pF)
                    }
                    "cat" -> {
                        rect.set(px - r * 0.24f, mouthY - r * 0.1f, px, mouthY + r * 0.12f)
                        c.drawArc(rect, 5f, 150f, false, pS)
                        rect.set(px, mouthY - r * 0.1f, px + r * 0.24f, mouthY + r * 0.12f)
                        c.drawArc(rect, 25f, 150f, false, pS)
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
                } else when (look("look_mouth", "wide")) {
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
                    "cat" -> {
                        rect.set(px - r * 0.22f, mouthY - r * 0.08f, px, mouthY + r * 0.1f)
                        c.drawArc(rect, 10f, 145f, false, pS)
                        rect.set(px, mouthY - r * 0.08f, px + r * 0.22f, mouthY + r * 0.1f)
                        c.drawArc(rect, 25f, 145f, false, pS)
                    }
                    "wave" -> {
                        path.reset(); path.moveTo(px - r * 0.22f, mouthY)
                        path.quadTo(px - r * 0.1f, mouthY + r * 0.1f, px, mouthY)
                        path.quadTo(px + r * 0.1f, mouthY - r * 0.1f, px + r * 0.22f, mouthY)
                        c.drawPath(path, pS)
                    }
                    else -> c.drawLine(px - r * 0.13f, mouthY, px + r * 0.13f, mouthY, pS)
                }
            }
        }

        c.restore()
    }

    // ------------------------------------------------- предмет "в руках"

    private fun drawHeldItem(c: Canvas, sim: PetSim, inter: InteractInfo, unit: Float, floorY: Float) {
        val ti = inter.target ?: return
        val prog = inter.prog
        val x = inter.holdX                                   // всегда у рук
        val y = floorY + inter.liftPx * prog + inter.holdYExtra

        // ---- Индивидуальность еды: масштаб/наклон/боб/крошки по типу
        // Жевание: боб ~1.7 Гц — предел глазомера при 20 FPS (выше уже дрожь),
        // усушка мягкая («откус» передаёт крошка + фейд, а не таяние)
        val ep = inter.eatProgress
        val held = HeldMotion.pose(ti.def.id, sim.stateTime / 1000f, ep,
            PetMotion.envelope((sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)))
        val sc = held.scale
        val tilt = held.tilt
        val yb = y // положение хвата общее для предмета и ладоней

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
            val fl = sin(sim.stateTime / 1000f * 10f) * 0.5f + 0.5f
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
        if (ti.def.id == "plushie" && sim.reaction == ItemDefs.Reaction.RUB) {
            // поглаживание: сердце-искра всплывает над мишкой в лапках
            val hp = (sin(sim.animPhase * 6f) * 0.5f + 0.5f)
            drawMiniHeart(c, x + unit * 1.0f, yb - unit * 1.6f, unit * 0.16f,
                0xFFF26D7E.toInt(), (hp * 200).toInt().coerceIn(0, 255))
        }
        if (ti.def.id == "flower" && sim.reaction == ItemDefs.Reaction.SMELL) {
            // нюхает цветок в лапках: лёгкая искорка у бутона
            drawSpark(c, x - unit * 0.4f, yb - unit * 2.2f, unit * 0.18f, 0xFF9FE8C4.toInt(),
                (abs(sin(sim.stateTime / 150f)) * 190f).toInt().coerceIn(0, 255))
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
        // Поза хвата уже вычислена в readInteract до отрисовки.
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
            pS.color = th("pet_outline", 0xFF4A4038.toInt())
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
                val reach = PetMotion.envelope((sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f))
                val sw = sin(sim.stateTime / 1000f * 7f) * unit * 0.35f * reach
                arm(
                    cx + r * 0.5f, cy - r * 0.35f,
                    cx + r * (0.55f + 0.55f * reach) + sw, cy - r * (0.15f + 1.45f * reach),
                    true
                )
            }

            PetSim.State.PULL -> {
                val (tipX, tipY) = pullCordPos(sim, unit, floorY)
                if (th("room_lamp", 0) == 0) {
                    arm(cx + r * 0.35f, cy - r * 0.30f, tipX, tipY, true)
                } else {
                    val motion = SwitchMotion.pose(sim.stateTime)
                    // Те же координаты клавиши, что в её отрисовке на стене.
                    val handX = lerp(cx + r * 0.55f, tipX, motion.reach)
                    val handY = lerp(cy + r * 0.16f,
                        tipY + unit * 0.06f * motion.press, motion.reach)
                    arm(cx + r * 0.48f, cy - r * 0.12f, handX, handY, true)
                }
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
                    // гладит мишку в лапках: руки кружат вокруг точки хвата
                    ItemDefs.Reaction.RUB -> {
                        val hy = floorY + inter.liftPx * inter.prog + inter.holdYExtra
                        val hug = PetMotion.envelope(
                            (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f))
                        val stroke = sin(sim.stateTime / 1000f * 4f) * unit * 0.10f * hug
                        arm(axL, ay, inter.holdX - unit * 0.48f, hy + unit * 0.25f + stroke, false)
                        arm(axR, ay, inter.holdX + unit * 0.48f, hy + unit * 0.25f - stroke, false)
                    }
                    ItemDefs.Reaction.PLAY -> {
                        val phase = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
                        val pose = InteractionMotion.floorPose(ti.def.id, phase, sim.dir)
                        val tx = itemX(ti.x) + pose.dx * unit
                        when (ti.def.id) {
                            "robot" -> {
                                // Одна лапа ставит робота, вторая нажимает кнопку на голове.
                                val press = PetMotion.envelope(phase)
                                if (phase < 0.44f) {
                                    arm(axL, ay, tx - unit * 0.55f, floorY - unit * 1.15f, false)
                                    arm(axR, ay, tx + unit * 0.12f,
                                        floorY - unit * (2.65f + 0.10f * press), true)
                                }
                            }
                            "top" -> {
                                // Короткий толчок по оси юлы, затем лапы отходят и наблюдают.
                                val release = smooth((phase / 0.28f).coerceIn(0f, 1f))
                                if (release < 0.98f) {
                                    arm(axR, ay, tx + sim.dir * unit * 0.18f,
                                        floorY - unit * 1.85f, true)
                                }
                            }
                            else -> {
                                arm(axL, ay, tx - unit * 0.35f, floorY - unit * 0.48f, false)
                                arm(axR, ay, tx + unit * 0.35f, floorY - unit * 0.48f, false)
                            }
                        }
                    }
                    // открывает крышку перед собой
                    ItemDefs.Reaction.OPEN -> {
                        val lift = inter.lidProg
                        arm(axL, ay, cx + sim.dir * r * 1.05f, floorY - unit * 1.55f - unit * 1.15f * lift, false)
                        arm(axR, ay, cx + sim.dir * r * 1.05f, floorY - unit * 1.55f - unit * 1.15f * lift, false)
                    }
                    ItemDefs.Reaction.PUSH -> {
                        val tx = itemX(ti.x)
                        arm(axL, ay, tx - unit * 0.35f, floorY - unit * 0.48f, false)
                        arm(axR, ay, tx + unit * 0.35f, floorY - unit * 0.48f, false)
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
        val style = th("room_bed", 0)
        if (style != 0) {
            furniture.bed(c, bx, floorY, unit, style, th("chair_back1", 0xFFAB7B44.toInt()), false)
            return
        }
        val outline = 0xFF4C3528.toInt()

        // широкая контактная тень
        pF.color = 0x1C000000
        rect.set(bx - unit * 2.05f, floorY - unit * 0.2f, bx + unit * 2.05f, floorY + unit * 0.16f)
        c.drawOval(rect, pF)

        // цельный силуэт современной wingback-спинки
        val back = gradientPaint("back").apply {
            shader = LinearGradient(bx - unit * 1.2f, floorY - unit * 3.25f, bx + unit * 1.1f, floorY - unit * 0.65f,
                th("chair_back1", 0xFFAB7B44.toInt()), th("chair_back2", 0xFF6D4523.toInt()), Shader.TileMode.CLAMP)
        }
        path.reset()
        path.moveTo(bx - unit * 1.08f, floorY - unit * 3.22f)
        path.quadTo(bx, floorY - unit * 3.5f, bx + unit * 1.08f, floorY - unit * 3.22f)
        path.quadTo(bx + unit * 1.46f, floorY - unit * 2.8f, bx + unit * 1.35f, floorY - unit * 1.0f)
        path.lineTo(bx - unit * 1.35f, floorY - unit * 1.0f)
        path.quadTo(bx - unit * 1.46f, floorY - unit * 2.8f, bx - unit * 1.08f, floorY - unit * 3.22f)
        path.close()
        c.drawPath(path, back)
        pS.color = outline; pS.strokeWidth = unit * 0.11f; c.drawPath(path, pS)

        // внутренняя мягкая панель и кожаный кант
        val panel = gradientPaint("panel").apply {
            shader = LinearGradient(0f, floorY - unit * 3.0f, 0f, floorY - unit * 1.0f,
                th("chair_back1", 0xFFAB7B44.toInt()), th("chair_back2", 0xFF6D4523.toInt()), Shader.TileMode.CLAMP)
        }
        rect.set(bx - unit * 0.93f, floorY - unit * 3.0f, bx + unit * 0.93f, floorY - unit * 1.08f)
        c.drawRoundRect(rect, unit * 0.48f, unit * 0.48f, panel)
        pS.color = 0x55F1D1A7; pS.strokeWidth = unit * 0.07f; c.drawRoundRect(rect, unit * 0.48f, unit * 0.48f, pS)

        // простёжка с утопленными пуговицами
        pS.color = 0x38513425; pS.strokeWidth = unit * 0.055f
        c.drawLine(bx - unit * 0.68f, floorY - unit * 2.55f, bx + unit * 0.68f, floorY - unit * 1.55f, pS)
        c.drawLine(bx + unit * 0.68f, floorY - unit * 2.55f, bx - unit * 0.68f, floorY - unit * 1.55f, pS)
        for ((dx, dy) in listOf(-0.5f to -2.35f, 0.5f to -2.35f, 0f to -1.95f, -0.5f to -1.55f, 0.5f to -1.55f)) {
            pF.color = 0x66402B20
            c.drawCircle(bx + unit * dx, floorY + unit * dy, unit * 0.09f, pF)
            pF.color = 0x55F4D2A5
            c.drawCircle(bx + unit * dx - unit * 0.025f, floorY + unit * dy - unit * 0.025f, unit * 0.03f, pF)
        }

        // глубокая отдельная подушка сиденья
        val seat = gradientPaint("seat").apply {
            shader = LinearGradient(0f, floorY - unit * 1.05f, 0f, floorY - unit * 0.08f,
                th("chair_seat1", 0xFFC08A57.toInt()), th("chair_seat2", 0xFF94613A.toInt()), Shader.TileMode.CLAMP)
        }
        rect.set(bx - unit * 1.28f, floorY - unit * 1.08f, bx + unit * 1.28f, floorY - unit * 0.12f)
        c.drawRoundRect(rect, unit * 0.36f, unit * 0.36f, seat)
        pS.color = outline; pS.strokeWidth = unit * 0.09f; c.drawRoundRect(rect, unit * 0.36f, unit * 0.36f, pS)
        pF.color = 0x32FFFFFF
        rect.set(bx - unit * 1.08f, floorY - unit * 1.0f, bx + unit * 0.62f, floorY - unit * 0.66f)
        c.drawOval(rect, pF)
    }

    private fun drawChairFront(c: Canvas, unit: Float, floorY: Float) {
        val bx = floorXfor(PetSim.BED_X, unit)
        val style = th("room_bed", 0)
        if (style != 0) {
            furniture.bed(c, bx, floorY, unit, style, th("chair_back1", 0xFFAB7B44.toInt()), true)
            return
        }

        val outline = 0xFF4C3528.toInt()
        val roll = gradientPaint("roll").apply {
            shader = LinearGradient(0f, floorY - unit * 2.0f, 0f, floorY,
                th("chair_roll1", 0xFFB98350.toInt()), th("chair_roll2", 0xFF8A5A2E.toInt()), Shader.TileMode.CLAMP)
        }
        // скруглённые подлокотники с мягкими передними роллами
        rect.set(bx - unit * 1.78f, floorY - unit * 1.82f, bx - unit * 1.02f, floorY - unit * 0.12f)
        c.drawRoundRect(rect, unit * 0.34f, unit * 0.34f, roll); pS.color = outline; pS.strokeWidth = unit * 0.09f; c.drawRoundRect(rect, unit * 0.34f, unit * 0.34f, pS)
        rect.set(bx + unit * 1.02f, floorY - unit * 1.82f, bx + unit * 1.78f, floorY - unit * 0.12f)
        c.drawRoundRect(rect, unit * 0.34f, unit * 0.34f, roll); c.drawRoundRect(rect, unit * 0.34f, unit * 0.34f, pS)
        pF.color = th("chair_seat1", 0xFFC08A57.toInt())
        pS.color = outline; pS.strokeWidth = unit * 0.09f
        rect.set(bx - unit * 1.72f, floorY - unit * 1.92f, bx - unit * 1.08f, floorY - unit * 1.55f); c.drawOval(rect, pF); c.drawOval(rect, pS)
        rect.set(bx + unit * 1.08f, floorY - unit * 1.92f, bx + unit * 1.72f, floorY - unit * 1.55f); c.drawOval(rect, pF); c.drawOval(rect, pS)
        pS.color = 0x66F2D0A4; pS.strokeWidth = unit * 0.055f
        c.drawLine(bx - unit * 1.43f, floorY - unit * 1.5f, bx - unit * 1.39f, floorY - unit * 0.32f, pS)
        c.drawLine(bx + unit * 1.39f, floorY - unit * 1.5f, bx + unit * 1.43f, floorY - unit * 0.32f, pS)

        // передняя царга и четыре скошенные деревянные ножки
        pF.color = th("chair_roll2", 0xFF8A5A2E.toInt())
        rect.set(bx - unit * 1.2f, floorY - unit * 0.42f, bx + unit * 1.2f, floorY - unit * 0.1f)
        c.drawRoundRect(rect, unit * 0.13f, unit * 0.13f, pF)
        pS.color = outline; pS.strokeWidth = unit * 0.08f
        c.drawRoundRect(rect, unit * 0.13f, unit * 0.13f, pS)
        pF.color = 0xFF493327.toInt()
        path.reset(); path.moveTo(bx - unit * 1.08f, floorY - unit * 0.16f); path.lineTo(bx - unit * 0.82f, floorY - unit * 0.16f)
        path.lineTo(bx - unit * 0.9f, floorY); path.lineTo(bx - unit * 1.14f, floorY); path.close(); c.drawPath(path, pF)
        path.reset(); path.moveTo(bx + unit * 0.82f, floorY - unit * 0.16f); path.lineTo(bx + unit * 1.08f, floorY - unit * 0.16f)
        path.lineTo(bx + unit * 1.14f, floorY); path.lineTo(bx + unit * 0.9f, floorY); path.close(); c.drawPath(path, pF)
    }

    /** мягкая подпись предмета при появлении: 250 мс въезд, 500 мс уход */
    private fun drawSpawnLabel(c: Canvas, name: String, x: Float, floorY: Float, unit: Float, age: Long) {
        val a = when {
            age < 250L -> age / 250f
            age > 1_200L -> (1_700f - age) / 500f
            else -> 1f
        }.coerceIn(0f, 1f)
        if (a <= 0.03f) return
        val pad = unit * 0.7f
        pZ.typeface = font ?: android.graphics.Typeface.DEFAULT
        pZ.textSize = unit * 0.62f
        val boxW = pZ.measureText(name) + unit * 0.9f
        val boxH = unit * 1.0f
        val cx = x.coerceIn(pad + boxW / 2f, w - pad - boxW / 2f)
        val cy = floorY - unit * 4.4f
        rect.set(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)
        pF.color = 0xFFFFFFFF.toInt()
        pF.alpha = (a * 205f).toInt()
        c.drawRoundRect(rect, boxH / 2f, boxH / 2f, pF)
        pZ.color = 0xFF3A3A36.toInt()
        pZ.alpha = (a * 255f).toInt()
        c.drawText(name, cx, cy + pZ.textSize * 0.36f, pZ)
        pF.alpha = 255
        pZ.alpha = 255
    }

    // Helpers -----------------------------------------------------------

    private fun floorXfor(xN: Float, unit: Float): Float {
        val pad = unit * 0.7f
        return pad + xN.coerceIn(0f, 1f) * (w - 2 * pad)
    }

    private fun smooth(p: Float): Float = PetMotion.ease(p)

    private fun circleAt(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        pF.color = col
        c.drawCircle(x, y, r, pF)
    }
}

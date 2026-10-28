package com.sting.petwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Художник сцены. Всё рисуется непрерывно: позы/углы/офсеты — функции
 * (state, stateTime, animPhase), поэтому переходы между действиями гладкие.
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
            try {
                bmp = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                return null
            }
            w = newW; h = newH
        }
        val c = Canvas(bmp!!)
        paintScene(c, sim)
        return bmp
    }

    // ---------------------------------------------------------------- сцена

    private fun paintScene(c: Canvas, sim: PetSim) {
        val unit = (w / 24f).coerceAtLeast(8f)
        val pad = unit * 0.7f
        val floorY = h - h * 0.115f
        val itemX: (Float) -> Float = { pad + it.coerceIn(0f, 1f) * (w - 2 * pad) }

        // фон с градиентом
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, pad, 0f, floorY,
                0xFFF2F2EF.toInt(), 0xFFE8E8E4.toInt(),
                Shader.TileMode.CLAMP
            )
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

        // стены: боковые затенения
        val wallL = Paint().apply {
            shader = LinearGradient(0f, 0f, pad * 2.4f, 0f, 0x1E555550, 0x00555550, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, pad * 2.4f, h.toFloat(), wallL)
        val wallR = Paint().apply {
            shader = LinearGradient(w.toFloat(), 0f, w - pad * 2.4f, 0f, 0x1E555550, 0x00555550, Shader.TileMode.CLAMP)
        }
        c.drawRect(w - pad * 2.4f, 0f, w.toFloat(), h.toFloat(), wallR)
        val ceil = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, pad * 1.6f, 0x16606058, 0x00606058, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), pad * 1.6f, ceil)

        // пол с градиентом + плинтус
        val floor = Paint().apply {
            shader = LinearGradient(0f, floorY, 0f, h.toFloat(), 0xFFE1E1DC.toInt(), 0xFFD3D3CE.toInt(), Shader.TileMode.CLAMP)
        }
        rect.set(pad, floorY, w - pad, h - pad)
        c.drawRect(rect, floor)
        pF.color = 0xFFDCDCD6.toInt()
        rect.set(pad, floorY - unit * 0.22f, w - pad, floorY)
        c.drawRect(rect, pF)
        pS.color = 0xFFB9B9B2.toInt()
        pS.strokeWidth = unit * 0.1f
        c.drawLine(pad + 1, floorY, w - pad - 1, floorY, pS)
        pS.color = 0x40FFFFFF
        c.drawLine(pad + 1, floorY + 1, w - pad - 1, floorY + 1, pS)

        // рамка поверх
        pS.color = 0x66C9C9C3.toInt()
        pS.strokeWidth = unit * 0.12f
        c.drawRect(pad, pad, w - pad, h - pad, pS)

        // ---- уютная лежанка (постоянный элемент комнаты)
        drawBed(c, unit, floorY)

        // ---- информация о взаимодействии (held item, руки, крышка)
        val inter = readInteract(sim, itemX, floorY, r = unit * 1.55f)

        // ---- предметы
        for (item in sim.items) {
            val isHeld = inter.holding && item === inter.target
            if (isHeld) continue // нарисуем в руках потом
            drawFloorItem(c, sim, item, itemX(item.x), floorY, unit, itemX)
        }

        // крышка подарка, если питомец её приоткрывает
        if (inter.lidProg > 0f && inter.target != null) {
            drawLidPop(c, inter, unit, floorY)
        }

        // питомец (тело+лицо), затем предмет в руках, поверх — ручки
        drawPet(c, sim, itemX(sim.x), floorY, unit, inter)

        if (inter.holding && inter.target != null && inter.holdProg > 0f) {
            drawHeldItem(c, sim, inter, unit, floorY)
        }
        drawArms(c, sim, inter, unit, floorY)

        // ---- конфетти из открытого подарка
        for (item in sim.items) {
            if (item.confetti && item.ageMs in 0L..420L) {
                drawConfetti(c, itemX(item.x), floorY, unit, item.ageMs / 420f)
            }
        }
    }

    // ------------------------------------------------------------ захват

    private class InteractInfo {
        var holding = false
        var target: PetSim.SpawnedItem? = null
        var holdProg = 0f        // 0..1: насколько ручки с предметом
        var holdDX = 0f          // сдвиг предмета в руках
        var holdDY = 0f
        var lidProg = 0f         // для подарка: насколько открыта крышка
        var armProg = 0f         // 0..1: вылезли ли руки
        var rubPhase = 0f
    }

    private fun readInteract(
        sim: PetSim, itemX: (Float) -> Float, floorY: Float, r: Float
    ): InteractInfo {
        val info = InteractInfo()
        if (sim.state != PetSim.State.INTERACT) return info
        val ti = sim.targetItem ?: return info
        if (!sim.items.contains(ti)) return info

        val t = sim.stateTime
        val dur = sim.stateDur
        info.target = ti
        info.rubPhase = sim.animPhase * 6f

        // прогресс вылезания рук: 0..1 первые 300мс, у финала убираем за 250мс
        val base = (t.toFloat() / PetSim.HOLD_AT).coerceIn(0f, 1f)
        val rel = if (t >= dur - PetSim.RELEASE_MS)
            ((dur - t).toFloat() / PetSim.RELEASE_MS).coerceIn(0f, 1f) else 1f
        info.armProg = (base * rel).coerceIn(0f, 1f)

        val needsHold = sim.reaction == ItemDefs.Reaction.SMELL ||
            sim.reaction == ItemDefs.Reaction.LIFT ||
            sim.reaction == ItemDefs.Reaction.RUB
        if (!needsHold) {
            if (sim.reaction == ItemDefs.Reaction.OPEN) {
                info.lidProg = ((t - (dur - 800f)).toFloat() / 800f).coerceIn(0f, 1f)
                info.holdProg = info.armProg
            }
            return info
        }

        info.holding = true
        info.holdProg = (info.armProg).coerceIn(0f, 1f)
        val ix = itemX(ti.x)
        info.holdDX = sim.dir * r * 0.62f
        info.holdDY = when (sim.reaction) {
            ItemDefs.Reaction.SMELL -> -r * 1.72f
            ItemDefs.Reaction.LIFT  -> -r * 1.05f
            else -> 0f
        }
        // мягкая дуга подъёма: ближе к grabbing предмет приподнимается плавно
        return info
    }

    // ------------------------------------------------------ предмет на полу

    private fun drawFloorItem(
        c: Canvas, sim: PetSim, item: PetSim.SpawnedItem,
        ix: Float, floorY: Float, unit: Float, itemX: (Float) -> Float
    ) {
        val def = item.def

        // ---- спавн: телеграф тени, падение с потолка, отскок, пыль
        val a = item.ageMs
        if (a < 0) {
            if (a < -1000) {
                // телеграф: тень на месте будущего падения
                val tp = ((a + 1500f) / 500f).coerceIn(0f, 1f)
                pF.color = 0xFF000000.toInt()
                pF.alpha = (tp * 46).toInt().coerceIn(0, 255)
                val rx = def.w * unit * 0.42f * (0.55f + 0.45f * tp)
                rect.set(ix - rx, floorY - unit * 0.18f, ix + rx, floorY + unit * 0.10f)
                c.drawOval(rect, pF)
                pF.alpha = 255
                return
            }
            // падение (0..0.62) и два отскока
            val p = ((a + 1000f) / 1000f).coerceIn(0f, 1f)
            val yOff: Float
            var squash = 1f
            when {
                p < 0.62f -> {
                    val q = p / 0.62f
                    yOff = -(1f - q * q) * unit * 9f
                }
                p < 0.82f -> {
                    val q = (p - 0.62f) / 0.20f
                    yOff = -sin(q * PI.toFloat()) * unit * 1.15f
                    if (q < 0.25f) squash = 1f - 0.3f * (1f - q * 4f)
                }
                else -> {
                    val q = (p - 0.82f) / 0.18f
                    yOff = -sin(q * PI.toFloat()) * unit * 0.35f
                    if (q < 0.25f) squash = 1f - 0.2f * (1f - q * 4f)
                }
            }
            // пыль при ударе
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

        // ---- обычное состояние: pop-in при выскакивании из подарка
        val pop = if (item.confetti) 1f else (a / 900f).coerceIn(0f, 1f)
        val sc = if (pop >= 1f) 1f else 0.55f + 0.45f * pop
        val fade = 1f - ((a - item.ttlMs + 1500f) / 1500f).coerceIn(0f, 1f)
        if (fade <= 0f) return
        val alpha = (fade * 255).toInt()
        c.save()
        c.scale(sc, sc, ix, floorY)
        pF.alpha = alpha; pS.alpha = alpha; pZ.alpha = alpha
        def.draw(c, ix, floorY, unit, sim.animPhase)
        c.restore()
        pF.alpha = 255; pS.alpha = 255; pZ.alpha = 255
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
            pF.color = when (i % 3) {
                0 -> 0xFFE05B5B.toInt()
                1 -> 0xFFF4C95D.toInt()
                else -> 0xFF7CC8A2.toInt()
            }
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

    // ------------------------------------------------------------ подарок

    private fun drawLidPop(c: Canvas, inter: InteractInfo, unit: Float, floorY: Float) {
        val ti = inter.target ?: return
        val p = inter.lidProg
        if (p <= 0f || p >= 1f) return
        val ix = floorXfor(ti.x, unit)
        val topY = floorY - unit * 1.05f - unit * 0.3f
        c.save()
        c.translate(ix, topY - p * unit * 2.4f)
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

    private fun drawPet(
        c: Canvas, sim: PetSim, cx: Float, floorY: Float, unit: Float, inter: InteractInfo
    ) {
        val r = unit * 1.55f
        val onBed = sim.state == PetSim.State.SLEEP && sim.sleepingInBed
        val bedLift = if (onBed) unit * 0.52f else 0f
        var cy = floorY - r * 0.92f - bedLift
        var dx = 0f
        var sx = 1f
        var sy = 1f
        var rot = 0f
        val st = sim.state

        // д soft тень (в лежанке — мягче и меньше)
        pF.color = if (onBed) 0x0A000000 else 0x0F000000
        rect.set(
            cx - r * (if (onBed) 1.15f else 1.45f), floorY - unit * 0.30f,
            cx + r * (if (onBed) 1.15f else 1.45f), floorY + unit * 0.18f
        )
        c.drawOval(rect, pF)
        pF.color = if (onBed) 0x0E000000 else 0x17000000
        rect.set(
            cx - r * (if (onBed) 0.85f else 1.0f), floorY - unit * 0.20f,
            cx + r * (if (onBed) 0.85f else 1.0f), floorY + unit * 0.12f
        )
        c.drawOval(rect, pF)

        val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
        when (st) {
            PetSim.State.HAPPY -> cy -= sin((p * PI).toFloat()) * (r * 1.5f)
            PetSim.State.WALK -> {
                cy -= abs(sin(sim.animPhase * 5.5f)) * r * 0.14f
                sx = 1f + 0.05f * sin(sim.animPhase * 11f)
                sy = 1f - 0.035f * sin(sim.animPhase * 11f)
            }
            PetSim.State.SLEEP -> {
                sx = 1f + 0.03f * sin(sim.animPhase * 1.2f)
                sy = 1f - 0.02f * sin(sim.animPhase * 1.2f)
            }
            PetSim.State.INTERACT -> sx = 1f + 0.05f * sin(sim.stateTime / 50f)
            // кувырок: полный оборот корпуса; плавные начала/концы
            PetSim.State.ROLL -> {
                val e = smooth(p)
                rot = e * 2f * PI.toFloat() * sim.dir
                cy -= sin((p * PI).toFloat()) * r * 0.3f
                sx = 1f + 0.12f * sin((p * PI).toFloat())
                sy = 1f - 0.10f * sin((p * PI).toFloat())
            }
            // покачивание маятником, возвращает в центр
            PetSim.State.SWAY -> {
                val osc = sin(p * PI.toFloat() * 3f) // 1.5 цикла, ends at 0
                rot = osc * 0.36f
                dx = osc * unit * 0.9f
            }
            PetSim.State.LOOK -> { /* глаза двигаются сами */ }
            // потягивание: тянется вверх и вниз по синусу
            PetSim.State.STRETCH -> {
                val s = sin((p * PI).toFloat())
                sy = 1f + 0.42f * s
                sx = 1f - 0.13f * s
            }
            else -> {
                sx = 1f + 0.02f * sin(sim.animPhase * 1.5f)
                sy = 1f - 0.015f * sin(sim.animPhase * 1.5f)
            }
        }

        val px = cx + dx
        c.save()
        c.rotate(rot * 57.2958f, px, cy)

        // ---- тело: градиент
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                px - r * 0.4f, cy - r * sy,
                px + r * 0.2f, cy + r * sy,
                intArrayOf(0xFF9ADDB8.toInt(), 0xFF7CC8A2.toInt(), 0xFF5FAE88.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        rect.set(px - r * sx, cy - r * sy, px + r * sx, cy + r * sy)
        c.drawOval(rect, body)
        pS.color = 0x6E3C7A63.toInt()
        pS.strokeWidth = unit * 0.11f
        c.drawOval(rect, pS)

        // блик
        pF.color = 0x52FFFFFF.toInt()
        rect.set(px - r * 0.62f * sx, cy - r * sy * 0.95f, px + r * 0.12f * sx, cy - r * sy * 0.40f)
        c.drawOval(rect, pF)

        // животик
        pF.color = 0xFFB4E3C9.toInt()
        rect.set(px - r * 0.55f * sx, cy + r * sy * 0.02f, px + r * 0.55f * sx, cy + r * sy * 0.84f)
        c.drawOval(rect, pF)

        // лицо
        val eyeY = cy - r * sy * 0.30f
        val eyeDx = r * 0.40f * sx
        val sleeping = st == PetSim.State.SLEEP
        val blinking = !sleeping && (sim.animPhase % 4.4f) < 0.14f
        val happy = st == PetSim.State.HAPPY || st == PetSim.State.INTERACT || st == PetSim.State.ROLL

        var lookX = sim.dir * 0.18f
        var lookY = 0f
        if (st == PetSim.State.LOOK) {
            val lp = sim.stateTime / 1000f
            lookX = sin(lp * PI * 1.4f).toFloat() * 0.25f
        } else {
            sim.targetItem?.let { ti ->
                val dxT = ti.x - sim.x
                val dyT = -(r * sy * 0.3f / r)
                val len = kotlin.math.sqrt(dxT * dxT + dyT * dyT).coerceAtLeast(0.001f)
                lookX = dxT / len * 0.2f
                lookY = dyT / len * 0.12f
            }
        }

        if (sleeping || st == PetSim.State.STRETCH) {
            pS.color = 0xFF2E3B34.toInt()
            pS.strokeWidth = unit * 0.16f
            if (sleeping) {
                rect.set(px - eyeDx - r * 0.18f, eyeY - r * 0.10f, px - eyeDx + r * 0.18f, eyeY + r * 0.10f)
                c.drawArc(rect, 20f, 140f, false, pS)
                rect.set(px + eyeDx - r * 0.18f, eyeY - r * 0.10f, px + eyeDx + r * 0.18f, eyeY + r * 0.10f)
                c.drawArc(rect, 20f, 140f, false, pS)
                pF.color = 0xFF2E3B34.toInt()
                c.drawCircle(px, eyeY + r * sy * 0.36f, unit * 0.11f, pF)
                for (i in 0..2) {
                    val t = sim.animPhase + i * 0.4f
                    val zy = cy - r * sy - unit * (0.6f + i * 0.55f + sin(t * 1.3f) * 0.2f)
                    val zx = px + r * 0.6f + i * unit * 0.55f
                    pZ.textSize = unit * (0.85f + i * 0.25f)
                    pZ.alpha = (230 - i * 70).coerceIn(0, 255)
                    c.drawText("Z", zx, zy, pZ)
                }
                pZ.alpha = 255
            } else {
                // потягивается: глаза-полумесяцы (счастливые дуги)
                rect.set(px - eyeDx - r * 0.16f, eyeY - r * 0.10f, px - eyeDx + r * 0.16f, eyeY + r * 0.10f)
                c.drawArc(rect, 20f, 140f, false, pS)
                rect.set(px + eyeDx - r * 0.16f, eyeY - r * 0.10f, px + eyeDx + r * 0.16f, eyeY + r * 0.10f)
                c.drawArc(rect, 20f, 140f, false, pS)
            }
        } else if (blinking && st != PetSim.State.ROLL) {
            pS.color = 0xFF2E3B34.toInt()
            pS.strokeWidth = unit * 0.16f
            c.drawLine(px - eyeDx - r * 0.15f, eyeY, px - eyeDx + r * 0.15f, eyeY, pS)
            c.drawLine(px + eyeDx - r * 0.15f, eyeY, px + eyeDx + r * 0.15f, eyeY, pS)
        } else {
            val er = r * 0.21f
            circleAt(c, px - eyeDx, eyeY, er, 0xFF2E3B34.toInt())
            circleAt(c, px + eyeDx, eyeY, er, 0xFF2E3B34.toInt())
            circleAt(c, px - eyeDx + lookX * er, eyeY + lookY * er - er * 0.25f, er * 0.40f, 0xFFFFFFFF.toInt())
            circleAt(c, px + eyeDx + lookX * er, eyeY + lookY * er - er * 0.25f, er * 0.40f, 0xFFFFFFFF.toInt())
            circleAt(c, px - eyeDx + lookX * er * 0.6f, eyeY + er * 0.35f, er * 0.16f, 0x30FFFFFF.toInt())
            circleAt(c, px + eyeDx + lookX * er * 0.6f, eyeY + er * 0.35f, er * 0.16f, 0x30FFFFFF.toInt())
        }

        // щёчки
        pF.color = 0x52F3A9A0.toInt()
        rect.set(px - eyeDx - r * 0.44f, eyeY + r * 0.12f, px - eyeDx - r * 0.15f, eyeY + r * 0.32f)
        c.drawOval(rect, pF)
        rect.set(px + eyeDx + r * 0.15f, eyeY + r * 0.12f, px + eyeDx + r * 0.44f, eyeY + r * 0.32f)
        c.drawOval(rect, pF)

        // рот
        val mouthY = eyeY + r * sy * 0.36f
        pS.strokeWidth = unit * 0.16f
        when {
            sleeping -> { /* нарисовано */ }
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
                pS.color = 0xFF2E3B34.toInt()
                rect.set(px - r * 0.28f, mouthY - r * 0.20f, px + r * 0.28f, mouthY + r * 0.08f)
                c.drawArc(rect, 15f, 150f, false, pS)
            }
            else -> {
                pS.color = 0xFF2E3B34.toInt()
                c.drawLine(px - r * 0.13f, mouthY, px + r * 0.13f, mouthY, pS)
            }
        }
        c.restore()
    }

    // --------------------------------------------------- предмет "в руках"

    private fun drawHeldItem(c: Canvas, sim: PetSim, inter: InteractInfo, unit: Float, floorY: Float) {
        val ti = inter.target ?: return
        val prog = inter.holdProg
        val ox = inter.holdDX * prog
        val oy = inter.holdDY * prog
        val ix0 = floorXfor(ti.x, unit)
        c.save()
        c.translate(ox, oy)
        c.translate(ix0, floorY)
        val wob = if (inter.holdDY != 0f) sin(sim.animPhase * 3f) * 4f else 0f
        c.rotate(wob, ix0, floorY)
        ti.def.draw(c, ix0, floorY, unit, sim.animPhase)
        c.restore()
    }

    // --------------------------------------------------------------- руки

    private fun drawArms(
        c: Canvas, sim: PetSim, inter: InteractInfo, unit: Float, floorY: Float
    ) {
        if (inter.armProg <= 0f) return
        val r = unit * 1.55f
        val cx = floorXfor(sim.x, unit)
        var cy = floorY - r * 0.92f
        if (sim.state == PetSim.State.HAPPY) {
            val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
            cy -= sin((p * PI).toFloat()) * (r * 1.5f)
        }
        val prog = inter.armProg

        data class Hand(val ax: Float, val ay: Float, val hx: Float, val hy: Float, val up: Boolean)
        val hands = ArrayList<Hand>()

        when (sim.state) {
            PetSim.State.STRETCH -> {
                val sp = sin(((sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)) * PI.toFloat())
                val up = sp * r * 1.35f
                hands.add(Hand(cx - r * 0.30f, cy - r * 0.85f, cx - r * 0.30f - r * 0.25f * sp, cy - r * 0.85f - up, true))
                hands.add(Hand(cx + r * 0.30f, cy - r * 0.85f, cx + r * 0.30f + r * 0.25f * sp, cy - r * 0.85f - up, true))
            }
            PetSim.State.INTERACT -> {
                val ti = inter.target ?: return
                val hix = floorXfor(ti.x, unit) + inter.holdDX * prog
                val hiy = floorY + inter.holdDY * prog
                val reach = prog // руки уже "длиной" согласно prog
                when (sim.reaction) {
                    ItemDefs.Reaction.EAT -> return // ест ртом, без рук
                    ItemDefs.Reaction.SMELL, ItemDefs.Reaction.LIFT -> {
                        hands.add(Hand(cx - r * 0.55f, cy + r * 0.20f, hix - unit * 0.30f, hiy - unit * 0.30f, false))
                        hands.add(Hand(cx + r * 0.55f, cy + r * 0.20f, hix + unit * 0.30f, hiy - unit * 0.30f, false))
                    }
                    ItemDefs.Reaction.RUB -> {
                        // руки кружат над предметом
                        val ax = hix
                        val ay = hiy - unit * 0.55f
                        val a1 = inter.rubPhase
                        val a2 = inter.rubPhase + PI.toFloat()
                        hands.add(Hand(cx - r * 0.5f, cy + r * 0.2f, ax + cos(a1) * unit * 0.38f, ay + sin(a1) * unit * 0.16f, false))
                        hands.add(Hand(cx + r * 0.5f, cy + r * 0.2f, ax + cos(a2) * unit * 0.38f, ay + sin(a2) * unit * 0.16f, false))
                    }
                    ItemDefs.Reaction.OPEN -> {
                        // руки тянутся к крышке и приподнимают её
                        val lift = inter.lidProg
                        hands.add(Hand(cx - r * 0.5f, cy + r * 0.2f, hix - unit * 0.5f, hiy - unit * 2.6f + unit * 1.4f * (1f - lift), false))
                        hands.add(Hand(cx + r * 0.5f, cy + r * 0.2f, hix + unit * 0.5f, hiy - unit * 2.6f + unit * 1.4f * (1f - lift), false))
                    }
                    else -> {
                        // толкнуть: обе ладони упираются в предмет
                        hands.add(Hand(cx - r * 0.5f, cy + r * 0.2f, hix - unit * 0.25f, hiy - unit * 0.35f, false))
                        hands.add(Hand(cx + r * 0.5f, cy + r * 0.2f, hix + unit * 0.25f, hiy - unit * 0.35f, false))
                    }
                }
            }
            else -> return
        }

        pS.strokeWidth = unit * 0.20f
        pS.color = 0xFF5CA985.toInt()
        for (hd in hands) {
            // позиция ладони умножается на prog вылезания (руки растут из тела)
            val hx = hd.ax + (hd.hx - hd.ax) * prog
            val hy = hd.ay + (hd.hy - hd.ay) * prog
            val mx = (hd.ax + hx) / 2f
            val my = (hd.ay + hy) / 2f + (if (hd.up) -unit * 0.30f else unit * 0.45f)
            path.reset()
            path.moveTo(hd.ax, hd.ay)
            path.quadTo(mx, my, hx, hy)
            c.drawPath(path, pS)
            // ладошка
            circleAt(c, hx, hy, unit * 0.20f, 0xFF9ADDB8.toInt())
            pS.color = 0x6E3C7A63.toInt()
            pS.strokeWidth = unit * 0.08f
            c.drawCircle(hx, hy, unit * 0.20f, pS)
            pS.strokeWidth = unit * 0.20f
            pS.color = 0xFF5CA985.toInt()
        }
    }

    // Helpers -----------------------------------------------------------

    /** Экранная X по нормализованной позиции на полу */
    private fun floorXfor(xN: Float, unit: Float): Float {
        val pad = unit * 0.7f
        return pad + xN.coerceIn(0f, 1f) * (w - 2 * pad)
    }

    // ------------------------------------------------------------- лежанка

    private fun drawBed(c: Canvas, unit: Float, floorY: Float) {
        val bx = floorXfor(PetSim.BED_X, unit)

        // мягкая тень под лежанкой
        pF.color = 0x12000000
        rect.set(bx - unit * 2.0f, floorY - unit * 0.16f, bx + unit * 2.0f, floorY + unit * 0.14f)
        c.drawOval(rect, pF)

        // наружный бортик (тёплый терракотовый)
        pF.color = 0xFFD9A06B.toInt()
        rect.set(bx - unit * 1.78f, floorY - unit * 0.95f, bx + unit * 1.78f, floorY + unit * 0.12f)
        c.drawOval(rect, pF)
        // блик на бортике сверху
        pF.color = 0x36FFFFFF.toInt()
        rect.set(bx - unit * 1.6f, floorY - unit * 0.95f, bx + unit * 1.6f, floorY - unit * 0.62f)
        c.drawOval(rect, pF)
        // внутренняя часть (светлая мягкая подушка)
        pF.color = 0xFFF3DFC6.toInt()
        rect.set(bx - unit * 1.32f, floorY - unit * 0.78f, bx + unit * 1.32f, floorY + unit * 0.02f)
        c.drawOval(rect, pF)
        // внутренняя мягкая тень для объёма
        pF.color = 0x22A58A5B.toInt()
        rect.set(bx - unit * 1.32f, floorY - unit * 0.30f, bx + unit * 1.32f, floorY + unit * 0.02f)
        c.drawOval(rect, pF)

        // стёжки на бортике (пунктирные дуги)
        pS.color = 0x8EB7743F.toInt()
        pS.strokeWidth = unit * 0.07f
        val gap = unit * 0.62f
        var i = -4
        while (i <= 4) {
            val sx = bx + i * gap
            c.drawLine(sx, floorY - unit * 0.20f, sx + unit * 0.26f, floorY - unit * 0.20f, pS)
            i++
        }
        pS.strokeWidth = unit * 0.12f
    }

    private fun smooth(p: Float): Float = p * p * (3f - 2f * p)

    private fun circleAt(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        pF.color = col
        c.drawCircle(x, y, r, pF)
    }
}

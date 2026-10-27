package com.sting.petwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Художник сцены. Рисует «уютную комнатку»: мягкий градиентный фон,
 * стены/пол/потолок = границы виджета, тени под предметами,
 *-floating пылинки, время суток, и объёмного "желейного" питомца.
 */
class SceneRenderer {

    private var bmp: Bitmap? = null
    private var w = 0
    private var h = 0

    private val pF = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pS = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pZ = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF75808B.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

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

    // ------------------------------------------------------------ сцена

    private fun paintScene(c: Canvas, sim: PetSim) {
        val unit = (w / 24f).coerceAtLeast(8f)
        val pad = unit * 0.7f
        val floorY = h - h * 0.115f
        val itemX: (Float) -> Float = { pad + it.coerceIn(0f, 1f) * (w - 2 * pad) }

        // ---- фон: небо комнаты с вертикальным градиентом
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, pad, 0f, floorY,
                0xFFF2F2EF.toInt(), 0xFFE8E8E4.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), floorY + 1f, bg)
        c.drawRect(0f, floorY, w.toFloat(), h.toFloat(), bg)

        // ---- время суток: лёгкая тонировка (не мешает читаемости)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour >= 22 || hour <= 5 -> c.drawColor(0x242E3A55)   // глубокая ночь: холоднее
            hour >= 20 -> c.drawColor(0x18FFBE86)               // вечер: тёплый персик
            hour <= 8  -> c.drawColor(0x12FFE9C4)               // утро: нежный крем
            else -> {}
        }
        val wall = Paint().apply {
            shader = LinearGradient(
                0f, 0f, pad * 2.4f, 0f,
                0x1E555550, 0x00555550,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, pad * 2.4f, h.toFloat(), wall)
        val wallR = Paint().apply {
            shader = LinearGradient(
                w.toFloat(), 0f, w - pad * 2.4f, 0f,
                0x1E555550, 0x00555550,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(w - pad * 2.4f, 0f, w.toFloat(), h.toFloat(), wallR)

        // ---- потолок: едва заметная полоска
        val ceil = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, pad * 1.6f,
                0x16606058, 0x00606058,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), pad * 1.6f, ceil)

        // ---- пол: градиент + плинтус + светлая кромка
        val floor = Paint().apply {
            shader = LinearGradient(
                0f, floorY, 0f, h.toFloat(),
                0xFFE1E1DC.toInt(), 0xFFD3D3CE.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        rect.set(pad, floorY, w - pad, h - pad)
        c.drawRect(rect, floor)
        // плинтус
        pF.color = 0xFFDCDCD6.toInt()
        rect.set(pad, floorY - unit * 0.22f, w - pad, floorY)
        c.drawRect(rect, pF)
        // кромка пола
        pS.color = 0xFFB9B9B2.toInt()
        pS.strokeWidth = unit * 0.1f
        c.drawLine(pad + 1, floorY, w - pad - 1, floorY, pS)
        pS.color = 0x40FFFFFF
        c.drawLine(pad + 1, floorY + 1, w - pad - 1, floorY + 1, pS)

        // ---- тонкая рамка-граница (стены комнаты) — поверх всего фона
        pS.color = 0x66C9C9C3.toInt()
        pS.strokeWidth = unit * 0.12f
        c.drawRect(pad, pad, w - pad, h - pad, pS)

        // ---- плавающие пылинки-блёстки (детерминированные по фазе)
        for (i in 0..5) {
            val t = sim.animPhase * (0.35f + i * 0.07f) + i * 1.93f
            val px = itemX((i * 0.19f + 0.06f + sin(t) * 0.02f).coerceIn(0f, 1f))
            val py = (0.12f + (i % 3) * 0.14f +
                sin(t * 0.8f + i) * 0.05f) * floorY
            val pr = unit * (0.05f + 0.03f * ((i + 1) % 3))
            pF.color = if (i % 2 == 0) 0x40FFFFFF.toInt() else 0x2A9FE8C4.toInt()
            c.drawCircle(px, py, pr, pF)
        }

        // ---- предметы: сначала тень, потом сам предмет
        for (item in sim.items) {
            val def = item.def
            val pop = (item.ageMs / 900f).coerceIn(0f, 1f)
            val sc = if (pop >= 1f) 1f else 0.55f + 0.45f * pop
            val fade = 1f - ((item.ageMs - item.ttlMs + 1500f) / 1500f).coerceIn(0f, 1f)
            if (fade <= 0f) continue
            val alpha = (fade * 255).toInt()
            val ix = itemX(item.x)

            // мягкая тень под предметом
            pF.color = 0xFF000000.toInt()
            pF.alpha = (26f * fade).toInt().coerceIn(0, 255)
            rect.set(
                ix - def.w * unit * 0.45f * sc, floorY - unit * 0.16f * sc,
                ix + def.w * unit * 0.45f * sc, floorY + unit * 0.10f * sc
            )
            c.drawOval(rect, pF)
            pF.alpha = alpha

            c.save()
            c.scale(sc, sc, ix, floorY)
            pF.alpha = alpha; pS.alpha = alpha; pZ.alpha = alpha
            def.draw(c, ix, floorY, unit, sim.animPhase)
            c.restore()
            pF.alpha = 255; pS.alpha = 255; pZ.alpha = 255
        }

        // ---- питомец
        drawPet(c, sim, itemX(sim.x), floorY, unit)
    }

    // ----------------------------------------------------------- питомец

    private fun drawPet(c: Canvas, sim: PetSim, cx: Float, floorY: Float, unit: Float) {
        val r = unit * 1.55f
        var cy = floorY - r * 0.92f
        val st = sim.state

        // двойная мягкая тень под питомцем
        pF.color = 0x0F000000
        rect.set(cx - r * 1.45f, floorY - unit * 0.30f, cx + r * 1.45f, floorY + unit * 0.18f)
        c.drawOval(rect, pF)
        pF.color = 0x17000000
        rect.set(cx - r * 1.0f, floorY - unit * 0.20f, cx + r * 1.0f, floorY + unit * 0.12f)
        c.drawOval(rect, pF)

        var sx = 1f
        val walking = st == PetSim.State.WALK
        when {
            st == PetSim.State.HAPPY -> {
                val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
                cy -= sin((p * PI).toFloat()) * (r * 1.5f)
            }
            walking -> {
                cy -= abs(sin(sim.animPhase * 5.5f)) * r * 0.14f
                sx = 1f + 0.05f * sin(sim.animPhase * 11f)
            }
            st == PetSim.State.SLEEP -> {
                sx = 1f + 0.03f * sin(sim.animPhase * 1.2f)
            }
            st == PetSim.State.INTERACT -> {
                sx = 1f + 0.06f * sin(sim.stateTime / 45f)
            }
            else -> sx = 1f + 0.02f * sin(sim.animPhase * 1.5f)
        }
        val ry = r / sx

        // ---- тело: вертикальный градиент (свет сверху)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - r * sx * 0.4f, cy - ry,
                cx + r * sx * 0.2f, cy + ry,
                intArrayOf(0xFF9ADDB8.toInt(), 0xFF7CC8A2.toInt(), 0xFF5FAE88.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        rect.set(cx - r * sx, cy - ry, cx + r * sx, cy + ry)
        c.drawOval(rect, body)

        // ---- обводка тела (тонкий тёмно-мятный контур)
        pS.color = 0x6E3C7A63.toInt()
        pS.strokeWidth = unit * 0.11f
        c.drawOval(rect, pS)

        // ---- блик на макушке (объём)
        pF.color = 0x52FFFFFF.toInt()
        rect.set(
            cx - r * 0.62f * sx, cy - ry * 0.95f,
            cx + r * 0.12f * sx, cy - ry * 0.40f
        )
        c.drawOval(rect, pF)

        // ---- животик (светлый)
        pF.color = 0xFFB4E3C9.toInt()
        rect.set(cx - r * 0.55f * sx, cy + ry * 0.02f, cx + r * 0.55f * sx, cy + ry * 0.84f)
        c.drawOval(rect, pF)

        // ---- глазки со зрачками и блеском
        val eyeY = cy - ry * 0.30f
        val eyeDx = r * 0.40f
        val sleeping = st == PetSim.State.SLEEP
        val blinking = !sleeping && (sim.animPhase % 4.4f) < 0.14f
        val happy = st == PetSim.State.HAPPY || st == PetSim.State.INTERACT

        var lookX = sim.dir * 0.18f
        var lookY = 0f
        sim.targetItem?.let { ti ->
            val dx = ti.x - sim.x
            val dy = -(ry * 0.3f / r)
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            lookX = dx / len * 0.2f
            lookY = dy / len * 0.12f
        }

        if (sleeping) {
            pS.color = 0xFF2E3B34.toInt()
            pS.strokeWidth = unit * 0.16f
            // закрытые глаза дугами (мило)
            rect.set(cx - eyeDx - r * 0.18f, eyeY - r * 0.10f, cx - eyeDx + r * 0.18f, eyeY + r * 0.10f)
            c.drawArc(rect, 20f, 140f, false, pS)
            rect.set(cx + eyeDx - r * 0.18f, eyeY - r * 0.10f, cx + eyeDx + r * 0.18f, eyeY + r * 0.10f)
            c.drawArc(rect, 20f, 140f, false, pS)
            // сонный ротик
            pF.color = 0xFF2E3B34.toInt()
            c.drawCircle(cx, eyeY + ry * 0.36f, unit * 0.11f, pF)
            // Z z z
            for (i in 0..2) {
                val t = sim.animPhase + i * 0.4f
                val zy = cy - ry - unit * (0.6f + i * 0.55f + sin(t * 1.3f) * 0.2f)
                val zx = cx + r * 0.6f + i * unit * 0.55f
                pZ.textSize = unit * (0.85f + i * 0.25f)
                pZ.alpha = (230 - i * 70).coerceIn(0, 255)
                c.drawText("Z", zx, zy, pZ)
            }
            pZ.alpha = 255
        } else if (blinking) {
            pS.color = 0xFF2E3B34.toInt()
            pS.strokeWidth = unit * 0.16f
            c.drawLine(cx - eyeDx - r * 0.15f, eyeY, cx - eyeDx + r * 0.15f, eyeY, pS)
            c.drawLine(cx + eyeDx - r * 0.15f, eyeY, cx + eyeDx + r * 0.15f, eyeY, pS)
        } else {
            val er = r * 0.21f
            // подложки-градиенты не нужны: тёмные "ягодки" с мягким контуром
            circleAt(c, cx - eyeDx, eyeY, er, 0xFF2E3B34.toInt())
            circleAt(c, cx + eyeDx, eyeY, er, 0xFF2E3B34.toInt())
            circleAt(c, cx - eyeDx + lookX * er, eyeY + lookY * er - er * 0.25f, er * 0.40f, 0xFFFFFFFF.toInt())
            circleAt(c, cx + eyeDx + lookX * er, eyeY + lookY * er - er * 0.25f, er * 0.40f, 0xFFFFFFFF.toInt())
            circleAt(c, cx - eyeDx + lookX * er * 0.6f, eyeY + er * 0.35f, er * 0.16f, 0x30FFFFFF.toInt())
            circleAt(c, cx + eyeDx + lookX * er * 0.6f, eyeY + er * 0.35f, er * 0.16f, 0x30FFFFFF.toInt())
        }

        // ---- щёчки
        pF.color = 0x52F3A9A0.toInt()
        rect.set(cx - eyeDx - r * 0.44f, eyeY + r * 0.12f, cx - eyeDx - r * 0.15f, eyeY + r * 0.32f)
        c.drawOval(rect, pF)
        rect.set(cx + eyeDx + r * 0.15f, eyeY + r * 0.12f, cx + eyeDx + r * 0.44f, eyeY + r * 0.32f)
        c.drawOval(rect, pF)

        // ---- рот: спокойный / счастливый / есть / нюхает
        val mouthY = eyeY + ry * 0.36f
        pS.strokeWidth = unit * 0.16f
        when {
            sleeping -> { /* уже нарисован */ }
            happy && (sim.reaction == ItemDefs.Reaction.EAT || sim.reaction == ItemDefs.Reaction.SMELL) -> {
                // открытый рот: нюхает/ест
                pF.color = 0xFF2E3B34.toInt()
                c.drawCircle(cx, mouthY + r * 0.02f, r * 0.11f, pF)
                if (sim.reaction == ItemDefs.Reaction.SMELL) {
                    // ноздри-сmmm? нет — маленькие "пары"
                    pS.color = 0x50FFFFFF
                    pS.strokeWidth = unit * 0.07f
                    c.drawLine(cx - r * 0.06f, mouthY - r * 0.14f, cx - r * 0.06f, mouthY - r * 0.26f, pS)
                    c.drawLine(cx + r * 0.06f, mouthY - r * 0.14f, cx + r * 0.06f, mouthY - r * 0.26f, pS)
                }
            }
            happy -> {
                // широкая улыбка с язычком
                pS.color = 0xFF2E3B34.toInt()
                rect.set(cx - r * 0.28f, mouthY - r * 0.20f, cx + r * 0.28f, mouthY + r * 0.08f)
                c.drawArc(rect, 15f, 150f, false, pS)
            }
            else -> {
                // спокойная точка-улыбка
                pS.color = 0xFF2E3B34.toInt()
                c.drawLine(cx - r * 0.13f, mouthY, cx + r * 0.13f, mouthY, pS)
            }
        }
    }

    private fun circleAt(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        pF.color = col
        c.drawCircle(x, y, r, pF)
    }
}

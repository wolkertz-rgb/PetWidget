package com.sting.petwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Рисует комнату: светло-серый фон, границы = стены, предметы, питомца.
 * Всё рисуется в один переиспользуемый Bitmap.
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
        color = 0xFF6B7280.toInt()
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
        val pad = unit * 0.7f              // отступ "стен" от краёв виджета
        val floorY = h - h * 0.10f          // уровень пола
        val itemX: (Float) -> Float = { pad + it.coerceIn(0f, 1f) * (w - 2 * pad) }

        // ---- фон комнаты
        pF.color = 0xFFEDEDEA.toInt()
        c.drawColor(0xFFEDEDEA.toInt())

        // стены: тонкая рамка сверху и по бокам
        pS.color = 0xFFD9D9D5.toInt()
        pS.strokeWidth = unit * 0.16f
        c.drawLines(
            floatArrayOf(
                pad, pad + h, pad, pad,           // left
                pad, pad, w - pad, pad,           // top
                w - pad, pad, w - pad, pad + h    // right
            ),
            pS
        )

        // пол
        pF.color = 0xFFE2E2DE.toInt()
        rect.set(pad, floorY, w - pad, h - pad)
        c.drawRect(rect, pF)
        pS.color = 0xFFCFCFCA.toInt()
        pS.strokeWidth = unit * 0.14f
        c.drawLine(pad, floorY, w - pad, floorY, pS)

        // ---- предметы
        for (item in sim.items) {
            val def = item.def
            val pop = (item.ageMs / 900f).coerceIn(0f, 1f)
            val sc = if (pop >= 1f) 1f else 0.55f + 0.45f * pop // плавное появление
            val fade = 1f - ((item.ageMs - item.ttlMs + 1500f) / 1500f).coerceIn(0f, 1f)
            if (fade <= 0f) continue
            val alpha = (fade * 255).toInt()
            c.save()
            c.scale(sc, sc, itemX(item.x), floorY)
            pF.alpha = alpha; pS.alpha = alpha; pZ.alpha = alpha
            def.draw(c, itemX(item.x), floorY, unit, sim.animPhase)
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

        // тень под питомцем
        pF.color = 0x14000000
        rect.set(cx - r * 1.1f, floorY - unit * 0.22f, cx + r * 1.1f, floorY + unit * 0.12f)
        c.drawOval(rect, pF)

        var sx = 1f
        val walking = st == PetSim.State.WALK
        when {
            st == PetSim.State.HAPPY -> {
                // прыжок-радость
                val p = (sim.stateTime.toFloat() / sim.stateDur).coerceIn(0f, 1f)
                cy -= sin((p * PI).toFloat()) * (r * 1.5f)
            }
            walking -> {
                cy -= abs(sin(sim.animPhase * 5.5f)) * r * 0.14f
                sx = 1f + 0.05f * sin(sim.animPhase * 11f)
            }
            st == PetSim.State.SLEEP -> {
                sx = 1f + 0.03f * sin(sim.animPhase * 1.2f) // медленное дыхание
            }
            st == PetSim.State.INTERACT -> {
                sx = 1f + 0.06f * sin(sim.stateTime / 45f)  // интересное подрагивание
            }
            else -> sx = 1f + 0.02f * sin(sim.animPhase * 1.5f) // тихое дыхание
        }
        val ry = r / sx

        // тело
        pF.color = 0xFF7FC9A6.toInt()
        rect.set(cx - r * sx, cy - ry, cx + r * sx, cy + ry)
        c.drawOval(rect, pF)
        // животик
        pF.color = 0xFFA9DFC2.toInt()
        rect.set(cx - r * 0.55f * sx, cy + ry * 0.05f, cx + r * 0.55f * sx, cy + ry * 0.82f)
        c.drawOval(rect, pF)

        // лицо
        val eyeY = cy - ry * 0.28f
        val eyeDx = r * 0.40f
        val sleeping = st == PetSim.State.SLEEP
        val blinking = !sleeping && (sim.animPhase % 4.4f) < 0.14f

        // зрачки смотрят на предмет-цель
        var lookX = sim.dir * 0.18f
        sim.targetItem?.let { ti ->
            val dx = ti.x - sim.x
            val dy = -ry * 0.3f / r
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            lookX = dx / len * 0.2f
        }

        if (sleeping) {
            // спит: глаза закрыты, "Z z z"
            pS.color = 0xFF2E3B34.toInt()
            pS.strokeWidth = unit * 0.16f
            c.drawLine(cx - eyeDx - r * 0.18f, eyeY, cx - eyeDx + r * 0.18f, eyeY, pS)
            c.drawLine(cx + eyeDx - r * 0.18f, eyeY, cx + eyeDx + r * 0.18f, eyeY, pS)
            // ротик маленький
            c.drawCircle(cx, eyeY + ry * 0.35f, unit * 0.12f, pS)
            // Zzz
            for (i in 0..2) {
                val t = sim.animPhase + i * 0.4f
                val zy = cy - ry - unit * (0.6f + i * 0.55f + sin(t * 1.3f) * 0.2f)
                val zx = cx + r * 0.6f + i * unit * 0.55f
                pZ.textSize = unit * (0.85f + i * 0.25f)
                c.drawText("Z", zx, zy, pZ)
            }
        } else if (blinking) {
            pS.color = 0xFF2E3B34.toInt()
            pS.strokeWidth = unit * 0.16f
            c.drawLine(cx - eyeDx - r * 0.15f, eyeY, cx - eyeDx + r * 0.15f, eyeY, pS)
            c.drawLine(cx + eyeDx - r * 0.15f, eyeY, cx + eyeDx + r * 0.15f, eyeY, pS)
        } else {
            // открытые глаза со зрачками
            val er = r * 0.20f
            circleAt(c, cx - eyeDx, eyeY, er, 0xFF2E3B34.toInt())
            circleAt(c, cx + eyeDx, eyeY, er, 0xFF2E3B34.toInt())
            circleAt(c, cx - eyeDx + lookX * er, eyeY - er * 0.25f, er * 0.38f, 0xFFFFFFFF.toInt())
            circleAt(c, cx + eyeDx + lookX * er, eyeY - er * 0.25f, er * 0.38f, 0xFFFFFFFF.toInt())
        }

        // щёчки
        pF.color = 0x66F3A9A0
        rect.set(cx - eyeDx - r * 0.42f, eyeY + r * 0.12f, cx - eyeDx - r * 0.14f, eyeY + r * 0.30f)
        c.drawOval(rect, pF)
        rect.set(cx + eyeDx + r * 0.14f, eyeY + r * 0.12f, cx + eyeDx + r * 0.42f, eyeY + r * 0.30f)
        c.drawOval(rect, pF)

        // рот
        val mouthY = eyeY + ry * 0.34f
        pS.strokeWidth = unit * 0.16f
        when {
            st == PetSim.State.HAPPY || st == PetSim.State.INTERACT -> {
                pS.color = 0xFF2E3B34.toInt()
                rect.set(cx - r * 0.28f, mouthY - r * 0.22f, cx + r * 0.28f, mouthY + r * 0.06f)
                c.drawArc(rect, 15f, 150f, false, pS)
            }
            sleeping -> { /* уже нарисован */ }
            else -> {
                pS.color = 0xFF2E3B34.toInt()
                c.drawLine(cx - r * 0.14f, mouthY, cx + r * 0.14f, mouthY, pS)
            }
        }
    }

    private fun circleAt(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        pF.color = col
        c.drawCircle(x, y, r, pF)
    }
}

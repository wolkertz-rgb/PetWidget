package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Мебель в координатах комнаты; выбор хранится в существующих настройках. */
internal class RoomFurniture {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.09f
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        color = 0xFF4A4038.toInt()
    }
    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0.2f) {
        p.color = color
        val rect = RectF(l, t, r, b)
        c.drawRoundRect(rect, radius, radius, p)
        c.drawRoundRect(rect, radius, radius, edge)
    }

    fun bed(c: Canvas, x: Float, floor: Float, u: Float, style: Int, color: Int, front: Boolean) {
        c.save(); c.translate(x, floor); c.scale(u, u)
        if (style == 1) {
            if (!front) {
                p.color = 0x22000000; c.drawOval(-1.95f, -0.18f, 1.95f, 0.12f, p)
                box(c, -1.9f, -1.22f, 1.9f, -0.05f, color, 0.55f)
                box(c, -1.55f, -0.95f, 1.55f, -0.32f, 0xFFE9DDC3.toInt(), 0.35f)
            } else {
                box(c, -1.85f, -0.54f, 1.85f, -0.08f, color, 0.22f)
                p.color = 0x55FFFFFF; c.drawOval(-1.5f, -0.49f, 1.35f, -0.32f, p)
            }
        } else {
            if (!front) {
                box(c, -1.65f, -2.65f, 1.65f, -0.3f, color, 0.42f)
                box(c, -1.4f, -2.38f, -0.04f, -1.15f, color, 0.25f)
                box(c, 0.04f, -2.38f, 1.4f, -1.15f, color, 0.25f)
                box(c, -1.65f, -1.05f, 1.65f, -0.35f, 0xFFE2D5BB.toInt(), 0.25f)
            } else {
                box(c, -2f, -1.55f, -1.25f, -0.2f, color, 0.3f)
                box(c, 1.25f, -1.55f, 2f, -0.2f, color, 0.3f)
                box(c, -1.45f, -0.5f, 1.45f, -0.2f, color, 0.1f)
                box(c, -1.5f, -0.2f, -1.2f, 0f, 0xFF594638.toInt(), 0.04f)
                box(c, 1.2f, -0.2f, 1.5f, 0f, 0xFF594638.toInt(), 0.04f)
            }
        }
        c.restore()
    }

    fun lamp(c: Canvas, width: Float, floor: Float, u: Float, bulbY: Float, style: Int, on: Boolean) {
        c.save(); c.scale(u, u)
        val x = width / u / 2f; val y = bulbY / u
        val glow = if (on) 0xFFFFE5A6.toInt() else 0xFFABA99C.toInt()
        if (style == 1) {
            box(c, x - 0.35f, 0f, x + 0.35f, 0.22f, 0xFF455A50.toInt(), 0.08f)
            c.drawLine(x, 0.22f, x, y - 0.3f, edge)
            c.drawLine(x - 1.45f, y - 0.2f, x + 1.45f, y - 0.2f, edge)
            for (i in -1..1) {
                box(c, x + i * 1.3f - 0.42f, y - 0.2f, x + i * 1.3f + 0.42f, y + 0.65f, glow, 0.27f)
            }
        } else {
            val tx = 23f; val base = floor / u
            box(c, tx - 0.7f, base - 0.2f, tx + 0.7f, base, 0xFF455A50.toInt(), 0.1f)
            box(c, tx - 0.09f, base - 5.25f, tx + 0.09f, base - 0.2f, 0xFF8D795A.toInt(), 0.05f)
            val shade = Path().apply {
                moveTo(tx - 0.65f, base - 5.65f); lineTo(tx + 0.65f, base - 5.65f)
                lineTo(tx + 1f, base - 4.3f); lineTo(tx - 1f, base - 4.3f); close()
            }
            p.color = if (on) 0xFFEFD8A7.toInt() else 0xFF9B927D.toInt()
            c.drawPath(shade, p); c.drawPath(shade, edge)
        }
        c.restore()
    }

    fun curtains(c: Canvas, u: Float, floor: Float, style: Int, closed: Boolean, color: Int) {
        val bottom = floor / u - 3.15f
        val top = (bottom - 5.8f).coerceAtLeast(1.3f)
        if (bottom - top < 1.5f) return
        c.save(); c.scale(u, u)
        box(c, 1.12f, top - 0.4f, 7.08f, top - 0.25f, 0xFF7D6951.toInt(), 0.07f)
        if (style == 2) {
            val end = if (closed) bottom else top + 0.9f
            box(c, 1.65f, top - 0.12f, 6.55f, end, color, 0.1f)
            for (i in 1..8) {
                val y = top + (end - top) * i / 9f
                c.drawLine(1.7f, y, 6.5f, y, edge)
            }
        } else {
            val extent = if (closed) 2.45f else 0.78f
            for (side in 0..1) {
                val l = if (side == 0) 1.4f else 6.8f - extent
                val r = l + extent
                box(c, l, top - 0.12f, r, bottom + 0.14f, color, 0.13f)
                p.color = 0x22FFFFFF
                for (fold in 0..2) c.drawRect(l + extent * (fold + 0.2f) / 3f, top, l + extent * (fold + 0.6f) / 3f, bottom, p)
                if (!closed) box(c, l - 0.04f, bottom - 1.4f, r + 0.04f, bottom - 1.22f, 0xFFCAAC72.toInt(), 0.05f)
            }
            if (style == 1) box(c, 1.4f, top - 0.15f, 6.8f, top + 0.55f, color, 0.23f)
        }
        c.restore()
    }

    fun decor(c: Canvas, u: Float, floor: Float, rug: Boolean, tv: Boolean, plant: Boolean) {
        c.save(); c.translate(0f, floor); c.scale(u, u)
        if (rug) {
            p.color = 0xFF7FA89D.toInt(); c.drawOval(7f, -0.35f, 17f, 0.95f, p)
            edge.color = 0xFFE0D6BD.toInt(); c.drawOval(7.5f, -0.18f, 16.5f, 0.75f, edge)
            edge.color = 0xFF4A4038.toInt()
        }
        if (tv) {
            box(c, 8f, -5.7f, 12.3f, -3f, 0xFF354B48.toInt(), 0.18f)
            box(c, 8.25f, -5.45f, 12.05f, -3.25f, 0xFF8AB3BE.toInt(), 0.08f)
            p.color = 0xFFB7D8D4.toInt(); c.drawOval(8.6f, -4.8f, 11.6f, -3.5f, p)
            c.drawLine(9.5f, -3f, 9.2f, -2.65f, edge); c.drawLine(10.8f, -3f, 11.1f, -2.65f, edge)
            box(c, 7.8f, -2.65f, 12.5f, -2.4f, 0xFFAD8D65.toInt(), 0.08f)
        }
        if (plant) {
            box(c, 0.8f, -1.1f, 2.2f, -0.05f, 0xFFD18F6C.toInt(), 0.22f)
            c.drawLine(1.5f, -1.1f, 1.5f, -3.1f, edge)
            p.color = 0xFF689B73.toInt()
            c.drawOval(0.55f, -2.9f, 1.55f, -2.1f, p)
            c.drawOval(1.45f, -3.45f, 2.45f, -2.5f, p)
            c.drawOval(0.65f, -2.05f, 1.55f, -1.5f, p)
        }
        c.restore()
    }
}

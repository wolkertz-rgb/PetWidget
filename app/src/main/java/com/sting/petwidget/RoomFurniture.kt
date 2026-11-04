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

    private fun shape(c: Canvas, path: Path, color: Int) {
        p.color = color; c.drawPath(path, p); c.drawPath(path, edge)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        p.color = color; c.drawOval(l, t, r, b, p); c.drawOval(l, t, r, b, edge)
    }

    fun bed(c: Canvas, x: Float, floor: Float, u: Float, style: Int, color: Int, front: Boolean) {
        c.save(); c.translate(x, floor); c.scale(u, u)
        if (style == 1) {
            if (!front) {
                p.color = 0x22000000; c.drawOval(-1.95f, -0.18f, 1.95f, 0.12f, p)
                oval(c, -2f, -1.4f, 2f, -0.05f, color)
                oval(c, -1.67f, -1.14f, 1.67f, -0.22f, 0xFF64564C.toInt())
                oval(c, -1.47f, -0.95f, 1.47f, -0.25f, 0xFFE9DDC3.toInt())
                p.color = 0x33FFFFFF; c.drawOval(-1.2f, -0.9f, 1.15f, -0.55f, p)
            } else {
                val rim = Path().apply {
                    moveTo(-2f, -0.83f)
                    cubicTo(-1.85f, 0.14f, 1.85f, 0.14f, 2f, -0.83f)
                    lineTo(1.55f, -0.75f)
                    cubicTo(1.25f, -0.2f, -1.25f, -0.2f, -1.55f, -0.75f); close()
                }
                shape(c, rim, color)
                edge.color = 0x77E9DDC3; edge.strokeWidth = 0.045f
                c.drawArc(-1.76f, -0.93f, 1.76f, -0.15f, 10f, 160f, false, edge)
                edge.color = 0xFF4A4038.toInt(); edge.strokeWidth = 0.09f
            }
        } else {
            // Диван шире кресла на 45%, но остаётся внутри правой стены.
            if (!front) {
                p.color = 0x22000000; c.drawOval(-2.7f, -0.2f, 2.7f, 0.15f, p)
                box(c, -2.45f, -3.15f, 2.45f, -0.35f, color, 0.55f)
                box(c, -2.15f, -2.92f, -0.05f, -1.05f, color, 0.38f)
                box(c, 0.05f, -2.92f, 2.15f, -1.05f, color, 0.38f)
                edge.color = 0x55FFFFFF; edge.strokeWidth = 0.05f
                c.drawLine(-1.86f, -2.65f, -0.3f, -2.65f, edge)
                c.drawLine(0.3f, -2.65f, 1.86f, -2.65f, edge)
                edge.color = 0xFF4A4038.toInt(); edge.strokeWidth = 0.09f
                box(c, -2.16f, -1.08f, -0.04f, -0.46f, color, 0.24f)
                box(c, 0.04f, -1.08f, 2.16f, -0.46f, color, 0.24f)
            } else {
                box(c, -2.65f, -1.88f, -1.96f, -0.23f, color, 0.32f)
                box(c, 1.96f, -1.88f, 2.65f, -0.23f, color, 0.32f)
                box(c, -2.15f, -0.53f, 2.15f, -0.23f, color, 0.12f)
                box(c, -2.18f, -0.23f, -1.85f, 0f, 0xFF594638.toInt(), 0.04f)
                box(c, 1.85f, -0.23f, 2.18f, 0f, 0xFF594638.toInt(), 0.04f)
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
            edge.color = 0xFF9B8055.toInt(); edge.strokeWidth = 0.12f
            c.drawLine(x, 0.22f, x, y + 0.05f, edge)
            val arms = Path().apply {
                moveTo(x - 1.65f, y - 0.35f)
                cubicTo(x - 1.4f, y + 0.55f, x + 1.4f, y + 0.55f, x + 1.65f, y - 0.35f)
            }
            c.drawPath(arms, edge)
            c.drawLine(x, y, x, y + 0.75f, edge)
            edge.color = 0xFF4A4038.toInt(); edge.strokeWidth = 0.07f
            oval(c, x - 2.1f, y - 0.85f, x - 1.2f, y + 0.05f, glow)
            oval(c, x + 1.2f, y - 0.85f, x + 2.1f, y + 0.05f, glow)
            oval(c, x - 0.48f, y + 0.42f, x + 0.48f, y + 1.38f, glow)
            p.color = 0x66FFFFFF
            c.drawOval(x - 1.98f, y - 0.73f, x - 1.7f, y - 0.4f, p)
            c.drawOval(x + 1.32f, y - 0.73f, x + 1.6f, y - 0.4f, p)
        } else {
            // Потолочный дисковый плафон вместо напольного торшера.
            c.drawLine(x, 0.1f, x, y - 0.4f, edge)
            oval(c, x - 1.75f, y - 0.5f, x + 1.75f, y + 0.55f, 0xFF556D63.toInt())
            oval(c, x - 1.55f, y - 0.05f, x + 1.55f, y + 0.55f, glow)
            p.color = 0x55FFFFFF; c.drawOval(x - 1.15f, y + 0.02f, x + 0.8f, y + 0.22f, p)
        }
        edge.strokeWidth = 0.09f
        c.restore()
    }

    fun curtains(c: Canvas, u: Float, floor: Float, style: Int, closed: Boolean, color: Int) {
        val bottom = floor / u - 3.15f
        val top = (bottom - 5.8f).coerceAtLeast(1.3f)
        if (bottom - top < 1.5f) return
        c.save(); c.scale(u, u)
        box(c, 1.12f, top - 0.4f, 7.08f, top - 0.25f, 0xFF7D6951.toInt(), 0.07f)
        if (style == 2) {
            val end = if (closed) bottom + 0.05f else top + 1.1f
            box(c, 1.55f, top - 0.1f, 6.65f, end, color, 0.07f)
            val folds = if (closed) 5 else 4
            for (i in 1..folds) {
                val y = top + (end - top) * i / folds
                val fold = Path().apply {
                    moveTo(1.6f, y - 0.09f); quadTo(4.1f, y + 0.19f, 6.6f, y - 0.09f)
                }
                edge.color = 0x554A4038; edge.strokeWidth = 0.045f
                c.drawPath(fold, edge)
                p.color = 0x20FFFFFF; c.drawRect(1.65f, y - 0.12f, 6.55f, y - 0.06f, p)
            }
            edge.color = 0xFF4A4038.toInt(); edge.strokeWidth = 0.09f
            box(c, 1.55f, end - 0.08f, 6.65f, end + 0.06f, color, 0.05f)
        } else if (style == 1) {
            // Парные полотна: сверху широкие, подхвачены у боков окна.
            val height = bottom - top
            for (side in 0..1) {
                c.save()
                if (side == 1) { c.translate(8.2f, 0f); c.scale(-1f, 1f) }
                val drape = Path().apply {
                    moveTo(1.4f, top - 0.1f); lineTo(4.1f, top - 0.1f)
                    if (!closed) {
                        cubicTo(3.9f, top + height * 0.23f, 2.1f, top + height * 0.35f, 1.95f, top + height * 0.64f)
                        quadTo(2.25f, bottom - 0.3f, 2.5f, bottom + 0.15f)
                    } else {
                        lineTo(4.1f, bottom + 0.1f)
                        quadTo(3.2f, bottom + 0.3f, 2.5f, bottom + 0.15f)
                    }
                    lineTo(1.4f, bottom + 0.15f); close()
                }
                shape(c, drape, color)
                edge.color = 0x55FFFFFF; edge.strokeWidth = 0.055f
                for (fold in 0..2) {
                    val seam = Path().apply {
                        moveTo(1.7f + fold * 0.65f, top + 0.05f)
                        quadTo(if (closed) 1.8f + fold * 0.65f else 2.1f,
                            top + height * 0.35f, if (closed) 1.8f + fold * 0.65f else 1.75f, bottom - 0.15f)
                    }
                    c.drawPath(seam, edge)
                }
                edge.color = 0xFF4A4038.toInt(); edge.strokeWidth = 0.09f
                if (!closed) box(c, 1.35f, top + height * 0.64f, 2.1f, top + height * 0.64f + 0.17f, 0xFFCEAD70.toInt(), 0.05f)
                c.restore()
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
        }
        c.restore()
    }

    fun decor(c: Canvas, u: Float, floor: Float, rug: Boolean, tv: Boolean, plant: Boolean,
              rugColor: Int, accent: Int) {
        c.save(); c.translate(0f, floor); c.scale(u, u)
        if (rug) {
            p.color = rugColor; c.drawOval(7f, -0.35f, 17f, 0.95f, p)
            edge.color = 0xFFE0D6BD.toInt(); c.drawOval(7.5f, -0.18f, 16.5f, 0.75f, edge)
            edge.color = 0xFF4A4038.toInt()
        }
        if (tv) {
            c.save(); c.translate(0.65f, 0f)
            box(c, 8f, -5.7f, 12.3f, -3f, 0xFF354B48.toInt(), 0.18f)
            box(c, 8.25f, -5.45f, 12.05f, -3.25f, 0xFF8AB3BE.toInt(), 0.08f)
            p.color = 0xFFB7D8D4.toInt(); c.drawOval(8.6f, -4.8f, 11.6f, -3.5f, p)
            c.drawLine(9.5f, -3f, 9.2f, -2.65f, edge); c.drawLine(10.8f, -3f, 11.1f, -2.65f, edge)
            box(c, 7.8f, -2.65f, 12.5f, -2.4f, accent, 0.08f)
            c.restore()
        }
        if (plant) {
            c.save(); c.translate(-0.6f, 0f)
            val pot = Path().apply {
                moveTo(0.75f, -1.2f); lineTo(2.25f, -1.2f)
                lineTo(2.04f, -0.15f); quadTo(1.5f, 0.05f, 0.96f, -0.15f); close()
            }
            shape(c, pot, accent)
            oval(c, 0.72f, -1.38f, 2.28f, -1.05f, accent)
            p.color = 0xFF5C4839.toInt(); c.drawOval(0.9f, -1.3f, 2.1f, -1.13f, p)
            val stem = Path().apply { moveTo(1.5f, -1.2f); quadTo(1.25f, -2.7f, 1.65f, -3.75f) }
            edge.color = 0xFF456C4D.toInt(); edge.strokeWidth = 0.075f; c.drawPath(stem, edge)
            for (i in 0..4) {
                val y = -1.65f - i * 0.43f
                val direction = if (i % 2 == 0) -1f else 1f
                val tipX = 1.5f + direction * 0.9f
                val leaf = Path().apply {
                    moveTo(1.5f, y)
                    cubicTo(1.5f + direction * 0.2f, y - 0.7f, tipX, y - 0.62f, tipX, y - 0.44f)
                    cubicTo(tipX, y - 0.1f, 1.5f + direction * 0.3f, y + 0.1f, 1.5f, y); close()
                }
                shape(c, leaf, if (i % 2 == 0) 0xFF609365.toInt() else 0xFF83AC72.toInt())
                c.drawLine(1.5f, y, tipX, y - 0.44f, edge)
            }
            edge.color = 0xFF4A4038.toInt(); edge.strokeWidth = 0.09f
            c.restore()
        }
        c.restore()
    }
}

package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** Мебель в координатах комнаты; выбор хранится в существующих настройках. */
internal class RoomFurniture {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.09f
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        color = 0xFF4A4038.toInt()
    }
    private val material = VectorFinish()
    private val bounds = android.graphics.RectF()
    private val detail = Path()

    private fun seam(c: Canvas, color: Int, width: Float) {
        val previousColor = edge.color
        val previousWidth = edge.strokeWidth
        edge.color = color
        edge.strokeWidth = width
        c.drawPath(detail, edge)
        edge.color = previousColor
        edge.strokeWidth = previousWidth
    }

    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0.2f) {
        material.apply(p, color, l, t, r, b)
        c.drawRoundRect(l, t, r, b, radius, radius, p)
        p.shader = null
        c.drawRoundRect(l, t, r, b, radius, radius, edge)
    }

    private fun shape(c: Canvas, path: Path, color: Int) {
        path.computeBounds(bounds, true)
        material.apply(p, color, bounds.left, bounds.top, bounds.right, bounds.bottom)
        c.drawPath(path, p)
        p.shader = null
        c.drawPath(path, edge)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        material.apply(p, color, l, t, r, b)
        c.drawOval(l, t, r, b, p)
        p.shader = null
        c.drawOval(l, t, r, b, edge)
    }

    fun bed(c: Canvas, x: Float, floor: Float, u: Float, style: Int, color: Int, front: Boolean) {
        c.save(); c.translate(x, floor); c.scale(u, u)
        val shade = VectorFinish.blend(color, 0xFF69442B.toInt(), .36f)
        val lit = VectorFinish.blend(color, 0xFFF9D896.toInt(), .23f)
        if (style == 1) {
            if (!front) {
                p.color = 0x2D4C392C
                c.drawOval(-2.10f, -.20f, 2.10f, .13f, p)
                oval(c,-2.02f,-1.31f,2.02f,-.05f,shade)
                oval(c,-1.95f,-1.34f,1.95f,-.14f,color)
                oval(c,-1.58f,-1.13f,1.58f,-.28f,0xFFF0D9AD.toInt())
            } else {
                detail.reset()
                detail.moveTo(-2f,-.74f)
                detail.cubicTo(-1.35f,-.31f,1.35f,-.31f,2f,-.74f)
                detail.cubicTo(1.98f,.20f,-1.98f,.20f,-2f,-.74f)
                detail.close()
                shape(c,detail,color)
                detail.reset()
                detail.moveTo(-1.76f,-.57f)
                detail.cubicTo(-1.11f,-.24f,1.11f,-.24f,1.76f,-.57f)
                seam(c,0x8FFFF0CC.toInt(),.043f)
            }
        } else {
            val half = if (style == 2) 2.40f else 1.58f
            val crown = if (style == 2) -3.12f else -3.57f
            if (!front) {
                p.color = 0x35433020
                c.drawOval(-half-.52f,-.23f,half+.65f,.15f,p)
                detail.reset()
                detail.moveTo(-half,-.43f)
                detail.cubicTo(-half-.11f,-1.47f,-half-.13f,crown+.61f,-half+.52f,crown+.19f)
                detail.cubicTo(-half+.93f,crown-.21f,half-.81f,crown-.16f,half-.41f,crown+.18f)
                detail.cubicTo(half+.20f,crown+.57f,half+.20f,-1.67f,half,-.43f)
                detail.close()
                shape(c,detail,shade)
                box(c,-half+.15f,crown+.17f,half-.15f,-.77f,color,.73f)
                detail.reset()
                detail.moveTo(-half+.36f,crown+.84f)
                detail.quadTo(-half+.61f,crown+.27f,-half+1.10f,crown+.29f)
                seam(c,0x80FFF0C0.toInt(),.061f)
                if (style == 2) {
                    detail.reset()
                    detail.moveTo(.04f,crown+.31f)
                    detail.quadTo(-.08f,-2.03f,.05f,-1.05f)
                    seam(c,0x65573828,.058f)
                }
                box(c,-half+.12f,-1.19f,half-.12f,-.44f,lit,.35f)
                // A compressed fold at the seat edge suggests soft upholstery.
                detail.reset()
                detail.moveTo(-half+.42f,-.86f)
                detail.quadTo(0f,-1.04f,half-.42f,-.86f)
                seam(c,0x7EFFF0CD,.051f)
            } else {
                box(c,-half,-.59f,half,-.16f,shade,.20f)
                for (side in -1..1 step 2) {
                    c.save()
                    c.translate(side*(half-.08f),0f)
                    c.scale(side.toFloat(),1f)
                    box(c,-.26f,-.32f,.10f,.02f,0xFF684A32.toInt(),.08f)
                    detail.reset()
                    detail.moveTo(-.41f,-.65f)
                    detail.cubicTo(-.55f,-1.35f,-.55f,-2.02f,-.17f,-2.13f)
                    detail.cubicTo(.34f,-2.28f,.55f,-1.94f,.52f,-1.49f)
                    detail.cubicTo(.51f,-.49f,.35f,-.22f,.06f,-.20f)
                    detail.cubicTo(-.15f,-.19f,-.36f,-.36f,-.41f,-.65f)
                    detail.close()
                    shape(c,detail,color)
                    detail.reset()
                    detail.moveTo(-.33f,-1.08f)
                    detail.cubicTo(-.44f,-1.87f,-.25f,-2.04f,.05f,-2.02f)
                    seam(c,0xAAFFF0C2.toInt(),.047f)
                    c.restore()
                }
            }
        }
        c.restore()
    }

    fun lamp(c: Canvas, width: Float, floor: Float, u: Float, bulbY: Float, style: Int, on: Boolean) {
        c.save(); c.scale(u, u)
        val x = width / u / 2f; val y = bulbY / u
        val glow = if (on) 0xFFFFE6A2.toInt() else 0xFFBDB9A2.toInt()
        if (style == 1) {
            box(c, x - 0.35f, 0f, x + 0.35f, 0.22f, 0xFF455A50.toInt(), 0.08f)
            edge.color = 0xFFC29758.toInt(); edge.strokeWidth = 0.15f
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
            oval(c, x - 1.83f, y - .46f, x + 1.83f, y + .55f, 0xFF758D6C.toInt())
            box(c,x-.39f,y-.58f,x+.39f,y-.33f,0xFFC59C61.toInt(),.12f)
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
            val height = bottom-top
            for (side in 0..1) {
                c.save()
                if (side == 1) { c.translate(8.2f,0f); c.scale(-1f,1f) }
                val hem = if (closed) 4.10f else 2.63f
                detail.reset()
                detail.moveTo(1.37f, top-.16f)
                detail.quadTo(2.75f,top-.32f,3.98f,top-.14f)
                if (closed) {
                    detail.cubicTo(4.17f,top+height*.31f,3.96f,bottom-.41f,4.10f,bottom+.09f)
                } else {
                    detail.cubicTo(3.91f,top+height*.29f,2.23f,top+height*.44f,2.02f,top+height*.62f)
                    detail.quadTo(2.08f,bottom-.35f,hem,bottom+.09f)
                }
                detail.quadTo(2.12f,bottom+.39f,1.35f,bottom+.11f)
                detail.cubicTo(1.56f,bottom-1f,1.40f,top+height*.26f,1.37f,top-.16f)
                detail.close()
                shape(c,detail,color)
                c.save()
                c.clipPath(detail)
                for (i in 0..3) {
                    val x=1.64f+i*.55f
                    val mid=if (closed) x+.13f else 1.55f+i*.12f
                    detail.reset()
                    detail.moveTo(x,top-.12f)
                    detail.cubicTo(x+.11f,top+height*.24f,mid,top+height*.51f,mid,top+height*.62f)
                    detail.quadTo(mid+.04f,bottom-.46f,mid+.22f+i*.10f,bottom+.20f)
                    seam(c,0x326B5E3F,.14f)
                    seam(c,0x43FFFAE4,.054f)
                }
                c.restore()
                if (!closed) {
                    val tie=top+height*.64f
                    oval(c,1.33f,tie-.13f,2.13f,tie+.15f,0xFFE7BD79.toInt())
                    oval(c,1.78f,tie-.14f,2.11f,tie+.18f,0xFFF5D697.toInt())
                }
                c.restore()
            }
        }
        c.restore()
    }

    fun decor(c: Canvas, u: Float, floor: Float, rug: Boolean, tv: Boolean, plant: Boolean,
              rugColor: Int, accent: Int) {
        c.save(); c.translate(0f, floor); c.scale(u, u)
        if (rug) {
            p.color = 0x22000000
            c.drawOval(6.8f, -.22f, 17.2f, .99f, p)
            material.apply(p, rugColor, 7f, -.35f, 17f, .95f)
            c.drawOval(7f, -.35f, 17f, .95f, p)
            p.shader = null
            edge.color = 0xFF526750.toInt()
            edge.strokeWidth = .059f
            c.drawOval(7f,-.35f,17f,.95f,edge)
            edge.color = 0xFFE9DCB5.toInt()
            edge.strokeWidth = .11f
            c.drawOval(7.47f,-.20f,16.53f,.76f,edge)
            edge.strokeWidth = .09f
            edge.color = 0xFF4A4038.toInt()
        }
        if (tv) {
            c.save(); c.translate(0.65f, 0f)
            box(c, 7.93f, -5.75f, 12.36f, -2.97f, 0xFF554D41.toInt(), .29f)
            box(c, 8.10f, -5.58f, 12.22f, -3.14f, 0xFF445B54.toInt(), .18f)
            box(c, 8.25f, -5.45f, 12.05f, -3.25f, 0xFF536C67.toInt(), 0.08f)
            p.color = 0x24F7EACE
            val reflection = Path().apply {
                moveTo(8.43f,-5.33f)
                lineTo(9.89f,-5.33f)
                lineTo(8.43f,-3.68f)
                close()
            }
            c.drawPath(reflection,p)
            c.drawLine(9.5f, -3f, 9.2f, -2.65f, edge); c.drawLine(10.8f, -3f, 11.1f, -2.65f, edge)
            box(c, 7.45f, -2.65f, 12.85f, -.38f, accent, .30f)
            box(c, 7.32f, -2.74f, 12.98f, -2.46f,
                VectorFinish.blend(accent,0xFFFFD3A1.toInt(),.31f), .13f)
            box(c, 7.78f, -2.34f, 9.25f, -.64f, accent, .13f)
            box(c, 11.04f, -2.34f, 12.52f, -.64f, accent, .13f)
            box(c, 9.54f, -2.30f, 10.78f, -.71f, 0xFF70503C.toInt(), .10f)
            box(c, 9.70f, -1.35f, 10.62f, -1.05f, 0xFFE9D5A5.toInt(), .06f)
            box(c, 9.63f, -1.01f, 10.68f, -.78f, 0xFF91AC80.toInt(), .05f)
            for (x0 in listOf(8f, 12.2f)) box(c, x0, -.38f, x0 + .27f, 0f, 0xFF68503D.toInt(), .06f)
            oval(c, 8.88f, -1.61f, 9.02f, -1.47f, 0xFFF5D18B.toInt())
            oval(c, 11.29f, -1.61f, 11.43f, -1.47f, 0xFFF5D18B.toInt())
            c.restore()
        }
        if (plant) {
            c.save(); c.translate(-0.6f, 0f)
            val pot = Path().apply {
                moveTo(0.75f, -1.2f); lineTo(2.25f, -1.2f)
                lineTo(2.04f, -0.15f); quadTo(1.5f, 0.05f, 0.96f, -0.15f); close()
            }
            p.color = 0x324B3423
            c.drawOval(.61f,-.20f,2.61f,.11f,p)
            shape(c, pot, accent)
            c.save()
            c.clipPath(pot)
            p.color=0x55FFF0C9
            c.drawRoundRect(.78f,-.69f,2.28f,-.46f,.05f,.05f,p)
            c.restore()
            oval(c, 0.72f, -1.38f, 2.28f, -1.05f, accent)
            p.color = 0xFF5C4839.toInt(); c.drawOval(0.9f, -1.3f, 2.1f, -1.13f, p)
            val stem = Path().apply { moveTo(1.5f, -1.2f); quadTo(1.25f, -2.7f, 1.65f, -3.75f) }
            edge.color = 0xFF456C4D.toInt(); edge.strokeWidth = 0.075f; c.drawPath(stem, edge)
            for (i in 0..4) {
                val y = -1.65f - i * .49f
                val direction = if (i % 2 == 0) -1f else 1f
                val tipX = 1.5f + direction * (1.02f - i*.08f)
                val leaf = Path().apply {
                    moveTo(1.5f, y)
                    cubicTo(1.5f + direction * 0.2f, y - 0.7f, tipX, y - 0.62f, tipX, y - 0.44f)
                    cubicTo(tipX, y - 0.1f, 1.5f + direction * 0.3f, y + 0.1f, 1.5f, y); close()
                }
                shape(c, leaf, if (i % 2 == 0) 0xFF609365.toInt() else 0xFF83AC72.toInt())
                detail.reset()
                detail.moveTo(1.5f,y)
                detail.quadTo(1.5f+direction*.48f,y-.42f,tipX,y-.44f)
                seam(c,0x65DFE1A2,.039f)
            }
            edge.color = 0xFF4A4038.toInt(); edge.strokeWidth = 0.09f
            c.restore()
        }
        c.restore()
    }
}

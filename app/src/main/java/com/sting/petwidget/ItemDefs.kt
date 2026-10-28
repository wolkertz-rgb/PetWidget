package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * 17 предметов, которые случайно появляются в комнате питомца.
 * Все рисуются векторно на Canvas — никаких картинок не нужно.
 *
 * Единица масштаба s = ~1/24 ширины виджета; от неё пропорциональны все размеры.
 */
object ItemDefs {

    enum class Kind { TOY, FOOD, BOX }
    enum class Reaction { EAT, PLAY, SMELL, PUSH, LIFT, RUB, OPEN }

    class Def(
        val id: String,
        val name: String,
        val kind: Kind,
        val reaction: Reaction,
        /** ширина предмета в "юнитах" (s) */
        val w: Float,
        /** canvas, cx (центр по X), floorY (уровень пола), s (юнит в px), t (время в секундах) */
        val draw: (Canvas, Float, Float, Float, Float) -> Unit
    )

    val ALL: List<Def> = listOf(
        ball(), block(), dice(), balloon(), plushie(), robot(), rocket(),
        boat(), flower(), top(),
        pizza(), sushi(), donut(), cookie(), coffee(), apple(), icecream(),
        box()
    )

    val byId: Map<String, Def> = ALL.associateBy { it.id }

    // ------------------------------------------------------------- краски

    private val pF = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pS = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    private fun fill(c: Canvas, col: Int) { pF.color = col; c.drawOval(rect, pF) }

    private fun circle(c: Canvas, cx: Float, cy: Float, r: Float, col: Int) {
        pF.color = col
        c.drawCircle(cx, cy, r, pF)
    }

    private fun ellipse(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        pF.color = col
        c.drawOval(rect, pF)
    }

    private fun roundRect(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, r: Float, col: Int) {
        rect.set(x0, y0, x1, y1)
        pF.color = col
        c.drawRoundRect(rect, r, r, pF)
    }

    private fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int) {
        pS.color = col
        pS.strokeWidth = w
        c.drawLine(x0, y0, x1, y1, pS)
    }

    // ------------------------------------------------------------ игрушки

    /** Мячик — пляжный, в полоску */
    private fun ball() = Def("ball", "Мячик", Kind.TOY, Reaction.PUSH, 2.2f) { c, cx, fy, s, _ ->
        val r = s * 0.95f
        val cy = fy - r
        circle(c, cx, cy, r, 0xFFF8F5EC.toInt())
        pF.color = 0xFF7CC8A2.toInt()
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, -60f, 60f, true, pF)      // мятный сектор
        pF.color = 0xFFF26D7E.toInt()
        c.drawArc(rect, 60f, 60f, true, pF)       // розовый сектор
        pF.color = 0xFFF4C95D.toInt()
        c.drawArc(rect, 180f, 60f, true, pF)      // жёлтый сектор
        line(c, cx - r, cy, cx + r, cy, s * 0.08f, 0x33FFFFFF.toInt())
        circle(c, cx - r * 0.35f, cy - r * 0.35f, r * 0.16f, 0x66FFFFFF.toInt())
    }

    /** Деревянный кубик */
    private fun block() = Def("block", "Кубик", Kind.TOY, Reaction.PUSH, 2.0f) { c, cx, fy, s, _ ->
        val h = s * 1.7f
        roundRect(c, cx - h, fy - h, cx + h, fy - s * 0.05f, s * 0.22f, 0xFFFBBF69.toInt())
        roundRect(c, cx - h, fy - h, cx + h, fy - h * 0.62f, s * 0.22f, 0xFFFCD492.toInt())
        line(c, cx - h * 0.5f, fy - h * 0.36f, cx + h * 0.5f, fy - h * 0.36f, s * 0.1f, 0xFFB45309.toInt())
    }

    /** Игральный кубик (кости) — берёт и рассматривает */
    private fun dice() = Def("dice", "Кости", Kind.TOY, Reaction.LIFT, 2.0f) { c, cx, fy, s, _ ->
        val h = s * 1.6f
        roundRect(c, cx - h, fy - h, cx + h, fy - s * 0.05f, s * 0.28f, 0xFFF5F5F4.toInt())
        val d = s * 0.14f
        circle(c, cx - h * 0.5f, fy - h * 0.65f, d, 0xFF44403C.toInt())
        circle(c, cx + h * 0.5f, fy - h * 0.65f, d, 0xFF44403C.toInt())
        circle(c, cx, fy - h * 0.45f, d, 0xFF44403C.toInt())
        circle(c, cx - h * 0.5f, fy - h * 0.25f, d, 0xFF44403C.toInt())
        circle(c, cx + h * 0.5f, fy - h * 0.25f, d, 0xFF44403C.toInt())
    }

    /** Воздушный шарик — слегка плавает и покачивается; питомец поднимает его */
    private fun balloon() = Def("balloon", "Шарик", Kind.TOY, Reaction.LIFT, 1.9f) { c, cx, fy, s, t ->
        val sway = sin(t * 1.4f) * s * 0.18f
        val bx = cx + sway
        val ry = s * 1.25f
        val by = fy - s * 3.4f
        ellipse(c, bx, by, s * 0.85f, ry, 0xFFF472B6.toInt())
        ellipse(c, bx - s * 0.25f, by - ry * 0.35f, s * 0.22f, ry * 0.22f, 0x59FFFFFF.toInt())
        // узелок
        pF.color = 0xFFDB2777.toInt()
        pF.style = Paint.Style.FILL
        val path = android.graphics.Path()
        path.moveTo(bx - s * 0.12f, by + ry)
        path.lineTo(bx + s * 0.12f, by + ry)
        path.lineTo(bx, by + ry + s * 0.25f)
        path.close()
        c.drawPath(path, pF)
        // верёвочка
        pS.color = 0xFF9CA3AF.toInt()
        pS.strokeWidth = s * 0.06f
        val path2 = android.graphics.Path()
        path2.moveTo(bx, by + ry + s * 0.25f)
        path2.quadTo(cx - sway, fy - s * 1.5f, cx, fy - s * 0.1f)
        c.drawPath(path2, pS)
    }

    /** Мягкий мишка — питомец его гладит */
    private fun plushie() = Def("plushie", "Мишка", Kind.TOY, Reaction.RUB, 2.4f) { c, cx, fy, s, _ ->
        val brown = 0xFFB98A5E.toInt()
        ellipse(c, cx, fy - s * 1.0f, s * 0.95f, s * 1.0f, brown) // тело
        circle(c, cx, fy - s * 2.3f, s * 0.72f, brown)            // голова
        circle(c, cx - s * 0.62f, fy - s * 2.85f, s * 0.28f, brown) // уши
        circle(c, cx + s * 0.62f, fy - s * 2.85f, s * 0.28f, brown)
        ellipse(c, cx, fy - s * 2.2f, s * 0.3f, s * 0.24f, 0xFFE8C9A8.toInt()) // морда
        circle(c, cx - s * 0.24f, fy - s * 2.42f, s * 0.08f, 0xFF3F2D1D.toInt())
        circle(c, cx + s * 0.24f, fy - s * 2.42f, s * 0.08f, 0xFF3F2D1D.toInt())
        circle(c, cx, fy - s * 2.16f, s * 0.08f, 0xFF3F2D1D.toInt()) // носик
        ellipse(c, cx - s * 0.85f, fy - s * 0.9f, s * 0.3f, s * 0.22f, 0xFFA9713F.toInt()) // лапки
        ellipse(c, cx + s * 0.85f, fy - s * 0.9f, s * 0.3f, s * 0.22f, 0xFFA9713F.toInt())
    }

    /** Робот с мигающей лампочкой */
    private fun robot() = Def("robot", "Робот", Kind.TOY, Reaction.PLAY, 2.2f) { c, cx, fy, s, t ->
        val body = 0xFFB8C4D9.toInt()
        roundRect(c, cx - s * 0.85f, fy - s * 1.7f, cx + s * 0.85f, fy - s * 0.02f, s * 0.26f, body)
        // экран-лицо
        roundRect(c, cx - s * 0.58f, fy - s * 3.0f, cx + s * 0.58f, fy - s * 1.65f, s * 0.22f, 0xFF2E3B4A.toInt())
        val on = (sin(t * 3.0f) > 0f)
        circle(c, cx - s * 0.26f, fy - s * 2.4f, s * 0.12f, 0xFF9FE8C4.toInt())
        circle(c, cx + s * 0.26f, fy - s * 2.4f, s * 0.12f, 0xFF9FE8C4.toInt())
        // улыбка роботу
        pS.color = 0xFF9FE8C4.toInt()
        pS.strokeWidth = s * 0.07f
        rect.set(cx - s * 0.2f, fy - s * 2.1f, cx + s * 0.2f, fy - s * 1.9f)
        c.drawArc(rect, 15f, 150f, false, pS)
        line(c, cx, fy - s * 3.0f, cx, fy - s * 3.5f, s * 0.08f, 0xFF64748B.toInt())
        circle(c, cx, fy - s * 3.62f, s * 0.16f, if (on) 0xFFFFD54D.toInt() else 0xFF7A6420.toInt())
        line(c, cx - s * 0.85f, fy - s * 1.2f, cx - s * 1.2f, fy - s * 0.7f, s * 0.16f, body)
        line(c, cx + s * 0.85f, fy - s * 1.2f, cx + s * 1.2f, fy - s * 0.7f, s * 0.16f, body)
    }

    /** Ракета — питомец поднимает её и рассматривает */
    private fun rocket() = Def("rocket", "Ракета", Kind.TOY, Reaction.LIFT, 2.2f) { c, cx, fy, s, _ ->
        val path = android.graphics.Path()
        val bodyTop = fy - s * 3.6f
        path.moveTo(cx, bodyTop)                                  // нос
        path.quadTo(cx + s * 0.85f, fy - s * 2.0f, cx + s * 0.62f, fy - s * 0.1f)
        path.lineTo(cx - s * 0.62f, fy - s * 0.1f)
        path.quadTo(cx - s * 0.85f, fy - s * 2.0f, cx, bodyTop)
        path.close()
        pF.color = 0xFFF1F5F9.toInt()
        c.drawPath(path, pF)
        pF.color = 0xFFEF4444.toInt()
        val fin = android.graphics.Path()
        fin.moveTo(cx - s * 0.6f, fy - s * 1.4f)
        fin.lineTo(cx - s * 1.05f, fy - s * 0.05f)
        fin.lineTo(cx - s * 0.6f, fy - s * 0.05f)
        fin.close()
        c.drawPath(fin, pF)
        val fin2 = android.graphics.Path()
        fin2.moveTo(cx + s * 0.6f, fy - s * 1.4f)
        fin2.lineTo(cx + s * 1.05f, fy - s * 0.05f)
        fin2.lineTo(cx + s * 0.6f, fy - s * 0.05f)
        fin2.close()
        c.drawPath(fin2, pF)
        circle(c, cx, fy - s * 2.4f, s * 0.32f, 0xFF60A5FA.toInt())
        circle(c, cx, fy - s * 2.4f, s * 0.18f, 0xFFBAE6FD.toInt())
    }

    /** Кораблик — питомец поднимает и рассматривает */
    private fun boat() = Def("boat", "Кораблик", Kind.TOY, Reaction.LIFT, 2.6f) { c, cx, fy, s, _ ->
        val path = android.graphics.Path()
        path.moveTo(cx - s * 1.2f, fy - s * 1.1f)
        path.lineTo(cx + s * 1.2f, fy - s * 1.1f)
        path.lineTo(cx + s * 0.75f, fy - s * 0.05f)
        path.lineTo(cx - s * 0.75f, fy - s * 0.05f)
        path.close()
        pF.color = 0xFFB45309.toInt()
        c.drawPath(path, pF)
        line(c, cx, fy - s * 1.1f, cx, fy - s * 3.1f, s * 0.1f, 0xFF78350F.toInt())
        pF.color = 0xFFF8FAFC.toInt()
        val sail = android.graphics.Path()
        sail.moveTo(cx + s * 0.1f, fy - s * 3.0f)
        sail.quadTo(cx + s * 1.15f, fy - s * 2.0f, cx + s * 0.1f, fy - s * 1.2f)
        sail.close()
        c.drawPath(sail, pF)
    }

    /** Цветок в горшке */
    private fun flower() = Def("flower", "Цветок", Kind.TOY, Reaction.SMELL, 2.0f) { c, cx, fy, s, _ ->
        val pot = s * 0.75f
        pF.color = 0xFFD97706.toInt()
        val potPath = android.graphics.Path()
        potPath.moveTo(cx - pot, fy - s * 1.4f)
        potPath.lineTo(cx + pot, fy - s * 1.4f)
        potPath.lineTo(cx + pot * 0.7f, fy - s * 0.05f)
        potPath.lineTo(cx - pot * 0.7f, fy - s * 0.05f)
        potPath.close()
        c.drawPath(potPath, pF)
        line(c, cx, fy - s * 1.4f, cx, fy - s * 3.0f, s * 0.1f, 0xFF16A34A.toInt())
        ellipse(c, cx - s * 0.4f, fy - s * 2.1f, s * 0.3f, s * 0.14f, 0xFF22C55E.toInt())
        val cy = fy - s * 3.3f
        for (i in 0..4) {
            val ang = i * (Math.PI * 2 / 5).toFloat() - 1.57f
            circle(c, cx + cos(ang) * s * 0.38f, cy + sin(ang) * s * 0.38f, s * 0.26f, 0xFFF472B6.toInt())
        }
        circle(c, cx, cy, s * 0.24f, 0xFFFDE047.toInt())
    }

    /** Юла */
    private fun top() = Def("top", "Юла", Kind.TOY, Reaction.PLAY, 2.0f) { c, cx, fy, s, t ->
        val rot = sin(t * 5.0f)
        line(c, cx + rot * s * 0.15f, fy - s * 2.6f, cx, fy - s * 0.15f, s * 0.14f, 0xFF7C3AED.toInt())
        ellipse(c, cx, fy - s * 1.75f, s * 0.85f, s * 0.5f, 0xFFA78BFA.toInt())
        ellipse(c, cx, fy - s * 1.75f, s * 0.85f, s * 0.5f, 0xFFA78BFA.toInt())
        ellipse(c, cx, fy - s * 1.5f, s * 0.5f, s * 0.3f, 0xFF7C3AED.toInt())
        circle(c, cx, fy - s * 2.75f, s * 0.16f, 0xFFFDE047.toInt())
    }

    // --------------------------------------------------------------- еда

    /** Пицца */
    private fun pizza() = Def("pizza", "Пицца", Kind.FOOD, Reaction.EAT, 2.6f) { c, cx, fy, s, _ ->
        val tipY = fy - s * 0.1f
        val topY = fy - s * 2.2f
        pF.color = 0xFFFBBF24.toInt()
        val slice = android.graphics.Path()
        slice.moveTo(cx, tipY)
        slice.lineTo(cx - s * 1.25f, topY)
        slice.lineTo(cx + s * 1.25f, topY)
        slice.close()
        c.drawPath(slice, pF)
        roundRect(c, cx - s * 1.25f, topY - s * 0.22f, cx + s * 1.25f, topY + s * 0.1f, s * 0.2f, 0xFFD97706.toInt())
        circle(c, cx, fy - s * 1.3f, s * 0.2f, 0xFFDC2626.toInt())
        circle(c, cx - s * 0.45f, fy - s * 1.0f, s * 0.17f, 0xFFDC2626.toInt())
        circle(c, cx + s * 0.45f, fy - s * 1.0f, s * 0.17f, 0xFFDC2626.toInt())
    }

    /** Суши */
    private fun sushi() = Def("sushi", "Суши", Kind.FOOD, Reaction.SMELL, 2.2f) { c, cx, fy, s, _ ->
        ellipse(c, cx, fy - s * 0.65f, s * 1.05f, s * 0.6f, 0xFFF8FAFC.toInt())
        ellipse(c, cx, fy - s * 1.3f, s * 1.0f, s * 0.35f, 0xFF1E293B.toInt())
        ellipse(c, cx, fy - s * 1.3f, s * 1.0f, s * 0.35f, 0xFF1E293B.toInt())
        circle(c, cx, fy - s * 1.25f, s * 0.14f, 0xFFFB923C.toInt())
    }

    /** Пончик */
    private fun donut() = Def("donut", "Пончик", Kind.FOOD, Reaction.EAT, 2.6f) { c, cx, fy, s, _ ->
        val cy = fy - s * 0.9f
        val r = s * 1.0f
        pS.color = 0xFFD97706.toInt()
        pS.strokeWidth = s * 0.75f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawOval(rect, pS)
        pS.color = 0xFFF472B6.toInt()
        pS.strokeWidth = s * 0.55f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, 175f, 190f, false, pS) // розовая глазурь сверху
        circle(c, cx - s * 0.4f, cy - s * 0.55f, s * 0.09f, 0xFFFDE047.toInt())
        circle(c, cx + s * 0.3f, cy - s * 0.65f, s * 0.09f, 0xFF60A5FA.toInt())
        circle(c, cx + s * 0.55f, cy - s * 0.25f, s * 0.09f, 0xFF4ADE80.toInt())
        circle(c, cx, cy, s * 0.3f, 0xFFEDEDED.toInt()) // дырка как фон
    }

    /** Печенье */
    private fun cookie() = Def("cookie", "Печенье", Kind.FOOD, Reaction.EAT, 2.2f) { c, cx, fy, s, _ ->
        circle(c, cx, fy - s * 0.8f, s * 0.95f, 0xFFD6A054.toInt())
        circle(c, cx - s * 0.3f, fy - s * 0.9f, s * 0.15f, 0xFF5C3A21.toInt())
        circle(c, cx + s * 0.35f, fy - s * 0.6f, s * 0.13f, 0xFF5C3A21.toInt())
        circle(c, cx - s * 0.05f, fy - s * 0.35f, s * 0.12f, 0xFF5C3A21.toInt())
        circle(c, cx + s * 0.5f, fy - s * 1.1f, s * 0.11f, 0xFF5C3A21.toInt())
    }

    /** Кофе с сердечком на кружке */
    private fun coffee() = Def("coffee", "Кофе", Kind.FOOD, Reaction.SMELL, 2.0f) { c, cx, fy, s, t ->
        roundRect(c, cx - s * 0.9f, fy - s * 1.8f, cx + s * 0.9f, fy - s * 0.05f, s * 0.25f, 0xFFFFF7EC.toInt())
        ellipse(c, cx, fy - s * 1.8f, s * 0.9f, s * 0.22f, 0xFF78350F.toInt())
        ellipse(c, cx, fy - s * 1.8f, s * 0.6f, s * 0.13f, 0xFFB45309.toInt())
        // сердечко на кружке
        pF.color = 0xFFE58C8C.toInt()
        val hy = fy - s * 0.85f
        path.reset()
        path.moveTo(cx, hy + s * 0.18f)
        path.quadTo(cx - s * 0.26f, hy, cx - s * 0.1f, hy - s * 0.14f)
        path.quadTo(cx, hy - s * 0.2f, cx, hy - s * 0.06f)
        path.quadTo(cx, hy - s * 0.2f, cx + s * 0.1f, hy - s * 0.14f)
        path.quadTo(cx + s * 0.26f, hy, cx, hy + s * 0.18f)
        c.drawPath(path, pF)
        line(c, cx + s * 0.9f, fy - s * 1.4f, cx + s * 0.9f, fy - s * 0.9f, s * 0.16f, 0xFFFFF7EC.toInt())
        // пар поднимается
        for (i in 0..1) {
            val ph = t * 2.0f + i * 1.6f
            val alpha = (128 + sin(ph) * 127).toInt().coerceIn(0, 255)
            pS.color = (alpha shl 24) or 0x009CA3AF
            pS.strokeWidth = s * 0.08f
            val x0 = cx - s * 0.3f + i * s * 0.6f
            val steamPath = android.graphics.Path()
            steamPath.moveTo(x0, fy - s * 2.3f)
            steamPath.quadTo(x0 + sin(ph) * s * 0.25f, fy - s * 2.8f, x0, fy - s * 3.3f)
            c.drawPath(steamPath, pS)
        }
    }

    /** Яблоко */
    private fun apple() = Def("apple", "Яблоко", Kind.FOOD, Reaction.EAT, 2.2f) { c, cx, fy, s, _ ->
        circle(c, cx, fy - s * 1.0f, s * 0.95f, 0xFFEF4444.toInt())
        circle(c, cx - s * 0.35f, fy - s * 1.25f, s * 0.2f, 0x5CFFFFFF.toInt())
        line(c, cx, fy - s * 1.9f, cx + s * 0.15f, fy - s * 2.4f, s * 0.1f, 0xFF78350F.toInt())
        rect.set(cx + s * 0.15f, fy - s * 2.5f, cx + s * 0.85f, fy - s * 2.05f)
        pF.color = 0xFF22C55E.toInt()
        c.save()
        c.rotate(-25f, cx + s * 0.5f, fy - s * 2.28f)
        c.drawOval(rect, pF)
        c.restore()
    }

    /** Мороженое */
    private fun icecream() = Def("icecream", "Мороженое", Kind.FOOD, Reaction.EAT, 2.2f) { c, cx, fy, s, _ ->
        val cone = android.graphics.Path()
        val topY = fy - s * 1.7f
        cone.moveTo(cx, fy - s * 0.05f)
        cone.lineTo(cx - s * 0.62f, topY)
        cone.lineTo(cx + s * 0.62f, topY)
        cone.close()
        pF.color = 0xFFD97706.toInt()
        c.drawPath(cone, pF)
        line(c, cx - s * 0.4f, topY - s * 0.55f, cx - s * 0.05f, topY - s * 0.15f, s * 0.06f, 0xFFB45309.toInt())
        line(c, cx + s * 0.05f, topY - s * 0.7f, cx + s * 0.42f, topY - s * 0.2f, s * 0.06f, 0xFFB45309.toInt())
        circle(c, cx, topY - s * 0.55f, s * 0.75f, 0xFFF9A8D4.toInt())
        circle(c, cx - s * 0.25f, topY - s * 0.8f, s * 0.18f, 0x59FFFFFF.toInt())
    }

    /** Подарочная коробка — питомец открывает её и достаёт предмет */
    private fun box() = Def("box", "Подарок", Kind.BOX, Reaction.OPEN, 2.8f) { c, cx, fy, s, _ ->
        val bw = s * 1.15f   // половина ширины
        val bh = s * 1.05f   // высота
        // коробка
        roundRect(c, cx - bw, fy - bh, cx + bw, fy - s * 0.02f, s * 0.1f, 0xFFE3A56F.toInt())
        // лента вертикальная
        rect.set(cx - s * 0.16f, fy - bh, cx + s * 0.16f, fy - s * 0.02f)
        pF.color = 0xFFE05B5B.toInt()
        c.drawRect(rect, pF)
        // крышка (чуть шире)
        roundRect(c, cx - bw * 1.08f, fy - bh - s * 0.3f, cx + bw * 1.08f, fy - bh + s * 0.12f, s * 0.09f, 0xFFD8945C.toInt())
        rect.set(cx - s * 0.18f, fy - bh - s * 0.3f, cx + s * 0.18f, fy - bh + s * 0.12f)
        pF.color = 0xFFE05B5B.toInt()
        c.drawRect(rect, pF)
        // бантик
        ellipse(c, cx - s * 0.26f, fy - bh - s * 0.42f, s * 0.24f, s * 0.14f, 0xFFE05B5B.toInt())
        ellipse(c, cx + s * 0.26f, fy - bh - s * 0.42f, s * 0.24f, s * 0.14f, 0xFFE05B5B.toInt())
        circle(c, cx, fy - bh - s * 0.42f, s * 0.09f, 0xFFB94141.toInt())
    }
}

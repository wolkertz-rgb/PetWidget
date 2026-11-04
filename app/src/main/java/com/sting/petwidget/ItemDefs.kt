package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * 18 предметов комнаты (10 игрушек + 7 еды + подарок), visual-v3.
 * Язык «мягкой игрушки»: узнаваемый силуэт, единый тёплый контур INK,
 * 2-3 тона + один белый блик, предметы чуть крупнее старых —
 * чтобы не угадывать, что заспавнилось.
 *
 * Единица масштаба s = ~1/24 ширины виджета; всё рисуется векторно на Canvas.
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
    // ЛЕНЬВО-инициализация: в юнит-тестах Android-классы не создаются, пока не начнётся рисование

    private val pF: Paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
    private val pS: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
    private val rect: RectF by lazy { RectF() }
    private val path: Path by lazy { Path() }

    /** единый тёплый контур всего мира (visual-v3) */
    private val INK = 0xFF4A4038.toInt()

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

    /** тёмный контур-«ink» вокруг круга / овала / прямоугольника / пути */
    private fun ink(c: Canvas, cx: Float, cy: Float, r: Float, w: Float) {
        pS.color = INK
        pS.strokeWidth = w
        c.drawCircle(cx, cy, r, pS)
    }

    private fun inkOval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, w: Float) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        pS.color = INK
        pS.strokeWidth = w
        c.drawOval(rect, pS)
    }

    private fun inkRound(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, r: Float, w: Float) {
        rect.set(x0, y0, x1, y1)
        pS.color = INK
        pS.strokeWidth = w
        c.drawRoundRect(rect, r, r, pS)
    }

    private fun fillPath(c: Canvas, color: Int) {
        pF.color = color
        c.drawPath(path, pF)
    }

    private fun inkPath(c: Canvas, w: Float) {
        pS.color = INK
        pS.strokeWidth = w
        c.drawPath(path, pS)
    }

    private fun contactShadow(c: Canvas, cx: Float, fy: Float, rx: Float, s: Float) {
        ellipse(c, cx, fy - s * 0.05f, rx, s * 0.13f, 0x26000000)
    }

    // ------------------------------------------------------------ игрушки

    /** Глянцевый мяч с цельными цветными панелями. */
    private fun ball() = Def("ball", "Мячик", Kind.TOY, Reaction.PUSH, 2.4f) { c, cx, fy, s, _ ->
        val r = s * 1.08f
        val cy = fy - r
        contactShadow(c, cx, fy, r * 0.82f, s)
        circle(c, cx, cy, r, 0xFFFFD166.toInt())
        rect.set(cx - r, cy - r, cx + r, cy + r)
        pF.color = 0xFFEF6F7B.toInt()
        c.drawArc(rect, 205f, 82f, true, pF)
        pF.color = 0xFF5AB9A6.toInt()
        c.drawArc(rect, 287f, 82f, true, pF)
        path.reset()
        path.moveTo(cx - r * 0.12f, cy - r)
        path.cubicTo(cx + r * 0.18f, cy - r * 0.5f, cx + r * 0.18f, cy + r * 0.5f, cx - r * 0.12f, cy + r)
        pS.color = 0xBFFFFFFF.toInt()
        pS.strokeWidth = s * 0.08f
        c.drawPath(path, pS)
        ink(c, cx, cy, r, s * 0.1f)
        ellipse(c, cx - r * 0.38f, cy - r * 0.42f, r * 0.18f, r * 0.28f, 0xA6FFFFFF.toInt())
    }

    /** Деревянный кубик в три четверти: верх, лицевая и боковая грани. */
    private fun block() = Def("block", "Кубик", Kind.TOY, Reaction.PUSH, 2.2f) { c, cx, fy, s, _ ->
        val left = cx - s * 1.05f
        val right = cx + s * 1.05f
        val top = fy - s * 2.05f
        val split = cx + s * 0.58f
        contactShadow(c, cx, fy, s * 1.0f, s)
        path.reset()
        path.moveTo(left, top + s * 0.38f); path.lineTo(cx + s * 0.45f, top)
        path.lineTo(right, top + s * 0.35f); path.lineTo(split, top + s * 0.72f); path.close()
        fillPath(c, 0xFFFFD99A.toInt()); inkPath(c, s * 0.1f)
        path.reset()
        path.moveTo(left, top + s * 0.38f); path.lineTo(split, top + s * 0.72f)
        path.lineTo(split, fy - s * 0.08f); path.lineTo(left, fy - s * 0.4f); path.close()
        fillPath(c, 0xFFF4B95F.toInt()); inkPath(c, s * 0.1f)
        path.reset()
        path.moveTo(split, top + s * 0.72f); path.lineTo(right, top + s * 0.35f)
        path.lineTo(right, fy - s * 0.42f); path.lineTo(split, fy - s * 0.08f); path.close()
        fillPath(c, 0xFFD9903D.toInt()); inkPath(c, s * 0.1f)
        roundRect(c, cx - s * 0.55f, fy - s * 1.45f, cx + s * 0.12f, fy - s * 0.65f, s * 0.14f, 0xFFFFE2A8.toInt())
        line(c, cx - s * 0.35f, fy - s * 1.25f, cx - s * 0.1f, fy - s * 0.85f, s * 0.12f, 0xFFC76D2E.toInt())
        line(c, cx - s * 0.1f, fy - s * 0.85f, cx - s * 0.43f, fy - s * 0.86f, s * 0.12f, 0xFFC76D2E.toInt())
    }

    /** Игральная кость с мягким скруглением и утопленными точками. */
    private fun dice() = Def("dice", "Кости", Kind.TOY, Reaction.LIFT, 2.2f) { c, cx, fy, s, _ ->
        val half = s * 1.02f
        val cy = fy - s * 1.02f
        contactShadow(c, cx, fy, s * 0.92f, s)
        roundRect(c, cx - half, cy - half, cx + half, cy + half, s * 0.34f, 0xFFFFFCF5.toInt())
        roundRect(c, cx - half * 0.82f, cy - half * 0.86f, cx + half * 0.64f, cy - half * 0.67f, s * 0.09f, 0xFFFFFFFF.toInt())
        inkRound(c, cx - half, cy - half, cx + half, cy + half, s * 0.34f, s * 0.1f)
        val d = s * 0.16f
        val o = s * 0.5f
        for ((dx, dy) in listOf(-o to -o, o to -o, 0f to 0f, -o to o, o to o)) {
            circle(c, cx + dx, cy + dy, d * 1.18f, 0xFFE0D8CA.toInt())
            circle(c, cx + dx, cy + dy - s * 0.03f, d, 0xFF514A43.toInt())
        }
    }

    /** Воздушный шарик — капля + узелок + верёвочка */
    private fun balloon() = Def("balloon", "Шарик", Kind.TOY, Reaction.LIFT, 2.1f) { c, cx, fy, s, t ->
        val sway = sin(t * 1.4f) * s * 0.16f
        val bx = cx + sway
        val ry = s * 1.35f
        val by = fy - s * 3.35f
        path.reset(); path.moveTo(bx, by + ry + s * 0.24f)
        path.cubicTo(cx - s * 0.5f, fy - s * 1.75f, cx + s * 0.45f, fy - s * 0.9f, cx, fy - s * 0.08f)
        pS.color = 0xFF8C837B.toInt(); pS.strokeWidth = s * 0.065f; c.drawPath(path, pS)
        ellipse(c, bx, by, s * 1.0f, ry, 0xFFEF6F9A.toInt())
        ellipse(c, bx + s * 0.35f, by + s * 0.08f, s * 0.5f, s * 1.02f, 0x1F8D174C)
        inkOval(c, bx, by, s * 1.0f, ry, s * 0.1f)
        path.reset(); path.moveTo(bx, by + ry - s * 0.05f)
        path.lineTo(bx - s * 0.2f, by + ry + s * 0.3f); path.lineTo(bx + s * 0.2f, by + ry + s * 0.3f); path.close()
        fillPath(c, 0xFFD94A7C.toInt()); inkPath(c, s * 0.07f)
        ellipse(c, bx - s * 0.34f, by - s * 0.48f, s * 0.2f, s * 0.38f, 0xA6FFFFFF.toInt())
    }

    /** Мягкий мишка — круглые уши, светлая морда */
    private fun plushie() = Def("plushie", "Мишка", Kind.TOY, Reaction.RUB, 2.6f) { c, cx, fy, s, _ ->
        val fur = 0xFFBE8657.toInt(); val darkFur = 0xFFA86D42.toInt(); val cream = 0xFFF2D2AE.toInt()
        contactShadow(c, cx, fy, s * 1.08f, s)
        circle(c, cx - s * 0.62f, fy - s * 3.0f, s * 0.4f, darkFur)
        circle(c, cx + s * 0.62f, fy - s * 3.0f, s * 0.4f, darkFur)
        ink(c, cx - s * 0.62f, fy - s * 3.0f, s * 0.4f, s * 0.09f)
        ink(c, cx + s * 0.62f, fy - s * 3.0f, s * 0.4f, s * 0.09f)
        circle(c, cx - s * 0.62f, fy - s * 3.0f, s * 0.2f, cream)
        circle(c, cx + s * 0.62f, fy - s * 3.0f, s * 0.2f, cream)
        ellipse(c, cx, fy - s * 1.18f, s * 0.94f, s * 1.24f, fur)
        inkOval(c, cx, fy - s * 1.18f, s * 0.94f, s * 1.24f, s * 0.1f)
        ellipse(c, cx - s * 0.93f, fy - s * 1.35f, s * 0.35f, s * 0.62f, darkFur)
        ellipse(c, cx + s * 0.93f, fy - s * 1.35f, s * 0.35f, s * 0.62f, darkFur)
        ellipse(c, cx - s * 0.56f, fy - s * 0.28f, s * 0.5f, s * 0.3f, darkFur)
        ellipse(c, cx + s * 0.56f, fy - s * 0.28f, s * 0.5f, s * 0.3f, darkFur)
        circle(c, cx, fy - s * 2.5f, s * 0.83f, fur); ink(c, cx, fy - s * 2.5f, s * 0.83f, s * 0.1f)
        ellipse(c, cx, fy - s * 2.28f, s * 0.46f, s * 0.35f, cream)
        circle(c, cx - s * 0.3f, fy - s * 2.62f, s * 0.1f, 0xFF453329.toInt())
        circle(c, cx + s * 0.3f, fy - s * 2.62f, s * 0.1f, 0xFF453329.toInt())
        ellipse(c, cx, fy - s * 2.35f, s * 0.13f, s * 0.1f, 0xFF453329.toInt())
        line(c, cx, fy - s * 2.25f, cx, fy - s * 2.1f, s * 0.055f, 0xFF76513B.toInt())
        line(c, cx - s * 0.16f, fy - s * 2.08f, cx, fy - s * 2.1f, s * 0.055f, 0xFF76513B.toInt())
        line(c, cx, fy - s * 2.1f, cx + s * 0.16f, fy - s * 2.08f, s * 0.055f, 0xFF76513B.toInt())
        path.reset(); path.moveTo(cx - s * 0.36f, fy - s * 1.65f); path.lineTo(cx, fy - s * 1.35f)
        path.lineTo(cx + s * 0.36f, fy - s * 1.65f); path.lineTo(cx + s * 0.25f, fy - s * 1.15f)
        path.lineTo(cx - s * 0.25f, fy - s * 1.15f); path.close(); fillPath(c, 0xFFDF5D67.toInt())
        circle(c, cx, fy - s * 1.38f, s * 0.13f, 0xFFF6C85F.toInt())
    }

    /** Робот — большой экран-лицо, светящиеся глаза-плашки */
    private fun robot() = Def("robot", "Робот", Kind.TOY, Reaction.PLAY, 2.4f) { c, cx, fy, s, t ->
        val body = 0xFFB9C9D8.toInt(); val side = 0xFF8299AE.toInt(); val dark = 0xFF314353.toInt()
        val blink = sin(t * 3.0f) > 0f
        contactShadow(c, cx, fy, s * 1.15f, s)
        line(c, cx - s * 0.86f, fy - s * 1.55f, cx - s * 1.27f, fy - s * 0.85f, s * 0.22f, side)
        line(c, cx + s * 0.86f, fy - s * 1.55f, cx + s * 1.27f, fy - s * 0.85f, s * 0.22f, side)
        circle(c, cx - s * 1.3f, fy - s * 0.75f, s * 0.2f, body)
        circle(c, cx + s * 1.3f, fy - s * 0.75f, s * 0.2f, body)
        roundRect(c, cx - s * 0.85f, fy - s * 1.85f, cx + s * 0.85f, fy - s * 0.45f, s * 0.25f, body)
        roundRect(c, cx + s * 0.48f, fy - s * 1.76f, cx + s * 0.74f, fy - s * 0.55f, s * 0.1f, side)
        inkRound(c, cx - s * 0.85f, fy - s * 1.85f, cx + s * 0.85f, fy - s * 0.45f, s * 0.25f, s * 0.1f)
        roundRect(c, cx - s * 0.47f, fy - s * 1.52f, cx + s * 0.25f, fy - s * 0.94f, s * 0.12f, 0xFFF0F6F7.toInt())
        circle(c, cx - s * 0.22f, fy - s * 1.23f, s * 0.11f, 0xFFEF6F6C.toInt())
        circle(c, cx + s * 0.08f, fy - s * 1.23f, s * 0.11f, 0xFFF4C95D.toInt())
        line(c, cx - s * 0.48f, fy - s * 0.45f, cx - s * 0.48f, fy - s * 0.12f, s * 0.24f, side)
        line(c, cx + s * 0.48f, fy - s * 0.45f, cx + s * 0.48f, fy - s * 0.12f, s * 0.24f, side)
        roundRect(c, cx - s * 0.8f, fy - s * 0.22f, cx - s * 0.2f, fy - s * 0.03f, s * 0.08f, dark)
        roundRect(c, cx + s * 0.2f, fy - s * 0.22f, cx + s * 0.8f, fy - s * 0.03f, s * 0.08f, dark)
        roundRect(c, cx - s * 0.72f, fy - s * 3.18f, cx + s * 0.72f, fy - s * 1.82f, s * 0.3f, body)
        inkRound(c, cx - s * 0.72f, fy - s * 3.18f, cx + s * 0.72f, fy - s * 1.82f, s * 0.3f, s * 0.1f)
        roundRect(c, cx - s * 0.52f, fy - s * 2.95f, cx + s * 0.52f, fy - s * 2.14f, s * 0.2f, dark)
        val glow = if (blink) 0xFFB9F4D8.toInt() else 0xFF63D6AE.toInt()
        roundRect(c, cx - s * 0.36f, fy - s * 2.72f, cx - s * 0.08f, fy - s * 2.48f, s * 0.08f, glow)
        roundRect(c, cx + s * 0.08f, fy - s * 2.72f, cx + s * 0.36f, fy - s * 2.48f, s * 0.08f, glow)
        line(c, cx - s * 0.2f, fy - s * 2.3f, cx + s * 0.2f, fy - s * 2.3f, s * 0.07f, glow)
        line(c, cx, fy - s * 3.18f, cx, fy - s * 3.58f, s * 0.09f, side)
        circle(c, cx, fy - s * 3.73f, s * 0.17f, if (blink) 0xFFFFD166.toInt() else 0xFFCF9D39.toInt())
    }

    /** Игрушечная ракета: цельный корпус, два плавника и сопло. */
    private fun rocket() = Def("rocket", "Ракета", Kind.TOY, Reaction.LIFT, 2.4f) { c, cx, fy, s, _ ->
        val top = fy - s * 4.05f
        contactShadow(c, cx, fy, s * 1.0f, s)
        // дальние плавники
        path.reset()
        path.moveTo(cx - s * 0.55f, fy - s * 1.45f); path.lineTo(cx - s * 1.25f, fy - s * 0.18f)
        path.lineTo(cx - s * 0.45f, fy - s * 0.5f); path.close(); fillPath(c, 0xFFDA5260.toInt()); inkPath(c, s * 0.1f)
        path.reset(); path.moveTo(cx + s * 0.55f, fy - s * 1.45f); path.lineTo(cx + s * 1.25f, fy - s * 0.18f)
        path.lineTo(cx + s * 0.45f, fy - s * 0.5f); path.close(); fillPath(c, 0xFFDA5260.toInt()); inkPath(c, s * 0.1f)
        // корпус
        path.reset(); path.moveTo(cx, top)
        path.cubicTo(cx + s * 0.92f, fy - s * 3.0f, cx + s * 0.78f, fy - s * 1.35f, cx + s * 0.47f, fy - s * 0.48f)
        path.lineTo(cx - s * 0.47f, fy - s * 0.48f)
        path.cubicTo(cx - s * 0.78f, fy - s * 1.35f, cx - s * 0.92f, fy - s * 3.0f, cx, top); path.close()
        fillPath(c, 0xFFF4F1E8.toInt()); inkPath(c, s * 0.11f)
        // красный обтекатель
        path.reset(); path.moveTo(cx, top); path.cubicTo(cx + s * 0.45f, fy - s * 3.55f, cx + s * 0.58f, fy - s * 3.05f, cx + s * 0.63f, fy - s * 2.8f)
        path.lineTo(cx - s * 0.63f, fy - s * 2.8f); path.cubicTo(cx - s * 0.58f, fy - s * 3.05f, cx - s * 0.45f, fy - s * 3.55f, cx, top); path.close()
        fillPath(c, 0xFFEF6570.toInt()); inkPath(c, s * 0.08f)
        // панель и иллюминатор
        roundRect(c, cx - s * 0.52f, fy - s * 1.45f, cx + s * 0.52f, fy - s * 0.78f, s * 0.2f, 0xFFE1DDD2.toInt())
        circle(c, cx, fy - s * 2.18f, s * 0.43f, 0xFF40586A.toInt())
        circle(c, cx, fy - s * 2.18f, s * 0.31f, 0xFF65C6D4.toInt())
        ellipse(c, cx - s * 0.11f, fy - s * 2.31f, s * 0.1f, s * 0.14f, 0xBFFFFFFF.toInt())
        ink(c, cx, fy - s * 2.18f, s * 0.43f, s * 0.08f)
        // сопло
        path.reset(); path.moveTo(cx - s * 0.38f, fy - s * 0.5f); path.lineTo(cx - s * 0.5f, fy - s * 0.08f)
        path.lineTo(cx + s * 0.5f, fy - s * 0.08f); path.lineTo(cx + s * 0.38f, fy - s * 0.5f); path.close()
        fillPath(c, 0xFF657786.toInt()); inkPath(c, s * 0.08f)
    }

    /** Кораблик — корпус-трапеция, белый парус, красный флажок */
    private fun boat() = Def("boat", "Кораблик", Kind.TOY, Reaction.LIFT, 2.8f) { c, cx, fy, s, _ ->
        contactShadow(c, cx, fy, s * 1.3f, s)
        line(c, cx - s * 0.1f, fy - s * 1.2f, cx - s * 0.1f, fy - s * 3.55f, s * 0.11f, 0xFF77513A.toInt())
        // большой парус
        path.reset(); path.moveTo(cx + s * 0.04f, fy - s * 3.38f)
        path.quadTo(cx + s * 1.32f, fy - s * 2.25f, cx + s * 0.08f, fy - s * 1.42f); path.close()
        fillPath(c, 0xFFF9F4E8.toInt()); inkPath(c, s * 0.09f)
        path.reset(); path.moveTo(cx - s * 0.22f, fy - s * 3.18f)
        path.lineTo(cx - s * 0.24f, fy - s * 1.48f); path.lineTo(cx - s * 1.03f, fy - s * 1.48f); path.close()
        fillPath(c, 0xFF6FC3C0.toInt()); inkPath(c, s * 0.09f)
        // корпус с верхней палубой
        path.reset(); path.moveTo(cx - s * 1.45f, fy - s * 1.32f); path.lineTo(cx + s * 1.45f, fy - s * 1.32f)
        path.quadTo(cx + s * 1.05f, fy - s * 0.15f, cx + s * 0.68f, fy - s * 0.08f)
        path.lineTo(cx - s * 0.82f, fy - s * 0.08f); path.quadTo(cx - s * 1.18f, fy - s * 0.38f, cx - s * 1.45f, fy - s * 1.32f); path.close()
        fillPath(c, 0xFFDF7355.toInt()); inkPath(c, s * 0.1f)
        line(c, cx - s * 1.18f, fy - s * 0.93f, cx + s * 1.22f, fy - s * 0.93f, s * 0.1f, 0xFFF4B06A.toInt())
        circle(c, cx - s * 0.5f, fy - s * 0.62f, s * 0.13f, 0xFF40586A.toInt())
        circle(c, cx + s * 0.02f, fy - s * 0.62f, s * 0.13f, 0xFF40586A.toInt())
        circle(c, cx + s * 0.54f, fy - s * 0.62f, s * 0.13f, 0xFF40586A.toInt())
        path.reset(); path.moveTo(cx - s * 0.1f, fy - s * 3.55f); path.lineTo(cx + s * 0.58f, fy - s * 3.31f)
        path.lineTo(cx - s * 0.1f, fy - s * 3.05f); path.close(); fillPath(c, 0xFFF1C453.toInt()); inkPath(c, s * 0.06f)
    }

    /** Цветок в горшке — крупный бутон из пяти лепестков */
    private fun flower() = Def("flower", "Цветок", Kind.TOY, Reaction.SMELL, 2.2f) { c, cx, fy, s, _ ->
        val pot = s * 0.78f
        contactShadow(c, cx, fy, s * 0.78f, s)
        // стебель и листья находятся за горшком
        line(c, cx, fy - s * 1.45f, cx, fy - s * 3.0f, s * 0.13f, 0xFF398B58.toInt())
        path.reset(); path.moveTo(cx - s * 0.03f, fy - s * 2.15f)
        path.quadTo(cx - s * 0.8f, fy - s * 2.55f, cx - s * 0.72f, fy - s * 1.85f)
        path.quadTo(cx - s * 0.35f, fy - s * 1.78f, cx - s * 0.03f, fy - s * 2.15f); path.close(); fillPath(c, 0xFF55A96B.toInt())
        path.reset(); path.moveTo(cx + s * 0.03f, fy - s * 2.48f)
        path.quadTo(cx + s * 0.7f, fy - s * 2.75f, cx + s * 0.62f, fy - s * 2.15f)
        path.quadTo(cx + s * 0.28f, fy - s * 2.12f, cx + s * 0.03f, fy - s * 2.48f); path.close(); fillPath(c, 0xFF72BB78.toInt())
        // горшок
        path.reset()
        path.moveTo(cx - pot, fy - s * 1.5f)
        path.lineTo(cx + pot, fy - s * 1.5f)
        path.lineTo(cx + pot * 0.68f, fy - s * 0.05f)
        path.lineTo(cx - pot * 0.68f, fy - s * 0.05f)
        path.close()
        fillPath(c, 0xFFD98758.toInt()); inkPath(c, s * 0.1f)
        roundRect(c, cx - pot * 1.08f, fy - s * 1.72f, cx + pot * 1.08f, fy - s * 1.34f, s * 0.13f, 0xFFF0A06A.toInt())
        inkRound(c, cx - pot * 1.08f, fy - s * 1.72f, cx + pot * 1.08f, fy - s * 1.34f, s * 0.13f, s * 0.08f)
        line(c, cx - s * 0.42f, fy - s * 1.05f, cx + s * 0.42f, fy - s * 0.88f, s * 0.07f, 0x66FFFFFF)
        // многослойный бутон
        val cy = fy - s * 3.38f
        for (i in 0..4) {
            val ang = i * (Math.PI * 2 / 5).toFloat() - 1.57f
            val px = cx + cos(ang) * s * 0.47f; val py = cy + sin(ang) * s * 0.47f
            circle(c, px, py, s * 0.38f, 0xFFF47FA1.toInt()); ink(c, px, py, s * 0.38f, s * 0.065f)
        }
        circle(c, cx, cy, s * 0.37f, 0xFFFFD166.toInt()); ink(c, cx, cy, s * 0.37f, s * 0.08f)
        circle(c, cx - s * 0.11f, cy - s * 0.12f, s * 0.07f, 0x80FFFFFF.toInt())
    }

    /** Юла — жёлтая шапочка, фиолетовый диск, наконечник */
    private fun top() = Def("top", "Юла", Kind.TOY, Reaction.PLAY, 2.2f) { c, cx, fy, s, t ->
        val tilt = 0f
        contactShadow(c, cx, fy, s * 0.88f, s)
        line(c, cx + tilt, fy - s * 3.0f, cx + tilt * 0.3f, fy - s * 2.35f, s * 0.16f, 0xFF6B4D8D.toInt())
        roundRect(c, cx - s * 0.34f + tilt, fy - s * 3.25f, cx + s * 0.34f + tilt, fy - s * 2.94f, s * 0.13f, 0xFFFFD166.toInt())
        inkRound(c, cx - s * 0.34f + tilt, fy - s * 3.25f, cx + s * 0.34f + tilt, fy - s * 2.94f, s * 0.13f, s * 0.07f)
        // колоколообразный корпус из двух слоёв
        path.reset(); path.moveTo(cx, fy - s * 2.58f)
        path.cubicTo(cx + s * 0.72f, fy - s * 2.48f, cx + s * 1.18f, fy - s * 1.83f, cx + s * 1.02f, fy - s * 1.48f)
        path.quadTo(cx, fy - s * 0.85f, cx - s * 1.02f, fy - s * 1.48f)
        path.cubicTo(cx - s * 1.18f, fy - s * 1.83f, cx - s * 0.72f, fy - s * 2.48f, cx, fy - s * 2.58f); path.close()
        fillPath(c, 0xFF9279D3.toInt()); inkPath(c, s * 0.1f)
        path.reset(); path.moveTo(cx - s * 0.96f, fy - s * 1.65f)
        path.quadTo(cx, fy - s * 1.08f, cx + s * 0.96f, fy - s * 1.65f)
        path.lineTo(cx + s * 0.85f, fy - s * 1.38f)
        path.quadTo(cx, fy - s * 0.9f, cx - s * 0.85f, fy - s * 1.38f); path.close(); fillPath(c, 0xFF5FC2B0.toInt())
        ellipse(c, cx, fy - s * 1.52f, s * 0.9f, s * 0.23f, 0x4DFFFFFF)
        // Нижняя чаша плавно сходится к короткому острию.
        path.reset(); path.moveTo(cx - s * 0.92f, fy - s * 1.4f)
        path.quadTo(cx - s * 0.6f, fy - s * 0.65f, cx, fy - s * 0.25f)
        path.quadTo(cx + s * 0.6f, fy - s * 0.65f, cx + s * 0.92f, fy - s * 1.4f)
        path.quadTo(cx, fy - s * 0.96f, cx - s * 0.92f, fy - s * 1.4f); path.close()
        fillPath(c, 0xFF6B4D8D.toInt()); inkPath(c, s * 0.09f)
        line(c, cx, fy - s * 0.28f, cx, fy - s * 0.06f, s * 0.12f, INK)
        // Светлая метка на корпусе показывает осевое вращение.
        val phase = t * 8f
        if (cos(phase) > 0f) {
            ellipse(c, cx + sin(phase) * s * 0.72f, fy - s * 1.85f,
                s * 0.09f * cos(phase), s * 0.17f, 0xA6FFFFFF.toInt())
        }
    }

    // --------------------------------------------------------------- еда

    /** Пицца — сектор с толстой корочкой и крупной пепперони */
    private fun pizza() = Def("pizza", "Пицца", Kind.FOOD, Reaction.EAT, 2.8f) { c, cx, fy, s, _ ->
        val tipY = fy - s * 0.1f
        val topY = fy - s * 2.5f
        path.reset()
        path.moveTo(cx, tipY)
        path.lineTo(cx - s * 1.35f, topY)
        path.lineTo(cx + s * 1.35f, topY)
        path.close()
        pF.color = 0xFFFBBF24.toInt()
        c.drawPath(path, pF)
        pS.color = INK
        pS.strokeWidth = s * 0.11f
        c.drawPath(path, pS)
        roundRect(c, cx - s * 1.4f, topY - s * 0.3f, cx + s * 1.4f, topY + s * 0.12f, s * 0.24f, 0xFFD97706.toInt())
        inkRound(c, cx - s * 1.4f, topY - s * 0.3f, cx + s * 1.4f, topY + s * 0.12f, s * 0.24f, s * 0.1f)
        circle(c, cx, fy - s * 1.5f, s * 0.26f, 0xFFDC2626.toInt())
        circle(c, cx - s * 0.52f, fy - s * 0.95f, s * 0.22f, 0xFFDC2626.toInt())
        circle(c, cx + s * 0.52f, fy - s * 0.95f, s * 0.22f, 0xFFDC2626.toInt())
    }

    /** Суши — рисовый овал, тёмная полоса нори, икринка сверху */
    private fun sushi() = Def("sushi", "Суши", Kind.FOOD, Reaction.EAT, 2.4f) { c, cx, fy, s, _ ->
        ellipse(c, cx, fy - s * 0.75f, s * 1.15f, s * 0.68f, 0xFFF8FAFC.toInt())
        inkOval(c, cx, fy - s * 0.75f, s * 1.15f, s * 0.68f, s * 0.1f)
        roundRect(c, cx - s * 0.32f, fy - s * 1.35f, cx + s * 0.32f, fy - s * 0.12f, s * 0.1f, 0xFF1E293B.toInt())
        circle(c, cx, fy - s * 1.28f, s * 0.18f, 0xFFFB923C.toInt())
        circle(c, cx - s * 0.06f, fy - s * 1.34f, s * 0.06f, 0x8CFFFFFF.toInt())
    }

    /** Пончик — толстое кольцо, розовая глазурь, посыпка, крупная дырка */
    private fun donut() = Def("donut", "Пончик", Kind.FOOD, Reaction.EAT, 2.8f) { c, cx, fy, s, _ ->
        val cy = fy - s * 1.05f
        val r = s * 1.05f
        pS.color = INK
        pS.strokeWidth = s * 1.2f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawOval(rect, pS)
        pS.color = 0xFFD97706.toInt()
        pS.strokeWidth = s * 0.95f
        c.drawOval(rect, pS)
        pS.color = 0xFFF472B6.toInt()
        pS.strokeWidth = s * 0.62f
        c.drawArc(rect, 170f, 200f, false, pS) // глазурь сверху
        line(c, cx - s * 0.7f, cy - s * 0.55f, cx - s * 0.45f, cy - s * 0.72f, s * 0.13f, 0xFFFDE047.toInt())
        line(c, cx + s * 0.5f, cy - s * 0.62f, cx + s * 0.72f, cy - s * 0.4f, s * 0.13f, 0xFF60A5FA.toInt())
        line(c, cx + s * 0.78f, cy + s * 0.2f, cx + s * 0.9f, cy + s * 0.45f, s * 0.13f, 0xFF4ADE80.toInt())
        circle(c, cx, cy, s * 0.42f, 0xFFEDEDED.toInt()) // дырка
        ink(c, cx, cy, s * 0.42f, s * 0.09f)
    }

    /** Печенье — диск с шестью крупными шоколадными каплями */
    private fun cookie() = Def("cookie", "Печенье", Kind.FOOD, Reaction.EAT, 2.4f) { c, cx, fy, s, _ ->
        val cy = fy - s * 0.95f
        circle(c, cx, cy, s * 1.05f, 0xFFD6A054.toInt())
        ink(c, cx, cy, s * 1.05f, s * 0.1f)
        circle(c, cx - s * 0.38f, cy - s * 0.4f, s * 0.19f, 0xFF5C3A21.toInt())
        circle(c, cx + s * 0.42f, cy - s * 0.28f, s * 0.17f, 0xFF5C3A21.toInt())
        circle(c, cx - s * 0.1f, cy + s * 0.05f, s * 0.2f, 0xFF5C3A21.toInt())
        circle(c, cx + s * 0.2f, cy + s * 0.52f, s * 0.16f, 0xFF5C3A21.toInt())
        circle(c, cx - s * 0.55f, cy + s * 0.42f, s * 0.15f, 0xFF5C3A21.toInt())
        circle(c, cx - s * 0.45f, cy - s * 0.58f, s * 0.12f, 0x59FFFFFF.toInt())
    }

    /** Кофе — высокая кружка, толстое кольцо-ручка, пар и сердечко */
    private fun coffee() = Def("coffee", "Кофе", Kind.FOOD, Reaction.EAT, 2.2f) { c, cx, fy, s, t ->
        roundRect(c, cx - s, fy - s * 2.0f, cx + s, fy - s * 0.05f, s * 0.3f, 0xFFFFF7EC.toInt())
        inkRound(c, cx - s, fy - s * 2.0f, cx + s, fy - s * 0.05f, s * 0.3f, s * 0.11f)
        // зерно сверху
        ellipse(c, cx, fy - s * 2.0f, s * 1.0f, s * 0.26f, 0xFF78350F.toInt())
        ellipse(c, cx, fy - s * 1.98f, s * 0.62f, s * 0.14f, 0xFFB45309.toInt())
        // ручка — толстое кольцо справа (тёмный обод + кремовая толщина)
        rect.set(cx + s * 0.92f, fy - s * 1.55f, cx + s * 1.52f, fy - s * 0.75f)
        pS.color = INK
        pS.strokeWidth = s * 0.3f
        c.drawOval(rect, pS)
        pS.color = 0xFFFFF7EC.toInt()
        pS.strokeWidth = s * 0.18f
        c.drawOval(rect, pS)
        // сердечко на кружке
        pF.color = 0xFFE58C8C.toInt()
        val hy = fy - s * 0.95f
        path.reset()
        path.moveTo(cx, hy + s * 0.22f)
        path.quadTo(cx - s * 0.3f, hy, cx - s * 0.12f, hy - s * 0.16f)
        path.quadTo(cx, hy - s * 0.24f, cx, hy - s * 0.06f)
        path.quadTo(cx, hy - s * 0.24f, cx + s * 0.12f, hy - s * 0.16f)
        path.quadTo(cx + s * 0.3f, hy, cx, hy + s * 0.22f)
        c.drawPath(path, pF)
        // пар поднимается
        for (i in 0..1) {
            val ph = t * 2.0f + i * 1.6f
            val alpha = (128 + sin(ph) * 127).toInt().coerceIn(0, 255)
            pS.color = (alpha shl 24) or 0x009CA3AF
            pS.strokeWidth = s * 0.09f
            val x1 = cx - s * 0.35f + i * s * 0.7f
            val steam = android.graphics.Path()
            steam.moveTo(x1, fy - s * 2.5f)
            steam.quadTo(x1 + sin(ph) * s * 0.3f, fy - s * 3.1f, x1, fy - s * 3.7f)
            c.drawPath(steam, pS)
        }
    }

    /** Яблоко — крупный красный бок, черенок, лист */
    private fun apple() = Def("apple", "Яблоко", Kind.FOOD, Reaction.EAT, 2.4f) { c, cx, fy, s, _ ->
        val cy = fy - s * 1.1f
        circle(c, cx, cy, s * 1.1f, 0xFFEF4444.toInt())
        ink(c, cx, cy, s * 1.1f, s * 0.1f)
        circle(c, cx - s * 0.4f, cy - s * 0.38f, s * 0.22f, 0x5CFFFFFF.toInt())
        line(c, cx, cy - s * 1.05f, cx + s * 0.18f, cy - s * 1.5f, s * 0.12f, 0xFF78350F.toInt())
        rect.set(cx + s * 0.08f, cy - s * 1.62f, cx + s * 0.95f, cy - s * 1.12f)
        pF.color = 0xFF22C55E.toInt()
        c.save()
        c.rotate(-22f, cx + s * 0.5f, cy - s * 1.37f)
        c.drawOval(rect, pF)
        c.restore()
    }

    /** Мороженое — вафельный рожок, мятный шар (не путать с розовым пончиком), вишня */
    private fun icecream() = Def("icecream", "Мороженое", Kind.FOOD, Reaction.EAT, 2.4f) { c, cx, fy, s, _ ->
        val topY = fy - s * 1.9f
        path.reset()
        path.moveTo(cx, fy - s * 0.05f)
        path.lineTo(cx - s * 0.66f, topY)
        path.lineTo(cx + s * 0.66f, topY)
        path.close()
        pF.color = 0xFFD97706.toInt()
        c.drawPath(path, pF)
        // Сетка обрезается силуэтом рожка и не торчит за его края.
        c.save()
        c.clipPath(path)
        for (i in -3..3) {
            val x = cx + i * s * 0.4f
            line(c, x - s, topY, x + s * 0.5f, fy, s * 0.055f, 0xFFB77A3F.toInt())
            line(c, x + s, topY, x - s * 0.5f, fy, s * 0.055f, 0xFFB77A3F.toInt())
        }
        c.restore()
        pS.color = INK
        pS.strokeWidth = s * 0.1f
        c.drawPath(path, pS)
        // Шар с мягким неровным краем плотно лежит на рожке.
        path.reset(); path.moveTo(cx - s * 0.78f, topY)
        path.cubicTo(cx - s * 1.05f, topY - s * 1.2f, cx + s * 1.05f, topY - s * 1.2f, cx + s * 0.78f, topY)
        path.quadTo(cx + s * 0.55f, topY + s * 0.18f, cx + s * 0.3f, topY + s * 0.05f)
        path.quadTo(cx, topY + s * 0.25f, cx - s * 0.3f, topY + s * 0.05f)
        path.quadTo(cx - s * 0.55f, topY + s * 0.18f, cx - s * 0.78f, topY); path.close()
        fillPath(c, 0xFFF6C4D6.toInt()); inkPath(c, s * 0.1f)
        // вишня с ножкой
        line(c, cx + s * 0.05f, topY - s * 1.05f, cx + s * 0.2f, topY - s * 1.3f, s * 0.06f, 0xFF538859.toInt())
        circle(c, cx + s * 0.05f, topY - s * 0.95f, s * 0.17f, 0xFFE05B5B.toInt())
        ink(c, cx + s * 0.05f, topY - s * 0.95f, s * 0.17f, s * 0.06f)
        // блик
        circle(c, cx - s * 0.3f, topY - s * 0.7f, s * 0.18f, 0x73FFFFFF.toInt())
    }

    /** Подарочная коробка — лента + крупный бант */
    private fun box() = Def("box", "Подарок", Kind.BOX, Reaction.OPEN, 3.0f) { c, cx, fy, s, _ ->
        val bw = s * 1.28f
        val bh = s * 1.2f
        roundRect(c, cx - bw, fy - bh, cx + bw, fy - s * 0.02f, s * 0.14f, 0xFFE3A56F.toInt())
        inkRound(c, cx - bw, fy - bh, cx + bw, fy - s * 0.02f, s * 0.14f, s * 0.1f)
        // лента вертикальная
        rect.set(cx - s * 0.2f, fy - bh, cx + s * 0.2f, fy - s * 0.02f)
        pF.color = 0xFFE05B5B.toInt()
        c.drawRect(rect, pF)
        // крышка (чуть шире)
        roundRect(c, cx - bw * 1.08f, fy - bh - s * 0.34f, cx + bw * 1.08f, fy - bh + s * 0.12f, s * 0.12f, 0xFFD8945C.toInt())
        inkRound(c, cx - bw * 1.08f, fy - bh - s * 0.34f, cx + bw * 1.08f, fy - bh + s * 0.12f, s * 0.12f, s * 0.09f)
        rect.set(cx - s * 0.22f, fy - bh - s * 0.34f, cx + s * 0.22f, fy - bh + s * 0.12f)
        pF.color = 0xFFE05B5B.toInt()
        c.drawRect(rect, pF)
        // бант
        ellipse(c, cx - s * 0.34f, fy - bh - s * 0.52f, s * 0.32f, s * 0.2f, 0xFFE05B5B.toInt())
        ellipse(c, cx + s * 0.34f, fy - bh - s * 0.52f, s * 0.32f, s * 0.2f, 0xFFE05B5B.toInt())
        circle(c, cx, fy - bh - s * 0.52f, s * 0.13f, 0xFFB94141.toInt())
    }
}

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

    // ------------------------------------------------------------ игрушки

    /** Мячик — пляжный, три сектора: читается даже пятном */
    private fun ball() = Def("ball", "Мячик", Kind.TOY, Reaction.PUSH, 2.4f) { c, cx, fy, s, _ ->
        val r = s * 1.12f
        val cy = fy - r
        circle(c, cx, cy, r, 0xFFF8F5EC.toInt())
        rect.set(cx - r, cy - r, cx + r, cy + r)
        pF.color = 0xFF7CC8A2.toInt()
        c.drawArc(rect, -60f, 60f, true, pF)      // мятный сектор
        pF.color = 0xFFF26D7E.toInt()
        c.drawArc(rect, 60f, 60f, true, pF)       // розовый сектор
        pF.color = 0xFFF4C95D.toInt()
        c.drawArc(rect, 180f, 60f, true, pF)      // жёлтый сектор
        ink(c, cx, cy, r, s * 0.1f)
        line(c, cx - r * 0.85f, cy, cx + r * 0.85f, cy, s * 0.08f, 0x40FFFFFF)
        circle(c, cx - r * 0.38f, cy - r * 0.4f, r * 0.2f, 0x8CFFFFFF.toInt())
    }

    /** Деревянный кубик — две грани + кант */
    private fun block() = Def("block", "Кубик", Kind.TOY, Reaction.PUSH, 2.2f) { c, cx, fy, s, _ ->
        val h = s * 1.85f
        roundRect(c, cx - h, fy - h, cx + h, fy - s * 0.04f, s * 0.26f, 0xFFFBBF69.toInt())
        roundRect(c, cx - h, fy - h, cx + h, fy - h * 0.66f, s * 0.26f, 0xFFFCD492.toInt())
        inkRound(c, cx - h, fy - h, cx + h, fy - s * 0.04f, s * 0.26f, s * 0.11f)
        line(c, cx - h * 0.5f, fy - h * 0.38f, cx + h * 0.5f, fy - h * 0.38f, s * 0.11f, 0xFFB45309.toInt())
        circle(c, cx - h * 0.55f, fy - h * 0.82f, s * 0.13f, 0x73FFFFFF.toInt())
    }

    /** Игральный кубик — пять крупных точек на белом */
    private fun dice() = Def("dice", "Кости", Kind.TOY, Reaction.LIFT, 2.2f) { c, cx, fy, s, _ ->
        val h = s * 1.8f
        roundRect(c, cx - h, fy - h, cx + h, fy - s * 0.04f, s * 0.34f, 0xFFFAF8F2.toInt())
        val d = s * 0.19f
        val o = h * 0.48f
        val cyc = fy - h * 0.52f
        val oy = o * 0.88f
        circle(c, cx - o, cyc - oy, d, 0xFF44403C.toInt())
        circle(c, cx + o, cyc - oy, d, 0xFF44403C.toInt())
        circle(c, cx, cyc, d, 0xFF44403C.toInt())
        circle(c, cx - o, cyc + oy, d, 0xFF44403C.toInt())
        circle(c, cx + o, cyc + oy, d, 0xFF44403C.toInt())
        inkRound(c, cx - h, fy - h, cx + h, fy - s * 0.04f, s * 0.34f, s * 0.11f)
    }

    /** Воздушный шарик — капля + узелок + верёвочка */
    private fun balloon() = Def("balloon", "Шарик", Kind.TOY, Reaction.LIFT, 2.1f) { c, cx, fy, s, t ->
        val sway = sin(t * 1.4f) * s * 0.18f
        val bx = cx + sway
        val ry = s * 1.45f
        val by = fy - s * 3.5f
        // верёвочка (узелок ляжет поверх)
        pS.color = 0xFF9CA3AF.toInt()
        pS.strokeWidth = s * 0.07f
        val str = android.graphics.Path()
        str.moveTo(bx, by + ry + s * 0.3f)
        str.quadTo(cx - sway, fy - s * 1.5f, cx, fy - s * 0.1f)
        c.drawPath(str, pS)
        ellipse(c, bx, by, s * 1.02f, ry, 0xFFF472B6.toInt())
        inkOval(c, bx, by, s * 1.02f, ry, s * 0.1f)
        pF.color = 0xFFDB2777.toInt()
        val knot = android.graphics.Path()
        knot.moveTo(bx - s * 0.16f, by + ry - s * 0.05f)
        knot.lineTo(bx + s * 0.16f, by + ry - s * 0.05f)
        knot.lineTo(bx, by + ry + s * 0.3f)
        knot.close()
        c.drawPath(knot, pF)
        ellipse(c, bx - s * 0.32f, by - ry * 0.42f, s * 0.24f, ry * 0.26f, 0x59FFFFFF.toInt())
    }

    /** Мягкий мишка — круглые уши, светлая морда */
    private fun plushie() = Def("plushie", "Мишка", Kind.TOY, Reaction.RUB, 2.6f) { c, cx, fy, s, _ ->
        val brown = 0xFFB98A5E.toInt()
        // ушки
        circle(c, cx - s * 0.62f, fy - s * 3.05f, s * 0.34f, brown)
        circle(c, cx + s * 0.62f, fy - s * 3.05f, s * 0.34f, brown)
        // голова
        circle(c, cx, fy - s * 2.45f, s * 0.78f, brown)
        // тело
        ellipse(c, cx, fy - s * 1.0f, s * 0.98f, s * 1.02f, brown)
        // контур мишки целиком
        ink(c, cx - s * 0.62f, fy - s * 3.05f, s * 0.34f, s * 0.09f)
        ink(c, cx + s * 0.62f, fy - s * 3.05f, s * 0.34f, s * 0.09f)
        ink(c, cx, fy - s * 2.45f, s * 0.78f, s * 0.1f)
        inkOval(c, cx, fy - s * 1.0f, s * 0.98f, s * 1.02f, s * 0.1f)
        // внутренняя ушек
        circle(c, cx - s * 0.62f, fy - s * 3.05f, s * 0.16f, 0xFFE8C9A8.toInt())
        circle(c, cx + s * 0.62f, fy - s * 3.05f, s * 0.16f, 0xFFE8C9A8.toInt())
        // морда
        ellipse(c, cx, fy - s * 2.22f, s * 0.4f, s * 0.3f, 0xFFE8C9A8.toInt())
        circle(c, cx - s * 0.3f, fy - s * 2.58f, s * 0.11f, 0xFF3F2D1D.toInt())
        circle(c, cx + s * 0.3f, fy - s * 2.58f, s * 0.11f, 0xFF3F2D1D.toInt())
        circle(c, cx - s * 0.27f, fy - s * 2.61f, s * 0.035f, 0x73FFFFFF.toInt())
        circle(c, cx + s * 0.33f, fy - s * 2.61f, s * 0.035f, 0x73FFFFFF.toInt())
        ellipse(c, cx, fy - s * 2.18f, s * 0.12f, s * 0.09f, 0xFF3F2D1D.toInt()) // носик
        // лапки
        ellipse(c, cx - s * 0.9f, fy - s * 0.92f, s * 0.32f, s * 0.24f, 0xFFA9713F.toInt())
        ellipse(c, cx + s * 0.9f, fy - s * 0.92f, s * 0.32f, s * 0.24f, 0xFFA9713F.toInt())
    }

    /** Робот — большой экран-лицо, светящиеся глаза-плашки */
    private fun robot() = Def("robot", "Робот", Kind.TOY, Reaction.PLAY, 2.4f) { c, cx, fy, s, t ->
        val body = 0xFFB8C4D9.toInt()
        val dark = 0xFF2E3B4A.toInt()
        val blink = sin(t * 3.0f) > 0f
        roundRect(c, cx - s * 0.95f, fy - s * 1.8f, cx + s * 0.95f, fy - s * 0.02f, s * 0.3f, body)
        inkRound(c, cx - s * 0.95f, fy - s * 1.8f, cx + s * 0.95f, fy - s * 0.02f, s * 0.3f, s * 0.1f)
        // голова-экран
        roundRect(c, cx - s * 0.68f, fy - s * 3.2f, cx + s * 0.68f, fy - s * 1.72f, s * 0.26f, dark)
        // глаза-плашки
        val glow = if (blink) 0xFFB6F0D0.toInt() else 0xFF68C9A4.toInt()
        roundRect(c, cx - s * 0.46f, fy - s * 2.9f, cx - s * 0.12f, fy - s * 2.52f, s * 0.1f, glow)
        roundRect(c, cx + s * 0.12f, fy - s * 2.9f, cx + s * 0.46f, fy - s * 2.52f, s * 0.1f, glow)
        // улыбка
        pS.color = 0xFF9FE8C4.toInt()
        pS.strokeWidth = s * 0.09f
        rect.set(cx - s * 0.26f, fy - s * 2.36f, cx + s * 0.26f, fy - s * 2.04f)
        c.drawArc(rect, 15f, 150f, false, pS)
        // антенна с широким шариком
        line(c, cx, fy - s * 3.2f, cx, fy - s * 3.72f, s * 0.09f, 0xFF64748B.toInt())
        circle(c, cx, fy - s * 3.9f, s * 0.19f, if (blink) 0xFFFFD54D.toInt() else 0xFF7A6420.toInt())
        // ручки
        line(c, cx - s * 0.95f, fy - s * 1.25f, cx - s * 1.35f, fy - s * 0.65f, s * 0.18f, body)
        line(c, cx + s * 0.95f, fy - s * 1.25f, cx + s * 1.35f, fy - s * 0.65f, s * 0.18f, body)
    }

    /** Ракета — красный нос, синий иллюминатор, плавники */
    private fun rocket() = Def("rocket", "Ракета", Kind.TOY, Reaction.LIFT, 2.4f) { c, cx, fy, s, _ ->
        val bodyTop = fy - s * 4.0f
        path.reset()
        path.moveTo(cx, bodyTop)                                  // нос
        path.quadTo(cx + s * 0.9f, fy - s * 2.0f, cx + s * 0.62f, fy - s * 0.1f)
        path.lineTo(cx - s * 0.62f, fy - s * 0.1f)
        path.quadTo(cx - s * 0.9f, fy - s * 2.0f, cx, bodyTop)
        path.close()
        pF.color = 0xFFF1F5F9.toInt()
        c.drawPath(path, pF)
        // нос-обтекатель
        pF.color = 0xFFEF4444.toInt()
        val nose = android.graphics.Path()
        nose.moveTo(cx, bodyTop)
        nose.quadTo(cx + s * 0.5f, fy - s * 3.2f, cx + s * 0.55f, fy - s * 2.8f)
        nose.lineTo(cx - s * 0.55f, fy - s * 2.8f)
        nose.quadTo(cx - s * 0.5f, fy - s * 3.2f, cx, bodyTop)
        nose.close()
        c.drawPath(nose, pF)
        // плавники
        pF.color = 0xFFEF4444.toInt()
        val fin = android.graphics.Path()
        fin.moveTo(cx - s * 0.6f, fy - s * 1.4f)
        fin.lineTo(cx - s * 1.2f, fy - s * 0.05f)
        fin.lineTo(cx - s * 0.6f, fy - s * 0.05f)
        fin.close()
        c.drawPath(fin, pF)
        val fin2 = android.graphics.Path()
        fin2.moveTo(cx + s * 0.6f, fy - s * 1.4f)
        fin2.lineTo(cx + s * 1.2f, fy - s * 0.05f)
        fin2.lineTo(cx + s * 0.6f, fy - s * 0.05f)
        fin2.close()
        c.drawPath(fin2, pF)
        // обводка корпуса поверх деталей
        pS.color = INK
        pS.strokeWidth = s * 0.1f
        c.drawPath(path, pS)
        // иллюминатор двойной
        circle(c, cx, fy - s * 2.05f, s * 0.38f, 0xFF60A5FA.toInt())
        circle(c, cx, fy - s * 2.05f, s * 0.2f, 0xFFBAE6FD.toInt())
        ink(c, cx, fy - s * 2.05f, s * 0.38f, s * 0.08f)
    }

    /** Кораблик — корпус-трапеция, белый парус, красный флажок */
    private fun boat() = Def("boat", "Кораблик", Kind.TOY, Reaction.LIFT, 2.8f) { c, cx, fy, s, _ ->
        path.reset()
        path.moveTo(cx - s * 1.4f, fy - s * 1.2f)
        path.lineTo(cx + s * 1.4f, fy - s * 1.2f)
        path.lineTo(cx + s * 0.9f, fy - s * 0.05f)
        path.lineTo(cx - s * 0.9f, fy - s * 0.05f)
        path.close()
        pF.color = 0xFFB45309.toInt()
        c.drawPath(path, pF)
        pS.color = INK
        pS.strokeWidth = s * 0.1f
        c.drawPath(path, pS)
        // мачта
        line(c, cx, fy - s * 1.2f, cx, fy - s * 3.4f, s * 0.11f, 0xFF78350F.toInt())
        // парус
        val sail = android.graphics.Path()
        sail.moveTo(cx + s * 0.12f, fy - s * 3.3f)
        sail.quadTo(cx + s * 1.35f, fy - s * 2.0f, cx + s * 0.12f, fy - s * 1.35f)
        sail.close()
        pF.color = 0xFFF8FAFC.toInt()
        c.drawPath(sail, pF)
        pS.color = INK
        pS.strokeWidth = s * 0.09f
        c.drawPath(sail, pS)
        // флажок
        val flag = android.graphics.Path()
        flag.moveTo(cx, fy - s * 3.4f)
        flag.lineTo(cx - s * 0.5f, fy - s * 3.2f)
        flag.lineTo(cx, fy - s * 3.0f)
        flag.close()
        pF.color = 0xFFE05B5B.toInt()
        c.drawPath(flag, pF)
    }

    /** Цветок в горшке — крупный бутон из пяти лепестков */
    private fun flower() = Def("flower", "Цветок", Kind.TOY, Reaction.SMELL, 2.2f) { c, cx, fy, s, _ ->
        val pot = s * 0.8f
        path.reset()
        path.moveTo(cx - pot, fy - s * 1.5f)
        path.lineTo(cx + pot, fy - s * 1.5f)
        path.lineTo(cx + pot * 0.68f, fy - s * 0.05f)
        path.lineTo(cx - pot * 0.68f, fy - s * 0.05f)
        path.close()
        pF.color = 0xFFD97706.toInt()
        c.drawPath(path, pF)
        pS.color = INK
        pS.strokeWidth = s * 0.1f
        c.drawPath(path, pS)
        roundRect(c, cx - pot * 1.06f, fy - s * 1.72f, cx + pot * 1.06f, fy - s * 1.34f, s * 0.12f, 0xFFEA9A3E.toInt())
        // стебель и лист
        line(c, cx, fy - s * 1.5f, cx, fy - s * 3.0f, s * 0.12f, 0xFF16A34A.toInt())
        ellipse(c, cx - s * 0.42f, fy - s * 2.2f, s * 0.32f, s * 0.15f, 0xFF22C55E.toInt())
        // бутон: пять лепестков + крупная сердцевина
        val cy = fy - s * 3.4f
        for (i in 0..4) {
            val ang = i * (Math.PI * 2 / 5).toFloat() - 1.57f
            circle(c, cx + cos(ang) * s * 0.46f, cy + sin(ang) * s * 0.46f, s * 0.32f, 0xFFF472B6.toInt())
        }
        circle(c, cx, cy, s * 0.28f, 0xFFFDE047.toInt())
        ink(c, cx, cy, s * 0.28f, s * 0.08f)
    }

    /** Юла — жёлтая шапочка, фиолетовый диск, наконечник */
    private fun top() = Def("top", "Юла", Kind.TOY, Reaction.PLAY, 2.2f) { c, cx, fy, s, t ->
        val rot = sin(t * 5.0f)
        val tilt = rot * s * 0.12f
        line(c, cx + tilt, fy - s * 2.9f, cx + tilt * 0.4f, fy - s * 2.4f, s * 0.16f, 0xFF7C3AED.toInt())
        circle(c, cx + tilt, fy - s * 3.1f, s * 0.2f, 0xFFFDE047.toInt())
        ellipse(c, cx, fy - s * 1.85f, s * 0.98f, s * 0.6f, 0xFFA78BFA.toInt())
        inkOval(c, cx, fy - s * 1.85f, s * 0.98f, s * 0.6f, s * 0.1f)
        ellipse(c, cx, fy - s * 1.55f, s * 0.55f, s * 0.32f, 0xFF7C3AED.toInt())
        line(c, cx, fy - s * 1.1f, cx, fy - s * 0.1f, s * 0.12f, 0xFF5B21B6.toInt())
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
        // вафельная сетка
        line(c, cx - s * 0.44f, topY + s * 0.1f, cx + s * 0.2f, fy - s * 0.15f, s * 0.06f, 0xFF8A4A08.toInt())
        line(c, cx - s * 0.1f, topY + s * 0.05f, cx + s * 0.45f, fy - s * 0.25f, s * 0.06f, 0xFF8A4A08.toInt())
        line(c, cx + s * 0.4f, topY + s * 0.15f, cx - s * 0.18f, fy - s * 0.18f, s * 0.06f, 0xFF8A4A08.toInt())
        pS.color = INK
        pS.strokeWidth = s * 0.1f
        c.drawPath(path, pS)
        // шар
        circle(c, cx, topY - s * 0.45f, s * 0.85f, 0xFFBFE8CF.toInt())
        ink(c, cx, topY - s * 0.45f, s * 0.85f, s * 0.1f)
        // вишня с ножкой
        line(c, cx + s * 0.05f, topY - s * 1.52f, cx + s * 0.2f, topY - s * 1.85f, s * 0.06f, 0xFF16A34A.toInt())
        circle(c, cx + s * 0.05f, topY - s * 1.42f, s * 0.2f, 0xFFE05B5B.toInt())
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

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

    private val foodArtwork by lazy { FoodArtwork() }
    private val toyArtwork by lazy { ToyArtwork() }

    val ALL: List<Def> = listOf(
        ball(), block(), dice(), balloon(), plushie(), robot(), rocket(),
        boat(), flower(), top(),
        pizza(), sushi(), donut(), cookie(), coffee(), apple(), icecream(),
        box()
    )

    // Keep the definition for items already present in saved games.
    val spawnable: List<Def> = ALL.filterNot { it.id == "flower" }

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
    private val INK = 0xFF514531.toInt()
    private val material by lazy { VectorFinish() }

    private fun circle(c: Canvas, cx: Float, cy: Float, r: Float, col: Int) {
        material.apply(pF, col, cx-r, cy-r, cx+r, cy+r)
        c.drawCircle(cx, cy, r, pF)
        pF.shader = null
    }

    private fun ellipse(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        material.apply(pF, col, rect.left, rect.top, rect.right, rect.bottom)
        c.drawOval(rect, pF)
        pF.shader = null
    }

    private fun roundRect(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, r: Float, col: Int) {
        rect.set(x0, y0, x1, y1)
        material.apply(pF, col, x0, y0, x1, y1)
        c.drawRoundRect(rect, r, r, pF)
        pF.shader = null
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
        path.computeBounds(rect, true)
        material.apply(pF, color, rect.left, rect.top, rect.right, rect.bottom)
        c.drawPath(path, pF)
        pF.shader = null
    }

    private fun inkPath(c: Canvas, w: Float) {
        pS.color = INK
        pS.strokeWidth = w
        c.drawPath(path, pS)
    }

    private var carrying = false

    fun drawCarried(def: Def, c: Canvas, x: Float, y: Float, unit: Float, time: Float) {
        carrying = true
        try {
            def.draw(c, x, y, unit, time)
        } finally {
            carrying = false
        }
    }

    fun drawRollingBall(c: Canvas, cx: Float, fy: Float, s: Float, degrees: Float) {
        contactShadow(c, cx, fy, s * .89f, s)
        c.save()
        c.rotate(degrees, cx, fy - s * 1.08f)
        try {
            drawCarried(byId.getValue("ball"), c, cx, fy, s, 0f)
        } finally {
            c.restore()
        }
    }

    private fun contactShadow(c: Canvas, cx: Float, fy: Float, rx: Float, s: Float) {
        if (carrying) return
        ellipse(c, cx, fy - s * 0.05f, rx, s * 0.13f, 0x26000000)
    }

    // ------------------------------------------------------------ игрушки

    /** Футбольный мяч: белая оболочка, чёрные пятиугольники и швы. */
    private fun ball() = Def("ball", "Мячик", Kind.TOY, Reaction.PUSH, 2.4f) { c, cx, fy, s, _ ->
        val r = s * 1.08f
        val cy = fy - r
        contactShadow(c, cx, fy, r * 0.82f, s)
        circle(c, cx, cy, r, 0xFFF7F1DB.toInt())
        c.save()
        path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(path)
        for (panel in 0..5) {
            val angle = -Math.PI.toFloat() / 2f + (panel - 1) * Math.PI.toFloat() * 2f / 5f
            val px = if (panel == 0) cx else cx + cos(angle) * r * 1.02f
            val py = if (panel == 0) cy else cy + sin(angle) * r * 1.02f
            val size = r * (if (panel == 0) 0.4f else 0.32f)
            if (panel > 0) {
                line(c, cx + cos(angle) * r * 0.38f, cy + sin(angle) * r * 0.38f,
                    px, py, s * 0.055f, 0xFF777974.toInt())
            }
            path.reset()
            for (vertex in 0..4) {
                val a = -Math.PI.toFloat() / 2f + vertex * Math.PI.toFloat() * 2f / 5f
                val vx = px + cos(a) * size; val vy = py + sin(a) * size
                if (vertex == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
            }
            path.close(); fillPath(c, 0xFF303936.toInt())
        }
        path.reset()
        path.moveTo(cx-r,cy+r*.38f)
        path.cubicTo(cx-r*.30f,cy+r*.98f,cx+r*.67f,cy+r*.55f,cx+r,cy-r*.05f)
        path.lineTo(cx+r,cy+r)
        path.lineTo(cx-r,cy+r)
        path.close()
        fillPath(c,0x24564F3F)
        c.restore()
        ink(c, cx, cy, r, s * 0.1f)
        ellipse(c, cx - r * 0.38f, cy - r * 0.42f, r * 0.18f, r * 0.28f, 0xA6FFFFFF.toInt())
    }

    /** Деревянный кубик в три четверти: верх, лицевая и боковая грани. */
    private fun block() = Def("block", "Кубик", Kind.TOY, Reaction.PUSH, 2.2f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        toyArtwork.draw("block",c,x,y,u,t)
    }

    /** Игральная кость с мягким скруглением и утопленными точками. */
    private fun dice() = Def("dice", "Кости", Kind.TOY, Reaction.LIFT, 2.2f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        toyArtwork.draw("dice",c,x,y,u,t)
    }

    /** Воздушный шарик — капля + узелок + верёвочка */
    private fun balloon() = Def("balloon", "Шарик", Kind.TOY, Reaction.LIFT, 2.1f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        toyArtwork.draw("balloon",c,x,y,u,t)
    }

    /** Мягкий мишка — круглые уши, светлая морда */
    private fun plushie() = Def("plushie", "Мишка", Kind.TOY, Reaction.RUB, 2.6f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        toyArtwork.draw("plushie",c,x,y,u,t)
    }

    /** Робот — большой экран-лицо, светящиеся глаза-плашки */
    private fun robot() = Def("robot", "Робот", Kind.TOY, Reaction.PLAY, 2.4f) { c, cx, fy, s, t ->
        drawRobot(c, cx, fy, s, t, 0f)
    }

    fun drawRobot(c: Canvas, cx: Float, fy: Float, s: Float, t: Float, gait: Float) {
        if (!carrying) contactShadow(c,cx,fy,s*.92f,s)
        toyArtwork.robot(c,cx,fy,s,t,gait)
    }

    /** Игрушечная ракета: цельный корпус, два плавника и сопло. */
    private fun rocket() = Def("rocket", "Ракета", Kind.TOY, Reaction.LIFT, 2.4f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        toyArtwork.draw("rocket",c,x,y,u,t)
    }

    /** Кораблик — корпус-трапеция, белый парус, красный флажок */
    private fun boat() = Def("boat", "Кораблик", Kind.TOY, Reaction.LIFT, 2.8f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        toyArtwork.draw("boat",c,x,y,u,t)
    }

    /** Цветок в горшке — крупный бутон из пяти лепестков */
    private fun flower() = Def("flower", "Цветок", Kind.TOY, Reaction.SMELL, 2.2f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        toyArtwork.draw("flower",c,x,y,u,t)
    }

    /** Юла — жёлтая шапочка, фиолетовый диск, наконечник */
    private fun top() = Def("top", "Юла", Kind.TOY, Reaction.PLAY, 2.2f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        toyArtwork.draw("top",c,x,y,u,t)
    }

    // --------------------------------------------------------------- еда

    /** Пицца — сектор с толстой корочкой и крупной пепперони */
    private fun pizza() = Def("pizza", "Пицца", Kind.FOOD, Reaction.EAT, 2.8f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        foodArtwork.draw("pizza",c,x,y,u,t)
    }

    private fun sushi() = Def("sushi", "Суши", Kind.FOOD, Reaction.EAT, 2.4f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        foodArtwork.draw("sushi",c,x,y,u,t)
    }

    private fun donut() = Def("donut", "Пончик", Kind.FOOD, Reaction.EAT, 2.8f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        foodArtwork.draw("donut",c,x,y,u,t)
    }

    private fun cookie() = Def("cookie", "Печенье", Kind.FOOD, Reaction.EAT, 2.4f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        foodArtwork.draw("cookie",c,x,y,u,t)
    }

    private fun coffee() = Def("coffee", "Кофе", Kind.FOOD, Reaction.EAT, 2.2f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        foodArtwork.draw("coffee",c,x,y,u,t)
    }

    private fun apple() = Def("apple", "Яблоко", Kind.FOOD, Reaction.EAT, 2.4f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        foodArtwork.draw("apple",c,x,y,u,t)
    }

    private fun icecream() = Def("icecream", "Мороженое", Kind.FOOD, Reaction.EAT, 2.4f) { c,x,y,u,t ->
        if (!carrying) contactShadow(c,x,y,u*.92f,u)
        foodArtwork.draw("icecream",c,x,y,u,t)
    }

    private fun box() = Def("box", "Подарок", Kind.BOX, Reaction.OPEN, 3.0f) { c, cx, fy, s, _ ->
        drawGift(c, cx, fy, s, 0f, 1)
    }

    fun drawGift(c: Canvas, cx: Float, fy: Float, s: Float, open: Float, direction: Int) {
        contactShadow(c,cx,fy,s*1.16f,s)
        val bw = s * 1.28f
        val bh = s * 1.2f
        roundRect(c, cx - bw, fy - bh, cx + bw, fy - s * 0.02f, s * 0.14f, 0xFFD6AB77.toInt())
        inkRound(c, cx - bw, fy - bh, cx + bw, fy - s * 0.02f, s * 0.14f, s * 0.1f)
        // лента вертикальная
        rect.set(cx - s * 0.2f, fy - bh, cx + s * 0.2f, fy - s * 0.02f)
        pF.color = 0xFFB95C49.toInt()
        c.drawRect(rect, pF)
        pF.color = 0x44FFE5AA
        c.drawRect(cx-s*.14f,fy-bh,cx-s*.08f,fy-s*.05f,pF)
        if (open > 0f) {
            ellipse(c, cx, fy - bh, bw, s * .19f, 0xFF9C633E.toInt())
            ellipse(c, cx, fy - bh - s * .04f, bw * .82f, s * .105f, 0xFF634C3C.toInt())
        }
        c.save()
        c.translate(s * open * direction * .58f, -s * open * .71f)
        c.rotate(-direction * open * 23f, cx, fy - bh)
        // крышка (чуть шире)
        roundRect(c, cx - bw * 1.08f, fy - bh - s * 0.34f, cx + bw * 1.08f, fy - bh + s * 0.12f, s * 0.12f, 0xFFD8945C.toInt())
        inkRound(c, cx - bw * 1.08f, fy - bh - s * 0.34f, cx + bw * 1.08f, fy - bh + s * 0.12f, s * 0.12f, s * 0.09f)
        rect.set(cx - s * 0.22f, fy - bh - s * 0.34f, cx + s * 0.22f, fy - bh + s * 0.12f)
        pF.color = 0xFFB95C49.toInt()
        c.drawRect(rect, pF)
        // бант
        ellipse(c, cx - s * 0.34f, fy - bh - s * 0.52f, s * 0.32f, s * 0.2f, 0xFFB95C49.toInt())
        ellipse(c, cx + s * 0.34f, fy - bh - s * 0.52f, s * 0.32f, s * 0.2f, 0xFFB95C49.toInt())
        circle(c, cx, fy - bh - s * 0.52f, s * 0.13f, 0xFFB94141.toInt())
        c.restore()
    }
}

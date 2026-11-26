package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import kotlin.math.PI

/** Ясный двор: общие с комнатой часы, собственное небо и игровая площадка. */
internal class YardScene {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val doorPath = Path()
    private val landscape = YardLandscape()
    private var swingWood = 0xFFB58B5F.toInt()
    private var swingStyle = 0
    private val material = VectorFinish()
    private val doorFinish by lazy { LinearGradient(0f,-5.6f,3.5f,0f,0xFF8DA697.toInt(),0xFF587B6C.toInt(),Shader.TileMode.CLAMP) }
    private val outdoorFinish by lazy {
        LinearGradient(0f, -5.6f, 3.5f, 0f, 0xFFA88059.toInt(), 0xFF685241.toInt(), Shader.TileMode.CLAMP)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
        material.apply(p, color, l, t, r, b)
        c.drawRoundRect(l,t,r,b,radius,radius,p)
        p.shader=null
    }
    fun door(c: Canvas, x: Float, floor: Float, u: Float, open: Float, style: Int) {
        c.save(); c.translate(x, floor); c.scale(u,u)
        // Ширина чистого проёма 3.5 единицы при диаметре пета 3.1.
        p.color=0x24000000;c.drawOval(-2.1f,-.12f,2.2f,.22f,p)
        box(c,-2.02f,-5.93f,2.02f,.02f,0xFF5A513D.toInt(),.45f)
        box(c,-1.89f,-5.80f,1.89f,-.02f,0xFFE9CE9C.toInt(),.36f)
        box(c,-1.75f,-5.6f,1.75f,-.04f,0xFF263B35.toInt(),.27f)
        val squeeze=1f-open.coerceIn(0f,1f)*.91f
        c.save();c.translate(-1.75f,0f);c.scale(squeeze,1f)
        p.shader = if (style == 1) outdoorFinish else doorFinish
        c.drawRoundRect(0f,-5.6f,3.5f,-.06f,.26f,.26f,p);p.shader=null
        edge.color=0xFF415D50.toInt();edge.strokeWidth=.055f
        c.drawRoundRect(.22f,-5.34f,3.28f,-.3f,.19f,.19f,edge)
        if (style == 2) {
            p.color = 0xFF415F53.toInt()
            c.drawCircle(1.75f, -4.33f, .74f, p)
            p.color = 0xFFE7CD93.toInt()
            c.drawCircle(1.75f, -4.33f, .67f, p)
            p.color = 0xFFADCDD2.toInt()
            c.drawCircle(1.75f, -4.33f, .54f, p)
        } else {
            doorPath.reset()
            doorPath.moveTo(.45f, -3.51f)
            doorPath.lineTo(.45f, -4.28f)
            doorPath.cubicTo(.45f, -5.59f, 3.05f, -5.59f, 3.05f, -4.28f)
            doorPath.lineTo(3.05f, -3.51f)
            doorPath.close()
            c.save()
            c.clipPath(doorPath)
            material.apply(p, if (style == 1) 0xFFEDC57C.toInt() else 0xFFBBD8CC.toInt(),
                .4f, -5.3f, 3.1f, -3.45f)
            c.drawPath(doorPath, p)
            p.shader = null
            edge.color = 0x89FFFBE0.toInt()
            edge.strokeWidth = .12f
            c.drawLine(.48f, -3.77f, 1.92f, -5.15f, edge)
            edge.strokeWidth = .063f
            c.drawLine(.68f, -3.66f, 2.19f, -5.12f, edge)
            if (style == 1) {
                edge.color = 0xFF947250.toInt()
                edge.strokeWidth = .092f
                c.drawLine(1.75f, -5.20f, 1.75f, -3.52f, edge)
                c.drawLine(.46f, -4.25f, 3.04f, -4.25f, edge)
            }
            c.restore()
            edge.color = if (style == 1) 0xFF775B42.toInt() else 0xFF4D6A59.toInt()
            edge.strokeWidth = .115f
            c.drawPath(doorPath, edge)
        }
        box(c,.42f,-3.3f,3.08f,-.55f,if (style == 1) 0xFF8F7051.toInt() else 0xFF739382.toInt(),.05f)
        edge.color = if (style == 1) 0xFF705940.toInt() else 0xFF597868.toInt()
        edge.strokeWidth=.035f
        c.drawRoundRect(.55f,-3.16f,2.95f,-.7f,.03f,.03f,edge)
        if (style == 1) {
            for (i in 1..3) {
                val boardX = .57f + i * .6f
                edge.color = 0x37705B43
                c.drawLine(boardX, -3.12f, boardX, -.72f, edge)
                edge.color = 0x3CDFBD91
                c.drawLine(boardX + .045f, -3.1f, boardX + .045f, -1.1f, edge)
            }
        }
        p.color = 0x345A451E
        c.drawOval(2.88f, -2.79f, 3.26f, -2.23f, p)
        box(c,2.87f,-2.83f,3.22f,-2.24f,0xFFB78E4D.toInt(),.145f)
        box(c,2.57f,-2.60f,3.10f,-2.43f,0xFFEEC277.toInt(),.078f)
        edge.color = 0xA9FFF4C1.toInt()
        edge.strokeWidth = .044f
        c.drawLine(2.66f,-2.56f,3.02f,-2.56f,edge)
        c.restore()
        box(c,-2.05f,-.06f,2.05f,.1f,0xFFA6987F.toInt(),.035f)
        c.restore()
    }
    private val dayGlow = SceneLighting.AmbientGlow(0.72f, .27f, .43f, 0x31FFE3A6)
    private val eveningGlow = SceneLighting.AmbientGlow(.73f, .38f, .39f, 0x2992BBF0)

    fun draw(c: Canvas, w: Float, h: Float, floor: Float, u: Float, now: Long, outing: Outing,
             prefs: android.content.SharedPreferences? = null) {
        swingStyle = prefs?.getInt("yard_swing_style", 0) ?: 0
        swingWood = prefs?.getInt("yard_swing",0xFFB58B5F.toInt()) ?: 0xFFB58B5F.toInt()
        c.save(); c.scale(u,u)
        landscape.draw(c,w/u,h/u,floor/u,now,prefs)
        val night = WindowScene.night(now)
        dayGlow.draw(c, w/u, h/u, floor/u, 1f - night)
        eveningGlow.draw(c, w/u, h/u, floor/u, night)
        door(c,.7f+outing.doorX*(w/u-1.4f),floor/u,1f,outing.doorOpen,prefs?.getInt("door_style", 0) ?: 0)
        c.restore()
        swing(c,w,floor,u,outing.swingAngle)
        swingSupports(c,w,floor,u)
    }

    /** Толщина бруса и светлая боковая грань дают читаемую деревянную конструкцию. */
    private fun beam(c: Canvas,x: Float,y: Float,x2: Float,y2: Float,width: Float) {
        val length=kotlin.math.hypot(x2-x,y2-y)
        c.save();c.translate(x,y)
        c.rotate(kotlin.math.atan2(y2-y,x2-x)*180f/PI.toFloat())
        box(c,0f,-width/2f,length,width/2f,swingWood,.045f)
        edge.color=0xFF4B5544.toInt();edge.strokeWidth=.06f
        c.drawRoundRect(0f,-width/2f,length,width/2f,.045f,.045f,edge)
        edge.color=0x66FFE9BB;edge.strokeWidth=.045f
        c.drawLine(.10f,-width*.25f,length-.10f,-width*.25f,edge)
        c.restore()
    }

    fun swing(c: Canvas,w: Float,floor: Float,u: Float,a: Float) {
        c.save();c.scale(u,u)
        val center=.7f+.80f*(w/u-1.4f);val f=floor/u
        val seatY=SwingMotion.offsetY(a)
        val perspective=SwingMotion.scale(a)
        p.color=0x21404D34;c.drawOval(center-2.8f,f-.1f,center+2.95f,f+.53f,p)
        // Дальние ножки, верхняя перекладина и диагональные распорки.
        beam(c,center-2f,f-.1f,center-1.6f,f-5.45f,.25f)
        beam(c,center+1.9f,f-.1f,center+1.55f,f-5.45f,.25f)
        beam(c,center-2.06f,f-5.45f,center+2.06f,f-5.45f,.44f)
        beam(c,center-1.6f,f-4.85f,center-.9f,f-5.4f,.16f)
        beam(c,center+1.55f,f-4.85f,center+.9f,f-5.4f,.16f)
        for(side in -1..1 step 2) {
            val anchor=center+side*1.28f
            edge.color=0xFF625238.toInt();edge.strokeWidth=.142f
            c.drawLine(anchor,f-4.85f,center+side*1.28f*perspective,f+seatY,edge)
            edge.color=0xFFF1DA9E.toInt();edge.strokeWidth=.082f
            c.drawLine(anchor,f-4.85f,center+side*1.28f*perspective,f+seatY,edge)
            box(c,anchor-.14f,f-5.64f,anchor+.14f,f-4.91f,0xFFC99555.toInt(),.11f)
            p.color=0xFFF3D18D.toInt()
            c.drawCircle(anchor,f-4.88f,.128f,p)
            edge.color=0xFF785834.toInt()
            edge.strokeWidth=.05f
            c.drawCircle(anchor,f-4.88f,.14f,edge)
            c.drawLine(anchor,f-5.25f,anchor,f-4.85f,edge)
        }
        c.save();c.translate(center,f+seatY);c.scale(perspective,perspective)
        // Спинка скамьи видна за питомцем; сиденье находится точно под ним.
        val cushion=VectorFinish.blend(swingWood,0xFFCCE0A0.toInt(),.40f)
        val shadow=VectorFinish.blend(swingWood,0xFF5C5236.toInt(),.38f)
        when (swingStyle) {
            1 -> {
                box(c,-1.56f,-1.44f,1.56f,-.04f,shadow,.43f)
                box(c,-1.44f,-1.36f,1.44f,-.16f,cushion,.45f)
                edge.color=0x8AF5E9B9.toInt()
                edge.strokeWidth=.062f
                c.drawRoundRect(-1.33f,-1.24f,1.33f,-.26f,.37f,.37f,edge)
                p.color=0x69736846
                for (i in -1..1) c.drawOval(i*.77f-.068f,-.66f,i*.77f+.068f,-.55f,p)
            }
            2 -> {
                box(c,-1.54f,-1.28f,1.54f,-1.06f,shadow,.11f)
                for (i in -2..2) box(c,i*.53f-.085f,-1.12f,i*.53f+.085f,-.12f,swingWood,.08f)
            }
            else -> {
                box(c,-1.56f,-1.43f,1.56f,-.03f,shadow,.39f)
                for (i in 0..2) {
                    val y=-1.35f+i*.40f
                    box(c,-1.45f,y,1.45f,y+.34f,cushion,.17f)
                    edge.color=0x80FFF2CB.toInt()
                    edge.strokeWidth=.040f
                    c.drawLine(-1.27f,y+.078f,1.23f,y+.078f,edge)
                }
            }
        }
        box(c,-1.62f,-.13f,1.62f,.24f,shadow,.15f)
        box(c,-1.61f,-.16f,1.61f,.06f,cushion,.12f)
        for (side in -1..1 step 2) {
            val x=side*1.43f
            box(c,x-.105f,-.75f,x+.105f,-.09f,shadow,.08f)
            box(c,x-.23f,-.85f,x+.23f,-.67f,swingWood,.08f)
        }
        edge.color=0xFF564931.toInt()
        edge.strokeWidth=.064f
        c.drawRoundRect(-1.62f,-.13f,1.62f,.24f,.15f,.15f,edge)
        c.restore();c.restore()
    }

    private fun swingSupports(c: Canvas,w: Float,floor: Float,u: Float) {
        c.save();c.scale(u,u)
        val center=.7f+.80f*(w/u-1.4f);val f=floor/u
        // Все опоры рисуются до питомца, включая ближние ножки.
        for(side in -1..1 step 2) {
            beam(c,center+side*2.35f,f+.12f,center+side*1.57f,f-5.45f,.43f)
            p.color=0xFFE4C689.toInt();c.drawCircle(center+side*1.65f,f-4.95f,.075f,p)
            box(c,center+side*2.35f-.28f,f+.04f,center+side*2.35f+.28f,f+.22f,0xFF818F70.toInt(),.06f)
        }
        c.restore()
    }
}

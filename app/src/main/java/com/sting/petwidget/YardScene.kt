package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import kotlin.math.PI

/** Ясный двор: общие с комнатой часы, собственное небо и игровая площадка. */
internal class YardScene {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val landscape = YardLandscape()
    private var swingWood = 0xFFB58B5F.toInt()
    private val doorFinish by lazy { LinearGradient(0f,-5.6f,3.5f,0f,0xFF8DA697.toInt(),0xFF587B6C.toInt(),Shader.TileMode.CLAMP) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
        p.color = color; c.drawRoundRect(l,t,r,b,radius,radius,p)
    }
    fun door(c: Canvas, x: Float, floor: Float, u: Float, open: Float) {
        c.save(); c.translate(x, floor); c.scale(u,u)
        // Ширина чистого проёма 3.5 единицы при диаметре пета 3.1.
        p.color=0x24000000;c.drawOval(-2.1f,-.12f,2.2f,.22f,p)
        box(c,-1.98f,-5.85f,1.98f,.02f,0xFF62564B.toInt(),.12f)
        box(c,-1.88f,-5.76f,1.88f,-.02f,0xFFE3D3B6.toInt(),.08f)
        box(c,-1.75f,-5.6f,1.75f,-.04f,0xFF263B35.toInt(),.035f)
        val squeeze=1f-open.coerceIn(0f,1f)*.91f
        c.save();c.translate(-1.75f,0f);c.scale(squeeze,1f)
        p.shader=doorFinish
        c.drawRoundRect(0f,-5.6f,3.5f,-.06f,.04f,.04f,p);p.shader=null
        edge.color=0xFF415D50.toInt();edge.strokeWidth=.055f
        c.drawRoundRect(.22f,-5.34f,3.28f,-.3f,.04f,.04f,edge)
        box(c,.42f,-5.12f,3.08f,-3.55f,0xFF41655D.toInt(),.06f)
        box(c,.5f,-5.04f,3f,-3.63f,0xFFB7D4CC.toInt(),.04f)
        edge.color=0x66FFFFFF;edge.strokeWidth=.055f;c.drawLine(.7f,-4.85f,2.4f,-3.82f,edge)
        box(c,.42f,-3.3f,3.08f,-.55f,0xFF739382.toInt(),.05f)
        edge.color=0xFF597868.toInt();edge.strokeWidth=.035f
        c.drawRoundRect(.55f,-3.16f,2.95f,-.7f,.03f,.03f,edge)
        box(c,2.95f,-2.9f,3.15f,-2.2f,0xFFB59A61.toInt(),.07f)
        edge.color=0xFFF1D99B.toInt();edge.strokeWidth=.09f;c.drawLine(3.04f,-2.52f,2.65f,-2.52f,edge)
        c.restore()
        box(c,-2.05f,-.06f,2.05f,.1f,0xFFA6987F.toInt(),.035f)
        c.restore()
    }
    fun draw(c: Canvas, w: Float, h: Float, floor: Float, u: Float, now: Long, outing: Outing,
             prefs: android.content.SharedPreferences? = null) {
        swingWood = prefs?.getInt("yard_swing",0xFFB58B5F.toInt()) ?: 0xFFB58B5F.toInt()
        c.save(); c.scale(u,u)
        landscape.draw(c,w/u,h/u,floor/u,now,prefs)
        door(c,.7f+outing.doorX*(w/u-1.4f),floor/u,1f,outing.doorOpen)
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
        p.color=0x21404D34;c.drawOval(center-2.8f,f-.1f,center+2.8f,f+.45f,p)
        // Дальние ножки, верхняя перекладина и диагональные распорки.
        beam(c,center-2f,f-.1f,center-1.6f,f-5.45f,.25f)
        beam(c,center+1.9f,f-.1f,center+1.55f,f-5.45f,.25f)
        beam(c,center-1.95f,f-5.45f,center+1.95f,f-5.45f,.34f)
        beam(c,center-1.6f,f-4.85f,center-.9f,f-5.4f,.16f)
        beam(c,center+1.55f,f-4.85f,center+.9f,f-5.4f,.16f)
        for(side in -1..1 step 2) {
            val anchor=center+side*1.28f
            edge.color=0xFF4C5D50.toInt();edge.strokeWidth=.10f
            c.drawLine(anchor,f-4.85f,center+side*1.28f*perspective,f+seatY,edge)
            edge.color=0xFFD4D0AD.toInt();edge.strokeWidth=.04f
            c.drawLine(anchor,f-4.85f,center+side*1.28f*perspective,f+seatY,edge)
            p.color=0xFFD7B97A.toInt();c.drawCircle(anchor,f-4.85f,.085f,p)
            c.drawLine(anchor,f-5.25f,anchor,f-4.85f,edge)
        }
        c.save();c.translate(center,f+seatY);c.scale(perspective,perspective)
        // Спинка скамьи видна за питомцем; сиденье находится точно под ним.
        box(c,-1.5f,-1.05f,1.5f,-.12f,swingWood,.09f)
        edge.color=0xFF4B5544.toInt();edge.strokeWidth=.055f
        c.drawRoundRect(-1.5f,-1.05f,1.5f,-.12f,.09f,.09f,edge)
        for(i in 0..5){val x=-1.25f+i*.5f;edge.color=0x66786D4E;c.drawLine(x,-.91f,x,-.25f,edge)}
        box(c,-1.58f,-.12f,1.58f,.18f,0xFFC8AD7D.toInt(),.07f)
        edge.color=0xFF4B5544.toInt();c.drawRoundRect(-1.58f,-.12f,1.58f,.18f,.07f,.07f,edge)
        c.restore();c.restore()
    }

    private fun swingSupports(c: Canvas,w: Float,floor: Float,u: Float) {
        c.save();c.scale(u,u)
        val center=.7f+.80f*(w/u-1.4f);val f=floor/u
        // Все опоры рисуются до питомца, включая ближние ножки.
        for(side in -1..1 step 2) {
            beam(c,center+side*2.35f,f+.12f,center+side*1.57f,f-5.45f,.32f)
            p.color=0xFFE4C689.toInt();c.drawCircle(center+side*1.65f,f-4.95f,.075f,p)
            box(c,center+side*2.35f-.28f,f+.04f,center+side*2.35f+.28f,f+.22f,0xFF818F70.toInt(),.06f)
        }
        c.restore()
    }
}

package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ясный двор: общие с комнатой часы, собственное небо и игровая площадка. */
internal class YardScene {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) * (1-t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1-t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1-t) + Color.blue(b) * t).toInt())
    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
        p.color = color; c.drawRoundRect(l,t,r,b,radius,radius,p)
    }
    fun door(c: Canvas, x: Float, floor: Float, u: Float, open: Float) {
        box(c,x-u*1.03f,floor-u*4.6f,x+u*1.03f,floor,0xFF605B4B.toInt(),u*.18f)
        box(c,x-u*.88f,floor-u*4.43f,x+u*.88f,floor,0xFF263B35.toInt(),u*.1f)
        val left=x-u*.85f
        val right=left+u*1.7f*(1f-open*.87f)
        box(c,left,floor-u*4.4f,right,floor-u*.03f,0xFFB88A60.toInt(),u*.09f)
        edge.color=0xFF725B46.toInt();edge.strokeWidth=u*.045f
        c.drawRoundRect(left+u*.13f,floor-u*4.15f,right-u*.08f,floor-u*.3f,u*.05f,u*.05f,edge)
        p.color=0xFFEBD799.toInt();c.drawCircle(right-u*.16f,floor-u*2.08f,u*.075f,p)
        box(c,x-u*1.15f,floor-u*.03f,x+u*1.15f,floor+u*.08f,0xFF8E806B.toInt(),u*.04f)
    }
    fun draw(c: Canvas, w: Float, h: Float, floor: Float, u: Float, now: Long, outing: Outing) {
        val n=WindowScene.night(now)
        c.drawColor(mix(0xFFCBE8EB.toInt(),0xFF233650.toInt(),n))
        val progress=(now%120_000L)/120_000f
        val sunX=u*4f+(w-u*6f)*progress
        val sunY=u*2.5f-sin(progress*PI.toFloat())*u*1.5f
        p.color=if(now%240_000L<120_000L) 0xFFFFDF8B.toInt() else 0xFFF3EDCD.toInt()
        c.drawCircle(sunX,sunY,u*.65f,p)
        if(n>.1f) for(i in 0..16){p.color=Color.argb((n*170).toInt(),245,243,219);c.drawCircle(u*(3f+(i*7%20)),u*(.6f+(i*13%21)*.1f),u*.025f,p)}
        for(i in 0..2){val x=(u*(6f+i*7f)+now%90_000L/90_000f*u*2f)%w;val y=u*(1.5f+i*.55f);p.color=mix(0xFFF6FAF4.toInt(),0xFF637387.toInt(),n);c.drawOval(x-u,y-u*.18f,x+u*1.6f,y+u*.28f,p);c.drawCircle(x,y-u*.15f,u*.4f,p)}
        p.color=mix(0xFF9ABC85.toInt(),0xFF405F50.toInt(),n)
        c.drawOval(-w*.15f,floor-u*4f,w*.75f,h+u*3f,p)
        c.drawOval(w*.3f,floor-u*3.3f,w*1.3f,h+u*3f,p)
        box(c,0f,floor-u*.2f,w,h,mix(0xFF88AE79.toInt(),0xFF3F5F4C.toInt(),n))
        val fence=mix(0xFFE8D7AF.toInt(),0xFF8A8B7B.toInt(),n)
        box(c,u*5.5f,floor-u*2.6f,w,floor-u*2.42f,fence)
        box(c,u*5.5f,floor-u*1.65f,w,floor-u*1.48f,fence)
        for(i in 6..24)box(c,u*i,floor-u*3f,u*(i+.28f),floor-u*.45f,fence,u*.1f)
        box(c,0f,u*1.8f,w*.255f,floor, mix(0xFFE2D5BB.toInt(),0xFF858877.toInt(),n),u*.12f)
        edge.color=mix(0xFFC6B598.toInt(),0xFF6D776C.toInt(),n);edge.strokeWidth=u*.035f
        for(i in 3..12)c.drawLine(0f,u*i,w*.255f,u*i,edge)
        door(c,u*.7f+outing.doorX*(w-u*1.4f),floor,u,outing.doorOpen)
        // Травинки и цветы лежат в слое земли, за питомцем и предметами.
        for(i in 0..18){val x=u*(.8f+(i*11%23));val y=floor+u*(.3f+(i%3)*.22f);edge.color=0x66516F4B;edge.strokeWidth=u*.025f;c.drawLine(x,y,x-u*.09f,y-u*.16f,edge);c.drawLine(x,y,x+u*.12f,y-u*.12f,edge)}
        swing(c,w,floor,u,outing.swingAngle)
    }
    fun swing(c: Canvas,w: Float,floor: Float,u: Float,a: Float) {
        val center=u*.7f+.60f*(w-u*1.4f)
        val top=floor-u*4.05f
        val dx=sin(a)*u*3.4f;val rise=(1f-cos(a))*u*3.4f
        edge.color=0xFF655B4A.toInt();edge.strokeWidth=u*.16f
        c.drawLine(center-u*2.2f,floor,center-u*1.55f,top-u*.4f,edge)
        c.drawLine(center+u*2.2f,floor,center+u*1.55f,top-u*.4f,edge)
        c.drawLine(center-u*1.8f,top-u*.4f,center+u*1.8f,top-u*.4f,edge)
        edge.color=0xFFC2B58E.toInt();edge.strokeWidth=u*.065f
        for(side in listOf(-1f,1f))c.drawLine(center+side*u*1.15f,top,center+side*u*1.15f+dx,floor-u*.65f-rise,edge)
        box(c,center-u*1.5f+dx,floor-u*.65f-rise,center+u*1.5f+dx,floor-u*.46f-rise,0xFFB78C5B.toInt(),u*.09f)
    }
}

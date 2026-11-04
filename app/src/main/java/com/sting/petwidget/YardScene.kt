package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ясный двор: общие с комнатой часы, собственное небо и игровая площадка. */
internal class YardScene {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
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
        c.save(); c.translate(x, floor); c.scale(u,u)
        // Ширина чистого проёма 3.5 единицы при диаметре пета 3.1.
        p.color=0x24000000;c.drawOval(-2.1f,-.12f,2.2f,.22f,p)
        box(c,-1.98f,-5.85f,1.98f,.02f,0xFF62564B.toInt(),.12f)
        box(c,-1.88f,-5.76f,1.88f,-.02f,0xFFE3D3B6.toInt(),.08f)
        box(c,-1.75f,-5.6f,1.75f,-.04f,0xFF263B35.toInt(),.035f)
        val squeeze=1f-open.coerceIn(0f,1f)*.91f
        c.save();c.translate(-1.75f,0f);c.scale(squeeze,1f)
        p.shader=LinearGradient(0f,-5.6f,3.5f,0f,0xFF8DA697.toInt(),0xFF587B6C.toInt(),Shader.TileMode.CLAMP)
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
    fun draw(c: Canvas, w: Float, h: Float, floor: Float, u: Float, now: Long, outing: Outing) {
        val n=WindowScene.night(now)
        c.drawColor(mix(0xFFCBE8EB.toInt(),0xFF233650.toInt(),n))
        val progress=(now%120_000L)/120_000f
        val sunX=-u*1.2f+(w+u*2.4f)*progress
        val sunY=floor-u*2.6f-sin(progress*PI.toFloat())*(floor-u*3.6f).coerceAtLeast(u)
        p.color=if(now%240_000L<120_000L) 0xFFFFDF8B.toInt() else 0xFFF3EDCD.toInt()
        c.drawCircle(sunX,sunY,u*.65f,p)
        if(n>.1f) for(i in 0..16){p.color=Color.argb((n*170).toInt(),245,243,219);c.drawCircle(u*(3f+(i*7%20)),u*(.6f+(i*13%21)*.1f),u*.025f,p)}
        for(i in 0..2){
            val x=((u*(6f+i*8f)+now%240_000L/240_000f*(w+u*8f))%(w+u*8f))-u*4f
            val y=u*(1.5f+i*.55f)
            p.shader=LinearGradient(0f,y-u*.7f,0f,y+u*.5f,mix(0xFFFFFFFF.toInt(),0xFF8A9BAD.toInt(),n),mix(0xFFD3E6E8.toInt(),0xFF526779.toInt(),n),Shader.TileMode.CLAMP)
            path.reset();path.moveTo(x-u*1.5f,y+u*.3f)
            path.cubicTo(x-u*2f,y-u*.5f,x-u*.8f,y-u*.8f,x-u*.4f,y-u*.4f)
            path.cubicTo(x-u*.2f,y-u*1.2f,x+u,y-u*1.1f,x+u*1.1f,y-u*.4f)
            path.cubicTo(x+u*2.2f,y-u*.5f,x+u*2.4f,y+u*.5f,x+u*1.5f,y+u*.5f)
            path.lineTo(x-u,y+u*.5f);path.close();c.drawPath(path,p);p.shader=null
        }
        p.color=mix(0xFF9ABC85.toInt(),0xFF405F50.toInt(),n)
        c.drawOval(-w*.15f,floor-u*4f,w*.75f,h+u*3f,p)
        c.drawOval(w*.3f,floor-u*3.3f,w*1.3f,h+u*3f,p)
        // Дальний лес и вода добавляют глубину между небом и забором.
        for(i in 0..20){
            val x=u*(3f+i*1.2f);val base=floor-u*(2.8f+(i%3)*.15f)
            edge.color=mix(0xFF617A58.toInt(),0xFF30493F.toInt(),n);edge.strokeWidth=u*.09f
            c.drawLine(x,base-u*.4f,x,base+u*.65f,edge)
            p.color=mix(if(i%2==0)0xFF668D68.toInt() else 0xFF7B9C72.toInt(),0xFF344F48.toInt(),n)
            c.drawOval(x-u*.7f,base-u*(1.2f+i%3*.25f),x+u*.7f,base+u*.2f,p)
        }
        path.reset();path.moveTo(w*.32f,floor-u*2.5f)
        path.cubicTo(w*.58f,floor-u*3.3f,w*.75f,floor-u*1.8f,w,floor-u*2.6f)
        path.lineTo(w,floor-u*1.8f)
        path.cubicTo(w*.70f,floor-u*.8f,w*.52f,floor-u*2.6f,w*.32f,floor-u*2.1f);path.close()
        p.color=mix(0xFF8DBBC0.toInt(),0xFF486E7E.toInt(),n);c.drawPath(path,p)
        edge.color=mix(0xFFC3DFD6.toInt(),0xFF78949B.toInt(),n);edge.strokeWidth=u*.04f
        for(i in 0..8)c.drawLine(w*.37f+i*u*1.6f,floor-u*2.2f,w*.37f+i*u*1.6f+u*.6f,floor-u*2.2f,edge)
        box(c,0f,floor-u*.2f,w,h,mix(0xFF88AE79.toInt(),0xFF3F5F4C.toInt(),n))
        val fence=mix(0xFFE8D7AF.toInt(),0xFF8A8B7B.toInt(),n)
        box(c,u*5.5f,floor-u*2.6f,w,floor-u*2.42f,fence)
        box(c,u*5.5f,floor-u*1.65f,w,floor-u*1.48f,fence)
        for(i in 6..24)box(c,u*i,floor-u*3f,u*(i+.28f),floor-u*.45f,fence,u*.1f)
        box(c,0f,floor-u*6.3f,w*.30f,floor, mix(0xFFE6DFCC.toInt(),0xFF858877.toInt(),n))
        edge.color=mix(0xFFC6B598.toInt(),0xFF6D776C.toInt(),n);edge.strokeWidth=u*.035f
        for(i in 1..12){val y=floor-u*6.3f+i*u*.48f;c.drawLine(0f,y,w*.30f,y,edge)}
        // Видимая часть длинного ската: конёк и продолжение дома за левой границей.
        path.reset();path.moveTo(-w,floor-u*6f);path.lineTo(-w,floor-u*13f)
        path.lineTo(w*.33f,floor-u*6.3f);path.lineTo(w*.33f,floor-u*6f);path.close()
        p.shader=LinearGradient(0f,floor-u*9f,w*.3f,floor-u*6f,
            mix(0xFF6C7D77.toInt(),0xFF475A60.toInt(),n),mix(0xFF475E55.toInt(),0xFF35494E.toInt(),n),Shader.TileMode.CLAMP)
        c.drawPath(path,p);p.shader=null
        edge.color=0xFF384E45.toInt();edge.strokeWidth=u*.12f;c.drawPath(path,edge)
        box(c,0f,floor-u*6.3f,w*.33f,floor-u*6.08f,0xFFBCA787.toInt(),u*.035f)
        box(c,0f,floor-u*6.08f,w*.33f,floor-u*5.94f,0xFF716E59.toInt(),u*.025f)
        box(c,w*.29f,floor-u*6f,w*.31f,floor,0xFF9B7952.toInt(),u*.06f)
        box(c,w*.294f,floor-u*5.85f,w*.302f,floor-u*.3f,0xFFC3A67D.toInt(),u*.025f)
        box(c,w*.282f,floor-u*6.05f,w*.318f,floor-u*5.8f,0xFFC0A078.toInt(),u*.04f)
        box(c,w*.282f,floor-u*.2f,w*.318f,floor+u*.04f,0xFFC0A078.toInt(),u*.04f)
        door(c,u*.7f+outing.doorX*(w-u*1.4f),floor,u,outing.doorOpen)
        // Травинки и цветы лежат в слое земли, за питомцем и предметами.
        for(i in 0..18){val x=u*(.8f+(i*11%23));val y=floor+u*(.3f+(i%3)*.22f);edge.color=0x66516F4B;edge.strokeWidth=u*.025f;c.drawLine(x,y,x-u*.09f,y-u*.16f,edge);c.drawLine(x,y,x+u*.12f,y-u*.12f,edge)}
        for(i in 0..22){val x=u*(7.5f+(i*1.83f)%15.5f);val y=floor-u*(.3f+(i%3)*.18f);edge.color=0xFF557A4C.toInt();edge.strokeWidth=u*.03f;c.drawLine(x,y,x,y-u*.25f,edge);p.color=if(i%2==0)0xFFE3CB8F.toInt() else 0xFFCEA2AD.toInt();for(j in 0..4){val a=j*PI.toFloat()*2f/5f;c.drawCircle(x+cos(a)*u*.07f,y-u*.28f+sin(a)*u*.07f,u*.06f,p)}}
        swing(c,w,floor,u,outing.swingAngle)
    }
    fun swing(c: Canvas,w: Float,floor: Float,u: Float,a: Float) {
        val center=u*.7f+.80f*(w-u*1.4f)
        val top=floor-u*4.85f
        val dx=sin(a)*u*4.2f;val rise=(1f-cos(a))*u*4.2f
        edge.color=0xFF655B4A.toInt();edge.strokeWidth=u*.16f
        c.drawLine(center-u*2.2f,floor,center-u*1.55f,top-u*.4f,edge)
        c.drawLine(center+u*2.2f,floor,center+u*1.55f,top-u*.4f,edge)
        c.drawLine(center-u*1.8f,top-u*.4f,center+u*1.8f,top-u*.4f,edge)
        edge.color=0xFFC2B58E.toInt();edge.strokeWidth=u*.065f
        for(side in listOf(-1f,1f))c.drawLine(center+side*u*1.15f,top,center+side*u*1.15f+dx,floor-u*.65f-rise,edge)
        box(c,center-u*1.5f+dx,floor-u*.65f-rise,center+u*1.5f+dx,floor-u*.46f-rise,0xFFB78C5B.toInt(),u*.09f)
        edge.color=0xFFD5B688.toInt();edge.strokeWidth=u*.035f
        c.drawLine(center-u*1.3f+dx,floor-u*.61f-rise,center+u*1.3f+dx,floor-u*.61f-rise,edge)
    }
    fun swingFront(c: Canvas,w: Float,floor: Float,u: Float) {
        val center=u*.7f+.80f*(w-u*1.4f)
        edge.color=0xFF544B3E.toInt();edge.strokeWidth=u*.22f
        c.drawLine(center+u*2.25f,floor+u*.12f,center+u*1.55f,floor-u*5.25f,edge)
        edge.color=0xFFB29A72.toInt();edge.strokeWidth=u*.065f
        c.drawLine(center+u*2.20f,floor,center+u*1.50f,floor-u*5.15f,edge)
    }
}

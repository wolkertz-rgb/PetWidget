package com.sting.petwidget

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Слои пейзажа в единицах ширины комнаты. Paint, Path и градиенты переиспользуются. */
internal class YardLandscape {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private data class Ramp(val top: Float, val bottom: Float, val a: Int, val b: Int, val shader: Shader)
    private val ramps = HashMap<String,Ramp>()
    private val cloudIds = arrayOf("cloud0","cloud1","cloud2")
    private var night = 0f
    private fun mix(a: Int,b: Int,t: Float) = Color.argb(
        (Color.alpha(a)*(1-t)+Color.alpha(b)*t).toInt(),
        (Color.red(a)*(1-t)+Color.red(b)*t).toInt(),
        (Color.green(a)*(1-t)+Color.green(b)*t).toInt(),
        (Color.blue(a)*(1-t)+Color.blue(b)*t).toInt())
    private fun tone(day: Long, dark: Long) = mix(day.toInt(),dark.toInt(),night)
    private fun ramp(id: String, top: Float, bottom: Float, a: Int, b: Int) {
        val old=ramps[id]
        val cached=if(old!=null && old.top==top && old.bottom==bottom && old.a==a && old.b==b) old else
            Ramp(top,bottom,a,b,LinearGradient(0f,top,0f,bottom.coerceAtLeast(top+.01f),a,b,Shader.TileMode.CLAMP)).also { ramps[id]=it }
        fill.shader=cached.shader
    }
    private fun line(c: Canvas,x: Float,y: Float,x2: Float,y2: Float,color: Int,width: Float=.055f) {
        ink.color=color;ink.strokeWidth=width;c.drawLine(x,y,x2,y2,ink)
    }
    private fun shape(c: Canvas,color: Int,outline: Int=0,width: Float=.055f) {
        fill.color=color;c.drawPath(path,fill);fill.shader=null
        if(outline!=0){ink.color=outline;ink.strokeWidth=width;c.drawPath(path,ink)}
    }
    private fun box(c: Canvas,l: Float,t: Float,r: Float,b: Float,color: Int,radius: Float=.05f,outline: Int=0) {
        fill.color=color;c.drawRoundRect(l,t,r,b,radius,radius,fill);fill.shader=null
        if(outline!=0){ink.color=outline;ink.strokeWidth=.055f;c.drawRoundRect(l,t,r,b,radius,radius,ink)}
    }
    private fun disc(c: Canvas,x: Float,y: Float,r: Float,color: Int) {fill.color=color;c.drawCircle(x,y,r,fill)}

    fun draw(c: Canvas,w: Float,h: Float,f: Float,now: Long,prefs: SharedPreferences?) {
        // 48 ступеней плавного сумеречного перехода; пересоздание Shader только при смене тона.
        night=(WindowScene.night(now)*48).toInt()/48f
        val outline=tone(0xFF435F50,0xFF243A3D)
        ramp("sky",0f,f,tone(0xFF8ED6EB,0xFF162940),tone(0xFFE7F3D2,0xFF607A86))
        box(c,0f,0f,w,h,Color.WHITE,0f)
        val progress=(now%120_000L)/120_000f
        val sunX=-1.1f+(w+2.2f)*progress
        val sunY=f-3f-sin(progress*PI.toFloat())*(f-4.2f).coerceAtLeast(1f)
        disc(c,sunX,sunY,.82f,tone(0x22FFE8A0,0x224F7690))
        disc(c,sunX,sunY,.56f,if(now%240_000L<120_000L)0xFFFFDF88.toInt() else 0xFFF7EACD.toInt())
        if(night>.1f)for(i in 0..21)disc(c,2f+(i*1.83f)%21f,.4f+(i*.73f)%3f,.025f,Color.argb((night*180).toInt(),244,239,211))
        for(i in 0..2){
            val x=((now%360_000L)/360_000f*(w+9f)+i*9f)%(w+9f)-4f
            val y=.9f+i*.58f
            ramp(cloudIds[i],y-.7f,y+.4f,tone(0xFFFFFEF1,0xFF97AFBA),tone(0xFFC3E2DC,0xFF667F93))
            path.reset();path.moveTo(x-1.6f,y+.25f)
            path.cubicTo(x-2f,y-.25f,x-1.25f,y-.65f,x-.8f,y-.35f)
            path.cubicTo(x-.65f,y-1f,x+.55f,y-1.02f,x+.7f,y-.3f)
            path.cubicTo(x+1.65f,y-.65f,x+2.15f,y+.4f,x+1.25f,y+.4f)
            path.lineTo(x-1.2f,y+.4f);path.close();shape(c,Color.WHITE)
        }
        // Силуэты гор, освещённые грани и снежные вершины.
        path.reset();path.moveTo(3f,f-3.8f);path.lineTo(9.1f,f-8.1f);path.lineTo(12.1f,f-5.8f)
        path.lineTo(16.2f,f-9.2f);path.lineTo(23.8f,f-3.7f);path.close()
        ramp("mountain",f-9.2f,f-3.6f,tone(0xFF95B9B0,0xFF41596B),tone(0xFFAAC8A4,0xFF516E78));shape(c,Color.WHITE)
        path.reset();path.moveTo(16.2f,f-9.2f);path.lineTo(15.6f,f-6.4f);path.lineTo(18.4f,f-5.8f);path.lineTo(23.8f,f-3.7f);path.close();shape(c,tone(0xFF7DA79E,0xFF354F60))
        path.reset();path.moveTo(14.7f,f-7.94f);path.lineTo(16.2f,f-9.2f);path.lineTo(18.1f,f-7.83f)
        path.lineTo(16.9f,f-8.1f);path.lineTo(16.2f,f-7.85f);path.lineTo(15.7f,f-8.25f);path.close();shape(c,tone(0xFFE7EBDD,0xFF819BA9))
        path.reset();path.moveTo(0f,f-3f);path.cubicTo(4f,f-6.8f,9f,f-6.8f,13f,f-3.8f)
        path.cubicTo(17f,f-7f,21f,f-6f,w,f-4.3f);path.lineTo(w,f);path.lineTo(0f,f);path.close()
        ramp("hills",f-6f,f,tone(0xFF91BB80,0xFF40685C),tone(0xFFCAE0A3,0xFF6C8A69));shape(c,Color.WHITE)
        // Извилистая река с двумя берегами и бликами, а не линия на горизонте.
        path.reset();path.moveTo(14f,f-5.1f);path.cubicTo(12f,f-4.1f,23f,f-4.6f,17f,f-2.7f)
        path.cubicTo(15f,f-2f,15f,f-1.5f,24f,f-.9f);path.lineTo(24f,f-2f)
        path.cubicTo(17f,f-2.6f,19f,f-2.8f,21f,f-3.5f);path.cubicTo(25f,f-5.1f,15f,f-4.5f,14.7f,f-5.1f);path.close()
        ramp("river",f-5.1f,f-.8f,tone(0xFF90C6C3,0xFF4C7E92),tone(0xFFC3E9DE,0xFF799FAD));shape(c,Color.WHITE,tone(0xFF7CA57F,0xFF3C645C),.07f)
        for(i in 0..8){val x=15.5f+(i*1.37f)%8f;val y=f-3.6f+(i%3)*.65f;line(c,x,y,x+.45f,y,tone(0xBFFFFFF0,0x668AAEB8),.035f)}
        for(i in 0..13)tree(c,7f+i*1.25f,f-2.6f+(i%3)*.18f,.64f+(i%3)*.12f,outline)
        path.reset();path.moveTo(0f,f-.3f);path.cubicTo(7f,f-.9f,16f,f-.4f,24f,f-.65f);path.lineTo(w,h);path.lineTo(0f,h);path.close()
        ramp("lawn",f-.7f,h,tone(0xFF8DBB78,0xFF486E54),tone(0xFFBDD78F,0xFF678464));shape(c,Color.WHITE,outline,.06f)
        fence(c,f,outline,prefs?.getInt("yard_fence",0xFFD8BB8B.toInt())?:0xFFD8BB8B.toInt())
        house(c,f,outline,prefs?.getInt("yard_house",0xFFE9D9BB.toInt())?:0xFFE9D9BB.toInt())
        // Плиты у крыльца, травяные островки и цветы в глубине участка.
        for(i in 0..3){val x=4.3f+i*1.1f;box(c,x-.8f,f+.05f+i*.10f,x+.7f,f+.25f+i*.10f,tone(0xFFC9C6AE,0xFF778474),.13f,outline)}
        for(i in 0..27){val x=7.4f+(i*1.71f)%16f;val y=f-.35f+(i%3)*.32f;line(c,x,y,x-.07f,y-.13f,tone(0x8861864D,0x88517250),.025f)}
        val decor=prefs?.getInt("yard_decor",1)?:1
        if(decor==1)flowerbed(c,10.7f,f,outline)
        if(decor==2){for(i in 0..4)box(c,9f+i*.5f,f-.2f-i%2*.15f,9.7f+i*.5f,f+.16f,tone(0xFFAFB6A0,0xFF71847D),.24f,outline);tree(c,10.2f,f-.12f,.6f,outline)}
        if(decor==3){box(c,10.3f,f-1.5f,10.65f,f,0xFFA8B1A0.toInt(),.1f,outline);fill.color=tone(0xFFCBD4BF,0xFF788F88);c.drawOval(9.65f,f-1.75f,11.3f,f-1.25f,fill);line(c,9.8f,f-1.5f,11.15f,f-1.5f,tone(0xFF92C6CB,0xFF627F97),.12f)}
        if(prefs?.getInt("yard_butterflies",1)!=0 && night<.65f)butterflies(c,f,now)
        if(prefs?.getInt("yard_nightlife",1)!=0)nightGarden(c,f,now,outline)
    }

    private fun tree(c: Canvas,x: Float,y: Float,s: Float,outline: Int){
        line(c,x,y,x,y-2.1f*s,tone(0xFF6C7250,0xFF374F44),.1f*s)
        path.reset();path.moveTo(x,y-3.6f*s)
        for(i in 0..3){val yy=y-(2.9f-i*.66f)*s;val spread=(.42f+i*.18f)*s
            path.lineTo(x+spread,yy);path.lineTo(x+spread*.52f,yy+.04f*s)}
        path.lineTo(x+.27f*s,y-.48f*s);path.lineTo(x-.27f*s,y-.48f*s)
        for(i in 3 downTo 0){val yy=y-(2.9f-i*.66f)*s;val spread=(.42f+i*.18f)*s;path.lineTo(x-spread*.52f,yy+.04f*s);path.lineTo(x-spread,yy)}
        path.close();shape(c,tone(0xFF578D6C,0xFF2F554F),outline,.035f)
        line(c,x,y-3.1f*s,x+.43f*s,y-1.1f*s,tone(0x557DB78A,0x334F7968),.06f*s)
    }
    private fun fence(c: Canvas,f: Float,outline: Int,color: Int){
        val wood=mix(color,0xFF455D59.toInt(),night*.55f)
        box(c,7f,f-2.12f,24f,f-1.94f,wood,.02f,outline);box(c,7f,f-1.16f,24f,f-.98f,wood,.02f,outline)
        for(i in 0..20){val x=7.2f+i*.84f;path.reset();path.moveTo(x,f-.45f);path.lineTo(x,f-2.27f);path.lineTo(x+.25f,f-2.48f);path.lineTo(x+.5f,f-2.27f);path.lineTo(x+.5f,f-.45f);path.close();shape(c,wood,outline,.04f);line(c,x+.11f,f-2.13f,x+.11f,f-.7f,0x33FFFFFF,.03f)}
        for(i in 0..4){val x=7f+i*4.2f;box(c,x,f-2.75f,x+.35f,f-.25f,wood,.03f,outline);box(c,x-.09f,f-2.8f,x+.44f,f-2.63f,wood,.055f,outline)}
    }
    private fun house(c: Canvas,f: Float,outline: Int,color: Int){
        ramp("wall",f-7.1f,f,mix(mix(color,Color.WHITE,.14f),0xFF536D70.toInt(),night*.55f),mix(color,0xFF657464.toInt(),night*.45f+.08f))
        box(c,-1f,f-7.1f,7.1f,f,Color.WHITE,0f,outline)
        for(i in 1..10){val y=f-7.1f+i*.58f;line(c,0f,y,7.1f,y,tone(0x559F9C82,0x55576E63),.025f)}
        box(c,0f,f-.55f,7.15f,f+.1f,tone(0xFF9EA48C,0xFF647668),.02f,outline)
        for(i in 0..9)line(c,i*.8f,f-.53f,i*.8f,f+.08f,0x55596752,.03f)
        // Большой скат продолжается за левый и верхний края; здесь только угол дома.
        path.reset();path.moveTo(-3f,f-6.9f);path.lineTo(-3f,f-10.5f);path.lineTo(7.65f,f-7.25f);path.lineTo(7.65f,f-6.9f);path.close()
        ramp("roof",f-10.5f,f-6.9f,tone(0xFF66887B,0xFF3D5760),tone(0xFF3F6257,0xFF283F4B));shape(c,Color.WHITE,outline,.12f)
        for(i in 0..7)line(c,i*1.05f-1f,f-9.8f+i*.32f,i*1.05f+.1f,f-7.25f,tone(0x886F9785,0x88566F73),.04f)
        box(c,0f,f-7.2f,7.65f,f-6.95f,tone(0xFFD9C5A1,0xFF8C9381),.025f,outline)
        box(c,0f,f-6.95f,7.65f,f-6.76f,tone(0xFF8D8B70,0xFF52685F),.04f,outline)
        box(c,6.88f,f-6.75f,7.25f,f+.12f,tone(0xFFA68A60,0xFF6A7560),.035f,outline)
        box(c,6.8f,f-.2f,7.34f,f+.15f,tone(0xFFCEC09A,0xFF8A947A),.04f,outline)
        box(c,6.81f,f-6.76f,7.33f,f-6.46f,tone(0xFFCEC09A,0xFF8A947A),.04f,outline)
        line(c,7f,f-6.3f,7f,f-.4f,0x55FFFFFF,.04f)
        // Небольшое боковое окно подчёркивает масштаб полноценного дома.
        box(c,.12f,f-5.5f,1.72f,f-3.45f,tone(0xFFE4D5B6,0xFF9AA68E),.08f,outline)
        box(c,.28f,f-5.34f,1.56f,f-3.6f,tone(0xFF9BC6C5,0xFF597889),.03f,outline)
        line(c,.92f,f-5.3f,.92f,f-3.65f,0xFFD9D2B5.toInt(),.065f)
        box(c,-.02f,f-3.5f,1.88f,f-3.32f,0xFF9E9B79.toInt(),.03f,outline)
    }
    private fun flowerbed(c: Canvas,x: Float,f: Float,outline: Int){
        box(c,x-1.3f,f-.3f,x+1.3f,f+.08f,tone(0xFF9D7957,0xFF65715D),.12f,outline)
        for(i in 0..6){val xx=x-1f+i*.34f;val yy=f-.55f-(i%3)*.19f
            line(c,xx,f-.2f,xx,yy,0xFF4D7B51.toInt(),.035f)
            for(j in 0..4){val a=j*PI.toFloat()*2f/5;disc(c,xx+cos(a)*.12f,yy+sin(a)*.12f,.10f,tone(if(i%2==0)0xFFF3BC88 else 0xFFD89DAF,0xFF979B87))}
            disc(c,xx,yy,.06f,0xFFF3D78D.toInt())}
    }
    private fun butterflies(c: Canvas,f: Float,now: Long){
        val t=(now%60_000L)/1000f
        for(i in 0..2){val x=10.6f+sin(t*.4f+i*2f)*1.9f;val y=f-2.8f+sin(t*.7f+i)*.6f
            val flap=.06f+kotlin.math.abs(sin(t*6f+i))*.13f
            fill.color=tone(if(i%2==0)0xFFF1BB78 else 0xFFC5A3DD,0xFF8E9C91)
            c.drawOval(x-flap,y-.12f,x,y+.10f,fill);c.drawOval(x,y-.12f,x+flap,y+.10f,fill)
            line(c,x,y-.08f,x,y+.08f,0xFF5A6550.toInt(),.025f)}
    }
    private fun nightGarden(c: Canvas,f: Float,now: Long,outline: Int){
        for(i in 0..1){val x=8f+i*5.2f;box(c,x-.04f,f-1.1f,x+.04f,f,outline,.02f)
            box(c,x-.24f,f-1.38f,x+.24f,f-.92f,tone(0xFFDFD9AF,0xFFFFDA88),.045f,outline)
            box(c,x-.31f,f-1.47f,x+.31f,f-1.33f,outline,.03f)
            if(night>.15f)disc(c,x,f-1.13f,.48f,Color.argb((night*24).toInt(),255,218,121))}
        if(night<.3f)return
        val t=(now%120_000L)/1000f
        for(i in 0..7){val x=8f+i*1.7f+sin(t*.7f+i)*.35f;val y=f-1f-sin(t*.5f+i*1.2f)*.7f
            disc(c,x,y,.10f,Color.argb((night*35).toInt(),232,244,144))
            disc(c,x,y,.033f,Color.argb((night*(130+80*sin(t*1.8f+i))).toInt(),241,246,166))}
    }
}

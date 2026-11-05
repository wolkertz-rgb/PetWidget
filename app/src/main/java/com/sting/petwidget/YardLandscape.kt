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
    private val pineIds = arrayOf("pine0","pine1","pine2","pine3")
    private val stoneIds = arrayOf("stone0","stone1","stone2")
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
        // Дальний лес на верхнем берегу, ближняя группа только слева от русла.
        for(i in 0..13)tree(c,7f+i*1.25f,f-5.35f,.38f+(i%3)*.06f,outline)
        for(i in 0..4)tree(c,7.5f+i*1.15f,f-2.65f+(i%2)*.12f,.66f+(i%3)*.09f,outline)
        // Вода рисуется после леса и маскирует кромки дальнего берега.
        path.reset();path.moveTo(14f,f-5.1f);path.cubicTo(12f,f-4.1f,23f,f-4.6f,17f,f-2.7f)
        path.cubicTo(15f,f-2f,15f,f-1.5f,24f,f-.9f);path.lineTo(24f,f-2f)
        path.cubicTo(17f,f-2.6f,19f,f-2.8f,21f,f-3.5f);path.cubicTo(25f,f-5.1f,15f,f-4.5f,14.7f,f-5.1f);path.close()
        ramp("river",f-5.1f,f-.8f,tone(0xFF90C6C3,0xFF4C7E92),tone(0xFFC3E9DE,0xFF799FAD));shape(c,Color.WHITE,tone(0xFF7CA57F,0xFF3C645C),.07f)
        for(i in 0..8){val x=15.5f+(i*1.37f)%8f;val y=f-3.6f+(i%3)*.65f;line(c,x,y,x+.45f,y,tone(0xBFFFFFF0,0x668AAEB8),.035f)}
        path.reset();path.moveTo(0f,f-.3f);path.cubicTo(7f,f-.9f,16f,f-.4f,24f,f-.65f);path.lineTo(w,h);path.lineTo(0f,h);path.close()
        ramp("lawn",f-.7f,h,tone(0xFF8DBB78,0xFF486E54),tone(0xFFBDD78F,0xFF678464));shape(c,Color.WHITE,outline,.06f)
        fence(c,f,outline,prefs?.getInt("yard_fence",0xFFD8BB8B.toInt())?:0xFFD8BB8B.toInt())
        house(c,f,outline,prefs?.getInt("yard_house",0xFFE9D9BB.toInt())?:0xFFE9D9BB.toInt())
        // Плиты у крыльца, травяные островки и цветы в глубине участка.
        steppingStones(c,f,outline)
        for(i in 0..27){val x=7.4f+(i*1.71f)%16f;val y=f-.35f+(i%3)*.32f;line(c,x,y,x-.07f,y-.13f,tone(0x8861864D,0x88517250),.025f)}
        val decor=prefs?.getInt("yard_decor",1)?:1
        if(decor==1)flowerbed(c,10.7f,f,outline)
        if(decor==2)rockGarden(c,10.7f,f,outline)
        if(decor==3)birdbath(c,10.7f,f,outline)
        if(prefs?.getInt("yard_butterflies",1)!=0 && night<.65f)butterflies(c,f,now)
        if(prefs?.getInt("yard_nightlife",1)!=0)nightGarden(c,f,now,outline)
    }

    private fun tree(c: Canvas,x: Float,y: Float,s: Float,outline: Int){
        c.save();c.translate(x,y);c.scale(s,s)
        box(c,-.11f,-2.3f,.11f,.08f,tone(0xFF8B7954,0xFF41594F),.03f,outline)
        for(tier in 0..3) {
            val tip=-3.65f+tier*.68f
            val spread=.34f+tier*.23f
            val base=tip+1.18f
            path.reset();path.moveTo(0f,tip)
            path.cubicTo(spread*.18f,tip+.37f,spread*.62f,base-.46f,spread,base-.12f)
            path.quadTo(spread*.72f,base+.01f,spread*.42f,base-.04f)
            path.quadTo(0f,base+.17f,-spread*.42f,base-.04f)
            path.quadTo(-spread*.72f,base+.01f,-spread,base-.12f)
            path.cubicTo(-spread*.58f,base-.46f,-spread*.18f,tip+.37f,0f,tip);path.close()
            ramp(pineIds[tier],tip,base,tone(0xFF77AB85,0xFF426B61),tone(0xFF3D765B,0xFF294D49))
            shape(c,Color.WHITE,outline,.035f)
            // Освещённая левая грань и тёмные кончики ветвей каждого яруса.
            path.reset();path.moveTo(-.035f,tip+.19f)
            path.quadTo(-spread*.28f,base-.43f,-spread*.77f,base-.15f)
            path.quadTo(-spread*.35f,base-.20f,-.035f,tip+.19f);path.close()
            shape(c,tone(0x558FC79A,0x335C8F79))
            line(c,-spread*.67f,base-.13f,-spread*.32f,base-.18f,tone(0x88406C51,0x88304D45),.025f)
        }
        c.restore()
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
        birdhouse(c,.92f,f-3.65f,outline)
    }
    private fun steppingStones(c: Canvas,f: Float,outline: Int) {
        // Между независимыми плитами остаётся трава, а нижняя грань даёт толщину.
        for(i in 0..3) {
            val x=4.3f+i*1.35f;val y=f+.15f+i*.14f
            fill.color=0x223B513B;c.drawOval(x-.6f,y-.04f,x+.62f,y+.26f,fill)
            path.reset();path.moveTo(x-.48f,y-.07f);path.lineTo(x-.22f,y-.17f)
            path.lineTo(x+.39f,y-.12f);path.lineTo(x+.53f,y+.04f)
            path.lineTo(x+.22f,y+.17f);path.lineTo(x-.43f,y+.13f);path.close()
            shape(c,tone(0xFF9DA58E,0xFF61796E),outline,.035f)
            path.reset();path.moveTo(x-.48f,y-.13f);path.lineTo(x-.22f,y-.23f)
            path.lineTo(x+.39f,y-.18f);path.lineTo(x+.53f,y-.02f)
            path.lineTo(x+.22f,y+.09f);path.lineTo(x-.43f,y+.05f);path.close()
            shape(c,tone(0xFFCFD0B9,0xFF8CA093),outline,.03f)
            line(c,x-.29f,y-.13f,x+.21f,y-.15f,tone(0xAFFFF2D4,0x668EA89A),.03f)
        }
    }
    private fun birdhouse(c: Canvas,x: Float,y: Float,outline: Int) {
        // Скворечник на кронштейнах заменяет несуществующее окно фасада.
        box(c,x-.96f,y+.1f,x+1.03f,y+.28f,tone(0xFFB89C70,0xFF7C8C74),.035f,outline)
        line(c,x-.60f,y+.29f,x-.60f,y+.68f,outline,.09f)
        line(c,x-.60f,y+.64f,x-.22f,y+.29f,outline,.08f)
        path.reset();path.moveTo(x-.65f,y+.1f);path.lineTo(x-.65f,y-1.18f)
        path.lineTo(x,y-1.69f);path.lineTo(x+.65f,y-1.18f);path.lineTo(x+.65f,y+.1f);path.close()
        ramp("birdhouse",y-1.6f,y+.1f,tone(0xFFE1C89A,0xFFA0AB8C),tone(0xFFB99968,0xFF718773));shape(c,Color.WHITE,outline,.055f)
        path.reset();path.moveTo(x+.65f,y-1.18f);path.lineTo(x+.84f,y-1.04f)
        path.lineTo(x+.84f,y+.02f);path.lineTo(x+.65f,y+.1f);path.close();shape(c,tone(0xFF9A825E,0xFF5E7868),outline,.035f)
        path.reset();path.moveTo(x-.83f,y-1.12f);path.lineTo(x,y-1.86f);path.lineTo(x+.88f,y-1.12f)
        path.lineTo(x+.72f,y-.99f);path.lineTo(x,y-1.59f);path.lineTo(x-.7f,y-.99f);path.close()
        shape(c,tone(0xFF9E7561,0xFF677B73),outline,.055f)
        disc(c,x,y-.82f,.23f,outline)
        line(c,x-.18f,y-.29f,x+.22f,y-.29f,tone(0xFFE4D0A0,0xFF9FAE8E),.09f)
        for(i in 0..1)line(c,x-.4f,y-.09f-i*.12f,x+.42f,y-.09f-i*.12f,0x44705F44,.025f)
    }
    private fun flowerbed(c: Canvas,x: Float,f: Float,outline: Int){
        fill.color=0x223B513B;c.drawOval(x-1.55f,f-.07f,x+1.55f,f+.3f,fill)
        box(c,x-1.4f,f-.48f,x+1.4f,f+.14f,tone(0xFFAD805B,0xFF64796A),.08f,outline)
        fill.color=tone(0xFF604E38,0xFF3F5348);c.drawOval(x-1.32f,f-.62f,x+1.32f,f-.29f,fill)
        for(i in 0..4){
            val xx=x-1.02f+i*.51f;val yy=f-.83f-(i%3)*.26f
            line(c,xx,f-.35f,xx,yy,tone(0xFF54794B,0xFF476957),.045f)
            for(side in -1..1 step 2){
                path.reset();path.moveTo(xx,f-.56f)
                path.quadTo(xx+side*.45f,f-1.05f,xx+side*.43f,f-.76f)
                path.quadTo(xx+side*.3f,f-.51f,xx,f-.56f);path.close()
                shape(c,tone(0xFF7EA566,0xFF506F5A),outline,.025f)
            }
            for(j in 0..5){val a=j*PI.toFloat()/3f;val px=xx+cos(a)*.17f;val py=yy+sin(a)*.17f
                fill.color=tone(if(i%2==0)0xFFEDB779 else 0xFFD599AD,0xFF8F9B86)
                c.drawOval(px-.11f,py-.13f,px+.11f,py+.13f,fill)}
            disc(c,xx,yy,.09f,tone(0xFFF6DC9A,0xFFB7BD8D))
            disc(c,xx-.025f,yy-.03f,.035f,0x88FFF6DD.toInt())
        }
        box(c,x-1.48f,f-.38f,x+1.48f,f-.19f,tone(0xFFC8A478,0xFF899980),.05f,outline)
        line(c,x-1.24f,f-.02f,x+1.24f,f-.02f,0x557B654A,.025f)
        for(side in -1..1 step 2)disc(c,x+side*1.23f,f-.28f,.035f,outline)
    }
    private fun pebble(c: Canvas,x: Float,y: Float,size: Float,outline: Int,variant: Int) {
        path.reset();path.moveTo(x-size*.64f,y)
        path.cubicTo(x-size*.7f,y-size*.37f,x-size*.20f,y-size*.7f,x+size*.14f,y-size*.59f)
        path.cubicTo(x+size*.53f,y-size*.65f,x+size*.8f,y-size*.13f,x+size*.6f,y)
        path.quadTo(x,y+size*.17f,x-size*.64f,y);path.close()
        ramp(stoneIds[variant],y-size*.65f,y+size*.17f,tone(0xFFCDD0BC,0xFF9BAC9D),tone(0xFF879887,0xFF536D62))
        shape(c,Color.WHITE,outline,.035f)
        line(c,x-size*.32f,y-size*.38f,x+size*.16f,y-size*.46f,tone(0x99FFF2D4,0x668DA99A),.04f)
    }
    private fun rockGarden(c: Canvas,x: Float,f: Float,outline: Int) {
        fill.color=tone(0xFFB9BE9B,0xFF73876C);c.drawOval(x-1.65f,f-.25f,x+1.65f,f+.25f,fill)
        pebble(c,x-.75f,f+.03f,.75f,outline,0)
        pebble(c,x+.25f,f-.04f,1.15f,outline,1)
        pebble(c,x+1.12f,f+.11f,.55f,outline,2)
        for(i in 0..4){val xx=x-.95f+i*.45f
            path.reset();path.moveTo(xx,f-.1f);path.quadTo(xx-.28f,f-.65f,xx-.37f,f-.5f)
            path.moveTo(xx,f-.1f);path.quadTo(xx+.06f,f-.8f,xx+.17f,f-.68f)
            ink.color=tone(0xFF6F955E,0xFF52765E);ink.strokeWidth=.045f;c.drawPath(path,ink)}
    }
    private fun birdbath(c: Canvas,x: Float,f: Float,outline: Int) {
        fill.color=0x223B513B;c.drawOval(x-.85f,f-.02f,x+.85f,f+.25f,fill)
        box(c,x-.6f,f-.12f,x+.6f,f+.09f,tone(0xFFB2BBA4,0xFF758E7C),.09f,outline)
        path.reset();path.moveTo(x-.3f,f-.15f);path.quadTo(x-.15f,f-.8f,x-.28f,f-1.35f)
        path.lineTo(x+.28f,f-1.35f);path.quadTo(x+.15f,f-.8f,x+.3f,f-.15f);path.close()
        ramp("pedestal",f-1.4f,f,tone(0xFFD5D8BF,0xFFA0B29B),tone(0xFF9EAF96,0xFF6E8978));shape(c,Color.WHITE,outline,.04f)
        path.reset();path.moveTo(x-.94f,f-1.54f);path.quadTo(x-.7f,f-.96f,x,f-1.05f)
        path.quadTo(x+.7f,f-.96f,x+.94f,f-1.54f);path.close()
        shape(c,tone(0xFFB8C2AA,0xFF829C8A),outline,.045f)
        fill.color=tone(0xFFE0E0C6,0xFFB0C2AC);c.drawOval(x-.94f,f-1.74f,x+.94f,f-1.34f,fill)
        ink.color=outline;ink.strokeWidth=.04f;c.drawOval(x-.94f,f-1.74f,x+.94f,f-1.34f,ink)
        fill.color=tone(0xFF9DCFD0,0xFF749CA9);c.drawOval(x-.72f,f-1.66f,x+.72f,f-1.41f,fill)
        line(c,x-.43f,f-1.54f,x+.20f,f-1.58f,tone(0xCCF1F8E2,0x889CB8B3),.035f)
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

package com.sting.petwidget

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
    private val stoneIds = arrayOf("stone0","stone1","stone2")
    private val sunHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 2.6f, 0x51FFE9A7, 0x00FFE9A7, Shader.TileMode.CLAMP)
    }
    private val moonHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1.9f, 0x46BBD5FA, 0x00BBD5FA, Shader.TileMode.CLAMP)
    }
    private val meadowGlint = SceneLighting.AmbientGlow(.62f, .93f, .36f, 0x29F5F2C0, .30f)
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
        val day = now % 240_000L < 120_000L
        c.save()
        c.translate(sunX, sunY)
        c.drawCircle(0f, 0f, if (day) 2.6f else 1.9f, if (day) sunHalo else moonHalo)
        c.restore()
        disc(c, sunX, sunY, .54f, if (day) 0xFFFFE4A2.toInt() else 0xFFF2EBDC.toInt())
        if (!day) {
            disc(c, sunX - .16f, sunY - .05f, .095f, 0x22A2AEC0)
            disc(c, sunX + .18f, sunY + .13f, .12f, 0x27A2AEC0)
        }
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
        // Air perspective: increasingly warm greens towards the yard, blue ridges behind the river.
        val valley = f - 4.8f
        hill(c, w, valley-.94f, f, 1.85f, 0f, "ridge", 0xFF8EBAB7, 0xFFCADBD0, 0xFF3C6376, 0xFF769092)
        hill(c, w, valley, f, 1.60f, .12f, "farMeadow", 0xFF8DB68E, 0xFFC5D5A4, 0xFF406C68, 0xFF628B77)
        hill(c, w, valley+.95f, f, 1.48f, -.10f, "nearMeadow", 0xFF83AA68, 0xFFADC87C, 0xFF466E55, 0xFF698A62)

        val source = w * .64f
        path.reset()
        path.moveTo(source, valley + .34f)
        path.cubicTo(w*.49f,valley+.71f,w*.68f,valley+1.22f,w*.60f,valley+1.68f)
        path.cubicTo(w*.58f,valley+2.08f,w*.46f,valley+2.20f,w*.53f,f-.85f)
        path.lineTo(w*.69f,f-.85f)
        path.cubicTo(w*.53f,valley+2.29f,w*.66f,valley+2.15f,w*.66f,valley+1.67f)
        path.cubicTo(w*.72f,valley+1.04f,w*.51f,valley+.67f,source,valley+.34f)
        path.close()
        ramp("river", valley+.3f, f-.85f, tone(0xFFABD5C6,0xFF597F95), tone(0xFF639EA3,0xFF315970))
        c.save()
        c.clipPath(path)
        fill.color=Color.WHITE
        c.drawRect(0f,valley,w,f,fill)
        fill.shader=null
        for (i in 0..11) {
            val y = valley+.48f+i*.33f
            val trip = ((now % 2700L) / 2700f+i*.23f) % 1f
            val x = w*(.46f+.20f*trip)
            line(c,x,y,x+.45f+i*.09f,y,tone(0xA8E2FAE2,0x67A7CFD8),.037f)
        }
        c.restore()
        tree(c,w*.42f,valley+.82f,.40f,outline)
        tree(c,w*.38f,valley+.91f,.30f,outline)
        tree(c,w*.77f,valley+.80f,.35f,outline)
        tree(c,w*.85f,valley+.49f,.46f,outline)
        tree(c,w*.89f,valley+.60f,.36f,outline)
        shrub(c, w*.51f, f-2.52f, .78f, tone(0xFF88AA5C,0xFF4A745D))
        shrub(c, w*.74f, f-2.32f, 1.10f, tone(0xFF84A656,0xFF456C5B))
        shrub(c, w*.97f, f-2.67f, .91f, tone(0xFF8EAF61,0xFF4D745D))
        hill(c,w,f-1.46f,h,.46f,.025f,"lawn",0xFFA5BC6A,0xFFD1D98F,0xFF5A8059,0xFF78976B)
        meadowGlint.draw(c,w,h,f,(1f-night)*.8f+.18f)
        fence(c,f,outline,prefs?.getInt("yard_fence",0xFFE5CCA0.toInt())?:0xFFE5CCA0.toInt(),
            (prefs?.getInt("yard_fence_style",0)?:0).coerceIn(0,2))
        house(c,f,outline,prefs?.getInt("yard_house",0xFFF1DDBB.toInt())?:0xFFF1DDBB.toInt())
        steppingStones(c,f,outline)
        // Irregular grass sits at the back and near the edges, leaving the pet's route clear.
        for (i in 0..42) {
            val x = 7.4f + (i * 1.71f) % 16.4f
            val y = f - .34f + (i * 13 % 19) * .08f
            val height = .10f + (i % 4) * .06f
            path.reset()
            path.moveTo(x-.15f,y)
            path.quadTo(x-.29f,y-height,x-.09f,y-height*.45f)
            path.quadTo(x,y-.06f,x+.08f,y-height)
            path.quadTo(x+.25f,y-height*.67f,x+.09f,y)
            path.close()
            shape(c,tone(0x74708747,0x8052744C))
        }
        val decor=prefs?.getInt("yard_decor",1)?:1
        if(decor==1)flowerbed(c,10.7f,f,outline)
        if(decor==2)rockGarden(c,10.7f,f,outline)
        if(decor==3)birdbath(c,10.7f,f,outline)
        if(prefs?.getInt("yard_butterflies",1)!=0 && night<.65f)butterflies(c,f,now)
        if(prefs?.getInt("yard_nightlife",1)!=0)nightGarden(c,f,now,outline)
    }

    private fun hill(c: Canvas, w: Float, y: Float, bottom: Float, rise: Float, skew: Float,
                     id: String, dayTop: Long, dayBase: Long, nightTop: Long, nightBase: Long) {
        path.reset()
        path.moveTo(0f,y)
        path.cubicTo(w*.13f,y-rise*.64f,w*(.28f+skew),y-rise*1.23f,w*(.43f+skew),y-rise*.40f)
        path.cubicTo(w*(.61f+skew),y+rise*.55f,w*.78f,y-rise*1.36f,w,y-rise*.53f)
        path.lineTo(w,bottom)
        path.lineTo(0f,bottom)
        path.close()
        ramp(id,y-rise,bottom,tone(dayTop,nightTop),tone(dayBase,nightBase))
        shape(c,Color.WHITE)
    }

    private fun shrub(c: Canvas,x: Float,y: Float,s: Float,color: Int) {
        c.save()
        c.translate(x,y)
        c.scale(s,s)
        path.reset()
        path.moveTo(-1.9f,0f)
        path.cubicTo(-2f,-.68f,-1.16f,-.98f,-.92f,-.44f)
        path.cubicTo(-1.04f,-1.33f,.15f,-1.57f,.30f,-.78f)
        path.cubicTo(.88f,-1.10f,1.30f,-.72f,1.30f,-.45f)
        path.quadTo(2.03f,-.68f,2.08f,0f)
        path.quadTo(0f,.20f,-1.9f,0f)
        path.close()
        ramp("shrub",-1.2f,0f,mix(color,Color.WHITE,.19f),color)
        shape(c,Color.WHITE)
        c.restore()
    }

    private fun tree(c: Canvas,x: Float,y: Float,s: Float,outline: Int) {
        c.save()
        c.translate(x,y)
        c.scale(s,s)
        val trunk = tone(0xFF816743,0xFF536254)
        val leaf = tone(0xFF679459,0xFF406C61)
        path.reset()
        path.moveTo(-.29f,-.50f)
        path.cubicTo(-1.02f,-.52f,-1.14f,-1.25f,-.72f,-1.65f)
        path.cubicTo(-.93f,-2.35f,-.48f,-2.70f,-.22f,-2.81f)
        path.cubicTo(-.03f,-3.40f,.64f,-3.20f,.64f,-2.55f)
        path.cubicTo(1.24f,-2.22f,.85f,-1.69f,.83f,-1.57f)
        path.cubicTo(1.05f,-.85f,.57f,-.42f,-.29f,-.50f)
        path.close()
        ramp("tree",-3.10f,-.46f,mix(leaf,Color.WHITE,.22f),leaf)
        shape(c,Color.WHITE,mix(leaf,outline,.24f),.040f)
        line(c,0f,.04f,.11f,-2.33f,trunk,.14f)
        line(c,.06f,-1.17f,-.43f,-1.74f,trunk,.073f)
        line(c,.07f,-1.54f,.49f,-2.06f,trunk,.067f)
        path.reset()
        path.moveTo(-.56f,-1.98f)
        path.quadTo(-.53f,-2.46f,-.14f,-2.58f)
        path.quadTo(-.19f,-2.21f,-.56f,-1.98f)
        shape(c,tone(0x6FE2E6A0,0x435D8B72))
        c.restore()
    }
    private fun fence(c: Canvas,f: Float,outline: Int,color: Int,style: Int){
        val wood=mix(color,0xFF485F54.toInt(),night*.58f)
        val edgeColor=mix(outline,wood,.57f)
        val light=mix(wood,Color.WHITE,.16f)
        val dark=mix(wood,outline,.18f)
        if(style==1) {
            // Широкие горизонтальные доски, регулярные стойки одной высоты.
            for(i in 0..3){
                val y=f-2.45f+i*.51f
                box(c,7f,y,24f,y+.40f,wood,.055f,edgeColor)
                line(c,7.16f,y+.33f,24f,y+.33f,dark,.033f)
                line(c,7.08f,y+.07f,24f,y+.07f,light,.035f)
            }
        } else if(style==2) {
            // Решётка замкнута рамой: диагонали не выходят за панель.
            c.save();c.clipRect(7f,f-2.45f,24f,f-.5f)
            for(i in 0..31){val x=5f+i*.65f
                line(c,x,f-.45f,x+2f,f-2.55f,dark,.10f)
                line(c,x,f-2.55f,x+2f,f-.45f,wood,.10f)}
            c.restore()
            box(c,7f,f-2.53f,24f,f-2.32f,wood,.03f,outline)
            box(c,7f,f-.64f,24f,f-.43f,wood,.03f,outline)
        } else {
            // Штакетник с мягкими верхушками и узкими одинаковыми промежутками.
            box(c,7f,f-2.08f,24f,f-1.90f,dark,.02f,outline)
            box(c,7f,f-1.04f,24f,f-.86f,dark,.02f,outline)
            for(i in 0..28) {
                val x=7f+i*.60f
                val crown = f - 2.35f - .17f * cos(i * .98f)
                path.reset()
                path.moveTo(x, f - .43f)
                path.lineTo(x, crown + .17f)
                path.quadTo(x + .26f, crown - .18f, x + .52f, crown + .17f)
                path.lineTo(x + .52f, f - .43f)
                path.close()
                ramp("pickets", f - 2.58f, f - .43f, light, dark)
                shape(c, Color.WHITE, edgeColor, .050f)
                line(c,x+.09f,f-2.22f,x+.09f,f-.67f,light,.035f)
                disc(c,x+.26f,f-1.99f,.022f,outline)
                disc(c,x+.26f,f-.95f,.022f,outline)
            }
        }
        for(i in 0..5) {
            val x=7f+i*3.4f
            box(c,x-.04f,f-2.54f,x+.22f,f-.33f,wood,.04f,outline)
            box(c,x-.09f,f-2.63f,x+.27f,f-2.45f,light,.045f,outline)
        }
    }
    private fun house(c: Canvas, f: Float, outline: Int, color: Int) {
        val corner = 7.68f
        val eave = f-7.58f
        val wall = mix(color,0xFF6D8B82.toInt(),night*.64f)
        val shadow = mix(wall,outline,.37f)
        val wood = tone(0xFFC58C50,0xFF6B7460)
        val woodLight = tone(0xFFE9B676,0xFF8F9475)
        val roof = tone(0xFF617E60,0xFF314E54)

        ramp("facade",eave,f,mix(wall,outline,.15f),mix(wall,Color.WHITE,.14f))
        path.reset()
        path.moveTo(-.6f,eave-1.36f)
        path.cubicTo(1.34f,eave-.91f,4.60f,eave-.22f,corner,eave+.34f)
        path.lineTo(corner,f)
        path.lineTo(-.6f,f)
        path.close()
        shape(c,Color.WHITE,outline,.075f)
        for (i in 0..9) {
            val x = i * .82f
            line(c,x,eave,x,f-.37f,mix(shadow,wall,.44f),.031f)
            line(c,x+.045f,eave,x+.045f,f-.37f,mix(wall,Color.WHITE,.41f),.023f)
        }

        path.reset()
        path.moveTo(-1f,eave)
        path.lineTo(corner+.46f,eave+.10f)
        path.lineTo(corner-.02f,eave+.81f)
        path.lineTo(-1f,eave+.92f)
        path.close()
        shape(c,shadow)
        box(c,corner-.40f,eave+.16f,corner+.12f,f-.13f,wood,.073f,outline)
        box(c,corner-.39f,eave+.22f,corner-.23f,f-.21f,woodLight,.045f)
        box(c,-.6f,f-.37f,corner+.14f,f+.02f,tone(0xFFD9C09B,0xFF7D8E7D),.04f,outline)
        box(c,2.13f,f-.11f,6.63f,f+.22f,tone(0xFFE7CCA0,0xFF829B88),.11f,outline)

        path.reset()
        path.moveTo(-1.30f,eave-2.98f)
        path.cubicTo(.68f,eave-2.69f,4.12f,eave-1.92f,6.72f,eave-1.20f)
        path.lineTo(corner+.83f,eave+.04f)
        path.quadTo(corner+.87f,eave+.19f,corner+.64f,eave+.22f)
        path.cubicTo(4.6f,eave-.50f,1.42f,eave-1.11f,-1.30f,eave-1.68f)
        path.close()
        ramp("roof",eave-3f,eave+.20f,mix(roof,Color.WHITE,.29f),roof)
        shape(c,Color.WHITE,outline,.085f)
        c.save()
        c.clipPath(path)
        for (row in 0..4) {
            val y=eave-2.90f+row*.29f
            line(c,-.60f,y,corner+.83f,y+1.87f,mix(roof,outline,.34f),.052f)
            for (i in 0..8) {
                val x=i*1f+(row%2)*.46f
                line(c,x,y+x*.21f,x+.17f,y+.33f+x*.21f,mix(roof,outline,.27f),.044f)
            }
        }
        c.restore()
        path.reset()
        path.moveTo(-1.30f,eave-1.68f)
        path.cubicTo(1.42f,eave-1.11f,4.6f,eave-.50f,corner+.64f,eave+.22f)
        path.lineTo(corner+.48f,eave+.49f)
        path.cubicTo(4.52f,eave-.16f,1.34f,eave-.74f,-1.30f,eave-1.34f)
        path.close()
        shape(c,mix(roof,outline,.36f),outline,.074f)
        path.reset()
        path.moveTo(corner-1.40f,eave+.15f)
        path.lineTo(corner-.20f,eave+.44f)
        path.lineTo(corner-.20f,eave+1.69f)
        path.quadTo(corner-.56f,eave+.66f,corner-1.40f,eave+.40f)
        path.close()
        shape(c,wood,outline,.074f)
        birdhouse(c,1.02f,f-3.51f,outline)
    }

    private fun steppingStones(c: Canvas,f: Float, outline: Int) {
        for (i in 0..4) {
            val x=4.52f+i*1.14f
            val y=f+.30f+i*.19f
            val width=.49f+i*.10f
            fill.color=tone(0x4E536238,0x674A6650)
            c.drawOval(x-width,y-.09f,x+width+.19f,y+.29f,fill)
            path.reset()
            path.moveTo(x-width,y)
            path.cubicTo(x-width*.72f,y-.36f,x+width*.89f,y-.39f,x+width,y-.04f)
            path.quadTo(x+width*.95f,y+.25f,x+.10f,y+.22f)
            path.quadTo(x-width*.97f,y+.23f,x-width,y)
            path.close()
            ramp("step$i",y-.23f,y+.25f,tone(0xFFF5DBAB,0xFF94A997),tone(0xFFC6A778,0xFF697F73))
            shape(c,Color.WHITE,mix(outline,0xFFB79E6C.toInt(),.55f),.045f)
            line(c,x-width*.55f,y-.10f,x+width*.22f,y-.17f,tone(0x94FFF0C8,0x69CCDED1),.040f)
        }
    }

    private fun birdhouse(c: Canvas,x: Float,y: Float,outline: Int) {
        c.save()
        c.translate(x,y)
        val clay=tone(0xFFD89161,0xFF9C8565)
        val dark=tone(0xFF99643E,0xFF596C55)
        fill.color=tone(0x33745333,0x4C415948)
        c.drawRoundRect(-.70f,-1.22f,.86f,.57f,.18f,.18f,fill)
        path.reset()
        path.moveTo(-.62f,-.68f)
        path.lineTo(.03f,-1.38f)
        path.lineTo(.58f,-.68f)
        path.lineTo(.58f,.34f)
        path.quadTo(0f,.44f,-.62f,.30f)
        path.close()
        ramp("birdhouse",-1.32f,.37f,mix(clay,Color.WHITE,.23f),clay)
        shape(c,Color.WHITE,outline,.067f)
        path.reset()
        path.moveTo(-.83f,-.65f)
        path.lineTo(-.01f,-1.64f)
        path.lineTo(.80f,-.68f)
        path.lineTo(.61f,-.55f)
        path.lineTo(-.01f,-1.31f)
        path.lineTo(-.63f,-.54f)
        path.close()
        shape(c,dark,outline,.069f)
        disc(c,0f,-.51f,.26f,dark)
        disc(c,0f,-.53f,.20f,mix(outline,dark,.22f))
        line(c,-.11f,-.03f,.16f,-.03f,tone(0xFFF4C98D,0xFFB0A47F),.091f)
        box(c,-.82f,.35f,.82f,.51f,dark,.038f,outline)
        line(c,-.60f,.53f,-.32f,.76f,dark,.080f)
        c.restore()
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

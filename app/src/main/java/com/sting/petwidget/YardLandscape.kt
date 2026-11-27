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
        ramp("river", valley+.3f, f-.85f, tone(0xFFB5DEDF,0xFF597F95), tone(0xFF73AFC8,0xFF315970))
        c.save()
        c.clipPath(path)
        fill.color=Color.WHITE
        c.drawRect(0f,valley,w,f,fill)
        fill.shader=null
        for (i in 0..15) {
            val phase = ((now % 6400L) / 6400f + i / 16f) % 1f
            val y = valley + .37f + phase * 3.4f
            val x = w * (.43f + (i % 5) * .045f)
            ink.color = tone(0xAEF4FFDE, 0x6CBBDAE0)
            ink.alpha = (ink.alpha * sin(phase * PI.toFloat())).toInt()
            ink.strokeWidth = .023f + .041f * phase
            path.reset()
            path.moveTo(x, y)
            path.quadTo(x + .65f, y + .11f * phase, x + 1.35f + phase, y)
            c.drawPath(path, ink)
        }
        c.restore()
        // Interrupted tree lines sit behind the bends and leave the channel open.
        for (i in 0..17) {
            val x = w * (.27f + i * .043f)
            if (x in w * .57f..w * .70f) continue
            val y = valley + .63f + sin(i * .68f) * .36f
            shrub(c, x, y, .24f + (i % 4) * .042f, tone(0xFF91AD78, 0xFF4E766C))
            if (i % 3 != 1) tree(c, x, y + .13f, .23f + (i % 4) * .055f, outline)
        }
        tree(c,w*.42f,valley+.82f,.40f,outline)
        tree(c,w*.38f,valley+.91f,.30f,outline)
        tree(c,w*.77f,valley+.80f,.35f,outline)
        tree(c,w*.85f,valley+.49f,.46f,outline)
        tree(c,w*.89f,valley+.60f,.36f,outline)
        shrub(c, w*.51f, f-2.52f, .78f, tone(0xFF88AA5C,0xFF4A745D))
        shrub(c, w*.74f, f-2.32f, 1.10f, tone(0xFF84A656,0xFF456C5B))
        shrub(c, w*.97f, f-2.67f, .91f, tone(0xFF8EAF61,0xFF4D745D))
        hill(c,w,f-1.46f,h,.46f,.025f,"lawn",0xFFABC06E,0xFFE1DE8E,0xFF5A8059,0xFF78976B)
        meadowGlint.draw(c,w,h,f,(1f-night)*.8f+.18f)
        fence(c,f,outline,prefs?.getInt("yard_fence",0xFFE5CCA0.toInt())?:0xFFE5CCA0.toInt(),
            (prefs?.getInt("yard_fence_style",0)?:0).coerceIn(0,2))
        shrub(c, w * .34f, f - .24f, .46f, tone(0xFF8AA05C, 0xFF45674E))
        shrub(c, w * .59f, f - .34f, .55f, tone(0xFF87A05E, 0xFF4A6F54))
        shrub(c, w * .96f, f - .28f, .74f, tone(0xFF8AA25F, 0xFF456A53))
        fill.color = tone(0x3974553D,0x47425151)
        c.drawOval(-2f,f-.48f,9.2f,f+.44f,fill)
        house(c,f,outline,prefs?.getInt("yard_house",0xFFF1DDBB.toInt())?:0xFFF1DDBB.toInt())
        gardenPath(c, w, h, f)
        lawnPlants(c, w, h, f)
        borderLeaves(c, .75f, h + .16f, .76f)
        borderLeaves(c, w - .78f, h + .22f, 1f)
        val decor=prefs?.getInt("yard_decor",1)?:1
        if(decor==1)flowerbed(c,10.7f,f,outline)
        if(decor==2)rockGarden(c,10.7f,f,outline)
        if(decor==3)birdbath(c,10.7f,f,outline)
        if(prefs?.getInt("yard_butterflies",1)!=0 && night<.65f)butterflies(c,f,now)
        if(prefs?.getInt("yard_nightlife",1)!=0)nightGarden(c,f,now,outline)
    }

    private fun borderLeaves(c: Canvas, x: Float, y: Float, size: Float) {
        val dark = tone(0xFF658649,0xFF31594D)
        val light = tone(0xFFA8B65B,0xFF638364)
        c.save()
        c.translate(x, y)
        c.scale(size, size)
        for (i in 0..5) {
            val rotation = -69f + i * 28f
            val reach = 1.14f + (i * 7 % 5) * .13f
            c.save()
            c.rotate(rotation)
            path.reset()
            path.moveTo(0f, .04f)
            path.cubicTo(-.75f,-.38f,-.57f,-reach*.87f,-.04f,-reach)
            path.cubicTo(.36f,-reach*.82f,.48f,-.34f,0f,.04f)
            path.close()
            ramp("borderLeaf",-reach,0f,if (i%2==0) light else mix(light,dark,.27f),dark)
            shape(c,Color.WHITE, dark, .049f)
            path.reset()
            path.moveTo(0f, -.08f)
            path.quadTo(-.19f, -.48f, -.05f, -reach*.82f)
            ink.color = mix(light,dark,.36f)
            ink.strokeWidth = .045f
            c.drawPath(path, ink)
            c.restore()
        }
        c.restore()
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
        val corner = 7.39f
        val eave = f - 7.82f
        val plaster = mix(color, 0xFF81968A.toInt(), night * .67f)
        val cream = mix(plaster, Color.WHITE, .19f)
        val frame = tone(0xFFB28350, 0xFF748374)
        val honey = tone(0xFFD9AE72, 0xFF8B9A83)
        val roof = tone(0xFF78905C, 0xFF46635E)
        val roofTrim = mix(roof, outline, .45f)

        // Broad shapes keep the cropped facade readable at widget size.
        path.reset()
        path.moveTo(-1.3f, eave - 1.78f)
        path.quadTo(3.36f, eave - .93f, corner, eave + .31f)
        path.lineTo(corner, f - .25f)
        path.quadTo(corner, f, corner - .25f, f)
        path.lineTo(-1.3f, f)
        path.close()
        shape(c, cream, outline, .087f)
        c.save()
        c.clipPath(path)
        val seam = mix(plaster, frame, .24f)
        val highlight = mix(cream, Color.WHITE, .38f)
        for (board in 1..13) {
            val y = f - .33f - board * .64f
            line(c,-1.3f,y,corner,y,seam,.030f)
            line(c,-1.3f,y+.055f,corner,y+.055f,highlight,.037f)
            val x = .14f + (board * 11 % 18) * .35f
            path.reset()
            path.moveTo(x,y+.32f)
            path.quadTo(x+.31f,y+.24f,x+.72f,y+.33f)
            ink.color = mix(seam, cream, .38f)
            ink.strokeWidth = .023f
            c.drawPath(path,ink)
        }
        c.restore()
        box(c, corner - .37f, eave + .05f, corner, f, honey, .09f)
        box(c, -1.2f, f - .32f, corner, f + .01f, honey, .065f, outline)

        path.reset()
        path.moveTo(-1.25f, eave - 4.2f)
        path.quadTo(3.20f, eave - 3.05f, 7.88f, eave - .26f)
        path.quadTo(8.14f, eave + .13f, 7.75f, eave + .19f)
        path.quadTo(3.13f, eave - .90f, -1.25f, eave - 1.91f)
        path.close()
        shape(c, roof, outline, .096f)
        c.save()
        c.clipPath(path)
        for (row in 0..3) {
            val y = eave - 3.69f + row * .60f
            path.reset()
            path.moveTo(-1.2f, y)
            path.quadTo(3.05f, y + .64f, 8f, y + 2.06f)
            ink.color = mix(roof, outline, .28f)
            ink.strokeWidth = .053f
            c.drawPath(path, ink)
        }
        c.restore()
        path.reset()
        path.moveTo(-1.3f, eave - 2.07f)
        path.quadTo(3.18f, eave - 1.29f, 7.81f, eave + .02f)
        path.quadTo(8.04f, eave + .24f, 7.76f, eave + .54f)
        path.quadTo(3.11f, eave - .71f, -1.3f, eave - 1.56f)
        path.close()
        shape(c, roofTrim, outline, .077f)

        val glass = tone(0xFFFFDA91, 0xFFE6AF67)
        box(c,-1.36f,f-6.16f,1.75f,f-2.36f,frame,.70f,outline)
        box(c,-1.13f,f-5.92f,1.49f,f-2.58f,glass,.55f)
        line(c,.24f,f-5.85f,.24f,f-2.64f,frame,.112f)
        line(c,-1f,f-4.30f,1.40f,f-4.30f,frame,.11f)
        box(c,-1.44f,f-2.77f,1.94f,f-2.30f,honey,.16f,outline)
        box(c,1.96f,f-.12f,6.68f,f+.23f,honey,.16f,outline)

        // A small window box repeats the garden palette against the light wall.
        box(c,-.89f,f-2.90f,1.61f,f-2.46f,tone(0xFFB97D55,0xFF7A8672),.11f,outline)
        for (sprig in 0..4) {
            val x = -.64f + sprig * .48f
            val y = f - 3.06f - (sprig % 3) * .15f
            line(c,x,f-2.88f,x+.09f,y,tone(0xFF617D47,0xFF658469),.074f)
            disc(c,x+.08f,y,.155f,tone(0xFFFFE4AA,0xFFCABA97))
            disc(c,x+.08f,y,.064f,tone(0xFFE0A45D,0xFFA69775))
        }
    }

    private fun gardenPath(c: Canvas, w: Float, h: Float, f: Float) {
        val depth = h - f
        val top = f + depth * .26f
        val bottom = f + depth * .72f
        val edge = tone(0xFFB99E6B, 0xFF6B806B)
        val surface = tone(0xFFE6CD99, 0xFF9FAE94)
        path.reset()
        path.moveTo(-1f, top + .34f)
        path.cubicTo(w*.25f,top-.22f,w*.40f,top+.09f,w*.54f,top+.39f)
        path.cubicTo(w*.73f,top+.83f,w*.86f,top-.20f,w+1f,top+.18f)
        path.lineTo(w+1f,bottom+.32f)
        path.cubicTo(w*.85f,bottom-.11f,w*.72f,bottom+.98f,w*.53f,bottom+.48f)
        path.cubicTo(w*.38f,bottom+.15f,w*.23f,bottom-.07f,-1f,bottom+.41f)
        path.close()
        ramp("gardenPath",top,bottom+.60f,surface,edge)
        shape(c,Color.WHITE,edge,.075f)

        // A short curved spur connects the doorstep to the main path.
        path.reset()
        path.moveTo(3.52f,f+.16f)
        path.lineTo(4.92f,f+.14f)
        path.quadTo(5.32f,top-.03f,6.88f,top+.10f)
        path.lineTo(5.83f,top+.39f)
        path.quadTo(4.23f,top+.43f,3.52f,f+.16f)
        path.close()
        shape(c,surface)
        for (i in 0..24) {
            val x = .2f + i * (w / 24f)
            val y = f + depth * (.49f + (i * 5 % 7) * .018f)
            fill.color = if (i % 3 == 1) edge else tone(0xFFC7AD79,0xFF839682)
            c.drawOval(x-.072f,y-.023f,x+.083f,y+.030f,fill)
        }
    }

    private fun lawnPlants(c: Canvas, w: Float, h: Float, f: Float) {
        val depth = h - f
        for (patch in 0..8) {
            val center = 8.95f + patch * (w - 9.05f) / 8f
            val base = if (patch % 2 == 0) f + .15f else h - depth * .11f
            val foliage = tone(0xFF749448, 0xFF517758)
            if (patch % 2 == 0 && patch != 6) {
                shrub(c,center,base,.36f + (patch % 3) * .11f,foliage)
            }
            for (sprig in 0..4) {
                val x = center + (sprig - 2) * .28f
                val y = base + (sprig * 3 % 5) * .11f
                val height = .18f + (patch + sprig * 3) % 5 * .065f
                path.reset()
                path.moveTo(x-.12f,y)
                path.quadTo(x-.31f,y-height*.83f,x-.17f,y-height)
                path.quadTo(x-.01f,y-height*.53f,x,y-.09f)
                path.quadTo(x+.13f,y-height,x+.23f,y-height*.95f)
                path.quadTo(x+.24f,y-.10f,x+.10f,y)
                path.close()
                shape(c, foliage)
                if ((sprig + patch) % 3 == 1) {
                    val tip = y - height - .10f
                    line(c,x,y,x-.025f,tip,foliage,.038f)
                    val petal = if (patch % 3 == 0) tone(0xFFF1AD9B,0xFFA08C8F)
                        else tone(0xFFFFE8A7,0xFFC2B79C)
                    for (part in 0..4) {
                        val angle = part * PI.toFloat() * .4f
                        disc(c,x-.025f+cos(angle)*.085f,tip+sin(angle)*.085f,.073f,petal)
                    }
                    disc(c,x-.025f,tip,.063f,tone(0xFFD9995D,0xFFA09369))
                }
            }
        }
    }

    private fun flowerbed(c: Canvas,x: Float,f: Float,outline: Int){
        fill.color=0x223B513B;c.drawOval(x-1.55f,f-.07f,x+1.55f,f+.3f,fill)
        box(c,x-1.4f,f-.48f,x+1.4f,f+.14f,tone(0xFFC99263,0xFF677E6C),.15f,outline)
        fill.color=tone(0xFF604E38,0xFF3F5348);c.drawOval(x-1.32f,f-.62f,x+1.32f,f-.29f,fill)
        for(i in 0..4){
            val xx=x-1.02f+i*.51f;val yy=f-.91f-(i%3)*.32f
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
        line(c,x-1.24f,f-.02f,x+1.24f,f-.02f,0x557B654A,.034f)
        line(c,x-1.34f,f-.34f,x+1.35f,f-.34f,tone(0xA9FFE1A5,0x6DBDCEAA),.044f)
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
        fill.color=tone(0xFFB4AE79,0xFF677C62)
        c.drawOval(x-1.72f,f-.32f,x+1.72f,f+.33f,fill)
        for(i in 0..8) disc(c,x-1.5f+i*.37f,f+.12f+(i%3)*.055f,.050f,
            tone(0xFFD8CCA3,0xFFA0AB8F))
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
        ink.color=tone(0xB5F7F9D7,0x889CB8B3)
        ink.strokeWidth=.038f
        c.drawArc(x-.57f,f-1.61f,x+.39f,f-1.44f,190f,131f,false,ink)
        c.drawArc(x-.19f,f-1.59f,x+.39f,f-1.50f,11f,135f,false,ink)
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

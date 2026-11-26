package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/** Rounded wooden and fabric toys, all anchored at their actual floor contact. */
internal class ToyArtwork {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeWidth=.085f
        strokeJoin=Paint.Join.ROUND
        strokeCap=Paint.Cap.ROUND
        color=0xFF514531.toInt()
    }
    private val material=VectorFinish()
    private val path=Path()
    private val ivory=0xFFF3E1BC.toInt()
    private val terra=0xFFD58F6D.toInt()
    private val sage=0xFF8DAB85.toInt()
    private val gold=0xFFE7BD73.toInt()

    private fun box(c: Canvas,l: Float,t: Float,r: Float,b: Float,color: Int,round: Float=.20f) {
        material.apply(p,color,l,t,r,b)
        c.drawRoundRect(l,t,r,b,round,round,p)
        p.shader=null
        c.drawRoundRect(l,t,r,b,round,round,ink)
    }

    private fun oval(c: Canvas,x: Float,y: Float,rx: Float,ry: Float,color: Int) {
        material.apply(p,color,x-rx,y-ry,x+rx,y+ry)
        c.drawOval(x-rx,y-ry,x+rx,y+ry,p)
        p.shader=null
        c.drawOval(x-rx,y-ry,x+rx,y+ry,ink)
    }

    private fun shape(c: Canvas,color: Int) {
        p.color=color
        c.drawPath(path,p)
        c.drawPath(path,ink)
    }

    fun draw(id: String,c: Canvas,x: Float,y: Float,u: Float,time: Float) {
        c.save()
        c.translate(x,y)
        c.scale(u,u)
        when(id) {
            "block" -> {
                box(c,-.95f,-1.94f,.95f,-.04f,sage,.27f)
                box(c,-.74f,-1.73f,.74f,-.24f,ivory,.19f)
                path.reset()
                path.moveTo(0f,-1.46f)
                path.lineTo(.40f,-.69f)
                path.lineTo(-.40f,-.69f)
                path.close()
                shape(c,terra)
            }
            "dice" -> {
                box(c,-1f,-2.04f,1f,-.04f,ivory,.32f)
                for ((px,py) in listOf(-.5f to -1.54f,.5f to -1.54f,0f to -1.04f,-.5f to -.54f,.5f to -.54f)) {
                    oval(c,px,py,.14f,.14f,0xFF6B7055.toInt())
                }
            }
            "plushie" -> {
                for (side in -1..1 step 2) oval(c,side*.65f,-2.93f,.38f,.39f,terra)
                oval(c,0f,-1.16f,.83f,1.11f,terra)
                for (side in -1..1 step 2) {
                    oval(c,side*.84f,-1.20f,.31f,.58f,terra)
                    oval(c,side*.48f,-.27f,.43f,.27f,terra)
                }
                oval(c,0f,-1.13f,.54f,.70f,ivory)
                oval(c,0f,-2.48f,.88f,.78f,terra)
                oval(c,0f,-2.24f,.42f,.30f,ivory)
                for (side in -1..1 step 2) oval(c,side*.32f,-2.57f,.075f,.105f,0xFF554633.toInt())
                oval(c,0f,-2.34f,.12f,.08f,0xFF554633.toInt())
                c.drawLine(0f,-2.25f,0f,-2.12f,ink)
                box(c,-.49f,-1.81f,.49f,-1.59f,sage,.08f)
            }
            "balloon" -> {
                val sway=sin(time*1.3f)*.10f
                path.reset()
                path.moveTo(sway,-2.03f)
                path.cubicTo(-.34f,-1.59f,.28f,-.60f,0f,-.04f)
                c.drawPath(path,ink)
                oval(c,sway,-3.28f,.94f,1.25f,terra)
                p.color=0x99FFF5D6.toInt()
                c.drawOval(sway-.59f,-4.11f,sway-.32f,-3.54f,p)
            }
            "rocket" -> {
                for (side in -1..1 step 2) {
                    path.reset()
                    path.moveTo(side*.42f,-1.45f)
                    path.quadTo(side*1.18f,-.85f,side*.97f,-.15f)
                    path.lineTo(side*.38f,-.50f)
                    path.close()
                    shape(c,terra)
                }
                path.reset()
                path.moveTo(0f,-4.02f)
                path.cubicTo(.98f,-3.35f,.81f,-1.29f,.45f,-.38f)
                path.lineTo(-.45f,-.38f)
                path.cubicTo(-.81f,-1.29f,-.98f,-3.35f,0f,-4.02f)
                path.close()
                shape(c,ivory)
                oval(c,0f,-2.32f,.43f,.47f,sage)
                oval(c,-.10f,-2.44f,.13f,.15f,0xFFDFEDD5.toInt())
                box(c,-.42f,-.48f,.42f,-.05f,0xFF6F7E6A.toInt(),.08f)
            }
            "boat" -> {
                ink.strokeWidth=.11f
                c.drawLine(-.10f,-.63f,-.10f,-3.45f,ink)
                ink.strokeWidth=.085f
                path.reset()
                path.moveTo(.02f,-3.40f)
                path.quadTo(1.14f,-2.55f,1.19f,-1.32f)
                path.lineTo(.02f,-1.32f)
                path.close()
                shape(c,ivory)
                path.reset()
                path.moveTo(-.25f,-2.97f)
                path.lineTo(-1.13f,-1.34f)
                path.lineTo(-.25f,-1.34f)
                path.close()
                shape(c,sage)
                path.reset()
                path.moveTo(-1.45f,-.96f)
                path.lineTo(1.45f,-.96f)
                path.quadTo(.98f,-.05f,.69f,-.04f)
                path.lineTo(-.84f,-.04f)
                path.close()
                shape(c,terra)
            }
            "flower" -> {
                ink.color=0xFF687D50.toInt()
                c.drawLine(0f,-.04f,0f,-2.46f,ink)
                oval(c,-.34f,-.94f,.40f,.18f,sage)
                oval(c,.34f,-1.38f,.40f,.18f,sage)
                ink.color=0xFF514531.toInt()
                for (i in 0..5) {
                    val angle=i*1.0472f
                    oval(c,cos(angle)*.58f,-2.52f+sin(angle)*.58f,.41f,.41f,ivory)
                }
                oval(c,0f,-2.52f,.39f,.39f,gold)
            }
            "top" -> {
                val rock = sin(time*8f)*.06f
                c.rotate(rock*57.3f,0f,-1.1f)
                box(c,-.15f,-3.11f,.15f,-2.28f,gold,.09f)
                oval(c,0f,-1.55f,1f,1.02f,sage)
                box(c,-.98f,-1.65f,.98f,-1.40f,ivory,.10f)
                path.reset()
                path.moveTo(-.28f,-.62f)
                path.lineTo(.28f,-.62f)
                path.lineTo(0f,-.04f)
                path.close()
                shape(c,gold)
            }
        }
        c.restore()
    }
}

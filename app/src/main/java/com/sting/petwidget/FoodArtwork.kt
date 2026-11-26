package com.sting.petwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Local coordinates retain the established grip points and floor origin. */
internal class FoodArtwork {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = .075f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF564633.toInt()
    }
    private val material = VectorFinish()
    private val path = Path()

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        material.apply(fill, color, l, t, r, b)
        c.drawOval(l,t,r,b,fill)
        fill.shader=null
        c.drawOval(l,t,r,b,line)
    }

    private fun box(c: Canvas,l: Float,t: Float,r: Float,b: Float,color: Int,radius: Float=.16f) {
        material.apply(fill,color,l,t,r,b)
        c.drawRoundRect(l,t,r,b,radius,radius,fill)
        fill.shader=null
        c.drawRoundRect(l,t,r,b,radius,radius,line)
    }

    private fun shape(c: Canvas,color: Int) {
        fill.color=color
        c.drawPath(path,fill)
        c.drawPath(path,line)
    }

    fun draw(id: String,c: Canvas,x: Float,floor: Float,u: Float,time: Float) {
        c.save()
        c.translate(x,floor)
        c.scale(u,u)
        when(id) {
            "pizza" -> {
                path.reset()
                path.moveTo(0f,-2.5f)
                path.quadTo(.58f,-1.73f,.98f,-.29f)
                path.quadTo(0f,.06f,-.98f,-.29f)
                path.close()
                shape(c,0xFFF2C867.toInt())
                line.color=0xFFC68C49.toInt()
                line.strokeWidth=.22f
                c.drawArc(-1f,-.55f,1f,.01f,12f,156f,false,line)
                line.color=0xFF564633.toInt()
                line.strokeWidth=.075f
                for ((px,py) in listOf(-.32f to -.68f,.30f to -.86f,0f to -1.56f)) {
                    oval(c,px-.18f,py-.16f,px+.18f,py+.16f,0xFFD77E5D.toInt())
                }
            }
            "sushi" -> {
                oval(c,-1.12f,-1.43f,1.12f,-.06f,0xFFF8EAD2.toInt())
                box(c,-.31f,-1.41f,.31f,-.10f,0xFF425D4C.toInt(),.08f)
                oval(c,-.23f,-1.39f,.23f,-1.09f,0xFFDE956C.toInt())
            }
            "donut", "cookie" -> {
                oval(c,-1.12f,-2f,1.12f,-.04f,0xFFD99F60.toInt())
                if (id == "donut") {
                    oval(c,-1.04f,-1.94f,1.04f,-.20f,0xFFE8A6A0.toInt())
                    oval(c,-.36f,-1.36f,.36f,-.73f,0xFF8C6145.toInt())
                    for (i in 0..5) {
                        val px = -.72f + i * .28f
                        val py = if (i % 2 == 0) -1.61f else -.41f
                        line.color=0xFFFBE2A2.toInt()
                        c.drawLine(px,py,px+.10f,py-.08f,line)
                    }
                    line.color=0xFF564633.toInt()
                } else {
                    for ((px,py) in listOf(-.5f to -1.5f,.38f to -1.54f,0f to -.96f,-.56f to -.53f,.53f to -.56f)) {
                        box(c,px-.12f,py-.10f,px+.12f,py+.10f,0xFF78523B.toInt(),.06f)
                    }
                }
            }
            "coffee" -> {
                oval(c,.70f,-1.68f,1.43f,-.60f,0xFFF4DEB8.toInt())
                oval(c,.90f,-1.47f,1.23f,-.82f,0xFFB58C62.toInt())
                box(c,-.95f,-1.97f,.95f,-.10f,0xFFF6E3BF.toInt(),.28f)
                oval(c,-.94f,-2.10f,.94f,-1.71f,0xFF76553C.toInt())
                oval(c,-.70f,-2f,.70f,-1.79f,0xFFC39562.toInt())
                line.color=0x70FFF4DF
                line.strokeWidth=.055f
                for (i in -1..1) {
                    path.reset()
                    path.moveTo(i*.29f,-2.25f)
                    path.cubicTo(i*.29f-.12f,-2.55f,i*.29f+.16f,-2.73f,i*.29f+sin(time*1.4f)*.10f,-2.97f)
                    c.drawPath(path,line)
                }
                line.color=0xFF564633.toInt()
                line.strokeWidth=.075f
            }
            "apple" -> {
                path.reset()
                path.moveTo(0f,-1.85f)
                path.cubicTo(.89f,-2.42f,1.35f,-1.26f,.74f,-.27f)
                path.quadTo(.40f,.06f,0f,-.12f)
                path.quadTo(-.40f,.06f,-.74f,-.27f)
                path.cubicTo(-1.35f,-1.26f,-.89f,-2.42f,0f,-1.85f)
                path.close()
                shape(c,0xFFE39171.toInt())
                line.strokeWidth=.11f
                c.drawLine(0f,-1.84f,.10f,-2.23f,line)
                line.strokeWidth=.075f
                path.reset()
                path.moveTo(.08f,-1.98f)
                path.quadTo(.60f,-2.46f,.79f,-2.02f)
                path.quadTo(.45f,-1.72f,.08f,-1.98f)
                shape(c,0xFF8AA87A.toInt())
                fill.color=0x80FFF2D5.toInt()
                c.drawOval(-.65f,-1.72f,-.43f,-1.19f,fill)
            }
            "icecream" -> {
                path.reset()
                path.moveTo(-.58f,-1.39f)
                path.lineTo(.58f,-1.39f)
                path.lineTo(0f,-.04f)
                path.close()
                shape(c,0xFFE7BC7D.toInt())
                line.color=0xFFBA8A54.toInt()
                line.strokeWidth=.035f
                c.drawLine(-.39f,-1.15f,.15f,-.48f,line)
                c.drawLine(.39f,-1.15f,-.15f,-.48f,line)
                line.color=0xFF564633.toInt()
                line.strokeWidth=.075f
                oval(c,-.72f,-2.58f,.72f,-1.16f,0xFFF2C1B4.toInt())
                fill.color=0x70FFF6DF
                c.drawOval(-.42f,-2.38f,-.11f,-1.99f,fill)
            }
        }
        c.restore()
    }
}

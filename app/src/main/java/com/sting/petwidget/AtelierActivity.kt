package com.sting.petwidget

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * АТЕЛЬЕ: экран кастомизации питомца.
 * Живой предпросмотр (тот же рендер, что и у виджета) + пресеты тем.
 * Применение — мгновенно, без пересборки APK.
 */
class AtelierActivity : Activity() {

    private lateinit var previewView: PreviewView

    // какой пресет выбран в превью (на виджет попадает по кнопке «Применить»)
    private var themeBuffer: Map<String, Int> = ThemePresets.MINT

    /** живой маленький мир для превью */
    private inner class PreviewView(private val context: Context) : View(context) {
        private val renderer = SceneRenderer()
        private val sim = PetSim(context)
        private var bmp: Bitmap? = null

        fun refresh() {
            renderer.lastPrefs = context.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
            renderer.density = resources.displayMetrics.density
            if (width < 20 || height < 20) {
                invalidate()
                return
            }
            sim.tick(140)                       // превью тикает как живой мир (~7 fps)
            bmp = renderer.render(sim, width, height)
            invalidate()
            postDelayed({ if (isShown) refresh() }, 140)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ------------------------------------------------ УХОД ЗА ЖИЗНЬЮ (старая миссия активатора)
        try {
            startForegroundService(
                Intent(this, PetService::class.java).setAction(PetService.ACTION_SHOW)
            )
        } catch (_: Exception) {}

        val prefs = getSharedPreferences("pet_state", Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= 31 && !prefs.getBoolean("alarms_asked", false)) {
            try {
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!am.canScheduleExactAlarms()) {
                    prefs.edit().putBoolean("alarms_asked", true).apply()
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= 23 && !prefs.getBoolean("battery_asked", false)) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                prefs.edit().putBoolean("battery_asked", true).apply()
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {}
            }
        }

        try { PetWidgetProvider.scheduleKeepAlive(this) } catch (_: Exception) {}

        // ------------------------------------------------ UI в стиле виджета
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F2EF"))
        }
        val scroll = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            addView(screen)
        }
        setContentView(scroll)

        screen.addView(smallGap(dp(14)))
        screen.addView(text("Ателье", 30f, bold = true, pad = dp(24)))
        screen.addView(text("Косметика для твоего питомца", 14f, color = 0xFF6B7280.toInt(), pad = dp(24)))
        screen.addView(smallGap(dp(10)))

        // карточка предпросмотра
        val previewHolder = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230)
            )
        }
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        previewHolder.addView(previewView)
        val previewCard = roundedCard(Color.WHITE, dp(24))
        previewCard.addView(previewHolder)
        screen.addView(previewCard)

        // ------------------------------------------------ ТЕМЫ
        screen.addView(smallGap(dp(10)))
        screen.addView(text("Темы", 19f, bold = true, pad = dp(24), top = dp(14)))
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ThemePresets.PRESETS.forEach { (name, tokens) ->
            presetRow.addView(presetCard(name, tokens))
        }
        val presetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(presetRow, HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
            ))
        }
        screen.addView(presetScroll)

        // ------------------------------------------------ ПРИМЕНИТЬ / СБРОС
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        btnRow.addView(actionButton("Применить к виджету", 0xFF7FC9A6.toInt()) {
            themeBuffer?.let { ThemePresets.apply(this, it) }
            PetWidgetProvider.burst(this, frames = 3, everyMs = 70)
            Toast.makeText(this, "Готово! Питомец переоделся 🎨", Toast.LENGTH_SHORT).show()
        }, weights(2f))
        btnRow.addView(smallGap(dp(10)))
        btnRow.addView(actionButton("Сбросить", 0xFFB9B9B2.toInt()) {
            ThemePresets.reset(this)
            themeBuffer = ThemePresets.MINT
            previewView.refresh()
            PetWidgetProvider.burst(this, frames = 3, everyMs = 70)
            Toast.makeText(this, "Вернули мятную тему 🌿", Toast.LENGTH_SHORT).show()
        }, weights(1.3f))
        screen.addView(btnRow)

        // ------------------------------------------------ УХОД
        screen.addView(smallGap(dp(10)))
        screen.addView(text("Уход", 19f, bold = true, pad = dp(24), top = dp(12)))
        val careRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        careRow.addView(actionButton("Проверить жизнь питомца", 0xFFE3ECE6.toInt()) {
            try {
                startForegroundService(Intent(this, PetService::class.java).setAction(PetService.ACTION_SHOW))
            } catch (_: Exception) {}
            Toast.makeText(this, "Питомец на связи 🌱", Toast.LENGTH_SHORT).show()
        }, weights(2f))
        screen.addView(careRow)
        screen.addView(smallGap(dp(28)))
    }

    // ------------------------------------------------ фоновая строка-буфер (после UI-строителей)

    // ------------------------------------------------ вспомогашки

    private fun smallGap(h: Int): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
        return v
    }

    private fun text(s: String, size: Float, bold: Boolean = false,
                     color: Int = 0xFF2E3B34.toInt(), pad: Int = 0, top: Int = 0): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        t.setPadding(pad, top, pad, dp(4))
        return t
    }

    private fun roundedCard(color: Int, radius: Int): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
        l.background = g
        l.setMargins(16)
        return l
    }

    private fun presetCard(name: String, tokens: Map<String, Int>): LinearLayout {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.gravity = Gravity.CENTER
        wrap.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(2), tokens["pet_top"] ?: Color.WHITE)
        }
        wrap.layoutParams = LinearLayout.LayoutParams(dp(84), dp(112)
        ).apply { setMargins(dp(6), 0, dp(6), 0) }
        wrap.setPadding(dp(6), dp(10), dp(6), dp(8))

        listOf("pet_top", "pet_mid", "pet_bottom").forEach { k ->
            wrap.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(tokens[k] ?: Color.WHITE)
                }
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            })
        }
        val label = TextView(this).apply {
            text = name
            textSize = 12f
            setTextColor(0xFF2E3B34.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        wrap.addView(label)

        wrap.setOnClickListener {
            themeBuffer = tokens
            previewView.refresh()
        }
        return wrap
    }

    private fun actionButton(label: String, color: Int, onClick: (View) -> Unit): LinearLayout {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(18).toFloat()
            }
        }
        val l = LinearLayout(this)
        l.addView(b, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        b.setOnClickListener { onClick(b) }
        return l
    }

    private fun weights(w: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)

    private fun View.setMargins(all: Int): View {
        (layoutParams as? LinearLayout.LayoutParams)?.setMargins(dp(all), 0, dp(all), 0)
        return this
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

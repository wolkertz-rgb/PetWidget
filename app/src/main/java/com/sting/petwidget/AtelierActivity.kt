package com.sting.petwidget

import android.app.Activity
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Экран ателье: живой предпросмотр и все рабочие настройки внешности. */
class AtelierActivity : Activity() {

    private lateinit var previewView: PreviewView
    private var cachedFont: Typeface? = null

    /** округлый фирменный шрифт (Rubik, кириллица) */
    private fun font(): Typeface? {
        if (cachedFont == null) {
            cachedFont = try { resources.getFont(R.font.rubik) } catch (_: Throwable) { null }
        }
        return cachedFont
    }

    private fun styleFont(t: TextView, bold: Boolean = false) {
        font()?.let { t.typeface = if (bold) Typeface.create(it, Typeface.BOLD) else it }
    }

    private fun styleButton(b: Button) {
        font()?.let { b.typeface = Typeface.create(it, Typeface.BOLD) }
    }
    /** индекс текущего значения лука (для подсветки чипа) */
    private fun lookIdx(key: String, def: String): Int {
        val cur = try { getThemePrefs().getString(key, def) ?: def } catch (_: Throwable) { def }
        return when (key) {
            "look_mouth" -> when (cur) { "wide" -> 0; "dimples" -> 1; "tooth" -> 2; "small" -> 3; "cat" -> 4; else -> 5 }
            "look_blush" -> when (cur) { "circles" -> 0; "stripes" -> 1; "hearts" -> 2; "freckles" -> 3; else -> 4 }
            "look_eyes" -> when (cur) { "classic" -> 0; "shine" -> 1; "sleepy" -> 2; "round" -> 3; "sparkle" -> 4; else -> 5 }
            "look_grin" -> when (cur) { "smile" -> 0; "open" -> 1; "neutral" -> 2; "ooh" -> 3; "tongue" -> 4; else -> 5 }
            "look_antenna" -> when (cur) { "ball" -> 0; "flower" -> 1; "string" -> 2; "leaf" -> 3; "star" -> 4; else -> 5 }
            else -> 0
        }
    }

    /** живой маленький мир для превью */
    private inner class PreviewView(private val context: Context) : View(context) {
        private val renderer = SceneRenderer()
        // превью тикает КОПИЮ мозга и ничего не пишет в prefs:
        // иначе это второй писатель параллельно сервису (урок №3 «Библии»)
        private val sim = PetSim(
            context.getSharedPreferences("pet_state", Context.MODE_PRIVATE),
            { System.currentTimeMillis() },
            kotlin.random.Random.Default,
            autoSave = false
        )
        private var bmp: Bitmap? = null
        private var lastTick = 0L

        private val ticker = object : Runnable {
            override fun run() {
                if (!isShown) return
                renderFrame()
                postDelayed(this, 240)
            }
        }

        fun refresh() {
            removeCallbacks(ticker)
            renderFrame()
            if (isShown) postDelayed(ticker, 240)
        }

        private fun renderFrame() {
            renderer.lastPrefs = context.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
            renderer.density = resources.displayMetrics.density
            renderer.font = font()
            if (width < 20 || height < 20) {
                invalidate()
                return
            }
            // 1X скорость: dt = реальное время между кадрами (4 кадра/сек, без ускорений)
            val now = android.os.SystemClock.uptimeMillis()
            val dt = if (lastTick == 0L) 240L else (now - lastTick).coerceIn(120L, 400L)
            lastTick = now
            sim.tick(dt)
            bmp = renderer.render(sim, width, height)
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            refresh()
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ------------------------------------------------ забота о жизни (автоматически)
        try {
            startForegroundService(
                Intent(this, PetService::class.java).setAction(PetService.ACTION_SHOW)
            )
        } catch (_: Exception) {}

        val prefs = getSharedPreferences("pet_state", Context.MODE_PRIVATE)

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

        window.statusBarColor = 0xFFF5F2EC.toInt()
        window.navigationBarColor = 0xFFF5F2EC.toInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        // ================================================= РАЗМЕТКА
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(28))
        }
        val scroll = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, MATCH)
            isVerticalScrollBarEnabled = false
            setBackgroundColor(0xFFF5F2EC.toInt())
            addView(screen)
        }
        setContentView(scroll)

        screen.addView(appHeader())
        screen.addView(gap(18))
        screen.addView(previewHero())
        screen.addView(gap(16))

        val contentHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        screen.addView(segmentBar(contentHost))
        screen.addView(gap(14))
        screen.addView(contentHost)
        screen.addView(gap(14))
        screen.addView(serviceStatus())
    }

    private fun appHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val avatar = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE2EFE7.toInt())
                setStroke(dp(1), 0xFFD2E3D8.toInt())
            }
            addView(ImageView(this@AtelierActivity).apply {
                setImageResource(R.drawable.ic_blob_avatar)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(50), dp(50)))
        header.addView(gapW(13))
        header.addView(column(
            text("Ателье", 27f, bold = true, color = 0xFF26332E.toInt()),
            text("Создай своего питомца", 13f, color = 0xFF7B817D.toInt(), vertical = dp(2))
        ), LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(TextView(this).apply {
            text = "LIVE"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(0xFF2B7254.toInt())
            styleFont(this, bold = true)
            background = GradientDrawable().apply {
                setColor(0xFFDCEFE4.toInt())
                cornerRadius = dp(11).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(24)))
        return header
    }

    private fun previewHero(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF263A34.toInt(), 0xFF182823.toInt())
            ).apply { cornerRadius = dp(28).toFloat() }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(16), 0, dp(16), 0)
            }
            elevation = dp(5).toFloat()
        }
        val caption = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(11))
        }
        caption.addView(text("Живой предпросмотр", 14f, bold = true, color = Color.WHITE),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        caption.addView(text("Изменения сразу в виджете", 11f, color = 0xFFB9CBC3.toInt()))
        card.addView(caption)

        val previewHolder = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFFF4F3EE.toInt())
                cornerRadius = dp(20).toFloat()
            }
            clipToOutline = true
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(232))
        }
        previewView = PreviewView(this).apply { layoutParams = FrameLayout.LayoutParams(MATCH, MATCH) }
        previewHolder.addView(previewView)
        card.addView(previewHolder)
        return card
    }

    private fun segmentBar(host: LinearLayout): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(0xFFE8E5DE.toInt())
                cornerRadius = dp(20).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(48)).apply {
                setMargins(dp(16), 0, dp(16), 0)
            }
        }
        val tabs = listOf("Питомец", "Комната", "Темы")
        val views = ArrayList<TextView>()

        fun select(index: Int) {
            views.forEachIndexed { i, tab ->
                tab.setTextColor(if (i == index) 0xFF26332E.toInt() else 0xFF777B78.toInt())
                tab.background = GradientDrawable().apply {
                    setColor(if (i == index) Color.WHITE else Color.TRANSPARENT)
                    cornerRadius = dp(16).toFloat()
                }
                tab.elevation = if (i == index) dp(2).toFloat() else 0f
            }
            host.removeAllViews()
            host.addView(when (index) {
                0 -> petControls()
                1 -> roomControls()
                else -> themeControls()
            })
        }

        tabs.forEachIndexed { index, title ->
            val tab = TextView(this).apply {
                text = title
                textSize = 13.5f
                gravity = Gravity.CENTER
                styleFont(this, bold = true)
                setOnClickListener { select(index) }
            }
            views.add(tab)
            bar.addView(tab, LinearLayout.LayoutParams(0, MATCH, 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        select(0)
        return bar
    }

    private fun petControls(): View {
        val card = sectionCard("Внешность питомца", "Цвет, характер и детали лица")
        card.addView(label("ОКРАС · ВЕРХ"))
        card.addView(swatchRow(listOf(0xFFA8E6C3, 0xFF9ADDC8, 0xFFB4EBCF, 0xFF63CFA3, 0xFF7FD8E6, 0xFFAFC8FF, 0xFFB8A6F5, 0xFFFFC9D8, 0xFFF58C8C, 0xFFF7B27A, 0xFFFFD08A, 0xFFE8E5DF), getThemePrefs().getInt("pet_top", 0xFFA8E6C3.toInt())) { putColor("pet_top", it) })
        card.addView(label("ОКРАС · СЕРЕДИНА"))
        card.addView(swatchRow(listOf(0xFF6FBE97, 0xFF48B69E, 0xFF55B9CE, 0xFF779FE0, 0xFFA98BDB, 0xFFF08BAA, 0xFFE98B75, 0xFFE9AE5E, 0xFF9CA8A1), getThemePrefs().getInt("pet_mid", 0xFF6FBE97.toInt())) { putColor("pet_mid", it) })
        card.addView(label("ОКРАС · НИЗ"))
        card.addView(swatchRow(listOf(0xFF4FA379, 0xFF3F9E77, 0xFF2C93A0, 0xFF3978B8, 0xFF5D6BAA, 0xFF7B62C4, 0xFFB94E77, 0xFFB94E4E, 0xFFD97E4E, 0xFFC38A3D, 0xFF5E6B75, 0xFF343B43), getThemePrefs().getInt("pet_bottom", 0xFF4FA379.toInt())) { putColor("pet_bottom", it) })
        card.addView(label("ЖИВОТИК"))
        card.addView(swatchRow(listOf(0xFFBDEBCF, 0xFFD3F4F0, 0xFFDCEBFF, 0xFFEDE6FA, 0xFFFFE0EA, 0xFFFFE9DC, 0xFFFFF0D6, 0xFFF7F9F7, 0xFFF1E4E7, 0xFFD9D2C8), getThemePrefs().getInt("pet_belly", 0xFFBDEBCF.toInt())) { putColor("pet_belly", it) })
        card.addView(divider())
        card.addView(label("УЛЫБКА"))
        card.addView(chipRow(listOf("Широкая", "Ямочки", "Зубик", "Скромная", "Кошачья", "Волна"), lookIdx("look_mouth", "wide")) { i ->
            putLook("look_mouth", when (i) { 0 -> "wide"; 1 -> "dimples"; 2 -> "tooth"; 3 -> "small"; 4 -> "cat"; else -> "wave" })
        })
        card.addView(label("ГЛАЗА"))
        card.addView(chipRow(listOf("Классика", "Блеск", "Сонные", "Круглые", "Искры", "Мягкие"), lookIdx("look_eyes", "classic")) { i ->
            putLook("look_eyes", when (i) { 0 -> "classic"; 1 -> "shine"; 2 -> "sleepy"; 3 -> "round"; 4 -> "sparkle"; else -> "soft" })
        })
        card.addView(swatchRow(listOf(0xFF22333B, 0xFF2B2233, 0xFF20455F, 0xFF245E50, 0xFF6B4D8D, 0xFF8B4C62, 0xFF6A4A2F, 0xFF111827), getThemePrefs().getInt("look_eyecolor", 0xFF22333B.toInt())) { putColor("look_eyecolor", it) })
        card.addView(label("РУМЯНЕЦ"))
        card.addView(chipRow(listOf("Кружки", "Полоски", "Сердечки", "Веснушки", "Звёзды"), lookIdx("look_blush", "circles")) { i ->
            putLook("look_blush", when (i) { 0 -> "circles"; 1 -> "stripes"; 2 -> "hearts"; 3 -> "freckles"; else -> "stars" })
        })
        card.addView(swatchRow(listOf(0xFFF3A9A0, 0xFFF17A85, 0xFFF5B791, 0xFFE5687B, 0xFFE9A4D5, 0xFFB8A6E8, 0xFF8CC8D8, 0xFFD09B75), getThemePrefs().getInt("pet_blush", 0xFFF3A9A0.toInt())) { putColor("pet_blush", it) })
        card.addView(label("НАСТРОЕНИЕ"))
        card.addView(chipRow(listOf("Улыбка", "Открытый", "Спокойный", "Удивлён", "Язычок", "Котик"), lookIdx("look_grin", "smile")) { i ->
            putLook("look_grin", when (i) { 0 -> "smile"; 1 -> "open"; 2 -> "neutral"; 3 -> "ooh"; 4 -> "tongue"; else -> "cat" })
        })
        card.addView(label("АНТЕННА"))
        card.addView(chipRow(listOf("Шарик", "Цветок", "Нить", "Листики", "Звезда", "Без неё"), lookIdx("look_antenna", "ball")) { i ->
            putLook("look_antenna", when (i) { 0 -> "ball"; 1 -> "flower"; 2 -> "string"; 3 -> "leaf"; 4 -> "star"; else -> "none" })
        })
        card.addView(swatchRow(listOf(0xFFF4C95D, 0xFFF472B6, 0xFFEF6570, 0xFF60A5FA, 0xFF7FC9A6, 0xFF72BB78, 0xFFA78BFA, 0xFFF59E5B), getThemePrefs().getInt("look_ante", 0xFFF4C95D.toInt())) { putColor("look_ante", it) })
        return card
    }

    private fun roomControls(): View {
        val card = sectionCard("Интерьер комнаты", "Настрой фон и любимое кресло")
        card.addView(label("ОБОИ"))
        card.addView(swatchRow(listOf(0xFFF2F2EF, 0xFFFDF1EA, 0xFFFFF0F4, 0xFFF7F0FF, 0xFFEFF7F3, 0xFFEAF7F8, 0xFFEFF3F9, 0xFFF8F2E4, 0xFF202631, 0xFF151A2A, 0xFF232840), getThemePrefs().getInt("room_bg_top", 0xFFF2F2EF.toInt())) {
            putColor("room_bg_top", it, mirror = "room_bg_bottom", mirrorShade = 0.94f)
        })
        card.addView(label("УЗОР"))
        card.addView(chipRow(listOf("Чисто", "Точки", "Полосы", "Зигзаг", "Клетка", "Звёзды", "Сердца", "Облака", "Листья"), getThemePrefs().getInt("wall_pattern", 0)) { i ->
            getThemePrefs().edit().putInt("wall_pattern", i).apply(); refreshAll()
        })
        card.addView(label("ЦВЕТ УЗОРА"))
        val patternColor = (getThemePrefs().getInt("wall_pattern_color", 0x2855604F) and 0x00FFFFFF) or 0xFF000000.toInt()
        card.addView(swatchRow(listOf(0xFF55604F, 0xFFB46672, 0xFF8C6BA8, 0xFF557FA8, 0xFF4F8C7B, 0xFFC28A42, 0xFF8A7564), patternColor) { col ->
            putColor("wall_pattern_color", (0x28 shl 24) or (col and 0x00FFFFFF))
        })
        card.addView(divider())
        card.addView(label("ПОЛ"))
        card.addView(swatchRow(listOf(0xFFE1E1DC, 0xFFEAD9CC, 0xFFF0D7DE, 0xFFE3DBEF, 0xFFCFE7E6, 0xFFD8E3D0, 0xFFD4DDEA, 0xFF3C3428, 0xFF232840), getThemePrefs().getInt("floor_top", 0xFFE1E1DC.toInt())) {
            putColor("floor_top", it, mirror = "floor_bottom", mirrorShade = 0.95f)
        })
        card.addView(label("ПАНЕЛЬ И ПЛИНТУС"))
        card.addView(swatchRow(listOf(0xFFDCDCD6, 0xFFEBD9C9, 0xFFEBD5DC, 0xFFDED6EA, 0xFFC8E2E0, 0xFFD6E1C2, 0xFFD1DCE8, 0xFF2A2F4A), getThemePrefs().getInt("room_skirt", 0xFFDCDCD6.toInt())) {
            putColor("room_skirt", it, mirror = "room_panel", mirrorShade = 0.97f)
        })
        card.addView(label("КРЕСЛО"))
        card.addView(swatchRow(listOf(0xFFAB7B44, 0xFF8A5A2E, 0xFFB76E58, 0xFFB76E88, 0xFF7B62C4, 0xFF2C5875, 0xFF477A69, 0xFF94A3B8, 0xFF4D515B), getThemePrefs().getInt("chair_back1", 0xFFAB7B44.toInt())) { col ->
            putColor("chair_back1", col, mirror = "chair_back2", mirrorShade = 0.7f)
            putColor("chair_seat1", shade(col, 1.08f), mirror = "chair_seat2", mirrorShade = 0.72f)
            putColor("chair_roll1", shade(col, 1.05f), mirror = "chair_roll2", mirrorShade = 0.7f)
        })
        return card
    }

    private fun themeControls(): View {
        val card = sectionCard("Готовые образы", "Один выбор меняет питомца и комнату")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ThemePresets.PRESETS.entries.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (name, tokens) -> row.addView(presetCard(name, tokens), LinearLayout.LayoutParams(0, dp(112), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(row)
        }
        card.addView(grid)
        card.addView(gap(12))
        card.addView(actionButton("Вернуть исходный мятный образ", 0xFFDEE8E1.toInt()) {
            ThemePresets.reset(this)
            refreshAll()
            Toast.makeText(this, "Исходный образ восстановлен", Toast.LENGTH_SHORT).show()
        })
        return card
    }

    private fun serviceStatus(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFFE8EFEA.toInt())
                cornerRadius = dp(20).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(16), 0, dp(16), 0) }
        }
        row.addView(View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF55A773.toInt()) }
        }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { setMargins(0, 0, dp(11), 0) })
        row.addView(column(
            text("Питомец на связи", 13.5f, bold = true, color = 0xFF2B4037.toInt()),
            text("Жизнь в виджете активна", 11.5f, color = 0xFF708078.toInt(), vertical = dp(1))
        ), LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(TextView(this).apply {
            text = "Запустить"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            styleFont(this, bold = true)
            background = GradientDrawable().apply { setColor(0xFF3D765E.toInt()); cornerRadius = dp(14).toFloat() }
            setOnClickListener {
                try { startForegroundService(Intent(this@AtelierActivity, PetService::class.java).setAction(PetService.ACTION_SHOW)) } catch (_: Exception) {}
                Toast.makeText(this@AtelierActivity, "Питомец на связи", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(dp(88), dp(34)))
        return row
    }

    private fun actionButton(label: String, color: Int, onClick: () -> Unit): LinearLayout {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13.5f
            setTextColor(if (Color.luminance(color) > 0.58f) 0xFF2E3B34.toInt() else Color.WHITE)
            minHeight = dp(48)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(16).toFloat()
                if (Color.luminance(color) > 0.7f) setStroke(dp(1), 0xFFD1DAD4.toInt())
            }
            styleButton(this)
        }
        val l = LinearLayout(this)
        l.addView(b, LinearLayout.LayoutParams(MATCH, WRAP))
        b.setOnClickListener { onClick() }
        return l
    }

    // ================================================= примитивы

    private fun getThemePrefs(): android.content.SharedPreferences =
        getSharedPreferences("pet_state", Context.MODE_PRIVATE)

    /** мгновенное применение косметики: превью + ОДИН дебаунс-обновление виджета */
    private val uiTick = android.os.Handler(android.os.Looper.getMainLooper())
    private var widgetRefreshPending = false

    private fun refreshAll() {
        previewView.refresh()
        if (widgetRefreshPending) return
        widgetRefreshPending = true
        uiTick.postDelayed({
            widgetRefreshPending = false
            PetWidgetProvider.burst(this, frames = 1, everyMs = 60)
        }, 400)
    }

    /** выбрать цвет-токен и применить */
    private fun putColor(key: String, col: Int, mirror: String? = null, mirrorShade: Float = 0.92f) {
        getThemePrefs().edit()
            .putInt(key, col)
            .apply()
        mirror?.let { getThemePrefs().edit().putInt(it, shade(col, mirrorShade)).apply() }
        refreshAll()
    }

    private fun putLook(key: String, value: String) {
        getThemePrefs().edit().putString(key, value).apply()
        refreshAll()
    }

    /** затемнить/осветлить цвет (f>1 — светлее) */
    private fun shade(col: Int, f: Float): Int {
        val r = (Color.red(col) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(col) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(col) * f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    private fun gap(h: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }

    private fun gapW(w: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), 1)
    }

    private fun text(s: String, size: Float, bold: Boolean = false,
                     color: Int = 0xFF2E3B34.toInt(), vertical: Int = 0): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        styleFont(t, bold)
        t.setPadding(0, vertical, 0, 0)
        return t
    }

    private fun label(s: String): TextView = TextView(this).apply {
        text = s
        textSize = 10.5f
        letterSpacing = 0.08f
        setTextColor(0xFF7A817D.toInt())
        setPadding(dp(2), dp(15), 0, dp(7))
        styleFont(this, bold = true)
    }

    /** Светлая рабочая поверхность с заголовком и кратким пояснением. */
    private fun sectionCard(title: String, subtitle: String? = null): LinearLayout {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l.background = GradientDrawable().apply {
            setColor(0xFFFFFEFB.toInt())
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), 0xFFE8E3D9.toInt())
        }
        l.elevation = dp(2).toFloat()
        l.setPadding(dp(18), dp(18), dp(18), dp(20))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(dp(16), 0, dp(16), 0)
        l.layoutParams = lp
        l.addView(text(title, 19f, bold = true, color = 0xFF28342F.toInt()))
        subtitle?.let { l.addView(text(it, 12.5f, color = 0xFF858985.toInt(), vertical = dp(3))) }
        l.addView(gap(4))
        return l
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(0xFFEDE9E1.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(18), 0, dp(2)) }
    }

    /** вертикальная колонка из двух текстов */
    private fun column(a: TextView, b: TextView): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.addView(a)
        l.addView(b)
        return l
    }

    /** горизонтальный ряд, который листается, если не влезает */
    private fun hScroll(v: View): HorizontalScrollView {
        val h = HorizontalScrollView(this)
        h.isHorizontalScrollBarEnabled = false
        h.addView(v, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        return h
    }

    /** ряд цветовых свотчей с выбором; начальный — по цвету из prefs */
    private fun swatchRow(colors: List<Long>, selectedCol: Int? = null, onPick: ((Int) -> Unit)? = null): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val circles = ArrayList<View>()
        colors.forEachIndexed { i, colRaw ->
            val col = colRaw.toInt()
            val isSel = if (selectedCol != null) col == selectedCol else i == 0
            val v = View(this)
            val g = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(col)
                setStroke(dp(3), if (isSel) 0xFF315F4D.toInt() else 0xFFE4E1DA.toInt())
            }
            v.background = g
            v.elevation = dp(1).toFloat()
            v.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { setMargins(dp(1), dp(1), dp(10), dp(3)) }
            v.setOnClickListener {
                circles.forEach { c2 ->
                    (c2.background as GradientDrawable).setStroke(dp(3), 0xFFE4E1DA.toInt())
                }
                (v.background as GradientDrawable).setStroke(dp(3), 0xFF315F4D.toInt())
                onPick?.invoke(col)
            }
            circles.add(v)
            row.addView(v)
        }
        return hScroll(row)
    }

    /** чип + опциональный живой выбор (пишет значение в prefs и обновляет виджет) */
    private fun chipRow(options: List<String>, initial: Int = 0, onPick: ((Int) -> Unit)? = null): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<TextView>()
        options.forEachIndexed { i, opt ->
            val chip = TextView(this).apply {
                text = opt
                textSize = 12.5f
                setTextColor(if (i == initial) 0xFFFFFFFF.toInt() else 0xFF59615D.toInt())
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (i == initial) 0xFF3D705B.toInt() else 0xFFF2F0EA.toInt())
                    cornerRadius = dp(15).toFloat()
                }
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(WRAP, dp(36)).apply {
                    setMargins(0, 0, dp(8), dp(3))
                }
                styleFont(this, bold = i == initial)
            }
            chip.setOnClickListener {
                chips.forEach { ch ->
                    (ch.background as GradientDrawable).setColor(0xFFF2F0EA.toInt())
                    ch.setTextColor(0xFF59615D.toInt())
                    styleFont(ch)
                }
                (chip.background as GradientDrawable).setColor(0xFF3D705B.toInt())
                chip.setTextColor(Color.WHITE)
                styleFont(chip, bold = true)
                onPick?.invoke(i)
            }
            chips.add(chip)
            row.addView(chip)
        }
        return hScroll(row)
    }

    // ------------------------------------------------ прочее

    private fun presetCard(name: String, tokens: Map<String, Int>): LinearLayout {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.background = GradientDrawable().apply {
            setColor(tokens["room_bg_top"] ?: Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0x1F26332E)
        }
        wrap.setPadding(dp(12), dp(11), dp(12), dp(10))
        val scene = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(tokens["floor_top"] ?: 0xFFE7E3DB.toInt())
                cornerRadius = dp(13).toFloat()
            }
        }
        scene.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens["pet_top"] ?: 0xFFA8E6C3.toInt())
                setStroke(dp(3), tokens["pet_bottom"] ?: 0xFF4FA379.toInt())
            }
        }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        scene.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(tokens["chair_back1"] ?: 0xFFAB7B44.toInt())
                cornerRadius = dp(5).toFloat()
            }
        }, FrameLayout.LayoutParams(dp(19), dp(30), Gravity.RIGHT or Gravity.BOTTOM).apply { setMargins(0, 0, dp(9), dp(7)) })
        wrap.addView(scene, LinearLayout.LayoutParams(MATCH, 0, 1f))
        wrap.addView(text(name, 12.5f, bold = true, color = 0xFF2D3733.toInt(), vertical = dp(7)))
        wrap.setOnClickListener {
            ThemePresets.apply(this, tokens)
            refreshAll()
            Toast.makeText(this, "Образ «$name» применён", Toast.LENGTH_SHORT).show()
        }
        return wrap
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

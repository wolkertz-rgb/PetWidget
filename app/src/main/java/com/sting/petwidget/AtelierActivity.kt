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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * КАСТОМИЗАЦИЯ: полный дизайн-макет всех разделов.
 * Рабочие функции: живой превью + пресеты тем.
 * Остальное — визуальные выборы (макет, логика подключится позже).
 */
class AtelierActivity : Activity() {

    private lateinit var previewView: PreviewView
    private var themeBuffer: Map<String, Int> = ThemePresets.MINT
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
        return when (def) {
            "smile" -> when (cur) { "wide" -> 0; "dimples" -> 1; "tooth" -> 2; else -> 0 }
            "circles" -> when (cur) { "circles" -> 0; "stripes" -> 1; else -> 2 }
            "classic" -> when (cur) { "classic" -> 0; "shine" -> 1; "sleepy" -> 2; else -> 3 }
            "open" -> when (cur) { "smile" -> 0; "open" -> 1; "neutral" -> 2; else -> 3 }
            "ball" -> when (cur) { "ball" -> 0; "flower" -> 1; "string" -> 2; else -> 3 }
            else -> 0
        }
    }

    /** живой маленький мир для превью */
    private inner class PreviewView(private val context: Context) : View(context) {
        private val renderer = SceneRenderer()
        private val sim = PetSim(context)
        private var bmp: Bitmap? = null
        private var lastTick = 0L

        fun refresh() {
            renderer.lastPrefs = context.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
            renderer.density = resources.displayMetrics.density
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
            postDelayed({ if (isShown) refresh() }, 240)
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

        // ================================================= РАЗМЕТКА
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, MATCH)
            isVerticalScrollBarEnabled = false
            addView(screen)
        }
        scroll.setBackgroundColor(Color.parseColor("#F2F2EF"))
        setContentView(scroll)

        // шапка
        screen.addView(gap(18))
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.setPadding(dp(22), 0, dp(22), 0)
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_blob_avatar)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        })
        header.addView(gapW(dp(12)))
        header.addView(column(
            text("Кастомизация", 25f, bold = true, vertical = dp(2)),
            text("Одеваем твоего питомца 🎨", 13f, color = 0xFF6B7280.toInt())
        ))
        screen.addView(header)
        screen.addView(gap(16))

        // ------------------------------------------------ ПРЕВЬЮ
        val previewCard = sectionCard("Предпросмотр")
        val previewHolder = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFFF7F7F4.toInt())
                cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(216))
        }
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        previewHolder.addView(previewView)
        previewCard.addView(previewHolder)
        screen.addView(previewCard)
        screen.addView(gap(12))

        // ------------------------------------------------ ПИТОМЕЦ
        val petCard = sectionCard("🦠 Питомец")
        petCard.addView(label("🎨 Цвет тела — верх"))
        petCard.addView(swatchRow(listOf(0xFFA8E6C3, 0xFF9ADDC8, 0xFFB4EBCF, 0xFF8EE0C4, 0xFF63CFA3, 0xFFF58C8C, 0xFFF7B27A, 0xFFB8A6F5), getThemePrefs().getInt("pet_top", 0xFFA8E6C3.toInt())) { col ->
            putColor("pet_top", col)
        })
        petCard.addView(label("🎨 Цвет тела — низ"))
        petCard.addView(swatchRow(listOf(0xFF4FA379, 0xFF5FAE88, 0xFF3F9E77, 0xFF2C93A0, 0xFFB94E4E, 0xFFD97E4E, 0xFF7B62C4, 0xFF5E6B75), getThemePrefs().getInt("pet_bottom", 0xFF4FA379.toInt())) { col ->
            putColor("pet_bottom", col)
        })
        petCard.addView(label("🎨 Животик"))
        petCard.addView(swatchRow(listOf(0xFFBDEBCF, 0xFFD3F4F0, 0xFFFFE9DC, 0xFFFFF0D6, 0xFFF7F9F7, 0xFFF1E4E7), getThemePrefs().getInt("pet_belly", 0xFFBDEBCF.toInt())) { col ->
            putColor("pet_belly", col)
        })
        petCard.addView(label("😊 Улыбка"))
        petCard.addView(chipRow(listOf("Широкая", "Ямочки", "Один зубик", "Скромная"), lookIdx("look_mouth", "smile")) { i ->
            putLook("look_mouth", when (i) { 0 -> "wide"; 1 -> "dimples"; 2 -> "tooth"; else -> "small" })
        })
        petCard.addView(label("😳 Румяшки"))
        petCard.addView(chipRow(listOf("Кружки", "Полоски", "Сердечки"), lookIdx("look_blush", "circles")) { i ->
            putLook("look_blush", when (i) { 0 -> "circles"; 1 -> "stripes"; else -> "hearts" })
        })
        petCard.addView(swatchRow(listOf(0xFFF3A9A0, 0xFFF17A85, 0xFFF5B791, 0xFFE5687B), getThemePrefs().getInt("pet_blush", 0xFFF3A9A0.toInt())) { col ->
            putColor("pet_blush", col)
        })
        petCard.addView(label("👀 Глаза"))
        petCard.addView(chipRow(listOf("Классика", "Блеск+", "Сонные", "Круглые"), lookIdx("look_eyes", "classic")) { i ->
            putLook("look_eyes", when (i) { 0 -> "classic"; 1 -> "shine"; 2 -> "sleepy"; else -> "round" })
        })
        petCard.addView(swatchRow(listOf(0xFF22333B, 0xFF2B2233, 0xFF20455F, 0xFF3B2233), getThemePrefs().getInt("look_eyecolor", 0xFF22333B.toInt())) { col ->
            putColor("look_eyecolor", col)
        })
        petCard.addView(label("👄 Рот — форма при радости"))
        petCard.addView(chipRow(listOf("Улыбка", "Открытый", "Нейтральный", "«Оо»"), lookIdx("look_grin", "smile")) { i ->
            putLook("look_grin", when (i) { 0 -> "smile"; 1 -> "open"; 2 -> "neutral"; else -> "ooh" })
        })
        petCard.addView(sliderBlock(
            "Тонкая подстройка цвета тела",
            listOf(0xFF2E3B34, 0xFF6FBE97, 0xFFF4F4F4),
            listOf("Тень", "Основа", "Свет")
        ))
        petCard.addView(label("📡 Антенна"))
        petCard.addView(chipRow(listOf("Шарик", "Цветочек", "Нить", "Ничего"), lookIdx("look_antenna", "ball")) { i ->
            putLook("look_antenna", when (i) { 0 -> "ball"; 1 -> "flower"; 2 -> "string"; else -> "none" })
        })
        petCard.addView(swatchRow(listOf(0xFFF4C95D, 0xFFF472B6, 0xFF60A5FA, 0xFF7FC9A6), getThemePrefs().getInt("look_ante", 0xFFF4C95D.toInt())) { col ->
            putColor("look_ante", col)
        })
        screen.addView(petCard)
        screen.addView(gap(12))

        // ------------------------------------------------ КОМНАТА
        val roomCard = sectionCard("🏠 Комната")
        roomCard.addView(label("🎨 Обои — цвет"))
        roomCard.addView(swatchRow(listOf(0xFFF2F2EF, 0xFFFDF1EA, 0xFFEFF7F3, 0xFFEFF3F9, 0xFFF8F2E4, 0xFF151A2A, 0xFF232840), getThemePrefs().getInt("room_bg_top", 0xFFF2F2EF.toInt())) { col ->
            putColor("room_bg_top", col, mirror = "room_bg_bottom", mirrorShade = 0.94f)
        })
        roomCard.addView(label("🧩 Узор обоев"))
        roomCard.addView(chipRow(listOf("Нет", "Точки", "Полоски", "Зигзаг", "Клетка", "Звёзды"), getThemePrefs().getInt("wall_pattern", 0)) { i ->
            getThemePrefs().edit().putInt("wall_pattern", i).apply()
            refreshAll()
        })
        roomCard.addView(label("🎨 Пол"))
        roomCard.addView(swatchRow(listOf(0xFFE1E1DC, 0xFFEAD9CC, 0xFFCFE7E6, 0xFFD8E3D0, 0xFF232840, 0xFF3C3428), getThemePrefs().getInt("floor_top", 0xFFE1E1DC.toInt())) { col ->
            putColor("floor_top", col, mirror = "floor_bottom", mirrorShade = 0.95f)
        })
        roomCard.addView(label("🎨 Плинтус и панель"))
        roomCard.addView(swatchRow(listOf(0xFFDCDCD6, 0xFFEBD9C9, 0xFFC8E2E0, 0xFFD6E1C2, 0xFF2A2F4A), getThemePrefs().getInt("room_skirt", 0xFFDCDCD6.toInt())) { col ->
            putColor("room_skirt", col, mirror = "room_panel", mirrorShade = 0.97f)
        })
        roomCard.addView(label("🛋 Кресло"))
        roomCard.addView(swatchRow(listOf(0xFFAB7B44, 0xFF8A5A2E, 0xFF94A3B8, 0xFF2C5875, 0xFF7B62C4, 0xFFB45309), getThemePrefs().getInt("chair_back1", 0xFFAB7B44.toInt())) { col ->
            putColor("chair_back1", col, mirror = "chair_back2", mirrorShade = 0.7f)
            putColor("chair_seat1", shade(col, 1.08f), mirror = "chair_seat2", mirrorShade = 0.72f)
            putColor("chair_roll1", shade(col, 1.05f), mirror = "chair_roll2", mirrorShade = 0.7f)
        })
        screen.addView(roomCard)
        screen.addView(gap(12))

        // ------------------------------------------------ ТЕМЫ (применяются сразу)
        val themeCard = sectionCard("🧸 Темы")
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ThemePresets.PRESETS.forEach { (name, tokens) ->
            presetRow.addView(presetCard(name, tokens))
        }
        themeCard.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(presetRow)
        })
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(2)) }
        btnRow.addView(actionButton("Вернуть мятную 🌿", 0xFFB9B9B2.toInt()) {
            ThemePresets.reset(this)
            themeBuffer = ThemePresets.MINT
            previewView.refresh()
            PetWidgetProvider.burst(this, frames = 1, everyMs = 60)
            Toast.makeText(this, "Мятная тема вернулась 🌿", Toast.LENGTH_SHORT).show()
        }, weights(2f))
        themeCard.addView(btnRow)
        screen.addView(themeCard)
        screen.addView(gap(12))

        // ------------------------------------------------ ЗАПАС (макет «скоро»)
        val soonCard = sectionCard("✨ Скоро в ателье")
        soonCard.addView(label("🕯 Свой цвет лампы и света"))
        soonCard.addView(label("🖼 Фото-обои"))
        soonCard.addView(label("🧣 Шарфик и шапочка питомцу"))
        soonCard.addView(label("🐾 Второй питомец"))
        screen.addView(soonCard)
        screen.addView(gap(12))

        // ------------------------------------------------ УХОД
        val careCard = sectionCard("🌱 Уход")
        careCard.addView(actionButton("Запустить жизнь питомца", 0xFFE3ECE6.toInt()) {
            try {
                startForegroundService(
                    Intent(this@AtelierActivity, PetService::class.java).setAction(PetService.ACTION_SHOW)
                )
            } catch (_: Exception) {}
            Toast.makeText(this@AtelierActivity, "Питомец на связи 🌱", Toast.LENGTH_SHORT).show()
        })
        screen.addView(careCard)
        screen.addView(gap(30))
    }

    // ---------------------------------------------------------- уход-макет удалён

    private fun actionButton(label: String, color: Int, onClick: () -> Unit): LinearLayout {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(if (color == 0xFFE3ECE6.toInt()) 0xFF2E3B34.toInt() else Color.WHITE)
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat() }
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
        textSize = 13.5f
        setTextColor(0xFF4B5563.toInt())
        setPadding(0, dp(10), 0, dp(4))
        styleFont(this)
    }

    /** белая скруглённая карточка-секция с заголовком */
    private fun sectionCard(title: String): LinearLayout {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(24).toFloat()
        }
        l.setPadding(dp(18), dp(16), dp(18), dp(16))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(dp(16), dp(4), dp(16), dp(4))
        l.layoutParams = lp
        l.addView(text(title, 17.5f, bold = true))
        l.addView(gap(6))
        return l
    }

    /** кружок-аватар для шапки */
    private fun petAvatar(colTop: Int, colBottom: Int, sizeDp: Int): View {
        val v = View(this)
        val g = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColors(intArrayOf(colTop, colBottom))
            orientation = GradientDrawable.Orientation.TL_BR
        }
        v.background = g
        v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return v
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
                setStroke(dp(3), if (isSel) 0xFF7FC9A6.toInt() else Color.TRANSPARENT)
            }
            v.background = g
            v.layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { setMargins(0, 0, dp(10), dp(4)) }
            v.setOnClickListener {
                circles.forEach { c2 ->
                    (c2.background as GradientDrawable).setStroke(dp(3), Color.TRANSPARENT)
                }
                (v.background as GradientDrawable).setStroke(dp(3), 0xFF7FC9A6.toInt())
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
                textSize = 13f
                setTextColor(if (i == initial) 0xFF2E3B34.toInt() else 0xFF4B5563.toInt())
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (i == initial) 0xFFE3F4EB.toInt() else Color.WHITE)
                    setStroke(dp(2), if (i == initial) 0xFF7FC9A6.toInt() else 0xFFE5E7EB.toInt())
                    cornerRadius = dp(16).toFloat()
                }
                setPadding(dp(12), 0, dp(12), 0)
                layoutParams = LinearLayout.LayoutParams(WRAP, dp(32)).apply {
                    setMargins(0, 0, dp(8), dp(4))
                }
                styleFont(this)
            }
            chip.setOnClickListener {
                chips.forEach { ch ->
                    (ch.background as GradientDrawable).setColor(Color.WHITE)
                    (ch.background as GradientDrawable).setStroke(dp(2), 0xFFE5E7EB.toInt())
                    ch.setTextColor(0xFF4B5563.toInt())
                }
                (chip.background as GradientDrawable).setColor(0xFFE3F4EB.toInt())
                (chip.background as GradientDrawable).setStroke(dp(2), 0xFF7FC9A6.toInt())
                chip.setTextColor(0xFF2E3B34.toInt())
                onPick?.invoke(i)
            }
            chips.add(chip)
            row.addView(chip)
        }
        return hScroll(row)
    }

    /** слайдеры тонкой подстройки — маленькая карточка с подписями */
    private fun sliderBlock(title: String, colors: List<Long>, names: List<String>): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFFF5F5F2.toInt())
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        card.addView(text(title, 12.5f, color = 0xFF6B7280.toInt()))
        colors.forEachIndexed { i, colRaw ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colRaw.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { setMargins(0, 0, dp(10), 0) }
            })
            row.addView(text(names[i], 12f, color = 0xFF6B7280.toInt()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(64), WRAP)
                setPadding(0, dp(6), 0, 0)
            })
            row.addView(SeekBar(this).apply {
                max = 255
                progress = 70 + i * 45
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(8), 0, dp(4), 0) }
            })
            card.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(6), 0, 0) })
        }
        return card
    }

    // ------------------------------------------------ прочее

    private fun presetCard(name: String, tokens: Map<String, Int>): LinearLayout {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        wrap.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(2), tokens["pet_top"] ?: Color.WHITE)
        }
        wrap.layoutParams = LinearLayout.LayoutParams(dp(84), dp(116)).apply { setMargins(dp(4), 0, dp(4), 0) }
        wrap.setPadding(dp(6), dp(10), dp(6), dp(8))

        listOf("pet_top", "pet_mid", "pet_bottom").forEach { k ->
            wrap.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(tokens[k] ?: Color.WHITE)
                }
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setMargins(0, dp(2), 0, 0)
                }
            })
        }
        wrap.addView(text(name, 12f, vertical = dp(6)).apply { gravity = Gravity.CENTER })
        wrap.setOnClickListener {
            themeBuffer = tokens
            ThemePresets.apply(this, tokens)
            previewView.refresh()
        }
        return wrap
    }

    private fun weights(w: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

package com.sting.petwidget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Каталог показывает одну группу настроек за раз. Значения и обработчики остаются исходными. */
internal class AtelierCatalog(
    private val context: Context,
    private val font: Typeface?,
    private val onNavigate: () -> Unit
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun title(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(0xFF263D34.toInt())
        typeface = Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    var back: (() -> Unit)? = null
        private set

    fun build(source: LinearLayout, pet: Boolean, yard: Boolean = false): View {
        val groups = linkedMapOf<String, LinearLayout>()
        var group: LinearLayout? = null
        val children = (3 until source.childCount).map { source.getChildAt(it) }
        for (child in children) {
            if (child.tag == "atelier-label" && child is TextView) {
                val label = child.text.toString()
                val key = if (yard) when(label) {
                    "ФАСАД", "ЗАБОР", "КАЧЕЛИ" -> "Оформление двора"
                    "САДОВЫЙ ДЕКОР" -> "Садовые декорации"
                    else -> "Живой сад"
                } else if (pet) when (label) {
                    "ОКРАС · ВЕРХ", "ОКРАС · СЕРЕДИНА", "ОКРАС · НИЗ", "ЖИВОТИК" -> "Окрас"
                    "УЛЫБКА", "НАСТРОЕНИЕ" -> "Улыбка и настроение"
                    "ГЛАЗА" -> "Глаза"
                    "РУМЯНЕЦ" -> "Румянец"
                    else -> "Антенна"
                } else when (label) {
                    "СПАЛЬНОЕ МЕСТО", "ЦВЕТ СПАЛЬНОГО МЕСТА" -> "Место для сна"
                    "СВЕТИЛЬНИК" -> "Освещение"
                    "ШТОРЫ" -> "Шторы"
                    "ОБОИ", "УЗОР", "ЦВЕТ УЗОРА" -> "Обои"
                    "ПОЛ", "ПАНЕЛЬ И ПЛИНТУС" -> "Пол и отделка"
                    else -> "Декор"
                }
                group = groups.getOrPut(key) { column() }
            }
            source.removeView(child)
            group?.addView(child)
        }
        val host = column().apply { setPadding(dp(20), dp(8), dp(20), dp(24)) }
        fun showMenu() {
            host.removeAllViews(); back = null
            host.addView(title(if (yard) "Твой двор" else if (pet) "Твой питомец" else "Твоя комната", 23f, true))
            host.addView(title("Выбери, что настроить", 14f).apply { setPadding(0,dp(7),0,dp(18)) })
            groups.forEach { (name, body) ->
                val row = column().apply {
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE); cornerRadius = dp(20).toFloat()
                        setStroke(dp(1), 0xFFE0E9E2.toInt())
                    }
                    setPadding(dp(18),dp(17),dp(18),dp(17))
                    layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) }
                    isFocusable = true
                }
                row.addView(title("$name  ›", 17f, true))
                val hint = when(name) {
                    "Оформление двора" -> "Цвет фасада, забора и качелей"
                    "Садовые декорации" -> "Клумба, каменный сад или поилка"
                    "Живой сад" -> "Бабочки, фонарики и светлячки"
                    "Окрас" -> "Основные оттенки и животик"
                    "Улыбка и настроение" -> "Выражение лица и форма улыбки"
                    "Глаза" -> "Форма и цвет глаз"
                    "Румянец" -> "Узор и оттенок щёк"
                    "Антенна" -> "Форма и цвет украшения"
                    "Декор" -> "Предметы в комнате и их цвета"
                    "Обои" -> "Цвет стен, узор и его оттенок"
                    "Место для сна" -> "Модель мебели и обивка"
                    "Шторы" -> "Модель, цвет и положение"
                    "Освещение" -> "Выбор светильника"
                    else -> "Цвет пола, панелей и плинтуса"
                }
                row.addView(title(hint,13f).apply { setTextColor(0xFF68796F.toInt());setPadding(0,dp(5),0,0) })
                row.setOnClickListener {
                    host.removeAllViews()
                    val backButton = title("‹  Все категории",15f,true).apply {
                        minHeight=dp(48);gravity=Gravity.CENTER_VERTICAL;isFocusable=true
                        setOnClickListener { showMenu();onNavigate() }
                    }
                    host.addView(backButton)
                    host.addView(title(name,25f,true).apply { setPadding(0,dp(10),0,dp(14)) })
                    (body.parent as? LinearLayout)?.removeView(body)
                    host.addView(body)
                    back = { showMenu();onNavigate() }
                    onNavigate()
                }
                host.addView(row)
            }
        }
        showMenu()
        return host
    }
}

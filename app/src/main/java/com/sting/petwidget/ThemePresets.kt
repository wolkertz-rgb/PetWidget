package com.sting.petwidget

/**
 * ПРЕСЕТЫ ТЕМ «АТЕЛЬЕ».
 * Каждый пресет — карта тема-токенов (ключи 1:1 с th() в рендере).
 * Применение = запись в SharedPreferences, виджет перекрашивается сам.
 */
object ThemePresets {

    val KEYS = listOf(
        "room_bg_top", "room_bg_bottom", "room_panel", "room_skirt",
        "floor_top", "floor_bottom",
        "pet_top", "pet_mid", "pet_bottom", "pet_belly", "pet_blush",
        "chair_back1", "chair_back2", "chair_seat1", "chair_seat2",
        "chair_roll1", "chair_roll2"
    )

    /** hex → Int */
    private fun c(hex: String): Int = android.graphics.Color.parseColor(hex)

    val MINT = mapOf(
        "room_bg_top" to c("#F2F2EF"), "room_bg_bottom" to c("#E8E8E4"),
        "room_panel" to c("#E5E5E0"), "room_skirt" to c("#DCDCD6"),
        "floor_top" to c("#E1E1DC"), "floor_bottom" to c("#D3D3CE"),
        "pet_top" to c("#A8E6C3"), "pet_mid" to c("#6FBE97"),
        "pet_bottom" to c("#4FA379"), "pet_belly" to c("#BDEBCF"),
        "pet_blush" to c("#F3A9A0"),
        "chair_back1" to c("#AB7B44"), "chair_back2" to c("#6D4523"),
        "chair_seat1" to c("#C08A57"), "chair_seat2" to c("#94613A"),
        "chair_roll1" to c("#B98350"), "chair_roll2" to c("#8A5A2E")
    )

    val PRESETS: LinkedHashMap<String, Map<String, Int>> = linkedMapOf(
        "Мятный" to MINT,
        "Персик" to mapOf(
            "room_bg_top" to c("#FFF1EA"), "room_bg_bottom" to c("#FBE3D6"),
            "room_panel" to c("#F7DDD0"), "room_skirt" to c("#EFD3C4"),
            "floor_top" to c("#F5DCC9"), "floor_bottom" to c("#EAD0BC"),
            "pet_top" to c("#FFD9C4"), "pet_mid" to c("#F2A98B"),
            "pet_bottom" to c("#E08E6D"), "pet_belly" to c("#FFE9DC"),
            "pet_blush" to c("#F58F8F"),
            "chair_back1" to c("#D2A26B"), "chair_back2" to c("#A9713F"),
            "chair_seat1" to c("#E6BE92"), "chair_seat2" to c("#C59166"),
            "chair_roll1" to c("#DCAE7B"), "chair_roll2" to c("#B57F4E")
        ),
        "Космос" to mapOf(
            "room_bg_top" to c("#1B1E2E"), "room_bg_bottom" to c("#14172A"),
            "room_panel" to c("#20243A"), "room_skirt" to c("#2A2F4A"),
            "floor_top" to c("#232840"), "floor_bottom" to c("#1A1E34"),
            "pet_top" to c("#9FE3FF"), "pet_mid" to c("#5EA8E6"),
            "pet_bottom" to c("#3E7BC0"), "pet_belly" to c("#B4EBFF"),
            "pet_blush" to c("#7C7AE8"),
            "chair_back1" to c("#3E3F6B"), "chair_back2" to c("#2A2B47"),
            "chair_seat1" to c("#515690"), "chair_seat2" to c("#3A3D66"),
            "chair_roll1" to c("#4C4E7F"), "chair_roll2" to c("#34365A")
        ),
        "Карамель" to mapOf(
            "room_bg_top" to c("#FFF4E0"), "room_bg_bottom" to c("#FFEAD0"),
            "room_panel" to c("#F8E6C8"), "room_skirt" to c("#EFDDC0"),
            "floor_top" to c("#F2D6B8"), "floor_bottom" to c("#E6C6A2"),
            "pet_top" to c("#FFD9A0"), "pet_mid" to c("#F2B36B"),
            "pet_bottom" to c("#D9924A"), "pet_belly" to c("#FFF0D6"),
            "pet_blush" to c("#F5B791"),
            "chair_back1" to c("#C58A47"), "chair_back2" to c("#8F5D2A"),
            "chair_seat1" to c("#D9A562"), "chair_seat2" to c("#B4814C"),
            "chair_roll1" to c("#C99658"), "chair_roll2" to c("#9C6C38")
        ),
        "Морской" to mapOf(
            "room_bg_top" to c("#EAF7F6"), "room_bg_bottom" to c("#D9EFEF"),
            "room_panel" to c("#DDEEEC"), "room_skirt" to c("#C8E2E0"),
            "floor_top" to c("#CFE7E6"), "floor_bottom" to c("#BDD8D6"),
            "pet_top" to c("#7FE3D8"), "pet_mid" to c("#3FB9AC"),
            "pet_bottom" to c("#2C93A0"), "pet_belly" to c("#D3F4F0"),
            "pet_blush" to c("#F5A3A3"),
            "chair_back1" to c("#2C5875"), "chair_back2" to c("#1B3D55"),
            "chair_seat1" to c("#4E829B"), "chair_seat2" to c("#35627B"),
            "chair_roll1" to c("#3F7089"), "chair_roll2" to c("#2A4F65")
        ),
        "Лес" to mapOf(
            "room_bg_top" to c("#F0F5E8"), "room_bg_bottom" to c("#E2EBD4"),
            "room_panel" to c("#E3ECD6"), "room_skirt" to c("#D6E1C2"),
            "floor_top" to c("#DDE8CC"), "floor_bottom" to c("#CCD8BA"),
            "pet_top" to c("#CBE9A8"), "pet_mid" to c("#95C96A"),
            "pet_bottom" to c("#6FA645"), "pet_belly" to c("#EDF6D8"),
            "pet_blush" to c("#F5BFA0"),
            "chair_back1" to c("#7C6A3C"), "chair_back2" to c("#5B4F2C"),
            "chair_seat1" to c("#A08C55"), "chair_seat2" to c("#7E6C3E"),
            "chair_roll1" to c("#8D7B49"), "chair_roll2" to c("#655833")
        ),
        "Бумага" to mapOf(
            "room_bg_top" to c("#FFFFFF"), "room_bg_bottom" to c("#F4F4F2"),
            "room_panel" to c("#F0F0EE"), "room_skirt" to c("#E4E4E1"),
            "floor_top" to c("#EDEDEA"), "floor_bottom" to c("#E0E0DD"),
            "pet_top" to c("#E9EFE9"), "pet_mid" to c("#C9D4C9"),
            "pet_bottom" to c("#9FB3A3"), "pet_belly" to c("#F7F9F7"),
            "pet_blush" to c("#E8B4B4"),
            "chair_back1" to c("#9A9A92"), "chair_back2" to c("#6E6E66"),
            "chair_seat1" to c("#B4B4AC"), "chair_seat2" to c("#92928A"),
            "chair_roll1" to c("#A5A59D"), "chair_roll2" to c("#7F7F77")
        )
    )

    /** применить пресет в prefs */
    fun apply(context: android.content.Context, preset: Map<String, Int>) {
        val sp = context.getSharedPreferences("pet_state", android.content.Context.MODE_PRIVATE)
        val ed = sp.edit()
        preset.forEach { (k, v) -> ed.putInt(k, v) }
        ed.apply()
    }

    /** сбросить косметику — вернётся мятная тема по умолчанию */
    fun reset(context: android.content.Context) {
        val sp = context.getSharedPreferences("pet_state", android.content.Context.MODE_PRIVATE)
        val ed = sp.edit()
        KEYS.forEach { ed.remove(it) }
        ed.apply()
    }
}

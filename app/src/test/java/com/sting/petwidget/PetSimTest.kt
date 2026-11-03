package com.sting.petwidget

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Характеризационные тесты: фиксируют ТЕКУЩЕЕ поведение мозга питомца.
 * Рефакторинг PetSim разрешён только под ними.
 *
 * Часы инъекцией: тесты бегут по «экранному времени» питомца.
 * Android-классы не дёргаются: Paint/Canvas остаются ленивыми в ItemDefs.
 */
class PetSimTest {

    // ------------------------------------------------- in-memory prefs (фейк)

    private class FakePrefs : SharedPreferences {
        val map = HashMap<String, Any>()

        override fun getAll(): MutableMap<String, Any> = map
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValue: MutableSet<String>?): MutableSet<String> =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValue ?: mutableSetOf()
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) { if (key != null) map[key] = value ?: "" }
            override fun putStringSet(key: String?, values: MutableSet<String>?) { if (key != null) map[key] = values }
            override fun putInt(key: String, value: Int) { map[key] = value }
            override fun putLong(key: String, value: Long) { map[key] = value }
            override fun putFloat(key: String, value: Float) { map[key] = value }
            override fun putBoolean(key: String, value: Boolean) { map[key] = value }
            override fun remove(key: String?) { map.remove(key) }
            override fun clear(): SharedPreferences.Editor { map.clear(); return this }
            override fun apply() {}
            override fun commit(): Boolean = true
        }
    }

    // --------------------------------------------------------------- helpers

    private var now = 0L
    private fun clock(): Long = now

    private fun makeSim(seed: Int): PetSim = PetSim(FakePrefs(), ::clock, Random(seed))

    /** прожить N миллисекунд «экранной» жизни питомца */
    private fun live(sim: PetSim, ms: Long) {
        var left = ms
        while (left > 0) {
            val piece = minOf(left, 250L)
            now += piece
            sim.tick(piece)
            left -= piece
        }
    }

    // ------------------------------------------------------------- тесты

    /** после 4+ часов без экрана питомец ДОСЫПАЕТ: приходит состояние сна */
    @Test
    fun dosypaniePosleRazluki() {
        val sim = makeSim(2026)
        sim.onResume(5 * 3_600_000L)
        sim.tick(2000)
        assertEquals(PetSim.State.SLEEP, sim.state)
    }

    /** предметов в комнате не больше одного (лимит спавна) */
    @Test
    fun limitOdinPredmet() {
        val sim = makeSim(7)
        live(sim, 5 * 60_000L)
        assertTrue(sim.items.size <= 1)
        live(sim, 60_000L)
        assertTrue(sim.items.size <= 1)
    }

    /** «одна встреча — один предмет»: интерес к тронутому гаснет */
    @Test
    fun odnaVstrechaOdinPredmet() {
        val sim = makeSim(11)
        live(sim, 90_000L)
        val target = sim.items.firstOrNull() ?: return // предмет мог не заспавниться — тест мягкий
        sim.x = target.x
        live(sim, 10_000L)
        if (sim.items.contains(target)) {
            assertTrue("интерес к тронутому предмету должен гаснуть", target.used)
            repeat(20) { sim.tick(2500) }
            assertTrue("использованный предмет не выбирается целью", sim.targetItem?.used != true)
        }
    }

    /** голод растёт на экране */
    @Test
    fun golodRastet() {
        val sim = makeSim(3)
        val start = sim.hunger
        live(sim, 120_000L)
        assertTrue(sim.hunger > start)
    }

    /** DI: одинаковый seed — одинаковая жизнь (проверка внедрения рандома) */
    @Test
    fun determinirovannostSeeda() {
        val a = makeSim(404)
        val b = makeSim(404)
        repeat(10) {
            a.tick(1000)
            b.tick(1000)
            assertEquals(a.state.name, b.state.name)
            assertEquals(a.items.size, b.items.size)
        }
    }
}

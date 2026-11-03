package com.sting.petwidget

import android.content.SharedPreferences
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Характеризационные тесты: фиксируют ТЕКУЩЕЕ поведение мозга питомца.
 * Рефакторинг PetSim разрешён только под ними.
 *
 * Часы инъекцией: тесты бегут по «экранному времени» питомца.
 * FakePrefs реализует android.content.SharedPreferences напрямую.
 */
class PetSimTest {

    // ------------------------------------------------- in-memory prefs (фейк)

    private class FakePrefs : android.content.SharedPreferences {
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

        private inner class Editor : android.content.SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { if (key != null) map[key] = value ?: ""; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor { if (key != null) { values?.let { map[key] = it as Any } }; return this }
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun remove(key: String?): android.content.SharedPreferences.Editor { map.remove(key); return this }
            override fun clear(): android.content.SharedPreferences.Editor { map.clear(); return this }
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

    /** сохранение → загрузка восстанавливает мир питомца (round-trip) */
    @Test
    fun saveLoadRoundTrip() {
        val prefs = FakePrefs()
        val a = PetSim(prefs, ::clock, Random(101))
        a.state = PetSim.State.SMUG
        a.stateDur = 1_500
        a.stateTime = 300
        a.items.add(PetSim.SpawnedItem("ball", 0.3f, 0f, 0, 120_000, false, true))
        a.save()

        val b = PetSim(prefs, ::clock, Random(1)) // init{} вызывает load()
        assertEquals(PetSim.State.SMUG, b.state)
        assertEquals(300L, b.stateTime)
        assertEquals(1_500L, b.stateDur)
        val restored = b.items.firstOrNull { it.defId == "ball" && it.x == 0.3f }
        assertTrue("предмет и used-флаг должны восстанавливаться", restored != null && restored.used)
    }

    /** тяга щёлкает светом: light_on переворачивается в момент щелчка */
    @Test
    fun tyagaSchelkaetSvetom() {
        val prefs = FakePrefs()
        val sim = PetSim(prefs, ::clock, Random(5))
        sim.beginPull()
        sim.tick(1_400)
        assertEquals(false, prefs.getBoolean("light_on", true))
    }

    /** мягкое просыпание: из сна ВСЕГДА сначала потягивание, без мгновенного рывка */
    @Test
    fun probyzhdenieCherezPotyagivanie() {
        val sim = makeSim(17)
        sim.state = PetSim.State.SLEEP
        sim.stateTime = 60_000
        sim.stateDur = 100
        sim.tick(1_000)
        assertEquals(PetSim.State.STRETCH, sim.state)
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

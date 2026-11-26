package com.sting.petwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Render the production Canvas paths, including layer ordering, at actual widget proportions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualV5RenderTest {
    private val output = File("build/visual-v5")
    private val context get() = RuntimeEnvironment.getApplication()

    private fun scene(name: String, yard: Boolean = false): Pair<PetSim, SceneRenderer> {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("light_on", true)
            .putInt("decor_rug", 1).putInt("decor_tv", 1).putInt("decor_plant", 1).apply()
        val sim = PetSim(prefs, { 60_000L }, Random(42), autoSave = false)
        val renderer = SceneRenderer().apply {
            persistPose = false
            strictRendering = true
            lastPrefs = prefs
            previewTime = 60_000L
            previewYard = yard
        }
        return sim to renderer
    }

    private fun save(bitmap: Bitmap?, folder: String, index: Int) {
        assertNotNull(bitmap)
        val destination = File(output, "%s/%03d.png".format(folder, index))
        destination.parentFile!!.mkdirs()
        destination.outputStream().use { assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun renderAllStatesAndEveryItemInteraction() {
        for (state in PetSim.State.entries) {
            val (sim, renderer) = scene(state.name)
            sim.state = state
            sim.stateDur = if (state == PetSim.State.PULL) PetSim.PULL_DUR else 2400L
            if (state == PetSim.State.PULL) {
                renderer.lastPrefs!!.edit().putInt("room_lamp", 1).apply()
                sim.x = sim.switchX
            }
            val count = (sim.stateDur / 50).toInt()
            for (frame in 0..count) {
                sim.stateTime = frame * 50L
                sim.animPhase = sim.stateTime / 1000f
                if (state == PetSim.State.WALK) sim.x = .30f + .29f * frame / count
                save(renderer.render(sim, 900, 500), state.name, frame)
            }
        }
        for (def in ItemDefs.ALL) {
            val (sim, renderer) = scene(def.id)
            val item = PetSim.SpawnedItem(def.id, .57f, 0f, 2000, 60_000)
            sim.items.add(item)
            sim.targetItem = item
            sim.reaction = def.reaction
            sim.state = PetSim.State.INTERACT
            sim.stateDur = 3600L
            for (frame in 0..72) {
                sim.dir = 1
                sim.stateTime = frame * 50L
                sim.animPhase = sim.stateTime / 1000f
                save(renderer.render(sim, 900, 500), def.id, frame)
                sim.dir = -1
                sim.targetItem!!.x = .43f
                if (frame % 6 == 0) save(renderer.render(sim, 900, 500), def.id + "-left", frame)
                sim.targetItem!!.x = .57f
            }
        }
    }

    @Test fun renderYardVariantsAndThemeCompatibility() {
        for (style in 0..2) {
            val (sim, renderer) = scene("yard$style", yard = true)
            renderer.lastPrefs!!.edit().putInt("yard_fence_style", style)
                .putInt("yard_swing_style", style).apply()
            for (frame in 0..200) {
                // The real outing controller animates a seated pet and bench together.
                sim.outing.javaClass.getDeclaredField("phase").apply { isAccessible = true }.setInt(sim.outing, 5)
                sim.outing.javaClass.getDeclaredField("time").apply { isAccessible = true }.setLong(sim.outing, frame * 50L)
                sim.state = PetSim.State.SIT
                sim.stateDur = 10_000
                sim.x = .8f
                sim.stateTime = frame * 50L
                sim.animPhase = frame * .05f
                save(renderer.render(sim, 900, 500), "yard$style", frame)
            }
        }
        for ((name, theme) in ThemePresets.PRESETS) {
            val (sim, renderer) = scene("theme$name")
            val editor = renderer.lastPrefs!!.edit()
            theme.forEach { (key, value) -> editor.putInt(key, value) }
            editor.apply()
            save(renderer.render(sim, 900, 500), "themes", ThemePresets.PRESETS.keys.indexOf(name))
        }
        for (bed in 0..2) for (lamp in 0..2) for (curtain in -1..2) {
            val (sim, renderer) = scene("interior-$bed-$lamp-$curtain")
            renderer.lastPrefs!!.edit().putInt("room_bed", bed).putInt("room_lamp", lamp)
                .putInt("room_curtain", curtain).putInt("curtain_closed", 1).apply()
            sim.sleepingInBed = true
            sim.state = PetSim.State.SLEEP
            sim.x = PetSim.BED_X
            sim.stateTime = 1500
            save(renderer.render(sim, 900, 500), "interiors", bed*12 + lamp*4 + curtain + 1)
        }
        for (style in 0..3) {
            val (pet, renderer) = scene("garden$style", yard = true)
            renderer.lastPrefs!!.edit().putInt("yard_decor", style).apply()
            pet.x = .63f
            save(renderer.render(pet, 900, 500), "garden-decor", style)
        }
        val (sim, renderer) = scene("night", yard = true)
        renderer.previewTime = 180_000L
        save(renderer.render(sim, 900, 500), "night", 0)
    }

    @Test fun renderContactSheet() {
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(248, 238, 216))
        for ((index, def) in ItemDefs.ALL.withIndex()) {
            val x = 100f + index % 6 * 200f
            val floor = 230f + index / 6 * 250f
            def.draw(canvas, x, floor, 42f, 0f)
        }
        save(image, "catalog", 0)
    }
}

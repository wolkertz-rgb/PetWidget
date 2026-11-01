package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Мост тапов по виджету: тап по питомцу = принудительный тест взаимодействия. */
class TapBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PetWidgetProvider.ACTION_LIGHT -> {
                val prefs = context.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
                val on = prefs.getBoolean("light_on", false)
                prefs.edit().putBoolean("light_on", !on).apply()
                PetWidgetProvider.burst(context, frames = 2, everyMs = 80)
            }
            else -> {
                // тап по виджету = ТЕСТ РУК: если есть предмет — мгновенно взаимодействуем
                try {
                    val sim = PetSim(context)
                    val started = sim.tapInteract()
                    if (!started) sim.tap()   // предметов нет — обычная радость
                    sim.save()
                } catch (_: Throwable) {}
                PetWidgetProvider.tryStartService(context)
                PetWidgetProvider.burst(context, frames = 24)
            }
        }
    }
}

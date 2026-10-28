package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Мост тапов по виджету: тап по лампочке (свет) и тап по остальному (питомец). */
class TapBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PetWidgetProvider.ACTION_LIGHT -> {
                val prefs = context.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
                val on = prefs.getBoolean("light_on", false)
                prefs.edit().putBoolean("light_on", !on).apply()
                // два кадра, чтобы сразу увидеть включение/выключение
                PetWidgetProvider.burst(context, frames = 2, everyMs = 80)
            }
            else -> {
                PetWidgetProvider.tryStartService(context)
                PetWidgetProvider.burst(context, frames = 24)
            }
        }
    }
}

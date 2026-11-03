package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/** Тапы по виджету: питомец = поглаживание; лампа = тумблер. */
class TapBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        val app = context.applicationContext
        val thread = HandlerThread("pet-tap").apply { start() }
        val handler = Handler(thread.looper)
        var closed = false
        fun finishOnce() {
            if (!closed) {
                closed = true
                thread.quitSafely()
                pr.finish()
            }
        }
        handler.post {
            try {
                when (intent.action) {
                    PetWidgetProvider.ACTION_LIGHT -> {
                        val prefs = app.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("light_on", !prefs.getBoolean("light_on", true)).apply()
                        PetWidgetProvider.burst(app, frames = 2, everyMs = 80) { finishOnce() }
                    }
                    else -> {
                        try {
                            val sim = PetSim(app)
                            sim.tap()
                            sim.save()
                        } catch (e: Exception) {
                            Log.w("PetWidget", "tap sim failed", e)
                        }
                        val started = PetWidgetProvider.tryStartService(app)
                        if (started) finishOnce()
                        else PetWidgetProvider.burst(app, frames = 10, everyMs = 350) { finishOnce() }
                    }
                }
            } catch (e: Exception) {
                Log.w("PetWidget", "tap failed", e)
                finishOnce()
            } finally {
                PetWidgetProvider.scheduleKeepAlive(app)
            }
        }
    }
}

package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/** Тап по виджету = поглаживание питомца. */
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
                val sim = PetSim(app)
                sim.tap()
                sim.save()
                val started = PetWidgetProvider.tryStartService(app)
                if (started) finishOnce()
                else PetWidgetProvider.burst(app, frames = 10, everyMs = 350) { finishOnce() }
            } catch (e: Exception) {
                Log.w("PetWidget", "tap failed", e)
                finishOnce()
            } finally {
                PetWidgetProvider.scheduleKeepAlive(app)
            }
        }
    }
}

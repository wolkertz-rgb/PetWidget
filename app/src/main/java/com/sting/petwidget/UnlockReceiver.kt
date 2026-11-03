package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/** Разблокировка экрана: короткий рывок жизни, если сервис не поднялся. */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        val app = context.applicationContext
        val thread = HandlerThread("pet-unlock").apply { start() }
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
                if (PetWidgetProvider.tryStartService(app)) {
                    finishOnce()
                } else {
                    PetWidgetProvider.burst(app, frames = 8) { finishOnce() }
                }
            } catch (e: Exception) {
                Log.w("PetWidget", "unlock tick failed", e)
                finishOnce()
            } finally {
                PetWidgetProvider.scheduleKeepAlive(app)
            }
        }
    }
}

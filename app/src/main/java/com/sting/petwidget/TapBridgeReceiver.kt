package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/** Тап по виджету = поглаживание: его обрабатывает сервис, либо «ходы» — никогда оба. */
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
                if (PetService.running) {
                    // сервис жив: тап по intent'у обрабатывает он сам (single writer)
                    PetWidgetProvider.tryStartService(app)
                    finishOnce()
                } else {
                    // сервис мёртв: сохраним реакцию в prefs — «ходы» её разыграют,
                    // иначе сбой старта сервиса обернётся потерей поглаживания
                    try {
                        PetSim(app).apply { tap(); save() }
                    } catch (e: Exception) {
                        Log.w("PetWidget", "tap sim failed", e)
                    }
                    if (!PetWidgetProvider.tryStartService(app)) {
                        PetWidgetProvider.burst(app, frames = 10, everyMs = 350) { finishOnce() }
                    } else {
                        finishOnce()
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

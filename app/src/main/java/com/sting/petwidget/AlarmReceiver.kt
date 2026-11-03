package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.util.Log

/**
 * Цепочка «ходов»: каждые 2 минуты. Работает только при включённом экране
 * (будильник не будит телефон), цепочка не рвётся даже при ошибках (finally).
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        val app = context.applicationContext
        val thread = HandlerThread("pet-alarm").apply { start() }
        Handler(thread.looper).post {
            try {
                val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isInteractive) {
                    // экран включён: пробуем сервис, иначе — один «ход» страховкой
                    if (!PetService.running && !PetWidgetProvider.tryStartService(app)) {
                        PetWidgetProvider.advanceAndRender(app)
                    }
                }
            } catch (e: Exception) {
                Log.w("PetWidget", "alarm tick failed", e)
            } finally {
                PetWidgetProvider.scheduleKeepAlive(app)
                thread.quitSafely()
                pr.finish()
            }
        }
    }
}

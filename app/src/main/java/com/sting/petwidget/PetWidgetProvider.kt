package com.sting.petwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

/**
 * Точка входа виджета. При добавлении/обновлении пытается запустить сервис жизни,
 * а если система не даёт (рестрикции фона) — рисует один статичный кадр,
 * чтобы питомец всё равно был виден.
 */
class PetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, awm: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateOne(context, awm, id)
        tryStartService(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?
    ) {
        updateOne(context, awm, appWidgetId)
        tryStartService(context)
    }

    override fun onEnabled(context: Context) {
        tryStartService(context)
        scheduleKeepAlive(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // если все виджеты удалены — сервис сам завершится (refreshIds → ids пуст)
        tryStartService(context)
    }

    companion object {

        private fun updateOne(context: Context, awm: AppWidgetManager, id: Int) {
            val rv = RemoteViews(context.packageName, R.layout.pet_widget)
            rv.setOnClickPendingIntent(R.id.scene, tapIntent(context))
            awm.updateAppWidget(id, rv)
        }

        private fun tapIntent(context: Context): PendingIntent =
            PendingIntent.getForegroundService(
                context, 11,
                Intent(context, PetService::class.java).setAction(PetService.ACTION_TAP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /** Раз в 15 минут мягко проверяем, что сервис жизни жив. Не будит телефон. */
        fun scheduleKeepAlive(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 7,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setInexactRepeating(
                    android.app.AlarmManager.ELAPSED_REALTIME,   // не будит телефон
                    android.os.SystemClock.elapsedRealtime() + 60_000,
                    15 * 60_000L,
                    pi
                )
            } catch (_: Exception) {}
        }

        fun tryStartService(context: Context) {
            try {
                context.startForegroundService(
                    Intent(context, PetService::class.java).setAction(PetService.ACTION_SHOW)
                )
            } catch (_: Exception) {
                // Система не разрешила запуск сервиса из фона — рисуем статичный кадр.
                // Питомец оживёт при следующем разблокировании (_ANDROID_UPDATE или тап).
                renderStatic(context)
            }
        }

        /** Один кадр без сервиса: питомец стоит и ждёт, состояние подтянуто из prefs. */
        fun renderStatic(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val sim = PetSim(context)
            // статичный кадр всё же "проживает" чуть времени, чтобы питомец
            // просыпался, если система нарисовала его в позе сна
            sim.tick(1_400)
            sim.tick(1_400)
            val renderer = SceneRenderer()

            for (id in ids) {
                val opts = awm.getAppWidgetOptions(id)
                val d = context.resources.displayMetrics.density
                val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                val w = (wDp * d).toInt().coerceIn(80, 1600)
                val h = (hDp * d).toInt().coerceIn(60, 900)
                val bmp = renderer.render(sim, w, h) ?: continue
                val rv = RemoteViews(context.packageName, R.layout.pet_widget)
                rv.setImageViewBitmap(R.id.scene, bmp)
                rv.setOnClickPendingIntent(R.id.scene, tapIntent(context))
                awm.updateAppWidget(id, rv)
            }
        }
    }
}

/** Пробуждение после перезагрузки телефона — питомец продолжает свою жизнь. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            context.startForegroundService(
                Intent(context, PetService::class.java).setAction(PetService.ACTION_SHOW)
            )
        } catch (_: Exception) {
            PetWidgetProvider.renderStatic(context)
        }
    }
}

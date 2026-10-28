package com.sting.petwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews

/**
 * Точка входа виджета. Двухуровневая жизнь питомца:
 *
 * Уровень 1 (идеальный): foreground-сервис PetService анимирует всё плавно.
 * Уровень 2 (страховка от фризилок вроде HiOS/TECNO): каждые ~2 минуты
 * точный будильник будит код, питомец "делает ход" и кадр перерисовывается.
 * Так питомец живёт на ЛЮБОМ телефоне, даже если сервис убивают.
 */
class PetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, awm: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateOne(context, awm, id)
        tryStartService(context)
        advanceAndRender(context)
        scheduleKeepAlive(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?
    ) {
        updateOne(context, awm, appWidgetId)
        advanceAndRender(context)
    }

    override fun onEnabled(context: Context) {
        tryStartService(context)
        scheduleKeepAlive(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        tryStartService(context)
    }

    companion object {

        const val ACTION_LIGHT = "com.sting.petwidget.LIGHT"

        /** PendingIntent для переключения света (используется в уведомлении) */
        fun lightIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 23,
                Intent(context, TapBridgeReceiver::class.java).setAction(ACTION_LIGHT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun applyClicks(rv: RemoteViews, context: Context) {
            rv.setOnClickPendingIntent(R.id.scene, tapIntent(context))
        }

        private fun updateOne(context: Context, awm: AppWidgetManager, id: Int) {
            try {
                val rv = RemoteViews(context.packageName, R.layout.pet_widget)
                applyClicks(rv, context)
                awm.updateAppWidget(id, rv)
            } catch (t: Throwable) {
                android.util.Log.e("PetWidget", "updateOne $id", t)
            }
        }

        private fun tapIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 11,
                Intent(context, TapBridgeReceiver::class.java).setAction(PetService.ACTION_TAP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /** Раз в ~2 минуты мягко проверяем, что сервис жив (не будит телефон). */
        fun scheduleKeepAlive(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 7,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val canExact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
            try {
                if (canExact) {
                    am.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME,
                        android.os.SystemClock.elapsedRealtime() + 120_000,
                        pi
                    )
                } else {
                    am.setInexactRepeating(
                        android.app.AlarmManager.ELAPSED_REALTIME,
                        android.os.SystemClock.elapsedRealtime() + 120_000,
                        120_000L,
                        pi
                    )
                }
            } catch (_: Exception) {}
        }

        fun tryStartService(context: Context) {
            try {
                context.startForegroundService(
                    Intent(context, PetService::class.java).setAction(PetService.ACTION_SHOW)
                )
            } catch (_: Exception) {
                // сервис не дают запустить — живём уровнем 2: будильник + ходы питомца
            }
        }

        /**
         * Серия живых кадров "прямо сейчас" — работает даже если сервис заморожен.
         * Вызывается сразу после тапа/разблокировки: в этот момент процесс не спит.
         */
        fun burst(context: Context, frames: Int = 24, everyMs: Long = 450L) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            for (i in 0 until frames) {
                handler.postDelayed({ advanceAndRender(context) }, i * everyMs)
            }
        }

        /**
         * Один "ход" питомца: подтягивает состояние, проживает время с прошлой
         * отметки и рисует свежий кадр. Работает без всякого сервиса.
         */
        fun advanceAndRender(context: Context) {
            try {
                // если сервис жив и рисует кадры — не мешаем ему
                val prefs = context.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
                val frameAt = prefs.getLong("frame_at", 0L)
                if (frameAt > 0 && System.currentTimeMillis() - frameAt < 2_500) return

                val awm = AppWidgetManager.getInstance(context)
                val ids = awm.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
                if (ids.isEmpty()) return

                val now = System.currentTimeMillis()
                val last = prefs.getLong("last_static", 0L)
                val step = if (last > 0) (now - last).coerceIn(300L, 60_000L) else 1_000L
                prefs.edit().putLong("last_static", now).apply()

                val sim = PetSim(context)
                sim.tick(step)
                sim.save()
                val renderer = SceneRenderer()
                renderer.lastPrefs = prefs
                renderer.lightOn = true
                renderer.density = context.resources.displayMetrics.density

                for (id in ids) {
                    val opts = awm.getAppWidgetOptions(id)
                    val d = context.resources.displayMetrics.density
                    val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                    val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                    val w = (wDp * d).toInt().coerceIn(80, 1100)
                    val h = (hDp * d).toInt().coerceIn(60, 640)
                    val bmp = renderer.render(sim, w, h) ?: continue
                    val rv = RemoteViews(context.packageName, R.layout.pet_widget)
                    rv.setImageViewBitmap(R.id.scene, bmp)
                    applyClicks(rv, context)
                    awm.updateAppWidget(id, rv)
                }
            } catch (_: Exception) {
                // даже если всё плохо — виджет не должен лежать
            }
        }
    }
}

/** Пробуждение после перезагрузки телефона. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PetWidgetProvider.tryStartService(context)
        PetWidgetProvider.advanceAndRender(context)
        PetWidgetProvider.scheduleKeepAlive(context)
    }
}

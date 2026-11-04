package com.sting.petwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews

/**
 * Точка входа виджета. Два режима жизни:
 *  A) сервис жив — он рисует кадры (turbo);
 *  B) сервис мёртв/отказан — «ходы»: будильник-цепочка + burst при событиях.
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

    override fun onDisabled(context: Context) {
        // последний виджет удалён: снять будильник и остановить сервис
        cancelKeepAlive(context)
        try { context.stopService(Intent(context, PetService::class.java)) } catch (e: Exception) {
            Log.w("PetWidget", "stopService failed", e)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        tryStartService(context)
    }

    companion object {

        fun updateOne(context: Context, awm: AppWidgetManager, id: Int) {
            try {
                val rv = RemoteViews(context.packageName, R.layout.pet_widget)
                applyClicks(rv, context)
                awm.updateAppWidget(id, rv)
            } catch (e: Exception) {
                Log.w("PetWidget", "updateOne $id failed", e)
            }
        }

        private const val KEEPALIVE_REQUEST = 7
        private const val KEEPALIVE_MS = 120_000L

        private fun tapIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 11,
                Intent(context, TapBridgeReceiver::class.java).setAction(PetService.ACTION_TAP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun keepAliveIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, KEEPALIVE_REQUEST,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun applyClicks(rv: RemoteViews, context: Context) {
            rv.setOnClickPendingIntent(R.id.scene, tapIntent(context))
        }

        /** одноразовый неточный будильник-цепочка (не будит телефон, без особых разрешений) */
        fun scheduleKeepAlive(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + KEEPALIVE_MS,
                    keepAliveIntent(context)
                )
            } catch (e: Exception) {
                Log.w("PetWidget", "keep-alive schedule failed", e)
            }
        }

        fun cancelKeepAlive(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try { am.cancel(keepAliveIntent(context)) } catch (e: Exception) {
                Log.w("PetWidget", "keep-alive cancel failed", e)
            }
        }

        /** запустить сервис жизни; false — система отказала (тогда живём «ходами»).
         *  action: ACTION_SHOW — обычный подъём; ACTION_TAP — тап-поглаживание,
         *  его обработает onStartCommand на живом сервисе (single writer). */
        fun tryStartService(context: Context, action: String = PetService.ACTION_SHOW): Boolean {
            return try {
                context.startForegroundService(
                    Intent(context, PetService::class.java).setAction(action)
                )
                true
            } catch (e: Exception) {
                Log.w("PetWidget", "service start rejected: ${e.javaClass.simpleName}")
                false
            }
        }

        /**
         * Серия живых кадров — на фоновом потоке, чтобы onReceive не держал main.
         * done() вызывается ровно один раз (для goAsync().finish()).
         */
        fun burst(context: Context, frames: Int = 8, everyMs: Long = 450L, done: () -> Unit = {}) {
            // Работающий сервис уже непрерывно рисует актуальные настройки.
            if (PetService.running) { done(); return }
            val app = context.applicationContext
            val thread = HandlerThread("pet-burst").apply { start() }
            val handler = Handler(thread.looper)
            repeat(frames) { i -> handler.postDelayed({ advanceAndRender(app) }, i * everyMs) }
            handler.postDelayed({ done(); thread.quitSafely() }, frames * everyMs)
        }

        /**
         * Один «ход» питомца без сервиса: подтянуть состояние, прожить время,
         * нарисовать кадр. Если сервис жив — пропускаем (рисует он).
         */
        fun advanceAndRender(context: Context) {
            try {
                if (PetService.running) return
                val prefs = context.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
                val awm = AppWidgetManager.getInstance(context)
                val ids = awm.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
                if (ids.isEmpty()) return

                val now = System.currentTimeMillis()
                val last = prefs.getLong("last_wall", 0L)
                val step = if (last > 0) (now - last).coerceIn(300L, 60_000L) else 1_000L

                val sim = PetSim(context)
                sim.tick(step)
                sim.save()                               // save() пишет и last_wall
                val renderer = SceneRenderer()
                renderer.lastPrefs = prefs
                renderer.density = context.resources.displayMetrics.density
                try { renderer.font = context.resources.getFont(R.font.rubik) } catch (_: Throwable) {}

                for (id in ids) {
                    val opts = awm.getAppWidgetOptions(id)
                    val d = context.resources.displayMetrics.density
                    val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                    val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                    val width = (wDp * d).toInt().coerceIn(80, 1100)
                    val height = (hDp * d).toInt().coerceIn(60, 640)
                    val bmp = renderer.render(sim, width, height) ?: continue
                    val rv = RemoteViews(context.packageName, R.layout.pet_widget)
                    rv.setImageViewBitmap(R.id.scene, bmp)
                    applyClicks(rv, context)
                    awm.updateAppWidget(id, rv)
                }
            } catch (e: Exception) {
                Log.w("PetWidget", "advanceAndRender failed", e)
            }
        }
    }
}

/** системные события: загрузка телефона и обновление приложения */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        try {
            PetWidgetProvider.tryStartService(context)
            PetWidgetProvider.advanceAndRender(context)
        } catch (e: Exception) {
            Log.w("PetWidget", "boot tick failed", e)
        } finally {
            PetWidgetProvider.scheduleKeepAlive(context)
            pr.finish()
        }
    }
}

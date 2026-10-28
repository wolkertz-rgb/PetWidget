package com.sting.petwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.RemoteViews

/**
 * Сердце питомца: живёт как лёгкий foreground-сервис и гоняет кадры сцены
 * ТОЛЬКО пока экран телефона включён. Экран погас — тики останавливаются,
 * состояние сохраняется, батарея не тратится. Экран включился — жизнь продолжается.
 */
class PetService : Service() {

    private lateinit var sim: PetSim
    private val renderer = SceneRenderer()
    private val handler = Handler(Looper.getMainLooper())
    private var awm: AppWidgetManager? = null
    private var ids: IntArray = IntArray(0)

    private var screenOn = false
    private var screenOffAt = 0L      // реальное время в момент погасания экрана
    private var lastFrameAt = 0L      // SystemClock.elapsedRealtime предыдущего кадра
    private var lastWallSaved = 0L    // реальное время последнего тика (для пропусков)
    private var sizeW = 0
    private var sizeH = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreen(true)
                Intent.ACTION_SCREEN_OFF -> onScreen(false)
            }
        }
    }

    // ------------------------------------------------------------ жизненный цикл

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        awm = AppWidgetManager.getInstance(this)
        refreshIds()

        val prefs = getSharedPreferences("pet_state", Context.MODE_PRIVATE)
        lastWallSaved = prefs.getLong("last_wall", 0L)

        sim = PetSim(this)
        renderer.lastPrefs = getSharedPreferences("pet_state", Context.MODE_PRIVATE)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenOn = pm.isInteractive
        if (!screenOn) screenOffAt = System.currentTimeMillis()

        // процесс мог пересоздаться, пока телефон был выключен — применим пропуск времени
        val startupGap = if (lastWallSaved > 0)
            (System.currentTimeMillis() - lastWallSaved).coerceAtLeast(0) else 0
        if (startupGap > 60_000) sim.onResume(startupGap)

        registerCompat()

        // подстрахуемся от "убийц фона" на TECNO/HiOS
        try { PetWidgetProvider.scheduleKeepAlive(this) } catch (_: Exception) {}
    }

    private fun registerCompat() {
        val flags = if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_NOT_EXPORTED else 0
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, flags)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TAP -> {
                if (this::sim.isInitialized && screenOn) {
                    sim.tap()
                    if (sizeW == 0) recomputeSizes()
                    handler.removeCallbacks(frameRunnable)
                    handler.post(frameRunnable)
                }
            }
            else -> { // ACTION_SHOW — виджет (пере)установлен
                refreshIds()
                if (screenOn && this::sim.isInitialized) {
                    recomputeSizes()
                    handler.removeCallbacks(frameRunnable)
                    handler.post(frameRunnable)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        if (this::sim.isInitialized) sim.save()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------ экран вкл/выкл

    private fun onScreen(on: Boolean) {
        if (on == screenOn || !this::sim.isInitialized) return
        screenOn = on
        if (on) {
            val gap = if (lastWallSaved > 0)
                (System.currentTimeMillis() - lastWallSaved).coerceAtLeast(0) else 0
            sim.onResume(gap)
            refreshIds()
            recomputeSizes()
            lastFrameAt = SystemClock.elapsedRealtime()
            handler.removeCallbacks(frameRunnable)
            handler.post(frameRunnable)
        } else {
            screenOffAt = System.currentTimeMillis()
            handler.removeCallbacks(frameRunnable)
            sim.save()
        }
    }

    // ------------------------------------------------------------ кадры

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!screenOn || !this@PetService::sim.isInitialized) return
            val now = SystemClock.elapsedRealtime()
            val dt = (now - lastFrameAt).coerceIn(50L, 2_000L)
            lastFrameAt = now
            lastWallSaved = System.currentTimeMillis()
            val prefs = getSharedPreferences("pet_state", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_wall", lastWallSaved).apply()

            if (sizeW == 0 || sizeH == 0) recomputeSizes()

            // состояние света лампочки + плотность экрана для отрисовки
            renderer.lightOn = prefs.getBoolean("light_on", false)
            renderer.density = resources.displayMetrics.density

            // синхронизируемся с общим состоянием — burst/будильник и сервис
            // всегда рисуют одну и ту же правду
            sim.load()
            sim.tick(dt)
            sim.save()
            prefs.edit().putLong("frame_at", System.currentTimeMillis()).apply()

            val bmp = renderer.render(sim, sizeW, sizeH)
            if (bmp != null && ids.isNotEmpty()) {
                val rv = RemoteViews(packageName, R.layout.pet_widget).apply {
                    setImageViewBitmap(R.id.scene, bmp)
                    PetWidgetProvider.applyClicks(this, this@PetService)
                }
                awm?.partiallyUpdateAppWidget(ids, rv)
            }
            handler.postDelayed(this, 50L) // ТУРБО: 20 ФПС, пока экран включён
        }
    }

    // ------------------------------------------------------------ размеры виджета

    private fun recomputeSizes() {
        if (ids.isEmpty()) { sizeW = 0; sizeH = 0; return }
        val opts = awm?.getAppWidgetOptions(ids.first())
        var wDp = 250; var hDp = 110
        if (opts != null) {
            wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        }
        val d = resources.displayMetrics.density
        sizeW = (wDp * d).toInt().coerceIn(80, 1100)
        sizeH = (hDp * d).toInt().coerceIn(60, 640)
    }

    private fun refreshIds() {
        ids = awm?.getAppWidgetIds(
            ComponentName(this, PetWidgetProvider::class.java)
        ) ?: IntArray(0)
        if (ids.isEmpty()) {
            // виджет(ы) сняли с рабочего стола — можно уйти на пенсию
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ------------------------------------------------------------ уведомление

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Питомец", NotificationManager.IMPORTANCE_MIN)
            ch.description = "Тихая служба жизни питомца"
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Питомец")
            .setContentText("Питомец живёт в виджете")
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher),
                    "Свет 💡",
                    PetWidgetProvider.lightIntent(this)
                ).build()
            )
        startForeground(NOTIF_ID, b.build())
    }

    companion object {
        const val ACTION_SHOW = "com.sting.petwidget.SHOW"
        const val ACTION_TAP = "com.sting.petwidget.TAP"
        private const val CHANNEL = "pet_life"
        private const val NOTIF_ID = 1
    }
}

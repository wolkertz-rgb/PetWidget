package com.sting.petwidget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.content.Context

/**
 * Разовая "кнопка активации" питомца. Открывается по иконке приложения.
 * Делает две вещи:
 *   1. Бодро (из foreground-контекста) стартует сервис жизни виджета —
 *      на прошивках вроде TECNO/HiOS запуск сервиса из виджета блокируется,
 *      а из открытой activity разрешён.
 *   2. Один раз просит систему не оптимизировать батарею для приложения —
 *      иначе умыкание приложений на TECNO будет душить сервис.
 */
class PetStarterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Заводим сервис
        try {
            startForegroundService(
                Intent(this, PetService::class.java).setAction(PetService.ACTION_SHOW)
            )
        } catch (_: Exception) {
        }

        // 2. Просим исключение из оптимизации батареи (один раз за установку)
        if (Build.VERSION.SDK_INT >= 23) {
            val prefs = getApplicationContext()
                .getSharedPreferences("pet_state", Context.MODE_PRIVATE)
            val asked = prefs.getBoolean("battery_asked", false)
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!asked && pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                    // не все прошивки умеют этот диалог — не страшно
                }
            }
            prefs.edit().putBoolean("battery_asked", true).apply()
        }

        // 3. Планируем дежурный "будильник" перезапуска
        try { PetWidgetProvider.scheduleKeepAlive(this) } catch (_: Exception) {}

        // Окно мгновенно закрывается, сервис продолжает жить
        finish()
    }
}

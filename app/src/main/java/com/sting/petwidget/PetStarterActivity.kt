package com.sting.petwidget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager

/**
 * Разовая «кнопка активации» питомца (иконка приложения).
 * 1) Из foreground-контекста стартует сервис жизни (TECNO блокирует запуск
 *    сервиса из виджета, но из открытой activity разрешает).
 * 2) Просит разрешение на уведомления (Android 13+) — чтобы сервис был виден.
 * 3) Просит исключение из оптимизации батареи — иначе HiOS морозит приложение.
 */
class PetStarterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Заводим сервис прямо сейчас
        try {
            startForegroundService(
                Intent(this, PetService::class.java).setAction(PetService.ACTION_SHOW)
            )
        } catch (_: Exception) {}

        // 2. Разрешение на уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                try {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    return // ответ придёт в onRequestPermissionsResult
                } catch (_: Exception) {}
            }
        }
        askBattery()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        askBattery()
    }

    private fun askBattery() {
        val prefs = applicationContext.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("battery_asked", false) && Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                prefs.edit().putBoolean("battery_asked", true).apply()
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {}
            }
        }
        // 3. Дежурный будильник перезапуска
        try { PetWidgetProvider.scheduleKeepAlive(this) } catch (_: Exception) {}
        finish()
    }
}

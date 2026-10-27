package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Срабатывает каждый раз при разблокировке экрана (USER_PRESENT можно
 * принимать из манифеста даже без живого процесса).
 * Делаем небольшой "рывок" жизни + тестовый подъём сервиса.
 */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PetWidgetProvider.tryStartService(context)
        PetWidgetProvider.burst(context, frames = 8)
        PetWidgetProvider.scheduleKeepAlive(context)
    }
}

package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

        /** Дежурный подталкиватель: будильник (не будит телефон, тикает только
 *  пока экран включён). Раз в 2 минуты проверяет сервис жизни.
 *  Если система его усыпила — перезапускает (и планирует следующий раз). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PetWidgetProvider.tryStartService(context)
        // страховка: независимо от того, дали ли системе запустить сервис,
        // питомец делает ход и кадр обновляется
        PetWidgetProvider.advanceAndRender(context)
        PetWidgetProvider.scheduleKeepAlive(context)
    }
}

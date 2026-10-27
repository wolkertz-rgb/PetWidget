package com.sting.petwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Мост тапа по виджету: пробует завести сервис и обязательно даёт "рывок жизни". */
class TapBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PetWidgetProvider.tryStartService(context)
        PetWidgetProvider.burst(context, frames = 24)
    }
}

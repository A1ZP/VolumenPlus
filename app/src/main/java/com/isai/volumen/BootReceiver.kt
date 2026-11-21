package com.isai.volumen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Vuelve a mostrar el botón flotante después de reiniciar o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Prefs.bubbleEnabled(context) && Settings.canDrawOverlays(context)) {
            FloatingVolumeService.start(context)
        }
    }
}

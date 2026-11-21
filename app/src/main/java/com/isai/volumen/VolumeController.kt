package com.isai.volumen

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.widget.Toast

/** Punto único para cambiar el volumen; lo usan la app, el botón flotante y los mosaicos. */
object VolumeController {

    /** Deja que Android elija el flujo activo (lo mismo que harían los botones físicos). */
    const val STREAM_AUTO = -1

    fun audio(ctx: Context): AudioManager =
        ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun adjust(ctx: Context, raise: Boolean, stream: Int = STREAM_AUTO, showUi: Boolean = true) {
        val dir = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        apply(ctx, dir, stream, showUi)
    }

    fun toggleMute(ctx: Context, stream: Int) =
        apply(ctx, AudioManager.ADJUST_TOGGLE_MUTE, stream, true)

    /** Muestra el panel de volumen del sistema sin cambiar nada. */
    fun showPanel(ctx: Context, stream: Int) =
        apply(ctx, AudioManager.ADJUST_SAME, stream, true)

    fun set(ctx: Context, stream: Int, value: Int) {
        try {
            audio(ctx).setStreamVolume(stream, value, 0)
        } catch (e: SecurityException) {
            dndError(ctx)
        }
    }

    fun min(ctx: Context, stream: Int): Int =
        if (Build.VERSION.SDK_INT >= 28) audio(ctx).getStreamMinVolume(stream) else 0

    fun percent(ctx: Context, stream: Int): Int {
        val am = audio(ctx)
        val max = am.getStreamMaxVolume(stream)
        if (max <= 0) return 0
        return am.getStreamVolume(stream) * 100 / max
    }

    private fun apply(ctx: Context, direction: Int, stream: Int, showUi: Boolean) {
        val am = audio(ctx)
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        try {
            if (stream == STREAM_AUTO) {
                am.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, flags)
            } else {
                am.adjustStreamVolume(stream, direction, flags)
            }
        } catch (e: SecurityException) {
            // Bajar el tono a 0 puede activar "No molestar", y Android lo bloquea sin permiso especial.
            dndError(ctx)
        }
    }

    private fun dndError(ctx: Context) =
        Toast.makeText(ctx, R.string.error_dnd, Toast.LENGTH_LONG).show()
}

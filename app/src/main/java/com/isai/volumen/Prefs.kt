package com.isai.volumen

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("volumen_prefs", Context.MODE_PRIVATE)

    fun bubbleEnabled(c: Context) = sp(c).getBoolean("bubble", false)
    fun setBubbleEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("bubble", v).apply()

    fun bubbleStream(c: Context) = sp(c).getInt("stream", VolumeController.STREAM_AUTO)
    fun setBubbleStream(c: Context, v: Int) = sp(c).edit().putInt("stream", v).apply()

    /** -1 = sin posición guardada todavía. */
    fun bubbleX(c: Context) = sp(c).getInt("x", -1)
    fun bubbleY(c: Context) = sp(c).getInt("y", -1)
    fun setBubblePos(c: Context, x: Int, y: Int) = sp(c).edit().putInt("x", x).putInt("y", y).apply()
}

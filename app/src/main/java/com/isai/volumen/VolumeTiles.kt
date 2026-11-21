package com.isai.volumen

import android.media.AudioManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Mosaicos del panel de Ajustes rápidos: no necesitan ningún permiso especial. */
abstract class BaseVolumeTile : TileService() {

    abstract val raise: Boolean

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        VolumeController.adjust(this, raise)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = getString(
                R.string.tile_subtitle,
                VolumeController.percent(this, AudioManager.STREAM_MUSIC)
            )
        }
        tile.updateTile()
    }
}

class VolumeUpTile : BaseVolumeTile() {
    override val raise = true
}

class VolumeDownTile : BaseVolumeTile() {
    override val raise = false
}

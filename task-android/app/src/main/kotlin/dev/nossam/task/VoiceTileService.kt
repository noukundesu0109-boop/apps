package dev.nossam.task

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class VoiceTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.voice_label)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val pending = VoiceWidget.voicePending(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(pending)
        } else {
            val open = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_VOICE, true)
            }
            @Suppress("DEPRECATION")
            startActivityAndCollapse(open)
        }
    }
}

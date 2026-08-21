package app.lawnchair.drivingmode

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.lawnchair.LawnchairLauncher

/** Quick Settings tile that toggles driving mode and always reflects its current state. */
class DrivingModeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val controller = DrivingModeController.current
        when {
            controller?.isShowing == true -> controller.hide()
            controller != null -> controller.show()
            else -> launchLauncherAndShow()
        }
        updateTileState()
    }

    // No live DrivingModeController means the launcher process isn't around to show the overlay
    // on directly - bring it up first, with an extra LawnchairLauncher.onCreate/onNewIntent check
    // for to show driving mode as soon as it's ready.
    private fun launchLauncherAndShow() {
        val intent = Intent(this, LawnchairLauncher::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LawnchairLauncher.EXTRA_START_DRIVING_MODE, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        qsTile?.apply {
            state = if (DrivingModeController.current?.isShowing == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}

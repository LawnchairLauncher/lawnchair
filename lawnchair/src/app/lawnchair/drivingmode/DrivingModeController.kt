package app.lawnchair.drivingmode

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.android.launcher3.Launcher

/**
 * Registered/unregistered from LawnchairLauncher.onCreate/onDestroy. Listens
 * for the paired Bluetooth device picked in DrivingModeSettingsActivity, and
 * shows/hides the driving-mode overlay in lockstep with connect/disconnect.
 */
class DrivingModeController(private val launcher: Launcher) {

    private val overlay = DrivingModeOverlay(launcher)

    // Read by LawnchairLauncher.createTouchControllers() to suppress
    // Launcher3's own swipe gestures (open all apps, etc.) while showing.
    val isShowing: Boolean get() = overlay.isShowing

    // Called from LawnchairLauncher.onNewIntent when the Home button/gesture fires while driving
    // mode is up, in place of Launcher3's normal "open search" handling for that event.
    fun requestGoHome() = overlay.requestGoHome()

    // Called from LawnchairLauncher.onConfigurationChanged (rotation) while driving mode is up.
    fun recreateOverlayForConfigChange() = overlay.recreateForConfigChange()

    // Called from DrivingModeTileService to toggle driving mode from the quick settings tile.
    fun show() = overlay.show()
    fun hide() = overlay.hide()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // This fires for ANY Bluetooth device connecting/disconnecting, not just the one the
            // user picked - e.g. headphones or a watch, possibly before they've ever opened
            // driving mode settings (where BLUETOOTH_CONNECT is actually requested). Reading
            // device.address without the permission throws SecurityException on API 31+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val deviceAddress = device?.address ?: return

            val targetAddress = DrivingModePrefs.getTargetDeviceAddress(context)
            if (targetAddress.isNullOrEmpty() || !deviceAddress.equals(targetAddress, ignoreCase = true)) {
                return
            }

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.i(TAG, "Target stereo connected — showing driving mode")
                    overlay.show()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.i(TAG, "Target stereo disconnected — hiding driving mode")
                    overlay.hide()
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        launcher.registerReceiver(receiver, filter)
        current = this
    }

    fun stop() {
        launcher.unregisterReceiver(receiver)
        overlay.hide()
        if (current === this) current = null
    }

    companion object {
        private const val TAG = "DrivingModeController"

        // Lets DrivingModeSettingsActivity's manual test buttons, and DrivingModeTileService,
        // trigger the real launcher's overlay without needing an actual Bluetooth event —
        // ACL_CONNECTED/DISCONNECTED are protected broadcasts we can't fake
        // from adb or another app anyway.
        var current: DrivingModeController? = null
            private set

        fun simulateConnect() {
            current?.overlay?.show()
        }

        fun simulateDisconnect() {
            current?.overlay?.hide()
        }
    }
}

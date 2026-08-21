package app.lawnchair.drivingmode

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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

        // Lets DrivingModeSettingsActivity's manual test buttons trigger the
        // real launcher's overlay without needing an actual Bluetooth event —
        // ACL_CONNECTED/DISCONNECTED are protected broadcasts we can't fake
        // from adb or another app anyway.
        private var current: DrivingModeController? = null

        fun simulateConnect() {
            current?.overlay?.show()
        }

        fun simulateDisconnect() {
            current?.overlay?.hide()
        }
    }
}

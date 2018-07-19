package ch.deletescape.lawnchair.gestures.dt2s

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.support.annotation.Keep
import android.view.KeyEvent
import android.view.MotionEvent

import ch.deletescape.lawnchair.Launcher
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.R

import java.io.DataOutputStream
import java.io.IOException
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.ComponentName

class DoubleTapGesture(private val controller: GestureController) : Gesture {

    override val isEnabled = true
    private val prefs = Utilities.getPrefs(controller.launcher) // controller.launcher.lawnchairPrefs
    private val delay get() = 350L // in v2 it's in preferences tho, idk if this should be changeable
    private val handlerClass get() = prefs.dt2sHandler

    private var lastDown = 0L

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                lastDown = if (ev.eventTime - lastDown <= delay) {
                    controller.createGestureHandler(handlerClass)?.onGestureTrigger()
                    0L
                } else {
                    ev.downTime
                }
            }
        }
        return false
    }

    @Keep
    class SleepGestureHandlerRoot(launcher: Launcher) : GestureHandler(launcher) {

        override fun onGestureTrigger() {
            try {
                val p = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(p.outputStream)
                outputStream.writeBytes("input keyevent ${KeyEvent.KEYCODE_POWER}\n")
                outputStream.writeBytes("exit\n")
                outputStream.flush()
                outputStream.close()
                p.waitFor()
                p.destroy()
            } catch (e: IOException) {
            } catch (e: InterruptedException) {
            }
        }
    }

    @Keep
    class SleepGestureHandlerTimeout(launcher: Launcher) : GestureHandler(launcher) {

        override fun onGestureTrigger() {
            if (!Utilities.ATLEAST_MARSHMALLOW || Settings.System.canWrite(launcher)) {
                launcher.startActivity(Intent(launcher, SleepTimeoutActivity::class.java))
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:${launcher.packageName}")
                launcher.startActivity(intent)
            }
        }
    }

    @Keep
    class SleepGestureHandlerDeviceAdmin(launcher: Launcher) : GestureHandler(launcher) {

        override fun onGestureTrigger() {
            val devicePolicyManager = launcher.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (devicePolicyManager != null) {
                if (devicePolicyManager.isAdminActive(ComponentName(launcher, SleepDeviceAdmin::class.java))) {
                    devicePolicyManager.lockNow()
                } else {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(launcher, SleepDeviceAdmin::class.java))
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, launcher.getString(R.string.dt2s_admin_hint))
                    launcher.startActivity(intent)
                }
            }
        }
    }
}

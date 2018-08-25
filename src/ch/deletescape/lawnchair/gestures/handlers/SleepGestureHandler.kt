/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.gestures.handlers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.support.annotation.Keep
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.R
import com.android.launcher3.Utilities
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.IOException

private val suThread = HandlerThread("su").apply { start() }
private val suHandler = Handler(suThread.looper)

@Keep
class SleepGestureHandlerRoot(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_sleep_root)!!

    private var currentSession: Session? = null
    private val destroy = Runnable { currentSession?.destroy() }
    private val sleep = Runnable {
        getSuSession().run {
            write("sendevent /dev/input/event1 1 116 1\n")
            write("sendevent /dev/input/event1 0 0 0\n")
            write("sendevent /dev/input/event1 1 116 0\n")
            write("sendevent /dev/input/event1 0 0 0\n")
        }
    }

    private fun getSuSession(): Session {
        if (currentSession == null || !currentSession!!.isAlive) {
            currentSession = Session()
        }
        return currentSession!!
    }

    override fun onDestroy() {
        super.onDestroy()
        suHandler.post(destroy)
    }

    override fun onGestureTrigger(controller: GestureController) {
        suHandler.post(sleep)
    }

    class Session {

        private val process = Runtime.getRuntime().exec("su")!!
        private val outputStream = DataOutputStream(process.outputStream)

        val isAlive get() = isAlive(process)

        fun write(string: String) {
            try {
                outputStream.writeBytes(string)
                outputStream.flush()
            } catch (e: IOException) {
            } catch (e: InterruptedException) {
            }
        }

        fun destroy() {
            try {
                if (isAlive) {
                    outputStream.writeBytes("exit\n")
                    outputStream.flush()
                    outputStream.close()
                }
                process.waitFor()
                process.destroy()
            } catch (e: IOException) {
            } catch (e: InterruptedException) {
            }
        }

        private fun isAlive(process: Process?): Boolean {
            if (process == null) return false
            return try {
                process.exitValue()
                false
            } catch (e: IllegalThreadStateException) {
                true
            }
        }
    }
}

@Keep
class SleepGestureHandlerTimeout(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_sleep_timeout)!!

    override fun onGestureTrigger(controller: GestureController) {
        val launcher = controller.launcher
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
class SleepGestureHandlerDeviceAdmin(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_sleep_device_admin)

    override fun onGestureTrigger(controller: GestureController) {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (devicePolicyManager.isAdminActive(ComponentName(context, SleepDeviceAdmin::class.java))) {
            devicePolicyManager.lockNow()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, SleepDeviceAdmin::class.java))
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.dt2s_admin_hint))
            context.startActivity(intent)
        }
    }

    class SleepDeviceAdmin : DeviceAdminReceiver() {

        override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
            return context.getString(R.string.dt2s_admin_warning)
        }
    }
}

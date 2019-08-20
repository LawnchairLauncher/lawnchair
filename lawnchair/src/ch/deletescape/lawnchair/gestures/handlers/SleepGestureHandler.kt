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

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.support.annotation.Keep
import android.view.View
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.root.IRootHelper
import ch.deletescape.lawnchair.root.RootHelperManager
import com.android.launcher3.R
import com.android.launcher3.Utilities
import org.json.JSONObject
import java.lang.reflect.Method

@Keep
class SleepGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {
    override val displayName: String = context.getString(R.string.action_sleep)

    override fun onGestureTrigger(controller: GestureController, view: View?) {
        method!!.sleep(controller)
    }

    // Preferred methods should appear earlier in the list
    private val method: SleepMethod? by lazy {
        listOf(
                SleepMethodPowerManager(context),
                SleepMethodRoot(context),
                SleepMethodPieAccessibility(context),
                SleepMethodDeviceAdmin(context)
        ).firstOrNull { it.supported }
    }

    override val isAvailable = true // At least the device admin method is always going to work

    abstract class SleepMethod(protected val context: Context) {
        abstract val supported: Boolean
        abstract fun sleep(controller: GestureController)
    }
}

@TargetApi(Build.VERSION_CODES.M)
class SleepMethodPowerManager(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override val supported = Utilities.ATLEAST_MARSHMALLOW && Utilities.hasPermission(context, "android.permission.DEVICE_POWER")

    private val clazz by lazy { PowerManager::class.java }
    private val powerManager: PowerManager by lazy { context.getSystemService(clazz) }
    private val goToSleep: Method by lazy {
        clazz.getDeclaredMethod("goToSleep", Long::class.java).apply {
            isAccessible = true
        }
    }

    private fun goToSleep(time: Long) {
        goToSleep.invoke(powerManager, time)
    }

    override fun sleep(controller: GestureController) {
        goToSleep(SystemClock.uptimeMillis())
    }

}

class SleepMethodRoot(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override val supported = RootHelperManager.isAvailable

    override fun sleep(controller: GestureController) {
        RootHelperManager.getInstance(context).run(IRootHelper::goToSleep)
    }

}

class SleepMethodPieAccessibility(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override val supported = Utilities.ATLEAST_P

    @TargetApi(Build.VERSION_CODES.P)
    override fun sleep(controller: GestureController) {
        context.lawnchairApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }
}

class SleepMethodDeviceAdmin(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override val supported = true

    override fun sleep(controller: GestureController) {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (devicePolicyManager.isAdminActive(ComponentName(context, SleepMethodDeviceAdmin.SleepDeviceAdmin::class.java))) {
            devicePolicyManager.lockNow()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, SleepMethodDeviceAdmin.SleepDeviceAdmin::class.java))
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

@Keep
class SleepGestureHandlerTimeout(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName: String = context.getString(R.string.action_sleep_timeout)

    override fun onGestureTrigger(controller: GestureController, view: View?) {
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

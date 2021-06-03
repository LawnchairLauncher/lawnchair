/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import app.lawnchair.gestures.GestureHandler
import app.lawnchair.lawnchairApp
import com.android.launcher3.R
import com.android.launcher3.Utilities

class SleepGestureHandler(context: Context) : GestureHandler() {

    override fun onTrigger() {
        method?.sleep()
    }

    private val method: SleepMethod? by lazy {
        listOf(
            SleepMethodPieAccessibility(context),
            SleepMethodDeviceAdmin(context)
        ).firstOrNull { it.supported }
    }

    abstract class SleepMethod(protected val context: Context) {
        abstract val supported: Boolean
        abstract fun sleep()
    }
}

class SleepMethodPieAccessibility(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override val supported = Utilities.ATLEAST_P

    @TargetApi(Build.VERSION_CODES.P)
    override fun sleep() {
        context.lawnchairApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }
}

class SleepMethodDeviceAdmin(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override val supported = true

    override fun sleep() {
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

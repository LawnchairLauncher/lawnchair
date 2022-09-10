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
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.GestureHandler
import app.lawnchair.lawnchairApp
import app.lawnchair.root.RootHelperManager
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.util.requireSystemService
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.R
import com.android.launcher3.Utilities

class SleepGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        methods.first { it.isSupported() }.sleep(launcher)
    }

    private val methods = listOf(
        SleepMethodRoot(context),
        SleepMethodPieAccessibility(context),
        SleepMethodDeviceAdmin(context)
    )

    abstract class SleepMethod(protected val context: Context) {
        abstract suspend fun isSupported(): Boolean
        abstract suspend fun sleep(launcher: LawnchairLauncher)
    }
}

class SleepMethodRoot(context: Context) : SleepGestureHandler.SleepMethod(context) {
    private val rootHelperManager = RootHelperManager.INSTANCE.get(context)

    override suspend fun isSupported() = rootHelperManager.isAvailable()

    override suspend fun sleep(launcher: LawnchairLauncher) {
        rootHelperManager.getService().goToSleep()
    }
}

class SleepMethodPieAccessibility(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override suspend fun isSupported() = Utilities.ATLEAST_P

    @TargetApi(Build.VERSION_CODES.P)
    override suspend fun sleep(launcher: LawnchairLauncher) {
        val app = context.lawnchairApp
        if (!app.isAccessibilityServiceBound()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ComposeBottomSheet.show(launcher) {
                ServiceWarningDialog(
                    title = R.string.dt2s_a11y_hint_title,
                    description = R.string.dt2s_a11y_hint,
                    settingsIntent = intent,
                    handleClose = { close(true) }
                )
            }
            return
        }
        launcher.lawnchairApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }
}

class SleepMethodDeviceAdmin(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override suspend fun isSupported() = true

    override suspend fun sleep(launcher: LawnchairLauncher) {
        val devicePolicyManager: DevicePolicyManager = context.requireSystemService()
        if (!devicePolicyManager.isAdminActive(ComponentName(context, SleepDeviceAdmin::class.java))) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(context, SleepDeviceAdmin::class.java)
            )
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, launcher.getString(R.string.dt2s_admin_hint))
            ComposeBottomSheet.show(launcher) {
                ServiceWarningDialog(
                    title = R.string.dt2s_admin_hint_title,
                    description = R.string.dt2s_admin_hint,
                    settingsIntent = intent,
                    handleClose = { close(true) }
                )
            }
            return
        }
        devicePolicyManager.lockNow()
    }

    class SleepDeviceAdmin : DeviceAdminReceiver() {

        override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
            return context.getString(R.string.dt2s_admin_warning)
        }
    }
}

@Composable
fun ServiceWarningDialog(
    title: Int,
    description: Int,
    settingsIntent: Intent,
    handleClose: () -> Unit
) {
    val context = LocalContext.current
    AlertBottomSheetContent(
        title = { Text(text = stringResource(id = title)) },
        text = { Text(text = stringResource(id = description)) },
        buttons = {
            OutlinedButton(
                onClick = handleClose
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Button(
                onClick = {
                    context.startActivity(settingsIntent)
                    handleClose()
                }
            ) {
                Text(text = stringResource(id = R.string.dt2s_warning_open_settings))
            }
        }
    )
}

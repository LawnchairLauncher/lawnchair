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
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.gestures.GestureHandler
import app.lawnchair.launcher
import app.lawnchair.lawnchairApp
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.preferences.components.BottomSheetState
import app.lawnchair.views.showBottomSheet
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.launch

class SleepGestureHandler(private val context: Context) : GestureHandler() {

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

    @ExperimentalMaterialApi
    @TargetApi(Build.VERSION_CODES.P)
    override fun sleep() {
        val app = context.lawnchairApp
        if (!app.isAccessibilityServiceBound()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.launcher.showBottomSheet { state ->
                ServiceWarningDialog(
                    title = R.string.dt2s_a11y_hint_title,
                    description = R.string.dt2s_a11y_hint,
                    settingsIntent = intent,
                    sheetState = state
                )
            }
            return
        }
        context.lawnchairApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }
}

class SleepMethodDeviceAdmin(context: Context) : SleepGestureHandler.SleepMethod(context) {
    override val supported = true

    @ExperimentalMaterialApi
    override fun sleep() {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!devicePolicyManager.isAdminActive(ComponentName(context, SleepDeviceAdmin::class.java))) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, SleepDeviceAdmin::class.java))
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.dt2s_admin_hint))
            context.launcher.showBottomSheet { state ->
                ServiceWarningDialog(
                    title = R.string.dt2s_admin_hint_title,
                    description = R.string.dt2s_admin_hint,
                    settingsIntent = intent,
                    sheetState = state
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

@ExperimentalMaterialApi
@Composable
fun ServiceWarningDialog(
    title: Int,
    description: Int,
    settingsIntent: Intent,
    sheetState: BottomSheetState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertBottomSheetContent(
        title = { Text(text = stringResource(id = title)) },
        text = { Text(text = stringResource(id = description)) },
        buttons = {
            OutlinedButton(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    scope.launch { sheetState.hide() }
                }
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(16.dp))
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    context.startActivity(settingsIntent)
                    scope.launch { sheetState.hide() }
                }
            ) {
                Text(text = stringResource(id = R.string.dt2s_warning_open_settings))
            }
        }
    )
}

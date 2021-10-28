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

package app.lawnchair.ui.preferences.components

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.util.lifecycleState
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.settings.SettingsActivity.EXTRA_FRAGMENT_ARG_KEY
import com.android.launcher3.settings.SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGS
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import kotlinx.coroutines.launch

@Composable
@ExperimentalMaterialApi
fun NotificationDotsPreference() {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()

    val enabled = notificationDotsEnabled()
    val serviceEnabled = notificationServiceEnabled()

    val showWarning = enabled && !serviceEnabled
    val summary = when {
        showWarning -> R.string.missing_notification_access_description
        enabled -> R.string.notification_dots_desc_on
        else -> R.string.notification_dots_desc_off
    }

    NotificationAccessConfirmation(
        sheetState = sheetState,
        onDismissRequest = {
            coroutineScope.launch {
                sheetState.hide()
            }
        }
    )

    PreferenceTemplate(
        title = { Text(text = stringResource(id = R.string.notification_dots)) },
        description = { Text(text = stringResource(id = summary)) },
        endWidget = if (showWarning) { {
            Icon(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp),
                painter = painterResource(id = R.drawable.ic_warning),
                contentDescription = "",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = ContentAlpha.medium)
            )
        } } else null,
        modifier = Modifier
            .clickable {
                if (showWarning) {
                    coroutineScope.launch {
                        sheetState.show()
                    }
                } else {
                    val extras = Bundle().apply {
                        putString(EXTRA_FRAGMENT_ARG_KEY, "notification_badging")
                    }
                    val intent = Intent("android.settings.NOTIFICATION_SETTINGS")
                        .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, extras)
                    context.startActivity(intent)
                }
            }
    )
}

@Composable
@ExperimentalMaterialApi
fun NotificationAccessConfirmation(
    sheetState: BottomSheetState,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    BottomSheet(sheetState = sheetState) {
        AlertBottomSheetContent(
            title = { Text(text = stringResource(id = R.string.missing_notification_access_label)) },
            text = {
                val appName = stringResource(id = R.string.derived_app_name)
                Text(text = stringResource(id = R.string.msg_missing_notification_access, appName))
            },
            buttons = {
                OutlinedButton(
                    onClick = onDismissRequest
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
                Spacer(modifier = Modifier.requiredWidth(16.dp))
                Button(
                    onClick = {
                        onDismissRequest()

                        val cn = ComponentName(context, NotificationListener::class.java)
                        val showFragmentArgs = Bundle()
                        showFragmentArgs.putString(
                            EXTRA_FRAGMENT_ARG_KEY,
                            cn.flattenToString()
                        )

                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(EXTRA_FRAGMENT_ARG_KEY, cn.flattenToString())
                            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, showFragmentArgs)
                        context.startActivity(intent)
                    }
                ) {
                    Text(text = stringResource(id = R.string.title_change_settings))
                }
            }
        )
    }
}

@Composable
fun notificationDotsEnabled(): Boolean {
    val context = LocalContext.current
    val enabledState = remember { mutableStateOf(false) }
    val observer = remember {
        SettingsCache.OnChangeListener {
            enabledState.value = SettingsCache.INSTANCE.get(context).getValue(NOTIFICATION_BADGING_URI)
        }
    }

    DisposableEffect(null) {
        val settingsCache = SettingsCache.INSTANCE.get(context)
        observer.onSettingsChanged(false)
        settingsCache.register(NOTIFICATION_BADGING_URI, observer)
        onDispose { settingsCache.unregister(NOTIFICATION_BADGING_URI, observer) }
    }

    return enabledState.value
}

@Composable
fun notificationServiceEnabled(): Boolean {
    val context = LocalContext.current

    fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val myListener = ComponentName(context, NotificationListener::class.java)
        return enabledListeners != null &&
                (enabledListeners.contains(myListener.flattenToString()) ||
                        enabledListeners.contains(myListener.flattenToShortString()))
    }

    val enabledState = remember { mutableStateOf(isNotificationServiceEnabled()) }
    val resumed = lifecycleState().isAtLeast(Lifecycle.State.RESUMED)

    if (resumed) {
        DisposableEffect(null) {
            enabledState.value = isNotificationServiceEnabled()
            onDispose { }
        }
    }

    return enabledState.value
}

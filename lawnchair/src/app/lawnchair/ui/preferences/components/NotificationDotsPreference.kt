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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.util.bottomSheetHandler
import app.lawnchair.util.lifecycleState
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.settings.SettingsActivity.EXTRA_FRAGMENT_ARG_KEY
import com.android.launcher3.settings.SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGS
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

@Composable
fun NotificationDotsPreference(enabled: Boolean, serviceEnabled: Boolean) {
    val bottomSheetHandler = bottomSheetHandler
    val context = LocalContext.current
    val showWarning = enabled && !serviceEnabled
    val summary = when {
        showWarning -> R.string.missing_notification_access_description
        enabled -> R.string.notification_dots_desc_on
        else -> R.string.notification_dots_desc_off
    }

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
                    bottomSheetHandler.show {
                        NotificationAccessConfirmation {
                            bottomSheetHandler.hide()
                        }
                    }
                } else {
                    val extras = bundleOf(EXTRA_FRAGMENT_ARG_KEY to "notification_badging")
                    val intent = Intent("android.settings.NOTIFICATION_SETTINGS")
                        .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, extras)
                    context.startActivity(intent)
                }
            }
    )
}

@Composable
fun NotificationAccessConfirmation(onDismissRequest: () -> Unit) {
    val context = LocalContext.current

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
            Spacer(modifier = Modifier.requiredWidth(8.dp))
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

fun notificationDotsEnabled(context: Context) = callbackFlow {
    val observer = SettingsCache.OnChangeListener {
        val enabled = SettingsCache.INSTANCE.get(context).getValue(NOTIFICATION_BADGING_URI)
        trySend(enabled)
    }
    val settingsCache = SettingsCache.INSTANCE.get(context)
    observer.onSettingsChanged(false)
    settingsCache.register(NOTIFICATION_BADGING_URI, observer)
    awaitClose { settingsCache.unregister(NOTIFICATION_BADGING_URI, observer) }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    val myListener = ComponentName(context, NotificationListener::class.java)
    return enabledListeners != null &&
            (enabledListeners.contains(myListener.flattenToString()) ||
                    enabledListeners.contains(myListener.flattenToShortString()))
}

@Composable
fun notificationServiceEnabled(): Boolean {
    val context = LocalContext.current

    val enabledState = remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    val resumed = lifecycleState().isAtLeast(Lifecycle.State.RESUMED)

    if (resumed) {
        DisposableEffect(null) {
            enabledState.value = isNotificationServiceEnabled(context)
            onDispose { }
        }
    }

    return enabledState.value
}

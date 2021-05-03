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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS as actionNotificationListenerSettings

@Composable
fun NotificationDotsPreference() {
    val context = LocalContext.current
    val extraFragmentArgKey = ":settings:fragment_args_key"
    val extraShowFragmentArgs = ":settings:show_fragment_args"
    val interactor = LocalPreferenceInteractor.current

    fun onClick() {
        val intent = if (interactor.notificationDotsEnabled.value) {
            Intent("android.settings.NOTIFICATION_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(extraFragmentArgKey, "notification_badging")
        } else {
            val cn = ComponentName(context, NotificationListener::class.java)
            val showFragmentArgs = bundleOf(extraFragmentArgKey to cn.flattenToString())
            Intent(actionNotificationListenerSettings)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(extraFragmentArgKey, cn.flattenToString())
                .putExtra(extraShowFragmentArgs, showFragmentArgs)
        }
        context.startActivity(intent)
    }

    PreferenceTemplate(height = 52.dp) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.notification_dots),
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}

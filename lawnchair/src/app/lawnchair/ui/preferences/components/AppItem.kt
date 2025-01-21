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

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.placeholder.PlaceholderHighlight
import app.lawnchair.ui.placeholder.fade
import app.lawnchair.ui.placeholder.placeholder
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.util.App
import com.android.launcher3.model.data.AppInfo

@Composable
fun AppItem(
    app: App,
    onClick: (app: App) -> Unit,
    widget: (@Composable () -> Unit)? = null,
) {
    AppItem(
        label = app.label,
        icon = app.icon,
        onClick = { onClick(app) },
        widget = widget,
    )
}

@Composable
fun AppItem(
    appInfo: AppInfo,
    onClick: (appInfo: AppInfo) -> Unit,
    widget: (@Composable () -> Unit)? = null,
) {
    AppItem(
        label = appInfo.title.toString(),
        icon = appInfo.bitmap.icon,
        onClick = { onClick(appInfo) },
        widget = widget,
    )
}

@Composable
fun AppItem(
    label: String,
    icon: Bitmap,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    widget: (@Composable () -> Unit)? = null,
) {
    AppItemLayout(
        modifier = modifier
            .clickable(onClick = onClick),
        widget = widget,
        icon = {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        },
        title = { Text(text = label) },
    )
}

@Composable
fun AppItemPlaceholder(
    modifier: Modifier = Modifier,
    widget: (@Composable () -> Unit)? = null,
) {
    AppItemLayout(
        icon = {
            Spacer(
                modifier = Modifier
                    .size(30.dp)
                    .placeholder(
                        visible = true,
                        highlight = PlaceholderHighlight.fade(),
                    ),
            )
        },
        title = {
            Spacer(
                modifier = Modifier
                    .width(120.dp)
                    .height(24.dp)
                    .placeholder(
                        visible = true,
                        highlight = PlaceholderHighlight.fade(),
                    ),
            )
        },
        widget = widget,
        modifier = modifier,
    )
}

@Composable
private fun AppItemLayout(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    widget: (@Composable () -> Unit)? = null,
) {
    PreferenceTemplate(
        title = title,
        modifier = modifier,
        startWidget = {
            widget?.let {
                it()
                Spacer(modifier = Modifier.requiredWidth(16.dp))
            }
            icon()
        },
        verticalPadding = 12.dp,
    )
}

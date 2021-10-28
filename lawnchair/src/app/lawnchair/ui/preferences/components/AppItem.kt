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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lawnchair.util.App
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder

@Composable
fun AppItem(
    app: App,
    onClick: (app: App) -> Unit,
    showDivider: Boolean = false,
    widgetSize: Dp = 0.dp,
    widget: (@Composable () -> Unit)? = null,
) {
    AppItem(
        label = app.label,
        icon = app.icon,
        onClick = { onClick(app) },
        showDivider = showDivider,
        widgetSize = widgetSize,
        widget = widget,
    )
}

@Composable
fun AppItem(
    label: String,
    icon: Bitmap,
    onClick: () -> Unit,
    showDivider: Boolean = false,
    widgetSize: Dp = 0.dp,
    widget: (@Composable () -> Unit)? = null,
) {
    AppItemLayout(
        modifier = Modifier
            .clickable(onClick = onClick),
        showDivider = showDivider,
        widgetSize = widgetSize,
        widget = widget,
        icon = {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        },
        title = { Text(text = label) }
    )
}

@Composable
fun AppItemPlaceholder(
    showDivider: Boolean = false,
    widgetSize: Dp = 0.dp,
    widget: (@Composable () -> Unit)? = null,
) {
    AppItemLayout(
        showDivider = showDivider,
        widgetSize = widgetSize,
        widget = widget,
        icon = {
            Spacer(
                modifier = Modifier
                    .size(30.dp)
                    .placeholder(
                        visible = true,
                        highlight = PlaceholderHighlight.fade(),
                    )
            )
        }
    ) {
        Spacer(
            modifier = Modifier
                .width(120.dp)
                .height(24.dp)
                .placeholder(
                    visible = true,
                    highlight = PlaceholderHighlight.fade(),
                )
        )
    }
}

@Composable
private fun AppItemLayout(
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    widgetSize: Dp = 0.dp,
    widget: (@Composable () -> Unit)? = null,
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
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
        showDivider = showDivider,
        dividerIndent = if (widgetSize != 0.dp) widgetSize + 16.dp else 0.dp,
        verticalPadding = 12.dp
    )
}

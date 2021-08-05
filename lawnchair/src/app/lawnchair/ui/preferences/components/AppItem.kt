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
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.lawnchair.util.App
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder

@Composable
fun AppItem(
    app: App,
    onClick: (app: App) -> Unit,
    showDivider: Boolean = true,
    widget: (@Composable RowScope.() -> Unit)?,
) {
    AppItem(
        label = app.label,
        icon = app.icon,
        onClick = { onClick(app) },
        showDivider = showDivider,
        widget = widget
    )
}

@Composable
fun AppItem(
    label: String,
    icon: Bitmap,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    widget: (@Composable RowScope.() -> Unit)? = null,
) {
    AppItemLayout(
        modifier = Modifier
            .clickable(onClick = onClick),
        showDivider = showDivider,
        widget = widget
    ) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = label,
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onBackground
        )
    }
}

@Composable
fun AppItemPlaceholder(
    showDivider: Boolean = true,
    widget: (@Composable RowScope.() -> Unit)? = null,
) {
    AppItemLayout(
        showDivider = showDivider,
        widget = widget
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .placeholder(
                    visible = true,
                    highlight = PlaceholderHighlight.fade(),
                )
        )
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(24.dp)
                .padding(start = 16.dp)
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
    showDivider: Boolean = true,
    widget: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(52.dp)
            .padding(horizontal = 16.dp)
    ) {
        widget?.let {
            it()
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1F),
                content = content
            )
            if (showDivider) Divider()
        }
    }
}

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.lawnchair.util.App

@Composable
fun AppItem(
    app: App,
    onClick: (app: App) -> Unit,
    showDivider: Boolean = true,
    content: (@Composable RowScope.() -> Unit)?,
) {
    AppItem(
        label = app.info.label.toString(),
        icon = app.icon,
        onClick = { onClick(app) },
        showDivider,
        content
    )
}

@Composable
fun AppItem(
    label: String,
    icon: Bitmap,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    content: (@Composable RowScope.() -> Unit)?,
) {
    PreferenceTemplate(
        height = 52.dp,
        showDivider = showDivider
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() }
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Image(
                icon.asImageBitmap(),
                null,
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = label,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            content?.invoke(this)
        }
    }
}

@ExperimentalAnimationApi
@Composable
fun AnimatedCheck(
    visible: Boolean,
    colorFilter: ColorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Image(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            colorFilter = colorFilter,
        )
    }
}

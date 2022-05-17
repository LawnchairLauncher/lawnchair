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

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun TopBar(
    backArrowVisible: Boolean,
    floating: Boolean,
    label: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val scrollFraction = if (floating) 1f else 0f

    val colors = TopAppBarDefaults.smallTopAppBarColors()
    val appBarContainerColor by colors.containerColor(scrollFraction)
    val navigationIconContentColor = colors.navigationIconContentColor(scrollFraction)
    val titleContentColor = colors.titleContentColor(scrollFraction)
    val actionIconContentColor = colors.actionIconContentColor(scrollFraction)

    Surface(color = appBarContainerColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(topBarSize)
                .padding(horizontal = 4.dp),
        ) {
            if (backArrowVisible) {
                CompositionLocalProvider(LocalContentColor provides navigationIconContentColor.value) {
                    ClickableIcon(
                        imageVector = backIcon(),
                        onClick = { backDispatcher?.onBackPressed() },
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = titleContentColor.value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = if (backArrowVisible) 4.dp else 12.dp)
                    .weight(weight = 1f),
            )
            CompositionLocalProvider(LocalContentColor provides actionIconContentColor.value) {
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

@Composable
fun backIcon(): ImageVector =
    if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        Icons.Rounded.ArrowBack
    } else {
        Icons.Rounded.ArrowForward
    }

val topBarSize = 64.dp

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

package app.lawnchair.ui.preferences.components.layout

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBar(
    backArrowVisible: Boolean,
    label: String,
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val buttonPadding = 4.dp

    if (isExpandedScreen) {
        TopAppBar(
            modifier = modifier,
            title = {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                Row(modifier = Modifier.padding(horizontal = buttonPadding)) {
                    actions()
                }
            },
            navigationIcon = {
                if (backArrowVisible) {
                    Box(modifier = Modifier.padding(buttonPadding)) {
                        ClickableIcon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            onClick = { backDispatcher?.onBackPressed() },
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors().copy(
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
    } else {
        LargeTopAppBar(
            modifier = modifier,
            title = {
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium,
                )
            },
            actions = {
                Row(modifier = Modifier.padding(horizontal = buttonPadding)) {
                    actions()
                }
            },
            navigationIcon = {
                if (backArrowVisible) {
                    Box(modifier = Modifier.padding(horizontal = buttonPadding)) {
                        ClickableIcon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            onClick = { backDispatcher?.onBackPressed() },
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors().copy(
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

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

import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    backArrowVisible: Boolean,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    label: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val containerColor: Color = MaterialTheme.colorScheme.surface
    val scrolledContainerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.0.dp)

    fun containerColor(colorTransitionFraction: Float): Color {
        return lerp(
            containerColor,
            scrolledContainerColor,
            FastOutLinearInEasing.transform(colorTransitionFraction)
        )
    }

    val backgroundColor = containerColor(scrollBehavior?.state?.overlappedFraction ?: 0f)

    val foregroundColors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent
    )

    Surface (
        color = backgroundColor
    ) {
        LargeTopAppBar(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            title = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = actions,
            navigationIcon = {
                if (backArrowVisible) {
                        ClickableIcon(
                            imageVector = backIcon(),
                            onClick = { backDispatcher?.onBackPressed() },
                        )
                } else {
                    null
                }
            },
            scrollBehavior = scrollBehavior,
            colors = foregroundColors
        )
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

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

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
@ExperimentalAnimationApi
fun PreferenceLayout(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState = rememberScrollState(),
    label: String,
    actions: @Composable RowScope.() -> Unit = {},
    backArrowVisible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    PreferenceScaffold(
        backArrowVisible = backArrowVisible,
        floating = rememberFloatingState(scrollState),
        label = label,
        actions = actions,
    ) {
        PreferenceColumn(
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            scrollState = scrollState,
            content = content
        )
    }
}

@Composable
@ExperimentalAnimationApi
fun PreferenceLayoutLazyColumn(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    label: String,
    actions: @Composable RowScope.() -> Unit = {},
    backArrowVisible: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    PreferenceScaffold(
        backArrowVisible = backArrowVisible,
        floating = rememberFloatingState(state),
        label = label,
        actions = actions,
    ) {
        PreferenceLazyColumn(
            modifier = modifier,
            enabled = enabled,
            state = state,
            content = content
        )
    }
}

@Composable
fun rememberFloatingState(state: ScrollState) =
    remember(state) {
        derivedStateOf { state.value > 0 }
    }

@Composable
fun rememberFloatingState(state: LazyListState) =
    remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }

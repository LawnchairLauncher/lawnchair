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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.LocalIsExpandedScreen

/**
 * Represents the layout of all Preference screens.
 * Uses a combination of [PreferenceScaffold] and [PreferenceColumn] to represent the layout.
 *
 * @param label the text to be displayed at the top of the screen
 * @param backArrowVisible whether to show the back arrow or not
 * @param verticalArrangement the vertical arrangement of the layout's children
 * @param horizontalAlignment the horizontal alignment of the layout's children
 * @param scrollState the [ScrollState] to use to allow vertical overflow
 * @param actions what content to show at the top-right of the layout
 * @param bottomBar what content to show at the bottom of the layout
 * @param content the actual content
 * @see [PreferenceLayoutLazyColumn]
 *
 * TODO: use DSL to represent all preferences
 */
@Composable
fun PreferenceLayout(
    label: String,
    modifier: Modifier = Modifier,
    backArrowVisible: Boolean = true,
    isExpandedScreen: Boolean = LocalIsExpandedScreen.current,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState? = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = { BottomSpacer() },
    content: @Composable ColumnScope.() -> Unit,
) {
    PreferenceScaffold(
        modifier = modifier,
        backArrowVisible = backArrowVisible,
        label = label,
        isExpandedScreen = isExpandedScreen,
        actions = actions,
        bottomBar = bottomBar,
    ) {
        PreferenceColumn(
            contentPadding = it,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            scrollState = scrollState,
            content = content,
        )
    }
}

/**
 * Represents the layout of all Preference screens.
 * This composable only composes and lays out the currently visible items.
 * Uses a combination of [PreferenceScaffold] and [PreferenceLazyColumn] to represent the layout.
 *
 * @param label the text to be displayed at the top of the screen
 * @param modifier the [Modifier] to apply at [PreferenceLazyColumn]
 * @param enabled whether the layout allows user input or not
 * @param backArrowVisible whether to show the back arrow or not
 * @param state the state object to be used to control or observe the list's state
 * @param actions what content to show at the top-right of the layout
 * @param content the actual content
 * @see [PreferenceLayout]
 *
 * TODO: use DSL to represent all preferences
 */
@Composable
fun PreferenceLayoutLazyColumn(
    label: String,
    modifier: Modifier = Modifier,
    isExpandedScreen: Boolean = LocalIsExpandedScreen.current,
    enabled: Boolean = true,
    backArrowVisible: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    PreferenceScaffold(
        backArrowVisible = backArrowVisible,
        label = label,
        isExpandedScreen = isExpandedScreen,
        actions = actions,
    ) {
        PreferenceLazyColumn(
            contentPadding = it,
            modifier = modifier,
            enabled = enabled,
            state = state,
            content = content,
        )
    }
}

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
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import kotlinx.coroutines.awaitCancellation

@Composable
@ExperimentalAnimationApi
fun PreferenceLayout(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    label: String,
    backArrowVisible: Boolean = true,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    NestedScrollStretch {
        Column(
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(preferenceLayoutPadding())
        ) {
            content()
        }
    }
    TopBar(
        backArrowVisible = backArrowVisible,
        floating = scrollState.value > 0,
        label = label
    )
}

@Composable
@ExperimentalAnimationApi
fun PreferenceLayoutLazyColumn(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    label: String,
    backArrowVisible: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    if (!enabled) {
        LaunchedEffect(key1 = null) {
            state.scroll(scrollPriority = MutatePriority.PreventUserInput) {
                awaitCancellation()
            }
        }
    }
    NestedScrollStretch {
        LazyColumn(
            modifier = modifier.fillMaxHeight(),
            contentPadding = preferenceLayoutPadding(),
            state = state
        ) {
            content()
        }
    }
    TopBar(
        backArrowVisible = backArrowVisible,
        floating = state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0,
        label = label
    )
}

@Composable
fun preferenceLayoutPadding() = rememberInsetsPaddingValues(
    insets = LocalWindowInsets.current.systemBars,
    additionalTop = topBarSize,
    additionalBottom = 16.dp
)

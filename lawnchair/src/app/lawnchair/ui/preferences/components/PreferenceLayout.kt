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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.util.Meta
import app.lawnchair.util.pageMeta
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.toPaddingValues

@Composable
fun PreferenceLayout(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    ProvideTopBarFloatingState(scrollState.value > 0)
    NestedScrollSpring {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(preferenceLayoutPadding()),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment
        ) {
            content()
        }
    }
}

@Composable
fun PreferenceLayoutLazyColumn(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val scrollState = rememberLazyListState()
    ProvideTopBarFloatingState(scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 0)
    NestedScrollSpring {
        LazyColumn(
            modifier = modifier.fillMaxHeight(),
            contentPadding = preferenceLayoutPadding(),
            state = scrollState
        ) {
            content()
        }
    }
}

@Composable
fun preferenceLayoutPadding() = rememberInsetsPaddingValues(
    insets = LocalWindowInsets.current.systemBars,
    additionalTop = topBarSize,
    additionalBottom = 16.dp
)

@Composable
private fun ProvideTopBarFloatingState(scrolled: Boolean) {
    val meta = remember(scrolled) { Meta(topBarFloating = scrolled) }
    pageMeta.provide(meta)
}

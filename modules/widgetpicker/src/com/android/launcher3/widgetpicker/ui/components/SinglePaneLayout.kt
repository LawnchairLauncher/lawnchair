/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.widgetpicker.ui.components

/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.ui.components.SinglePaneLayoutDimensions.searchBarBottomMargin

/**
 * A layout that shows all the widget picker content in a column like pane.
 *
 * @param searchBar A sticky search bar shown on top in the left pane.
 * @param content the primary content e.g. widgets expand collapse list.
 * @param bottomFloatingContent an option toolbar that floats at the bottom of the screen and
 * allows users to choose what to see in the [content]
 */
@Composable
fun SinglePaneLayout(
    searchBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    bottomFloatingContent: (@Composable () -> Unit)? = null,
) {
    val topContent: @Composable BoxScope.() -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            searchBar()
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(searchBarBottomMargin)
            )
            content()
        }
    }

    val floatingContent: @Composable BoxScope.() -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            contentAlignment = Alignment.Center
        ) { bottomFloatingContent?.let { it() } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        topContent()
        floatingContent()
    }
}

private object SinglePaneLayoutDimensions {
    val searchBarBottomMargin = 16.dp
}

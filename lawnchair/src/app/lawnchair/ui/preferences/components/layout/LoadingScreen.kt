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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Creates a simple loading animation with [Crossfade] and [ContainedLoadingIndicator].
 * @param isLoading Defines whether the content is still loading or not
 * @param content Content to appear or disappear based on the value of [isLoading]
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingScreen(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Crossfade(
        targetState = isLoading,
        label = "",
        modifier = modifier,
    ) {
        if (it) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(128.dp),
                )
            }
        } else {
            content()
        }
    }
}

/**
 * Creates a simple loading animation with [Crossfade] and [ContainedLoadingIndicator]. [obj] will be passed as a parameter of [content].
 * @param obj A key representing the content object
 * @param content Content to appear or disappear based on the value of [obj].
 */
@Composable
fun <T> LoadingScreen(
    obj: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    LoadingScreen(
        isLoading = obj == null,
        modifier = modifier,
    ) {
        content(obj!!)
    }
}

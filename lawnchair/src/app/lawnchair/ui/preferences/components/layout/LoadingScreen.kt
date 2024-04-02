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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Creates a simple loading animation with [Crossfade] and [CircularProgressIndicator].
 * @param isLoading Defines whether the content is still loading or not
 * @param content Content to appear or disappear based on the value of [isLoading]
 */
@Composable
fun LoadingScreen(
    isLoading: Boolean,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
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
                CircularProgressIndicator()
            }
        } else {
            content()
        }
    }
}

/**
 * Creates a simple loading animation with [Crossfade] and [CircularProgressIndicator]. [obj] will be passed as a parameter of [content].
 * @param obj A key representing the content object
 * @param content Content to appear or disappear based on the value of [obj].
 */
@Composable
fun <T> LoadingScreen(
    obj: T?,
    content: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LoadingScreen(
        isLoading = obj == null,
        content = {
            content(obj!!)
        },
        modifier = modifier,
    )
}

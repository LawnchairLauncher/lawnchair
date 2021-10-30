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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lawnchair.util.smartBorder
import androidx.compose.material3.MaterialTheme

fun LazyListScope.preferenceGroupItems(
    count: Int,
    heading: String? = null,
    isFirstChild: Boolean,
    key: ((index: Int) -> Any)? = null,
    showDividers: Boolean = true,
    dividerStartIndent: Dp = 0.dp,
    dividerEndIndent: Dp = 0.dp,
    itemContent: @Composable LazyItemScope.(index: Int) -> Unit
) {
    val actualStartIndent = 16.dp + dividerStartIndent
    val actualEndIndent = 16.dp + dividerEndIndent
    item { PreferenceGroupHeading(heading, isFirstChild) }
    items(count, key) {
        PreferenceGroupItem(cutTop = it > 0, cutBottom = it < count - 1) {
            if (showDividers && it > 0) {
                Divider(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f),
                    modifier = Modifier.padding(
                        start = actualStartIndent,
                        end = actualEndIndent
                    )
                )
            }
            itemContent(it)
        }
    }
}

inline fun <T> LazyListScope.preferenceGroupItems(
    items: List<T>,
    heading: String? = null,
    isFirstChild: Boolean,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    showDividers: Boolean = true,
    dividerStartIndent: Dp = 0.dp,
    dividerEndIndent: Dp = 0.dp,
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit
) {
    preferenceGroupItems(
        items.size,
        heading,
        isFirstChild,
        if (key != null) { index: Int -> key(index, items[index]) } else null,
        showDividers = showDividers,
        dividerStartIndent = dividerStartIndent,
        dividerEndIndent = dividerEndIndent,
    ) {
        itemContent(it, items[it])
    }
}

@Composable
fun PreferenceGroupItem(
    cutTop: Boolean,
    cutBottom: Boolean,
    content: @Composable () -> Unit
) {
    val shape = remember(cutTop, cutBottom) {
        val top = if (cutTop) 0.dp else 12.dp
        val bottom = if (cutBottom) 0.dp else 12.dp
        RoundedCornerShape(top, top, bottom, bottom)
    }
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp)
            .smartBorder(
                1.dp,
                brush = SolidColor(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f)),
                shape,
                cutTop,
                cutBottom,
            )
            .clip(shape)
    ) {
        content()
    }
}

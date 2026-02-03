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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.preferenceGroupColor

/**
 * PreferenceGroup with scope-based content for position-aware items.
 * Items added via scope automatically get isFirst/isLast tracking
 */
@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    heading: String? = null,
    description: String? = null,
    showDescription: Boolean = true,
    itemSpacing: Dp = 4.dp,
    content: @Composable PreferenceGroupScope.() -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        PreferenceGroupHeading(heading)

        // Rebuild the list on every composition to handle structure changes
        val items = remember { mutableStateListOf<PreferenceGroupItemData>() }
        items.clear()
        val scope = PreferenceGroupScopeImpl(items)
        content(scope)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            val laidOutItems = items.filter { it.shouldBeLaidOut }
            val count = laidOutItems.size

            laidOutItems.forEachIndexed { index, itemData ->
                androidx.compose.runtime.key(itemData.key ?: index) {
                    val position = PreferenceGroupItemPosition(
                        isFirst = index == 0,
                        isLast = index == count - 1,
                    )

                    AnimatedVisibility(
                        visibleState = itemData.visibleState,
                        enter = itemData.enter,
                        exit = itemData.exit,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = preferenceGroupItemShape(position),
                            color = preferenceGroupColor(),
                            content = { itemData.content(this, position) },
                        )
                    }
                }
            }
        }
        PreferenceGroupDescription(description = description, showDescription = showDescription)
    }
}

interface PreferenceGroupScope {
    @Composable
    fun Item(
        key: Any? = null,
        visible: Boolean = true,
        enter: EnterTransition = expandVertically() + fadeIn(),
        exit: ExitTransition = shrinkVertically() + fadeOut(),
        content: @Composable AnimatedVisibilityScope.(position: PreferenceGroupItemPosition) -> Unit,
    )
}

private data class PreferenceGroupItemData(
    val key: Any?,
    val visibleState: MutableTransitionState<Boolean>,
    val enter: EnterTransition,
    val exit: ExitTransition,
    val content: @Composable AnimatedVisibilityScope.(position: PreferenceGroupItemPosition) -> Unit,
) {
    val shouldBeLaidOut: Boolean
        get() = visibleState.currentState || visibleState.targetState
}

private class PreferenceGroupScopeImpl(
    private val items: SnapshotStateList<PreferenceGroupItemData>,
) : PreferenceGroupScope {

    @Composable
    override fun Item(
        key: Any?,
        visible: Boolean,
        enter: EnterTransition,
        exit: ExitTransition,
        content: @Composable AnimatedVisibilityScope.(position: PreferenceGroupItemPosition) -> Unit,
    ) {
        val visibleState = remember { MutableTransitionState(visible) }
        visibleState.targetState = visible

        items.add(
            PreferenceGroupItemData(
                key = key,
                visibleState = visibleState,
                enter = enter,
                exit = exit,
                content = content,
            ),
        )
    }
}

data class PreferenceGroupItemPosition(
    val isFirst: Boolean = false,
    val isLast: Boolean = false,
)

fun preferenceGroupItemShape(
    position: PreferenceGroupItemPosition,
    largeCorner: Dp = 24.dp,
    smallCorner: Dp = 4.dp,
): Shape {
    val topCorner = if (position.isFirst) largeCorner else smallCorner
    val bottomCorner = if (position.isLast) largeCorner else smallCorner
    return RoundedCornerShape(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )
}

@Composable
fun PreferenceGroupHeading(
    heading: String?,
    modifier: Modifier = Modifier,
) {
    if (heading != null) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier
                .height(48.dp)
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { this.heading() },
            )
        }
    } else {
        Spacer(modifier = modifier.requiredHeight(8.dp))
    }
}

@Composable
fun PreferenceGroupDescription(
    modifier: Modifier = Modifier,
    description: String? = null,
    showDescription: Boolean = true,
) {
    description?.let {
        ExpandAndShrink(
            modifier = modifier,
            visible = showDescription,
        ) {
            Row(modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp)) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

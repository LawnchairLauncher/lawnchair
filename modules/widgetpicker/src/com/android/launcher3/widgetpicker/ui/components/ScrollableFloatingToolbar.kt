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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A horizontal toolbar that displays the provided [tabs] in a scrollable row.
 * - When the tab content is longer, the selected tab is auto-scrolled into the visible area.
 * - When the tab content is less than the [maxWidth], the toolbar wraps the contents.
 *
 * This is a mixed variant of the androidx compose material3 library's FloatingToolbar and
 * PrimaryScrollableTabRow components. Visually it looks like the FloatingToolbar and in additional
 * supports longer tab names by enabling scrolling behavior similar to a PrimaryScrollableTabRow.
 * - compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/FloatingToolbar.kt
 * - compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/TabRow.kt
 *
 * This design is suitable for 2-3 tabs.
 *
 * @param tabs A list of tabs (typically [LeadingIconToolbarTab]) that can be arranged in a
 *   scrollable row.
 * @param selectedTabIndex the tab that is currently selected; this enables bringing the tab into
 *   the visible area.
 * @param modifier additional modifications to be applied to the top level of the toolbar.
 * @param shape shape to clip the contents of the toolbar; defaults to fully rounded corners.
 * @param edgePadding padding applied horizontally on sides of the toolbar; in case of long length
 *   tabs, visually the padding will appear only on left (in LTR for instance) and when you scroll
 *   completely to right, the padding will appear on right; in case tab content is smaller in
 *   length, the padding appears on both sides.
 * @param maxWidth if the toolbar needs to be constraint to a specific width.
 * @param minTabWidth minimum width to be guaranteed for individual tabs
 * @param shadowElevation The size of the shadow below the surface.
 */
@Composable
fun ScrollableFloatingToolbar(
    tabs: List<@Composable () -> Unit>,
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    shape: Shape = ScrollableFloatingToolbarDefaults.shape,
    edgePadding: Dp = ScrollableFloatingToolbarDefaults.edgePadding,
    maxWidth: Dp = ScrollableFloatingToolbarDefaults.maxWidth,
    minTabWidth: Dp = ScrollableFloatingToolbarDefaults.minTabWidth,
    shadowElevation: Dp = ScrollableFloatingToolbarDefaults.shadowElevation,
) {
    check(tabs.size in 2..3) { "Unexpected number of tabs: ${tabs.size}. Suitable for 2-3 tabs." }

    val scrollState = rememberScrollState()

    Surface(
        color = WidgetPickerTheme.colors.toolbarBackground,
        shadowElevation = shadowElevation,
        shape = shape,
        modifier = modifier.wrapContentSize(align = Alignment.Center),
    ) {
        ScrollableTabsLayout(
            tabs = tabs,
            scrollState = scrollState,
            maxWidth = maxWidth,
            edgePadding = edgePadding,
            minTabWidth = minTabWidth,
            selectedTabIndex = selectedTabIndex,
        )
    }
}

@Composable
private fun ScrollableTabsLayout(
    tabs: List<@Composable () -> Unit>,
    scrollState: ScrollState,
    maxWidth: Dp,
    edgePadding: Dp,
    minTabWidth: Dp,
    selectedTabIndex: Int,
) {
    val coroutineScope = rememberCoroutineScope()
    val tabsScrollState =
        remember(scrollState, coroutineScope) {
            TabsScrollState(scrollState = scrollState, coroutineScope = coroutineScope)
        }

    val tabsContent: @Composable () -> Unit = { tabs.forEach { it() } }
    Layout(
        contents = listOf(tabsContent),
        modifier =
            Modifier.widthIn(max = maxWidth) // constraint the layout itself to specific max.
                .wrapContentSize(align = Alignment.Center)
                .padding(vertical = ScrollableFloatingToolbarDefaults.verticalPadding)
                .horizontalScroll(scrollState)
                .selectableGroup()
                .clipToBounds(),
    ) { (tabMeasurables), constraints ->
        val edgePaddingPx = edgePadding.roundToPx()

        val minTabWidthPx = minTabWidth.roundToPx()
        val layoutHeightPx = tabMeasurables.maxIntrinsicHeight()

        val tabConstraints =
            constraints.copy(
                minWidth = minTabWidthPx,
                minHeight = layoutHeightPx,
                maxHeight = layoutHeightPx,
            )
        val tabPlaceables = tabMeasurables.fastMap { it.measure(tabConstraints) }

        // Start with the left padding and add up the width of each tab.
        // Also keep track of the position of each tab.
        val (accumulatedWidthPx: Int, tabPositions: List<TabPosition>) =
            tabPlaceables.fold(initial = Pair(edgePaddingPx, mutableListOf<TabPosition>())) {
                (accumulatedWidthPx, accumulatedPositions),
                placeable ->
                val tabWidthPx = maxOf(minTabWidthPx, placeable.width)
                accumulatedPositions.add(
                    TabPosition(leftPx = accumulatedWidthPx, widthPx = tabWidthPx)
                )

                Pair(accumulatedWidthPx + tabWidthPx, accumulatedPositions)
            }
        // add right padding
        val totalLayoutWidthPx = accumulatedWidthPx + edgePaddingPx

        layout(totalLayoutWidthPx, layoutHeightPx) {
            tabPlaceables.fastForEachIndexed { index, placeable ->
                placeable.placeRelative(x = tabPositions[index].leftPx, y = 0)
            }

            tabsScrollState.scrollToSelectedTab(
                tabPositions = tabPositions,
                selectedTab = selectedTabIndex,
                totalWidthPx = totalLayoutWidthPx,
            )
        }
    }
}

private data class TabPosition(val leftPx: Int, val widthPx: Int)

private class TabsScrollState(
    private val scrollState: ScrollState,
    private val coroutineScope: CoroutineScope,
) {
    private var selectedTab: Int? = null

    fun scrollToSelectedTab(tabPositions: List<TabPosition>, selectedTab: Int, totalWidthPx: Int) {
        if (this.selectedTab != selectedTab) {
            this.selectedTab = selectedTab
            tabPositions.getOrNull(selectedTab)?.let { selectedTabPosition ->
                val targetScrollOffset =
                    offsetRequiredToCenterTheTab(
                        tabPosition = selectedTabPosition,
                        totalToolbarWidth = totalWidthPx,
                        visibleRightOffset = scrollState.maxValue,
                    )

                if (scrollState.value != targetScrollOffset) {
                    coroutineScope.launch { scrollState.animateScrollTo(targetScrollOffset) }
                }
            }
        }
    }

    /**
     * Returns the offset position to scroll to such that the given tab is positioned in center of
     * the visible toolbar width.
     */
    private fun offsetRequiredToCenterTheTab(
        tabPosition: TabPosition,
        totalToolbarWidth: Int,
        visibleRightOffset: Int,
    ): Int {
        val visibleToolbarWidth = totalToolbarWidth - visibleRightOffset

        val currentTabStart = tabPosition.leftPx
        val visibleToolbarCenter = visibleToolbarWidth / 2
        val tabHalfWidth = tabPosition.widthPx / 2
        val centeredTabOffset = currentTabStart - (visibleToolbarCenter - tabHalfWidth)

        return centeredTabOffset.coerceIn(0, visibleRightOffset.coerceAtLeast(0))
    }
}

/**
 * Finds out the smallest height in px beyond which increasing the height never decreases the width
 * of measurable(s) in this list.
 */
private fun List<Measurable>.maxIntrinsicHeight() =
    fastFold(initial = 0) { curr, measurable ->
        maxOf(curr, measurable.maxIntrinsicHeight(Constraints.Infinity))
    }

/** Holds default values used by the [ScrollableFloatingToolbar]. */
object ScrollableFloatingToolbarDefaults {
    val shape = CircleShape

    val verticalPadding = 8.dp
    val edgePadding: Dp = 8.dp
    val maxWidth: Dp = 348.dp
    val minTabWidth: Dp = 90.dp

    val shadowElevation: Dp = 3.dp
}

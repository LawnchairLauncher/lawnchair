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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.shared.model.AppIcon
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.model.DisplayableWidgetApp
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**
 * Displays a various apps on device that host widgets.
 *
 * Apps are displays as a header that user can select OR expand/collapse depending on the
 * [widgetAppHeaderStyle].
 */
@Composable
fun WidgetAppsList(
    widgetApps: List<DisplayableWidgetApp>,
    selectedWidgetAppId: WidgetAppId?,
    widgetAppHeaderStyle: WidgetAppHeaderStyle,
    modifier: Modifier,
    onWidgetAppClick: (DisplayableWidgetApp) -> Unit,
    appIcons: Map<WidgetAppId, WidgetAppIcon>,
    widgetPreviews: Map<WidgetId, WidgetPreview>,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    showDragShadow: Boolean,
    bottomContentSpacing: Dp = 0.dp,
    headerDescriptionStyle: AppHeaderDescriptionStyle = AppHeaderDescriptionStyle.WIDGETS_COUNT,
    emptyWidgetsErrorMessage: String? = null,
) {
    if (widgetApps.isEmpty()) {
        NoWidgetsError(
            modifier = modifier,
            errorMessage =
                emptyWidgetsErrorMessage
                    ?: stringResource(R.string.widgets_list_no_widgets_available),
        )
    } else {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(WidgetAppsListDimensions.itemSpacing),
            modifier = modifier.clip(WidgetAppsListDimensions.largeShape),
        ) {
            items(
                count = widgetApps.size,
                key = { index -> widgetApps[index].id.toString() },
                contentType = { widgetAppHeaderStyle },
            ) { index ->
                val widgetApp = widgetApps[index]
                val selected = widgetApp.id == selectedWidgetAppId

                val title = widgetApp.widgetHeaderTitle()
                val description = widgetApp.widgetHeaderDescription(headerDescriptionStyle)

                val appIconForItem =
                    remember(appIcons) {
                        appIcons[widgetApp.id]
                            ?: WidgetAppIcon(AppIcon.PlaceHolderAppIcon, AppIconBadge.NoBadge)
                    }
                val appIcon: @Composable () -> Unit =
                    remember(appIconForItem) {
                        { WidgetAppIcon(widgetAppIcon = appIconForItem, size = AppIconSize.MEDIUM) }
                    }

                when (widgetAppHeaderStyle) {
                    WidgetAppHeaderStyle.EXPANDABLE -> {
                        ExpandableWidgetAppHeader(
                            isFirst = index == 0,
                            isLast = index == widgetApps.lastIndex,
                            expanded = selected,
                            widgetApp = widgetApp,
                            appIcon = appIcon,
                            title = title,
                            description = description,
                            widgetPreviews = widgetPreviews,
                            onWidgetAppClick = onWidgetAppClick,
                            onWidgetInteraction = onWidgetInteraction,
                            showDragShadow = showDragShadow,
                        )
                    }

                    WidgetAppHeaderStyle.CLICKABLE ->
                        SelectableListHeader(
                            modifier = Modifier.fillMaxWidth(),
                            leadingAppIcon = appIcon,
                            selected = selected,
                            title = title,
                            subTitle = description,
                            shape = WidgetAppsListDimensions.largeShape,
                            onSelect = { onWidgetAppClick(widgetApp) },
                        )
                }
            }

            if (bottomContentSpacing > 0.dp) {
                item(key = SPACER_LIST_ITEM_TYPE, contentType = SPACER_LIST_ITEM_TYPE) {
                    Spacer(modifier.height(bottomContentSpacing))
                }
            }
        }
    }
}

@Composable
private fun NoWidgetsError(modifier: Modifier, errorMessage: String) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = errorMessage,
            style = WidgetPickerTheme.typography.noWidgetsErrorText,
            color = WidgetPickerTheme.colors.noWidgetsErrorText,
        )
    }
}

@Composable
private fun ExpandableWidgetAppHeader(
    isLast: Boolean,
    isFirst: Boolean,
    expanded: Boolean,
    widgetApp: DisplayableWidgetApp,
    appIcon: @Composable () -> Unit,
    title: String,
    description: String,
    widgetPreviews: Map<WidgetId, WidgetPreview>,
    onWidgetAppClick: (DisplayableWidgetApp) -> Unit,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    showDragShadow: Boolean,
) {
    val expandedContent: @Composable () -> Unit =
        remember(widgetApp, widgetPreviews) {
            {
                WidgetsGrid(
                    widgetSizeGroups = widgetApp.widgetSizeGroups,
                    showAllWidgetDetails = true,
                    previews = widgetPreviews,
                    modifier = Modifier.fillMaxWidth(),
                    onWidgetInteraction = onWidgetInteraction,
                    showDragShadow = showDragShadow,
                )
            }
        }

    ExpandableListHeader(
        modifier = Modifier.fillMaxWidth(),
        expanded = expanded,
        leadingAppIcon = appIcon,
        title = title,
        subTitle = description,
        expandedContent = expandedContent,
        onClick = { onWidgetAppClick(widgetApp) },
        shape =
            when {
                isFirst && isLast && !expanded -> WidgetAppsListDimensions.largeShape
                isFirst -> WidgetAppsListDimensions.topLargeShape
                isLast -> WidgetAppsListDimensions.bottomLargeShape
                else -> WidgetAppsListDimensions.smallShape
            },
    )
}

@Composable
private fun DisplayableWidgetApp.widgetHeaderTitle(): String {
    return title?.toString() ?: stringResource(R.string.widgets_list_header_app_name_fallback_label)
}

@Composable
private fun DisplayableWidgetApp.widgetHeaderDescription(style: AppHeaderDescriptionStyle): String {
    return when (style) {
        AppHeaderDescriptionStyle.WIDGETS_COUNT -> widgetsCountString(widgetsCount)

        AppHeaderDescriptionStyle.COMBINED_WIDGETS_TITLE ->
            widgetSizeGroups.flatMap { it.widgets }.map { it.label }.joinToString { it }
    }
}

/** Type of app Header. */
enum class WidgetAppHeaderStyle {
    // Clicking selects the item. Uses header background color to highlight that header is selected.
    CLICKABLE,

    // Clicking expands the item. Uses a arrow icon at end to indicate that header is selected.
    EXPANDABLE,
}

enum class AppHeaderDescriptionStyle {
    WIDGETS_COUNT,
    COMBINED_WIDGETS_TITLE,
}

private const val SPACER_LIST_ITEM_TYPE = "spacer"

private object WidgetAppsListDimensions {
    val itemSpacing = 4.dp

    val largeRadius = 24.dp
    val smallRadius = 4.dp

    /** For entire list and clickable headers */
    val largeShape = RoundedCornerShape(largeRadius)

    /** For first expandable item */
    val topLargeShape =
        RoundedCornerShape(
            topStart = largeRadius,
            topEnd = largeRadius,
            bottomStart = smallRadius,
            bottomEnd = smallRadius,
        )

    /** For last expandable item -- when in collapsed state */
    val bottomLargeShape =
        RoundedCornerShape(
            topStart = smallRadius,
            topEnd = smallRadius,
            bottomStart = largeRadius,
            bottomEnd = largeRadius,
        )

    /** For middle expandable items and last expandable item when in expanded state. */
    val smallShape = RoundedCornerShape(smallRadius)
}

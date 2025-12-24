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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.ui.LocalWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetPickerCui
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**
 * A list header in widget picker that when [expanded] displays the [expandedContent].
 *
 * Useful for single pane layouts where content is shown inline with the header on selection.
 *
 * @param modifier modifier for the top level composable of header
 * @param expanded whether to show the [expandedContent] below the header
 * @param leadingAppIcon an app icon shown in the beginning of the header row
 * @param title a short 1 line title for the header
 * @param accessibilityPrefix an optional prefix to be used for content description of the header
 *   e.g. to differentiate between work / personal app when displayed together in same list.
 * @param subTitle a short 1 line description (e.g. number of items in the [expandedContent]).
 * @param expandedContent the content for the header when its selected
 * @param onClick action to perform on click; e.g. manage the expand / collapse state
 * @param shape shape for the header e.g. a different shape based on position in the list
 */
@Composable
fun ExpandableListHeader(
    modifier: Modifier,
    expanded: Boolean,
    leadingAppIcon: @Composable () -> Unit,
    title: String,
    accessibilityPrefix: String? = null,
    subTitle: String,
    expandedContent: @Composable () -> Unit,
    onClick: () -> Unit,
    shape: RoundedCornerShape,
) {
    val haptic = LocalHapticFeedback.current
    val cuiReporter = LocalWidgetPickerCuiReporter.current
    val localView = LocalView.current
    var isFocused by remember { mutableStateOf(false) }

    val finalModifier =
        modifier
            .clip(shape = shape)
            .background(color = WidgetPickerTheme.colors.expandableListItemsBackground)

    val expandedState = remember { MutableTransitionState(expanded) }
    LaunchedEffect(expanded) { expandedState.targetState = expanded }
    LaunchedEffect(Unit) {
        snapshotFlow { Pair(expandedState.currentState, expandedState.targetState) }
            .collect {
                if (it.first == it.second) {
                    if (expandedState.currentState) {
                        cuiReporter.report(WidgetPickerCui.WIDGET_APP_EXPAND_END, localView)
                    }
                } else {
                    if (expandedState.targetState) {
                        cuiReporter.report(WidgetPickerCui.WIDGET_APP_EXPAND_BEGIN, localView)
                    }
                }
            }
    }

    Column(modifier = finalModifier) {
        WidgetAppHeader(
            modifier =
                Modifier.onFocusChanged { isFocused = it.isFocused }
                    .clickable(
                        interactionSource = null,
                        indication = if (isFocused) null else LocalIndication.current,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onClick()
                    }
                    .semantics {
                        if (expanded) {
                            collapse {
                                onClick()
                                true
                            }
                        } else {
                            expand {
                                onClick()
                                true
                            }
                        }
                    },
            leadingIcon = { leadingAppIcon() },
            title = title,
            accessibilityPrefix = accessibilityPrefix,
            subTitle = subTitle,
            headerTextStyle = ExpandedListHeaderDefaults.headerTextStyle,
            trailingButton = { ExpandCollapseIndicator(expanded) },
            isFocused = isFocused,
        )
        AnimatedVisibility(
            visibleState = expandedState,
            enter = ExpandedListHeaderDefaults.contentExpandAnimationSpec,
            exit = ExpandedListHeaderDefaults.contentCollapseAnimationSpec,
            modifier = Modifier.fillMaxWidth(),
        ) {
            expandedContent()
        }
    }
}

/**
 * A list header in widget picker that is selectable by clicking it.
 *
 * Useful for two pane layouts where content is shown in right pane while header is shown in left.
 *
 * @param modifier modifier for the top level composable of header
 * @param selected whether to show highlight the header's background to indicate its currently
 *   selected.
 * @param leadingAppIcon an app icon shown in the beginning of the header row
 * @param title a short 1 line title for the header
 * @param accessibilityPrefix an optional prefix to be used for content description of the header
 *   e.g. to differentiate between work / personal app when displayed together in same list.
 * @param subTitle a short 1 line description (e.g. number of widgets in the selected app).
 * @param onSelect action to perform when user clicks to select the header
 * @param shape shape for the header e.g. depending on position in the list, a different corner
 */
@Composable
fun SelectableListHeader(
    modifier: Modifier,
    selected: Boolean,
    leadingAppIcon: @Composable () -> Unit,
    title: String,
    accessibilityPrefix: String? = null,
    subTitle: String,
    onSelect: () -> Unit,
    shape: RoundedCornerShape,
) {
    var isFocused by remember { mutableStateOf(false) }

    WidgetAppHeader(
        modifier =
            modifier
                .onFocusChanged { isFocused = it.isFocused }
                .semantics(mergeDescendants = true) { this.selected = selected }
                .clip(shape = shape)
                .background(
                    color =
                        if (selected) {
                            WidgetPickerTheme.colors.selectedListHeaderBackground
                        } else {
                            WidgetPickerTheme.colors.unselectedListHeaderBackground
                        }
                )
                .clickable(
                    interactionSource = null,
                    indication = if (isFocused) null else LocalIndication.current,
                ) {
                    // It is fine for clickable to do nothing if its already selected.
                    // If we had removed clickable when someone selects a header, the keyboard
                    // navigation will go back to top on selection of an item instead of retaining
                    // focus. b/434746613
                    if (!selected) {
                        onSelect()
                    }
                },
        leadingIcon = { leadingAppIcon() },
        title = title,
        accessibilityPrefix = accessibilityPrefix,
        subTitle = subTitle,
        headerTextStyle =
            if (selected) {
                SelectableListHeaderDefaults.selectedHeaderTextStyle
            } else {
                SelectableListHeaderDefaults.unSelectedHeaderTextStyle
            },
        isFocused = isFocused,
    )
}

/**
 * A selectable header that can be shown for suggested (featured) widgets option.
 *
 * @param modifier modifier for top level composable of the suggestions header.
 * @param selected if the header is currently selected.
 * @param widgetsCount number of suggested widgets.
 * @param shortcutsCount number of suggested shortcuts (if supported).
 * @param onSelect action to perform when user selects the header.
 * @param shape shape for the header e.g. depending on position in the list, a different corner.
 */
@Composable
fun SelectableSuggestionsHeader(
    modifier: Modifier,
    selected: Boolean,
    widgetsCount: Int,
    shortcutsCount: Int,
    onSelect: () -> Unit,
    shape: RoundedCornerShape,
) {
    SelectableListHeader(
        modifier = modifier,
        selected = selected,
        shape = shape,
        title = stringResource(R.string.featured_widgets_tab_label),
        subTitle = widgetsCountString(widgets = widgetsCount, shortcuts = shortcutsCount),
        leadingAppIcon = {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = WidgetPickerTheme.colors.featuredHeaderLeadingIcon,
                modifier =
                    Modifier.clip(shape)
                        .background(WidgetPickerTheme.colors.featuredHeaderLeadingIconBackground)
                        .minimumInteractiveComponentSize(),
            )
        },
        onSelect = {
            if (!selected) {
                onSelect()
            }
        },
    )
}

@Composable
private fun WidgetAppHeader(
    modifier: Modifier,
    leadingIcon: @Composable () -> Unit,
    title: String,
    accessibilityPrefix: String?,
    subTitle: String,
    headerTextStyle: HeaderTextStyle,
    trailingButton: (@Composable () -> Unit)? = null,
    isFocused: Boolean = false,
) {
    val focusBorderModifier =
        if (isFocused) {
            modifier.border(
                width = ListHeaderDimensions.focusOutlineStrokeWidth,
                color = WidgetPickerTheme.colors.focusOutline,
                shape = RoundedCornerShape(ListHeaderDimensions.focusOutlineRadius),
            )
        } else modifier

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .then(focusBorderModifier)
                .height(height = ListHeaderDimensions.headerHeight)
                .padding(horizontal = ListHeaderDimensions.headerHorizontalPadding),
    ) {
        leadingIcon()
        HeaderText(
            modifier =
                Modifier.weight(1f)
                    .padding(horizontal = ListHeaderDimensions.centerTextHorizontalPadding),
            title = title,
            accessibilityPrefix = accessibilityPrefix,
            subTitle = subTitle,
            textStyle = headerTextStyle,
        )
        trailingButton?.let { it() }
    }
}

@Composable
private fun HeaderText(
    title: String,
    accessibilityPrefix: String?,
    subTitle: String,
    textStyle: HeaderTextStyle,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textStyle.titleColor,
            style = textStyle.titleTextStyle,
            modifier =
                Modifier.semantics {
                    if (accessibilityPrefix != null) {
                        contentDescription = "$accessibilityPrefix $title"
                    } // else default derived from title text
                },
        )
        Text(
            text = subTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textStyle.subTitleColor,
            style = textStyle.subTitleTextStyle,
        )
    }
}

private object ListHeaderDimensions {
    val headerHeight = 80.dp
    val headerHorizontalPadding = 16.dp
    val centerTextHorizontalPadding = 16.dp

    val focusOutlineRadius = 28.dp
    val focusOutlineStrokeWidth = 3.dp
}

private object ExpandedListHeaderDefaults {
    val contentExpandAnimationSpec = fadeIn(tween(durationMillis = 500)) + expandVertically()

    // Fade out content faster than shrink timing for smoother visual of closing.
    val contentCollapseAnimationSpec =
        fadeOut(tween(durationMillis = 250)) +
            shrinkVertically(animationSpec = tween(500), targetHeight = { 0 })

    val headerTextStyle
        @Composable
        get() =
            HeaderTextStyle(
                titleColor = WidgetPickerTheme.colors.expandableListHeaderTitle,
                subTitleColor = WidgetPickerTheme.colors.expandableListHeaderSubTitle,
                titleTextStyle = WidgetPickerTheme.typography.unSelectedListHeaderSubTitle,
                subTitleTextStyle = WidgetPickerTheme.typography.expandableListHeaderSubTitle,
            )
}

private object SelectableListHeaderDefaults {
    val unSelectedHeaderTextStyle
        @Composable
        get() =
            HeaderTextStyle(
                titleColor = WidgetPickerTheme.colors.unSelectedListHeaderTitle,
                subTitleColor = WidgetPickerTheme.colors.unSelectedListHeaderSubTitle,
                titleTextStyle = WidgetPickerTheme.typography.unSelectedListHeaderTitle,
                subTitleTextStyle = WidgetPickerTheme.typography.unSelectedListHeaderSubTitle,
            )

    val selectedHeaderTextStyle
        @Composable
        get() =
            HeaderTextStyle(
                titleColor = WidgetPickerTheme.colors.selectedListHeaderTitle,
                subTitleColor = WidgetPickerTheme.colors.selectedListHeaderSubTitle,
                titleTextStyle = WidgetPickerTheme.typography.selectedListHeaderTitle,
                subTitleTextStyle = WidgetPickerTheme.typography.selectedListHeaderSubTitle,
            )
}

internal data class HeaderTextStyle(
    val titleColor: Color,
    val titleTextStyle: TextStyle,
    val subTitleColor: Color,
    val subTitleTextStyle: TextStyle,
)

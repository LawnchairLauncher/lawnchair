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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode.Companion.Keyboard
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.WidgetInteractionSource
import com.android.launcher3.widgetpicker.ui.components.AddButtonDefaults.TOGGLE_ANIMATION_DURATION
import com.android.launcher3.widgetpicker.ui.components.WidgetDetailsDimensions.INVISIBLE_ALPHA
import com.android.launcher3.widgetpicker.ui.components.WidgetDetailsDimensions.VISIBLE_ALPHA
import com.android.launcher3.widgetpicker.ui.components.WidgetDetailsDimensions.singleLineDetailsHeight
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**
 * Displays the details of the widget that can be shown below their previews.
 *
 * @param widget the information about the widget that can be used to display the details
 * @param appIcon an optional app icon that can be displayed when widget is shown outside of the
 *   app's context e.g. in recommendations.
 * @param showAllDetails when set, besides the widget label, also shows widget spans and 1-3 line
 *   long description
 * @param showAddButton when set, displays the add button instead of details.
 * @param widgetInteractionSource section in which this widget is being displayed
 * @param onWidgetAddClick callback when user clicks on the add button to add the widget
 * @param onClick callback when user clicks on the details of a widget.
 * @param onHoverChange callback when user hovers on the details of a widget.
 * @param traversalIndex index of traversal of this item within parent.
 * @param modifier modifier for the top level composable.
 */
@Composable
fun WidgetDetails(
    widget: PickableWidget,
    appIcon: (@Composable () -> Unit)?,
    showAllDetails: Boolean,
    showAddButton: Boolean,
    widgetInteractionSource: WidgetInteractionSource,
    onWidgetAddClick: (WidgetInteractionInfo.WidgetAddInfo) -> Unit,
    onClick: (WidgetId) -> Unit,
    onHoverChange: (Boolean) -> Unit,
    traversalIndex: Int,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val widgetLabelContentDescription =
        stringResource(
            R.string.widget_details_accessibility_label,
            widget.label,
            widget.sizeInfo.spanX,
            widget.sizeInfo.spanY,
        )
    val customAccessibilityAddActionLabel =
        stringResource(R.string.widget_tap_to_add_button_content_description, widget.label)

    val onWidgetAddAction = {
        onWidgetAddClick(
            WidgetInteractionInfo.WidgetAddInfo(
                source = widgetInteractionSource,
                widgetInfo = widget.widgetInfo,
            )
        )
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    val isHovered by interactionSource.collectIsHoveredAsState()
    val inputModeManager = LocalInputModeManager.current

    val detailsAlpha: Float by
        animateFloatAsState(
            targetValue = if (showAddButton) INVISIBLE_ALPHA else VISIBLE_ALPHA,
            animationSpec = tween(durationMillis = TOGGLE_ANIMATION_DURATION),
            label = "detailsAlphaAnimation",
        )

    // Set fixed size where possible to improve text measurements.
    val sizeModifier =
        if (!showAllDetails || widget.description == null) {
            Modifier.height(singleLineDetailsHeight).fillMaxWidth()
        } else {
            Modifier.fillMaxSize()
        }

    LaunchedEffect(isHovered) { onHoverChange(isHovered) }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .semantics(mergeDescendants = true) {
                    this.traversalIndex = traversalIndex.toFloat()
                    isTraversalGroup = true
                    testTag = buildWidgetPickerTestTag(WIDGET_DETAILS_TEST_TAG)
                    customActions =
                        listOf(
                            CustomAccessibilityAction(customAccessibilityAddActionLabel) {
                                onWidgetAddAction()
                                true
                            }
                        )
                    if (showAddButton) {
                        // When showing add button there is no text on outer container, so setting
                        // its description here instead of text content; so it can be called out.
                        contentDescription = widgetLabelContentDescription
                    }
                }
                .then(sizeModifier)
                .borderOnFocus(
                    enabled = inputModeManager.inputMode == Keyboard,
                    color = WidgetPickerTheme.colors.focusOutline,
                    cornerSize = WidgetDetailsDimensions.focusOutlineRadius,
                    strokeWidth = WidgetDetailsDimensions.focusOutlineStrokeWidth,
                    padding = WidgetDetailsDimensions.focusOutlinePadding,
                )
                .clickable(
                    onClickLabel =
                        if (showAddButton) {
                            stringResource(R.string.widget_tap_to_hide_add_button_label)
                        } else {
                            stringResource(R.string.widget_tap_to_show_add_button_label)
                        },
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick(widget.id)
                }
                .padding(horizontal = WidgetDetailsDimensions.horizontalPadding)
                .padding(
                    top =
                        if (showAllDetails) {
                            WidgetDetailsDimensions.multiLineDetailsTopPadding
                        } else {
                            0.dp
                        }
                ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier =
                Modifier.defaultMinSize(minHeight = LocalMinimumInteractiveComponentSize.current)
                    .graphicsLayer { alpha = detailsAlpha }
                    .semantics {
                        if (showAddButton) {
                            hideFromAccessibility()
                        }
                    }
                    .fillMaxSize(),
        ) {
            WidgetLabel(
                label = widget.label,
                appIcon = appIcon,
                modifier =
                    Modifier.semantics {
                            this.contentDescription =
                                if (showAllDetails) {
                                    widget.label
                                } else {
                                    widgetLabelContentDescription
                                }
                        }
                        .hoverable(interactionSource = interactionSource),
            )
            if (showAllDetails) {
                WidgetSpanSizeLabel(spanX = widget.sizeInfo.spanX, spanY = widget.sizeInfo.spanY)
                widget.description?.let { WidgetDescription(it) }
            }
        }
        AnimatedVisibility(
            visible = showAddButton,
            modifier = Modifier.fillMaxSize(),
            enter = AddButtonDefaults.enterTransition,
            exit = AddButtonDefaults.exitTransition,
        ) {
            AddButton(
                widget = widget,
                autoFocus = !isHovered && inputModeManager.inputMode == Keyboard,
                onClick = onWidgetAddAction,
            )
        }
    }
}

@Composable
private fun AddButton(widget: PickableWidget, autoFocus: Boolean, onClick: () -> Unit) {
    val accessibleDescription =
        stringResource(R.string.widget_tap_to_add_button_content_description, widget.label)
    var hasTextOverflow by remember { mutableStateOf(false) }
    val addButtonFocusRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(
            modifier =
                Modifier.semantics { this.contentDescription = accessibleDescription }
                    .focusRequester(addButtonFocusRequester)
                    .borderOnFocus(
                        enabled = inputModeManager.inputMode == Keyboard,
                        color = WidgetPickerTheme.colors.focusOutline,
                        cornerSize = CornerSize(AddButtonDimensions.focusOutlineRadius),
                        strokeWidth = AddButtonDimensions.focusOutlineStrokeWidth,
                        padding = AddButtonDimensions.focusOutlinePadding,
                    )
                    .width(IntrinsicSize.Max)
                    .defaultMinSize(minHeight = LocalMinimumInteractiveComponentSize.current),
            shape = RoundedCornerShape(AddButtonDimensions.buttonRadius),
            contentPadding = AddButtonDimensions.paddingValues,
            elevation = null, // not needed, set to null to avoid extra work.
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = WidgetPickerTheme.colors.addButtonBackground,
                    contentColor = WidgetPickerTheme.colors.addButtonContent,
                ),
            onClick = onClick,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null, // decorative
            )
            Text(
                text = stringResource(R.string.widget_tap_to_add_button_label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.padding(
                            end =
                                if (hasTextOverflow) {
                                    0.dp
                                } else {
                                    AddButtonDimensions.textEndPadding
                                }
                        )
                        .clearAndSetSemantics {} // description set on parent
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            // Don't show text is it leads to overflow
                            if (hasTextOverflow) {
                                layout(0, 0) {}
                            } else {
                                layout(placeable.width, placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                        },
                onTextLayout = { textLayoutResult: TextLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow != hasTextOverflow) {
                        hasTextOverflow = textLayoutResult.hasVisualOverflow
                    }
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            // Request focus; if we are in non-touch mode, button will be focusable and get focus.
            addButtonFocusRequester.requestFocus()
        }
    }
}

/** The label / short title of the widget provided by the developer in the manifest. */
@Composable
private fun WidgetLabel(label: String, appIcon: (@Composable () -> Unit)?, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (appIcon != null) {
            appIcon()
            Spacer(
                modifier =
                    Modifier.width(WidgetDetailsDimensions.appIconLabelSpacing).fillMaxHeight()
            )
        }
        Text(
            modifier = Modifier.width(IntrinsicSize.Max),
            text = label,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = WidgetPickerTheme.colors.widgetLabel,
            style = WidgetPickerTheme.typography.widgetLabel,
        )
    }
}

/**
 * Display a long description provided by the developers for the widget in their appwidget provider
 * info.
 */
@Composable
private fun WidgetDescription(description: CharSequence) {
    Text(
        text = description.toString(),
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        maxLines = 3,
        color = WidgetPickerTheme.colors.widgetDescription,
        style = WidgetPickerTheme.typography.widgetDescription,
    )
}

/** Span (X and Y) sizing info for the widget. */
@Composable
private fun WidgetSpanSizeLabel(spanX: Int, spanY: Int) {
    val spanContentDescription =
        stringResource(R.string.widget_span_dimensions_accessible_format, spanX, spanY)

    Text(
        text = stringResource(R.string.widget_span_dimensions_format, spanX, spanY),
        textAlign = TextAlign.Center,
        maxLines = 1,
        color = WidgetPickerTheme.colors.widgetSpanText,
        style = WidgetPickerTheme.typography.widgetSpanText,
        modifier = Modifier.semantics { contentDescription = spanContentDescription },
    )
}

private const val WIDGET_DETAILS_TEST_TAG = "widget_details"

private object WidgetDetailsDimensions {
    val horizontalPadding: Dp = 4.dp
    val multiLineDetailsTopPadding: Dp = 8.dp
    val appIconLabelSpacing = 8.dp

    val singleLineDetailsHeight = 72.dp

    val focusOutlinePadding = 0.dp
    val focusOutlineRadius = CornerSize(18.dp)
    val focusOutlineStrokeWidth = 3.dp

    const val VISIBLE_ALPHA = 1f
    const val INVISIBLE_ALPHA = 0f
}

private object AddButtonDimensions {
    val paddingValues = PaddingValues(start = 8.dp, top = 11.dp, end = 8.dp, bottom = 11.dp)

    // Padding when showing add icon and the text
    val textEndPadding = 8.dp

    val buttonRadius = 50.dp
    val focusOutlinePadding = 2.dp
    val focusOutlineRadius = buttonRadius
    val focusOutlineStrokeWidth = 3.dp
}

private object AddButtonDefaults {
    const val TOGGLE_ANIMATION_DURATION = 400
    val enterTransition = fadeIn(animationSpec = tween(TOGGLE_ANIMATION_DURATION))
    val exitTransition = fadeOut(animationSpec = tween(TOGGLE_ANIMATION_DURATION))
}

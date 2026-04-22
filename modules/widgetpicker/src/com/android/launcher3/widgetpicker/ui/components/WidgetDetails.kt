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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.components.AddButtonDefaults.TOGGLE_ANIMATION_DURATION
import com.android.launcher3.widgetpicker.ui.components.WidgetDetailsDimensions.INVISIBLE_ALPHA
import com.android.launcher3.widgetpicker.ui.components.WidgetDetailsDimensions.VISIBLE_ALPHA
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
 * @param onWidgetAddClick callback when user clicks on the add button to add the widget
 * @param modifier modifier for the top level composable.
 */
@Composable
fun WidgetDetails(
    widget: PickableWidget,
    appIcon: (@Composable () -> Unit)?,
    showAllDetails: Boolean,
    showAddButton: Boolean,
    onWidgetAddClick: (WidgetInteractionInfo.WidgetAddInfo) -> Unit,
    onAddButtonToggle: (WidgetId) -> Unit,
    modifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val contentDescription =
        stringResource(
            R.string.widget_details_accessibility_label,
            widget.label,
            widget.sizeInfo.spanX,
            widget.sizeInfo.spanY,
        )

    val detailsAlpha: Float by
        animateFloatAsState(
            targetValue = if (showAddButton) INVISIBLE_ALPHA else VISIBLE_ALPHA,
            animationSpec = tween(durationMillis = TOGGLE_ANIMATION_DURATION),
            label = "detailsAlphaAnimation",
        )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
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
                    onAddButtonToggle(widget.id)
                }
                .padding(
                    horizontal = WidgetDetailsDimensions.horizontalPadding,
                    vertical = WidgetDetailsDimensions.verticalPadding,
                ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier =
                Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                    .minimumInteractiveComponentSize()
                    .graphicsLayer { alpha = detailsAlpha }
                    .fillMaxSize(),
        ) {
            WidgetLabel(label = widget.label, appIcon = appIcon, modifier = Modifier)
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
                onClick = {
                    onWidgetAddClick(WidgetInteractionInfo.WidgetAddInfo(widget.widgetInfo))
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                },
            )
        }
    }
}

@Composable
private fun AddButton(widget: PickableWidget, onClick: () -> Unit) {
    val accessibleDescription =
        stringResource(R.string.widget_tap_to_add_button_content_description, widget.label)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(
            modifier = Modifier.minimumInteractiveComponentSize(),
            contentPadding = AddButtonDimensions.paddingValues,
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
                modifier = Modifier.semantics { this.contentDescription = accessibleDescription },
                text = stringResource(R.string.widget_tap_to_add_button_label),
            )
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
    val contentDescription =
        stringResource(R.string.widget_span_dimensions_accessible_format, spanX, spanY)

    Text(
        text = stringResource(R.string.widget_span_dimensions_format, spanX, spanY),
        textAlign = TextAlign.Center,
        maxLines = 1,
        color = WidgetPickerTheme.colors.widgetSpanText,
        style = WidgetPickerTheme.typography.widgetSpanText,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    )
}

private object WidgetDetailsDimensions {
    val horizontalPadding: Dp = 4.dp
    val verticalPadding: Dp = 12.dp
    val appIconLabelSpacing = 8.dp

    const val VISIBLE_ALPHA = 1f
    const val INVISIBLE_ALPHA = 0f
}

private object AddButtonDimensions {
    val paddingValues = PaddingValues(start = 8.dp, top = 11.dp, end = 16.dp, bottom = 11.dp)
}

private object AddButtonDefaults {
    const val TOGGLE_ANIMATION_DURATION = 400
    val enterTransition = fadeIn(animationSpec = tween(TOGGLE_ANIMATION_DURATION))
    val exitTransition = fadeOut(animationSpec = tween(TOGGLE_ANIMATION_DURATION))
}

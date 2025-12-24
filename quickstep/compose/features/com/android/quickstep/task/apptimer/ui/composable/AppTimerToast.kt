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

package com.android.quickstep.task.apptimer.ui.composable

import android.app.ActivityOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.quickstep.task.apptimer.TaskAppTimerUiState
import com.android.quickstep.task.apptimer.ViewModel
import com.android.quickstep.task.apptimer.ui.composable.AppTimerToastDimensions.ICON_ONLY_WIDTH_RATIO_THRESHOLD
import com.android.quickstep.task.apptimer.ui.composable.AppTimerToastDimensions.ICON_SHORT_TEXT_WIDTH_RATIO_THRESHOLD

@Composable
fun AppTimerToast(
    appTimerUiState: TaskAppTimerUiState,
    viewModel: ViewModel<TaskAppTimerUiState>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = appTimerUiState is TaskAppTimerUiState.Timer,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        when (appTimerUiState) {
            is TaskAppTimerUiState.Timer -> ActiveTimerToast(viewModel, appTimerUiState, modifier)
            else -> {
                /* Do nothing */
            }
        }
    }
}

@Composable
private fun ActiveTimerToast(
    viewModel: ViewModel<TaskAppTimerUiState>,
    appTimerUiState: TaskAppTimerUiState.Timer,
    modifier: Modifier = Modifier,
    iconTextSpacing: Dp = 4.dp,
) {
    val view = LocalView.current
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.digital_wellbeing_toast_height))
                .clickable(
                    onClick = {
                        viewModel.startActivityWithScaleUpAnimation(
                            ActivityOptions.makeScaleUpAnimation(
                                view,
                                0,
                                0,
                                view.width,
                                view.height,
                            ),
                            view.context,
                            appTimerUiState.taskPackageName,
                            appTimerUiState.taskDescription,
                        )
                    }
                ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryFixed,
    ) {
        CustomTimerToastLayout(viewModel, appTimerUiState, iconTextSpacing, modifier)
    }
}

@Composable
private fun CustomTimerToastLayout(
    viewModel: ViewModel<TaskAppTimerUiState>,
    appTimerUiState: TaskAppTimerUiState.Timer,
    iconTextSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    val formattedDuration =
        viewModel.getFormattedDuration(appTimerUiState.timeRemaining, LocalContext.current)
    Layout(
        content = {
            Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            FormattedTimeRemainingText(formattedDuration)
            FullTimeRemainingText(formattedDuration)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        require(measurables.size == 3) { "Active timer toast layout requires 3 measurables" }

        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val relaxedConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val iconPlaceable = measurables[0].measure(relaxedConstraints)
        val fullTextPlaceable = measurables[2].measure(relaxedConstraints)

        val contentWidth =
            iconPlaceable.width + iconTextSpacing.roundToPx() + fullTextPlaceable.width

        val contentToLayoutWidthRatio = contentWidth.toFloat() / layoutWidth

        layout(layoutWidth, layoutHeight) {
            when {
                contentToLayoutWidthRatio > ICON_ONLY_WIDTH_RATIO_THRESHOLD -> {
                    layoutPlaceables(
                        layoutWidth,
                        layoutHeight,
                        iconTextSpacing.roundToPx(),
                        listOf(iconPlaceable),
                    )
                }

                contentToLayoutWidthRatio > ICON_SHORT_TEXT_WIDTH_RATIO_THRESHOLD -> {
                    val formattedTextPlaceable = measurables[1].measure(relaxedConstraints)
                    layoutPlaceables(
                        layoutWidth,
                        layoutHeight,
                        iconTextSpacing.roundToPx(),
                        listOf(iconPlaceable, formattedTextPlaceable),
                    )
                }

                else -> {
                    layoutPlaceables(
                        layoutWidth,
                        layoutHeight,
                        iconTextSpacing.roundToPx(),
                        listOf(iconPlaceable, fullTextPlaceable),
                    )
                }
            }
        }
    }
}

private fun Placeable.PlacementScope.layoutPlaceables(
    layoutWidth: Int,
    layoutHeight: Int,
    spacing: Int,
    placeables: List<Placeable>,
) {
    val contentWidth =
        placeables.sumOf { it.width } + (spacing * (placeables.size - 1)).coerceAtLeast(0)

    var currentX = (layoutWidth - contentWidth).coerceAtLeast(0) / 2

    placeables.forEach { placeable ->
        val y = (layoutHeight - placeable.height) / 2

        placeable.placeRelative(x = currentX, y = y)

        currentX += placeable.width + spacing
    }
}

@Composable
private fun FormattedTimeRemainingText(formattedDuration: String) =
    TimeRemainingText(formattedDuration)

@Composable
private fun FullTimeRemainingText(formattedDuration: String) =
    TimeRemainingText(stringResource(R.string.time_left_for_app, formattedDuration))

@Composable
private fun TimeRemainingText(text: String) =
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSecondaryFixed,
        style = MaterialTheme.typography.bodyMedium,
    )

private object AppTimerToastDimensions {
    // If the full text ("$icon $formattedDuration left today") takes a lot more space than is
    // available, it is likely short text won't fit either. So, we fallback to just an icon.
    const val ICON_ONLY_WIDTH_RATIO_THRESHOLD = 1.2f

    // If the full text fits but leaves very little space, use short text instead for
    // comfortable viewing.
    const val ICON_SHORT_TEXT_WIDTH_RATIO_THRESHOLD = 0.8f
}

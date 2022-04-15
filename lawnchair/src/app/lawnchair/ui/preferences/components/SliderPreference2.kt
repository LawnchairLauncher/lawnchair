/*
 * Copyright 2022, Lawnchair
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

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.R
import kotlin.math.roundToInt

@Composable
fun PreferenceCollectorScope.SliderPreference2(
    label: String,
    value: Int,
    edit: suspend PreferenceManager2.(Int) -> Unit,
    valueRange: ClosedRange<Int>,
    step: Int,
    showAsPercentage: Boolean = false,
    showDivider: Boolean = false,
) {
    SliderPreference2(
        label = label,
        value = value,
        onValueChange = { edit { this.edit(it) } },
        valueRange = valueRange,
        step = step,
        showAsPercentage = showAsPercentage,
        showDivider = showDivider,
    )
}

@Composable
fun SliderPreference2(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedRange<Int>,
    step: Int,
    showAsPercentage: Boolean = false,
    showDivider: Boolean = false,
) {
    val start = valueRange.start.toFloat()
    val endInclusive = valueRange.endInclusive.toFloat()
    SliderPreference2(
        label = label,
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = start..endInclusive,
        step = step.toFloat(),
        showAsPercentage = showAsPercentage,
        showDivider = showDivider,
    )
}

@Composable
fun PreferenceCollectorScope.SliderPreference2(
    label: String,
    value: Float,
    edit: suspend PreferenceManager2.(Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    showAsPercentage: Boolean = false,
    showDivider: Boolean = false,
) {
    SliderPreference2(
        label = label,
        value = value,
        onValueChange = { edit { this.edit(it) } },
        valueRange = valueRange,
        step = step,
        showAsPercentage = showAsPercentage,
        showDivider = showDivider,
    )
}

@Composable
fun SliderPreference2(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    showAsPercentage: Boolean = false,
    showDivider: Boolean = false,
) {
    var sliderValue by remember { mutableStateOf(value) }

    DisposableEffect(value) {
        sliderValue = value
        onDispose { }
    }

    PreferenceTemplate(
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Text(text = label)
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground,
                ) {
                    val value = snapSliderValue2(valueRange.start, sliderValue, step)
                    Text(
                        text = if (showAsPercentage) stringResource(
                            id = R.string.n_percent,
                            (value * 100).roundToInt()
                        ) else value.roundToInt().toString(),
                    )
                }
            }
        },
        description = {
            Slider(
                value = sliderValue,
                onValueChange = { newValue -> sliderValue = newValue },
                onValueChangeFinished = { onValueChange(sliderValue) },
                valueRange = valueRange,
                steps = getSteps2(valueRange, step),
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 12.dp)
                    .padding(horizontal = 10.dp)
                    .height(24.dp),
            )
        },
        showDivider = showDivider,
        applyPaddings = false,
    )
}

fun getSteps2(
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
): Int {
    if (step == 0f) return 0
    val start = valueRange.start
    val end = valueRange.endInclusive
    val steps = ((end - start) / step).toInt()
    require(start + step * steps == end)
    return steps - 1
}

fun snapSliderValue2(
    start: Float,
    value: Float,
    step: Float,
): Float {
    if (step == 0f) return value
    val distance = value - start
    val stepsFromStart = (distance / step).roundToInt()
    val snappedDistance = stepsFromStart * step
    return start + snappedDistance
}

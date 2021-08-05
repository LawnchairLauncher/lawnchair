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

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.rememberTransformAdapter
import kotlin.math.roundToInt

@Composable
fun SliderPreference(
    label: String,
    adapter: PreferenceAdapter<Int>,
    valueRange: ClosedRange<Int>,
    step: Int,
    showAsPercentage: Boolean = false,
    showDivider: Boolean = true
) {
    val transformedAdapter = rememberTransformAdapter(
        adapter = adapter,
        transformGet = { it.toFloat() },
        transformSet = { it.roundToInt() }
    )
    val start = valueRange.start.toFloat()
    val endInclusive = valueRange.endInclusive.toFloat()
    SliderPreference(
        label = label,
        adapter = transformedAdapter,
        valueRange = start..endInclusive,
        step = step.toFloat(),
        showAsPercentage = showAsPercentage,
        showDivider = showDivider
    )
}

@Composable
fun SliderPreference(
    label: String,
    adapter: PreferenceAdapter<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    showAsPercentage: Boolean = false,
    showDivider: Boolean = true
) {
    var adapterValue by adapter
    var sliderValue by remember { mutableStateOf(adapterValue) }

    DisposableEffect(adapterValue) {
        sliderValue = adapterValue
        onDispose { }
    }

    PreferenceTemplate(height = 76.dp, showDivider = showDivider) {
        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Spacer(modifier = Modifier.requiredHeight(2.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onBackground
                )
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    val value = snapSliderValue(valueRange.start, sliderValue, step)
                    Text(
                        text = if (showAsPercentage) {
                            "${(value * 100).roundToInt()}%"
                        } else {
                            "${value.roundToInt()}"
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.requiredHeight(2.dp))
            Slider(
                value = sliderValue,
                onValueChange = { newValue -> sliderValue = newValue },
                onValueChangeFinished = { adapterValue = sliderValue },
                valueRange = valueRange,
                steps = getSteps(valueRange, step),
                modifier = Modifier
                    .height(24.dp)
                    .padding(start = 10.dp, end = 10.dp)
            )
        }
    }
}

fun getSteps(valueRange: ClosedFloatingPointRange<Float>, step: Float): Int {
    if (step == 0f) return 0
    val start = valueRange.start
    val end = valueRange.endInclusive
    val steps = ((end - start) / step).toInt()
    if (start + step * steps != end) {
        throw IllegalArgumentException("value range must be a multiple of step")
    }
    return steps - 1
}

fun snapSliderValue(start: Float, value: Float, step: Float): Float {
    if (step == 0f) return value
    val distance = value - start
    val stepsFromStart = (distance / step).roundToInt()
    val snappedDistance = stepsFromStart * step
    return start + snappedDistance
}

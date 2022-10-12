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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.rememberTransformAdapter
import com.android.launcher3.R
import kotlin.math.roundToInt

@Composable
fun SliderPreference(
    label: String,
    adapter: PreferenceAdapter<Int>,
    valueRange: ClosedRange<Int>,
    step: Int,
    showAsPercentage: Boolean = false,
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
    )
}

@Composable
fun SliderPreference(
    label: String,
    adapter: PreferenceAdapter<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    showAsPercentage: Boolean = false,
) {
    var adapterValue by adapter
    var sliderValue by remember { mutableStateOf(adapterValue) }

    DisposableEffect(adapterValue) {
        sliderValue = adapterValue
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
                    .padding(horizontal = 16.dp)
            ) {
                Text(text = label)
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    val value = snapSliderValue(valueRange.start, sliderValue, step)
                    Text(
                        text = if (showAsPercentage) stringResource(
                            id = R.string.n_percent,
                            (value * 100).roundToInt()
                        ) else value.roundToInt().toString()
                    )
                }
            }
        },
        description = {
            Slider(
                value = sliderValue,
                onValueChange = { newValue -> sliderValue = newValue },
                onValueChangeFinished = { adapterValue = sliderValue },
                valueRange = valueRange,
                steps = getSteps(valueRange, step),
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 12.dp)
                    .padding(horizontal = 10.dp)
                    .height(24.dp)
            )
        },
        applyPaddings = false
    )
}

fun getSteps(valueRange: ClosedFloatingPointRange<Float>, step: Float): Int {
    if (step == 0f) return 0
    val start = valueRange.start
    val end = valueRange.endInclusive
    val steps = ((end - start) / step).toInt()
    require (start + step * steps == end) {
        "value range must be a multiple of step"
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

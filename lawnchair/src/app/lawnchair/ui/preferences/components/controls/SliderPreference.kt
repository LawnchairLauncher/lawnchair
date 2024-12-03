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

package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.rememberTransformAdapter
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R
import kotlin.math.roundToInt

@Composable
fun SliderPreference(
    label: String,
    adapter: PreferenceAdapter<Int>,
    valueRange: ClosedRange<Int>,
    step: Int,
    showAsPercentage: Boolean = false,
    showUnit: String = "",
) {
    val transformedAdapter = rememberTransformAdapter(
        adapter = adapter,
        transformGet = { it.toFloat() },
        transformSet = { it.roundToInt() },
    )
    val start = valueRange.start.toFloat()
    val endInclusive = valueRange.endInclusive.toFloat()
    SliderPreference(
        label = label,
        adapter = transformedAdapter,
        valueRange = start..endInclusive,
        step = step.toFloat(),
        showAsPercentage = showAsPercentage,
        showUnit = showUnit,
    )
}

@Composable
fun SliderPreference(
    label: String,
    adapter: PreferenceAdapter<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    modifier: Modifier = Modifier,
    showAsPercentage: Boolean = false,
    showUnit: String = "",
) {
    var adapterValue by adapter
    var sliderValue by remember { mutableFloatStateOf(adapterValue) }

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
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                ) {
                    val value = snapSliderValue(valueRange.start, sliderValue, step)
                    Text(
                        text = if (showAsPercentage) {
                            stringResource(
                                id = R.string.n_percent,
                                (value * 100).roundToInt(),
                            ) + " $showUnit"
                        } else {
                            value.roundToInt().toString() + " $showUnit"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .height(24.dp),
            )
        },
        modifier = modifier,
        applyPaddings = false,
    )
}

fun getSteps(valueRange: ClosedFloatingPointRange<Float>, step: Float): Int {
    if (step == 0f) return 0
    val start = valueRange.start.toBigDecimal()
    val end = valueRange.endInclusive.toBigDecimal()
    val decimalSteps = (end - start) / step.toBigDecimal()
    val steps = decimalSteps.toInt()
    require(decimalSteps.compareTo(steps.toBigDecimal()) == 0) {
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

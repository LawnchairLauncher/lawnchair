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
import kotlin.math.roundToInt

@Composable
fun SliderPreference(
    label: String,
    adapter: PreferenceAdapter<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
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
                    Text(
                        text = if (showAsPercentage) {
                            "${(sliderValue * 100).roundToInt()}%"
                        } else {
                            "${sliderValue.roundToInt()}"
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
                steps = steps,
                modifier = Modifier
                    .height(24.dp)
                    .padding(start = 10.dp, end = 10.dp)
            )
        }
    }
}

package app.lawnchair.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.util.round
import kotlin.math.roundToInt

@Composable
fun SliderPreference(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    showAsPercentage: Boolean = false,
    showDivider: Boolean = true
) =
    PreferenceTemplate(height = 76.dp, showDivider = showDivider) {
        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Spacer(modifier = Modifier.requiredHeight(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(
                        text = if (showAsPercentage) {
                            "${(value.round(2) * 100).toInt()}%"
                        } else {
                            "${value.roundToInt()}"
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.requiredHeight(2.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier
                    .height(24.dp)
                    .padding(start = 10.dp, end = 10.dp)
            )
        }
    }
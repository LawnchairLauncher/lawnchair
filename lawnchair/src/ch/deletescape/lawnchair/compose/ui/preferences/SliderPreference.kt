package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import kotlin.math.roundToInt

@Composable
fun SliderPreference(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    showAsPercentage: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(72.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
            Text(
                text = if (showAsPercentage) {
                    "${(DecimalFormat("#.#").format(value).toFloat() * 100).toInt()}%"
                } else {
                    "${value.roundToInt()}"
                }
            )
        }
        Spacer(modifier = Modifier.requiredHeight(4.dp))
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
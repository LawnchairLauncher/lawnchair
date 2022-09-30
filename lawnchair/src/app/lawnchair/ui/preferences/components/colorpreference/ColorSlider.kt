package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.toRange
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import app.lawnchair.ui.preferences.components.getSteps
import app.lawnchair.ui.preferences.components.snapSliderValue
import com.android.launcher3.R
import kotlin.math.roundToInt

@Composable
fun RgbColorSlider(
    label: String,
    colorStart: Color,
    colorEnd: Color,
    value: Int,
    onValueChange: (Float) -> Unit,
) {

    val step = 0f
    val rgbRange = 0f..255f

    PreferenceTemplate(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(text = label)
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides androidx.compose.material.MaterialTheme.colors.onBackground,
                ) {
                    val valueText = snapSliderValue(rgbRange.start, value.toFloat(), step)
                        .roundToInt().toString()
                    Text(text = valueText)
                }
            }
        },
        description = {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(6.dp)
                        .clip(CircleShape)
                        .background(colorStart),
                )
                Slider(
                    value = value.toFloat(),
                    onValueChange = onValueChange,
                    valueRange = rgbRange,
                    steps = getSteps(rgbRange, step),
                    modifier = Modifier
                        .height(24.dp)
                        .weight(1f)
                        .padding(horizontal = 3.dp),
                )
                Box(
                    modifier = Modifier
                        .requiredSize(6.dp)
                        .clip(CircleShape)
                        .background(colorEnd),
                )
            }
        },
        applyPaddings = false,
    )

}

@Composable
fun HsbColorSlider(
    type: HsbSliderType,
    value: Float,
    onValueChange: (Float) -> Unit,
) {

    val step = 0f

    val range = when (type) {
        HsbSliderType.HUE -> 0f..359f
        else -> 0f..1f
    }

    val showAsPercentage = when (type) {
        HsbSliderType.HUE -> false
        else -> true
    }

    val label = when (type) {
        HsbSliderType.HUE -> stringResource(id = R.string.hsb_hue)
        HsbSliderType.SATURATION -> stringResource(id = R.string.hsb_saturation)
        HsbSliderType.BRIGHTNESS -> stringResource(id = R.string.hsb_brightness)
    }

    PreferenceTemplate(
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(text = label)
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides androidx.compose.material.MaterialTheme.colors.onBackground,
                ) {
                    val valueText = snapSliderValue(range.start, value, step)
                    Text(
                        text = if (showAsPercentage) stringResource(
                            id = R.string.n_percent,
                            (valueText * 100).roundToInt(),
                        ) else "${valueText.roundToInt()}°",
                    )
                }
            }
        },
        description = {
            Column(
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            ) {
                if (type == HsbSliderType.HUE) {
                    val brushColors = arrayListOf<Color>()
                    val stepSize = 6
                    repeat((range.endInclusive - range.start).toInt() / stepSize) {
                        val newColor = Color.hsv(
                            hue = it * stepSize.toFloat(),
                            saturation = 1f,
                            value = 1f,
                        )
                        brushColors.add(newColor)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .padding(horizontal = 16.dp)
                            .requiredHeight(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(brush = Brush.horizontalGradient(brushColors)),
                    )
                }
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = { },
                    valueRange = range,
                    steps = getSteps(range, step),
                    colors = SliderDefaults.colors(),
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth(),
                )
            }
        },
        applyPaddings = false,
    )
}

enum class HsbSliderType {
    HUE, SATURATION, BRIGHTNESS
}

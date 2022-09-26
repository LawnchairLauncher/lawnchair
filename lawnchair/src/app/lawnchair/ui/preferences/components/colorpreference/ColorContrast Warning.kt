package app.lawnchair.ui.preferences.components.colorpreference

import androidx.annotation.ColorInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.PreferenceTemplate

private const val CONTRAST_THRESHOLD = 1.5

/**
 * Displays a warning when [foregroundColor] & [backgroundColor] have less than optimal contrast with each other.
 *
 * @see CONTRAST_THRESHOLD
 */
@Composable
fun ColorContrastWarning(
    foregroundColor: ColorOption,
    backgroundColor: ColorOption,
    text: String,
) {
    val context = LocalContext.current
    val foregroundColorInt = foregroundColor.colorPreferenceEntry.lightColor(context)
    val backgroundColorInt = backgroundColor.colorPreferenceEntry.lightColor(context)
    ColorContrastWarning(
        foregroundColor = foregroundColorInt,
        backgroundColor = backgroundColorInt,
        text = text,
    )
}

/**
 * Displays a warning when [foregroundColor] & [backgroundColor] have less than optimal contrast with each other.
 *
 * @see CONTRAST_THRESHOLD
 */
@Composable
fun ColorContrastWarning(
    @ColorInt foregroundColor: Int,
    @ColorInt backgroundColor: Int,
    text: String,
) {

    val enoughContrast = if (foregroundColor != 0 && backgroundColor != 0) {
        ColorUtils.calculateContrast(
            foregroundColor,
            backgroundColor,
        ) >= CONTRAST_THRESHOLD
    } else true

    if (!enoughContrast) {
        PreferenceTemplate(
            title = {},
            description = {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.error,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    tint = MaterialTheme.colorScheme.error,
                    contentDescription = null,
                )
            },
        )
    }

}

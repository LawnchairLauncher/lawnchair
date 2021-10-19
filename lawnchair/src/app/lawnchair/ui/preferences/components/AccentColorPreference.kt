package app.lawnchair.ui.preferences.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.R

private val customOptionsValues = listOf(
    ColorOption.CustomColor(0xFFF32020),
    ColorOption.CustomColor(0xFFF20D69),
    ColorOption.CustomColor(0xFF7452FF),
    ColorOption.CustomColor(0xFF2C41C9),
    ColorOption.LawnchairBlue,
    ColorOption.CustomColor(0xFF00BAD6),
    ColorOption.CustomColor(0xFF00A399),
    ColorOption.CustomColor(0xFF47B84F),
    ColorOption.CustomColor(0xFFFFBB00),
    ColorOption.CustomColor(0xFFFF9800),
    ColorOption.CustomColor(0xFF7C5445),
    ColorOption.CustomColor(0xFF67818E)
)

private val options = listOf(ColorOption.SystemAccent, ColorOption.WallpaperPrimary)
    .filter(ColorOption::isSupported)
    .map(ColorOption::preferenceOption)
private val customOptions = customOptionsValues.map(ColorOption::preferenceOption)

@Composable
@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun AccentColorPreference() {
    ColorPreference(
        previewColor = MaterialTheme.colors.primary,
        colorAdapter = preferenceManager().accentColor.getAdapter(),
        lastCustomColorAdapter = preferenceManager().lastCustomAccent.getAdapter(),
        label = stringResource(id = R.string.accent_color),
        options = options,
        customOptions = customOptions
    )
}

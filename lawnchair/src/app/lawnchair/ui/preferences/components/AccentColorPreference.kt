package app.lawnchair.ui.preferences.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference2
import com.android.launcher3.R

val staticColors = listOf(
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
).map(ColorOption::colorPreferenceEntry)

val dynamicColors = listOf(ColorOption.SystemAccent, ColorOption.WallpaperPrimary)
    .filter(ColorOption::isSupported)
    .map(ColorOption::colorPreferenceEntry)

@Composable
fun PreferenceCollectorScope.AccentColorPreference(
    value: ColorOption,
    edit: suspend PreferenceManager2.(ColorOption) -> Unit,
) {
    ColorPreference2(
        value = value,
        onValueChange = { edit { this.edit(it) } },
        label = stringResource(id = R.string.accent_color),
        dynamicEntries = dynamicColors,
        staticEntries = staticColors,
    )
}

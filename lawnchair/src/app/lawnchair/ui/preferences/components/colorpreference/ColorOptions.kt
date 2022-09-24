package app.lawnchair.ui.preferences.components.colorpreference

import app.lawnchair.theme.color.ColorOption

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

val dynamicColorsWithDefault = dynamicColors + listOf(ColorOption.Default.colorPreferenceEntry)

package app.lawnchair.theme.color

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Suppress("MemberVisibilityCanBePrivate", "unused")
object ColorTokens {
    val Neutral1_0 = SwatchColorToken(Swatch.Neutral1, Shade.S0)
    val Neutral1_10 = SwatchColorToken(Swatch.Neutral1, Shade.S10)
    val Neutral1_50 = SwatchColorToken(Swatch.Neutral1, Shade.S50)
    val Neutral1_100 = SwatchColorToken(Swatch.Neutral1, Shade.S100)
    val Neutral1_200 = SwatchColorToken(Swatch.Neutral1, Shade.S200)
    val Neutral1_500 = SwatchColorToken(Swatch.Neutral1, Shade.S500)
    val Neutral1_700 = SwatchColorToken(Swatch.Neutral1, Shade.S700)
    val Neutral1_800 = SwatchColorToken(Swatch.Neutral1, Shade.S800)
    val Neutral1_900 = SwatchColorToken(Swatch.Neutral1, Shade.S900)

    val Neutral2_50 = SwatchColorToken(Swatch.Neutral2, Shade.S50)
    val Neutral2_100 = SwatchColorToken(Swatch.Neutral2, Shade.S100)
    val Neutral2_200 = SwatchColorToken(Swatch.Neutral2, Shade.S200)
    val Neutral2_300 = SwatchColorToken(Swatch.Neutral2, Shade.S300)
    val Neutral2_500 = SwatchColorToken(Swatch.Neutral2, Shade.S500)
    val Neutral2_700 = SwatchColorToken(Swatch.Neutral2, Shade.S700)
    val Neutral2_800 = SwatchColorToken(Swatch.Neutral2, Shade.S800)

    val Accent1_50 = SwatchColorToken(Swatch.Accent1, Shade.S50)
    val Accent1_100 = SwatchColorToken(Swatch.Accent1, Shade.S100)
    val Accent1_200 = SwatchColorToken(Swatch.Accent1, Shade.S200)
    val Accent1_300 = SwatchColorToken(Swatch.Accent1, Shade.S300)
    val Accent1_500 = SwatchColorToken(Swatch.Accent1, Shade.S500)
    val Accent1_600 = SwatchColorToken(Swatch.Accent1, Shade.S600)

    val Accent2_50 = SwatchColorToken(Swatch.Accent2, Shade.S50)
    val Accent2_100 = SwatchColorToken(Swatch.Accent2, Shade.S100)
    val Accent2_600 = SwatchColorToken(Swatch.Accent2, Shade.S600)

    val Accent3_50 = SwatchColorToken(Swatch.Accent3, Shade.S50)
    val Accent3_100 = SwatchColorToken(Swatch.Accent3, Shade.S100)

    val SurfaceLight = Neutral1_500.setLStar(98.0)
    val SurfaceDark = Neutral1_800
    @JvmField val Surface = DayNightColorToken(SurfaceLight, SurfaceDark)

    val SurfaceVariantLight = Neutral2_100
    val SurfaceVariantDark = Neutral1_700

    @JvmField val ColorAccent = DayNightColorToken(Accent1_600, Accent1_100)
    @JvmField val ColorBackground = DayNightColorToken(Neutral1_50, Neutral1_900)
    @JvmField val ColorBackgroundFloating = DayNightColorToken(Neutral1_50, Neutral1_900)
    @JvmField val ColorPrimary = DayNightColorToken(Neutral1_50, Neutral1_900)
    @JvmField val TextColorPrimary = DayNightColorToken(Neutral1_900, Neutral1_50)
    @JvmField val TextColorPrimaryInverse = TextColorPrimary.inverse()
    @JvmField val TextColorSecondary = DayNightColorToken(StaticColorToken(0xde000000), Neutral2_200)

    @JvmField val AllAppsHeaderProtectionColor = DayNightColorToken(Neutral1_100, Neutral1_700)
    @JvmField val AllAppsScrimColor = ColorBackground
    @JvmField val AllAppsTabBackgroundSelected = DayNightColorToken(Accent1_100, Accent2_100)

    @JvmField val FocusHighlight = DayNightColorToken(Neutral1_0, Neutral1_700)
    @JvmField val GroupHighlight = Surface
    @JvmField val OverviewScrim = DayNightColorToken(Neutral2_500.setLStar(87.0), Neutral1_800)
    @JvmField val SearchboxHighlight = DayNightColorToken(SurfaceVariantLight, Neutral1_800)

    @JvmField val FolderDotColor = Accent2_50
    @JvmField val FolderFillColor = DayNightColorToken(Neutral1_50.setLStar(98.0), Neutral2_50.setLStar(30.0))

    @JvmField val PopupColorPrimary = DayNightColorToken(Accent2_50, Neutral2_800)
    @JvmField val PopupColorSecondary = DayNightColorToken(Neutral2_100, Neutral1_900)
    @JvmField val PopupColorTertiary = DayNightColorToken(Neutral2_300, Neutral2_700)

    @JvmField val PopupShadeFirst = DayNightColorToken(PopupColorPrimary.setLStar(98.0), PopupColorPrimary.setLStar(20.0))
    @JvmField val PopupShadeSecond = DayNightColorToken(PopupColorPrimary.setLStar(95.0), PopupColorPrimary.setLStar(15.0))
    @JvmField val PopupShadeThird = DayNightColorToken(PopupColorPrimary.setLStar(90.0), PopupColorPrimary.setLStar(10.0))

    @JvmField val QsbIconTintPrimary = DayNightColorToken(Accent3_50.setLStar(48.0), Accent3_100)
    @JvmField val QsbIconTintSecondary = DayNightColorToken(Accent1_50.setLStar(40.0), Accent1_300)
    @JvmField val QsbIconTintTertiary = DayNightColorToken(Accent2_50.setLStar(48.0), Accent2_50.setLStar(98.0))
    @JvmField val QsbIconTintQuaternary = DayNightColorToken(Accent1_50.setLStar(35.0), Accent1_100)

    @JvmField val WallpaperPopupScrim = Neutral1_900

    @JvmField val WidgetsPickerScrim = DayNightColorToken(Neutral1_200, Neutral1_900).setAlpha(0.8f)

    @JvmField val WorkspaceAccentColor = DarkTextColorToken(Accent1_100, Accent2_600)
}

@Composable
fun colorToken(token: ColorToken): Color {
    val context = LocalContext.current
    val intColor = token.resolveColor(context)
    return Color(intColor)
}

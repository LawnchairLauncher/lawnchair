package app.lawnchair.theme.color.tokens

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
    val Neutral1_400 = SwatchColorToken(Swatch.Neutral1, Shade.S400)
    val Neutral1_500 = SwatchColorToken(Swatch.Neutral1, Shade.S500)
    val Neutral1_700 = SwatchColorToken(Swatch.Neutral1, Shade.S700)
    val Neutral1_800 = SwatchColorToken(Swatch.Neutral1, Shade.S800)
    val Neutral1_900 = SwatchColorToken(Swatch.Neutral1, Shade.S900)

    val Neutral2_50 = SwatchColorToken(Swatch.Neutral2, Shade.S50)
    val Neutral2_100 = SwatchColorToken(Swatch.Neutral2, Shade.S100)
    val Neutral2_200 = SwatchColorToken(Swatch.Neutral2, Shade.S200)
    val Neutral2_300 = SwatchColorToken(Swatch.Neutral2, Shade.S300)
    val Neutral2_500 = SwatchColorToken(Swatch.Neutral2, Shade.S500)
    val Neutral2_600 = SwatchColorToken(Swatch.Neutral2, Shade.S600)
    val Neutral2_700 = SwatchColorToken(Swatch.Neutral2, Shade.S700)
    val Neutral2_800 = SwatchColorToken(Swatch.Neutral2, Shade.S800)
    val Neutral2_900 = SwatchColorToken(Swatch.Neutral2, Shade.S900)

    val Accent1_10 = SwatchColorToken(Swatch.Accent1, Shade.S10)
    val Accent1_50 = SwatchColorToken(Swatch.Accent1, Shade.S50)
    val Accent1_100 = SwatchColorToken(Swatch.Accent1, Shade.S100)
    val Accent1_200 = SwatchColorToken(Swatch.Accent1, Shade.S200)
    val Accent1_300 = SwatchColorToken(Swatch.Accent1, Shade.S300)
    val Accent1_400 = SwatchColorToken(Swatch.Accent1, Shade.S400)
    val Accent1_500 = SwatchColorToken(Swatch.Accent1, Shade.S500)
    val Accent1_600 = SwatchColorToken(Swatch.Accent1, Shade.S600)
    val Accent1_700 = SwatchColorToken(Swatch.Accent1, Shade.S700)
    val Accent1_900 = SwatchColorToken(Swatch.Accent1, Shade.S900)

    val Accent2_50 = SwatchColorToken(Swatch.Accent2, Shade.S50)
    val Accent2_100 = SwatchColorToken(Swatch.Accent2, Shade.S100)
    val Accent2_300 = SwatchColorToken(Swatch.Accent2, Shade.S300)
    val Accent2_500 = SwatchColorToken(Swatch.Accent2, Shade.S500)
    val Accent2_600 = SwatchColorToken(Swatch.Accent2, Shade.S600)
    val Accent2_800 = SwatchColorToken(Swatch.Accent2, Shade.S800)

    val Accent3_10 = SwatchColorToken(Swatch.Accent3, Shade.S10)
    val Accent3_50 = SwatchColorToken(Swatch.Accent3, Shade.S50)
    val Accent3_100 = SwatchColorToken(Swatch.Accent3, Shade.S100)
    val Accent3_200 = SwatchColorToken(Swatch.Accent3, Shade.S200)
    val Accent3_400 = SwatchColorToken(Swatch.Accent3, Shade.S400)
    val Accent3_600 = SwatchColorToken(Swatch.Accent3, Shade.S600)
    val Accent3_800 = SwatchColorToken(Swatch.Accent3, Shade.S800)

    val SurfaceLight = Neutral1_500.setLStar(98.0)
    val SurfaceDark = Neutral1_800

    @JvmField val Surface = DayNightColorToken(SurfaceLight, SurfaceDark)

    val SurfaceVariantLight = Neutral2_100
    val SurfaceVariantDark = Neutral1_700

    @JvmField val ColorAccent = DayNightColorToken(Accent1_600, Accent1_100)

    @JvmField val ColorBackground = DayNightColorToken(Neutral1_50, Neutral1_900)

    @JvmField val ColorBackgroundFloating = DayNightColorToken(Neutral2_50, Neutral2_900)

    @JvmField val ColorPrimary = DayNightColorToken(Neutral1_50, Neutral1_900)

    @JvmField val TextColorPrimary = DayNightColorToken(Neutral1_900, Neutral1_50)

    @JvmField val TextColorPrimaryInverse = TextColorPrimary.inverse()

    @JvmField val TextColorSecondary = DayNightColorToken(StaticColorToken(0xde000000), Neutral2_200)

    @JvmField val AllAppsHeaderProtectionColor = DayNightColorToken(Neutral1_100, Neutral2_600.setLStar(15.0))

    @JvmField val AllAppsScrimColor = ColorBackground

    @JvmField val AllAppsTabBackground = DayNightColorToken(Neutral2_600.setLStar(90.0), Neutral2_600.setLStar(22.0))

    @JvmField val AllAppsTabBackgroundSelected = DayNightColorToken(Accent1_600, Accent1_600.setLStar(80.0))

    @JvmField val FocusHighlight = DayNightColorToken(Neutral1_0, Neutral1_700)

    @JvmField val GroupHighlight = Surface

    @JvmField val OverviewScrimColor = DayNightColorToken(Neutral2_500.setLStar(87.0), Neutral1_800)

    @JvmField val OverviewScrim = DayNightColorToken(Neutral2_500.setLStar(87.0), Neutral1_800)
        .withPreferences { prefs ->
            val translucent = prefs.recentsTranslucentBackground.get()
            val translucentIntensity = prefs.recentsTranslucentBackgroundAlpha.get()
            if (translucent) setAlpha(translucentIntensity) else this
        }

    @JvmField val SearchboxHighlight = DayNightColorToken(Neutral2_600.setLStar(98.0), Neutral1_800)

    @JvmField val FolderDotColor = Accent3_100

    @JvmField val DotColor = Accent3_200

    @JvmField
    val FolderBackgroundColor = DayNightColorToken(
        SwatchColorToken(
            Swatch.Accent2,
            Shade.S200,
        ),
        SwatchColorToken(Swatch.Accent2, Shade.S700),
    )

    @JvmField val FolderIconBorderColor = ColorPrimary

    @JvmField val FolderPaginationColor = DayNightColorToken(Accent1_600, Accent2_100)

    @JvmField val FolderPreviewColor = DayNightColorToken(Accent2_50.setLStar(80.0), Accent2_50.setLStar(30.0))

    @JvmField val PopupColorPrimary = DayNightColorToken(Accent2_50, Neutral2_800)

    @JvmField val PopupColorSecondary = DayNightColorToken(Neutral2_100, Neutral1_900)

    @JvmField val PopupColorTertiary = DayNightColorToken(Neutral2_300, Neutral2_700)

    @JvmField val PopupShadeFirst = DayNightColorToken(PopupColorPrimary.setLStar(98.0), PopupColorPrimary.setLStar(20.0))

    @JvmField val PopupShadeSecond = DayNightColorToken(PopupColorPrimary.setLStar(95.0), PopupColorPrimary.setLStar(15.0))

    @JvmField val PopupShadeThird = DayNightColorToken(PopupColorPrimary.setLStar(90.0), PopupColorPrimary.setLStar(10.0))

    @JvmField val QsbIconTintPrimary = DayNightColorToken(Accent3_400, Accent3_100)

    @JvmField val QsbIconTintSecondary = DayNightColorToken(Accent1_500, Accent1_400)

    @JvmField val QsbIconTintTertiary = DayNightColorToken(Accent2_300, Accent1_10)

    @JvmField val QsbIconTintQuaternary = DayNightColorToken(Accent1_600, Accent1_100)

    @JvmField val WallpaperPopupScrim = Neutral1_900

    @JvmField val WidgetsPickerScrim = DayNightColorToken(Neutral1_200, Neutral1_900).setAlpha(0.8f)

    @JvmField val WorkspaceAccentColor = DarkTextColorToken(Accent1_100, Accent2_600)

    @JvmField val DropTargetHoverTextColor = DarkTextColorToken(Accent1_900, Accent1_100)

    @JvmField val WidgetListRowColor = DayNightColorToken(Neutral1_10, Neutral2_800)

    @JvmField val SurfaceDimColor = DayNightColorToken(Neutral2_600.setLStar(87.0), Neutral2_600.setLStar(6.0))

    @JvmField val SurfaceBrightColor = DayNightColorToken(Neutral2_600.setLStar(98.0), Neutral2_600.setLStar(24.0))

    @JvmField val PrimaryButton = Accent1_600

    @JvmField val WidgetAddButtonBackgroundColor = PrimaryButton

    val SwitchThumbOn = Accent1_100
    val SwitchThumbOff = DayNightColorToken(Neutral2_300, Neutral1_400)
    val SwitchThumbDisabled = DayNightColorToken(Neutral2_100, Neutral1_700)

    val SwitchTrackOn = DayNightColorToken(Accent1_600, Accent2_500.setLStar(51.0))
    val SwitchTrackOff = DayNightColorToken(Neutral2_500.setLStar(45.0), Neutral1_700)

    @JvmField val PredictedPlateColor = Accent1_300
}

@Composable
fun colorToken(token: ColorToken): Color {
    val context = LocalContext.current
    val intColor = token.resolveColor(context)
    return Color(intColor)
}

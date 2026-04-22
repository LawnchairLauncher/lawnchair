/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.NotificationDotsPreference
import app.lawnchair.ui.preferences.components.ThemePreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorContrastWarning
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import app.lawnchair.ui.preferences.components.notificationServiceEnabled
import app.lawnchair.ui.preferences.navigation.GeneralIconPack
import app.lawnchair.ui.preferences.navigation.GeneralIconShape
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.patrykmichalik.opto.core.firstBlocking

@Composable
fun GeneralPreferences() {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val themedIconsAdapter = prefs.themedIcons.getAdapter()
    val drawerThemedIconsAdapter = prefs.drawerThemedIcons.getAdapter()
    val iconShapeAdapter = prefs2.iconShape.getAdapter()

    val currentIconPackName = iconPacks
        .find { it.packageName == preferenceManager().iconPackPackage.get() }
        ?.name
    val themedIconsEnabled = ThemedIconsState.getForSettings(
        themedIcons = themedIconsAdapter.state.value,
        drawerThemedIcons = drawerThemedIconsAdapter.state.value,
    ) != ThemedIconsState.Off
    val iconStyleSubtitle = if (currentIconPackName != null && themedIconsEnabled) {
        stringResource(
            id = R.string.x_and_y,
            currentIconPackName,
            stringResource(id = R.string.themed_icon_title),
        )
    } else {
        currentIconPackName
    }
    val iconShapeSubtitle = iconShapeEntries(context)
        .firstOrNull { it.value == iconShapeAdapter.state.value }
        ?.label?.invoke()
        ?: stringResource(id = R.string.custom)

    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = stringResource(id = R.string.general_label),
    ) {
        PreferenceGroup {
            Item {
                SwitchPreference(
                    adapter = prefs.allowRotation.getAdapter(),
                    label = stringResource(id = R.string.home_screen_rotation_label),
                    description = stringResource(id = R.string.home_screen_rotation_description),
                )
            }
        }
        if (BuildConfig.APPLICATION_ID.contains("nightly")) {
            PreferenceGroup(heading = stringResource(id = R.string.updater)) {
                Item {
                    SwitchPreference(
                        adapter = prefs2.autoUpdaterNightly.getAdapter(),
                        label = stringResource(id = R.string.auto_updater_label),
                        description = stringResource(id = R.string.auto_updater_description),
                    )
                }
            }
        }
        ExpandAndShrink(visible = prefs2.enableFontSelection.asState().value) {
            PreferenceGroup(heading = stringResource(id = R.string.font_label)) {
                Item {
                    FontPreference(
                        fontPref = prefs.fontWorkspace,
                        label = stringResource(R.string.fontWorkspace),
                    )
                }
                Item {
                    FontPreference(
                        fontPref = prefs.fontHeading,
                        label = stringResource(R.string.fontHeading),
                    )
                }
                Item {
                    FontPreference(
                        fontPref = prefs.fontHeadingMedium,
                        label = stringResource(R.string.fontHeadingMedium),
                    )
                }
                Item {
                    FontPreference(
                        fontPref = prefs.fontBody,
                        label = stringResource(R.string.fontBody),
                    )
                }
                Item {
                    FontPreference(
                        fontPref = prefs.fontBodyMedium,
                        label = stringResource(R.string.fontBodyMedium),
                    )
                }
            }
        }
        val wrapAdaptiveIcons = prefs.wrapAdaptiveIcons.getAdapter()

        PreferenceGroup(
            heading = stringResource(id = R.string.icons),
            description = stringResource(id = (R.string.adaptive_icon_background_description)),
            showDescription = wrapAdaptiveIcons.state.value,
        ) {
            Item {
                NavigationActionPreference(
                    label = stringResource(id = R.string.icon_style_label),
                    destination = GeneralIconPack,
                    subtitle = iconStyleSubtitle,
                )
            }
            Item(
                "themed_icon",
                themedIconsEnabled,
            ) {
                SwitchPreference(
                    adapter = prefs.transparentIconBackground.getAdapter(),
                    label = stringResource(id = R.string.transparent_background_icons_label),
                    description = stringResource(id = R.string.transparent_background_icons_description),
                )
            }
            Item {
                NavigationActionPreference(
                    label = stringResource(id = R.string.icon_shape_label),
                    destination = GeneralIconShape(ShapeRoute.APP_SHAPE),
                    subtitle = iconShapeSubtitle,
                    endWidget = {
                        IconShapePreview(iconShape = iconShapeAdapter.state.value)
                    },
                )
            }
            Item {
                SwitchPreference(
                    adapter = wrapAdaptiveIcons,
                    label = stringResource(id = R.string.auto_adaptive_icons_label),
                    description = stringResource(id = R.string.auto_adaptive_icons_description),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs.shadowBGIcons.getAdapter(),
                    label = stringResource(id = R.string.shadow_bg_icons_label),
                )
            }
            Item(
                "wrap_adaptive_icons",
                wrapAdaptiveIcons.state.value,
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.background_lightness_label),
                    adapter = prefs.coloredBackgroundLightness.getAdapter(),
                    valueRange = 0F..1F,
                    step = 0.1f,
                    showAsPercentage = true,
                )
            }
        }

        val accentColorAdapter = prefs2.accentColor.getAdapter()
        val showColorStyle = !(Utilities.ATLEAST_S && accentColorAdapter.state.value == ColorOption.SystemAccent) ||
            !Utilities.ATLEAST_S

        PreferenceGroup(heading = stringResource(id = R.string.colors)) {
            Item { ThemePreference() }
            Item { ColorPreference(preference = prefs2.accentColor) }
            Item(
                "color_style",
                showColorStyle,
            ) { ColorStylePreference(prefs2.colorStyle.getAdapter()) }
        }

        val notificationEnabled by remember { notificationDotsEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val serviceEnabled = notificationServiceEnabled()
        val showNotificationCountAdapter = prefs2.showNotificationCount.getAdapter()
        val showNotificationCount = showNotificationCountAdapter.state.value
        val dotColor = prefs2.notificationDotColor.asState().value
        val dotTextColor = prefs2.notificationDotTextColor.asState().value

        PreferenceGroup(heading = stringResource(id = R.string.notification_dots)) {
            Item { NotificationDotsPreference(enabled = notificationEnabled, serviceEnabled = serviceEnabled) }
            val canDisplayNotificationDot = notificationEnabled && serviceEnabled
            Item(
                "notification_dot_color",
                canDisplayNotificationDot,
            ) { ColorPreference(preference = prefs2.notificationDotColor) }
            Item(
                "notification_dot_counter_toggle",
                canDisplayNotificationDot,
            ) {
                SwitchPreference(
                    adapter = showNotificationCountAdapter,
                    label = stringResource(id = R.string.show_notification_count),
                )
            }
            Item(
                "notification_dot_text_color",
                canDisplayNotificationDot && showNotificationCount,
            ) { ColorPreference(preference = prefs2.notificationDotTextColor) }
            Item(
                "notification_dot_color_contrast_warning",
                canDisplayNotificationDot && showNotificationCount,
            ) {
                NotificationDotColorContrastWarnings(
                    dotColor = dotColor,
                    dotTextColor = dotTextColor,
                )
            }
        }
    }
}

@Composable
private fun ColorStylePreference(
    adapter: PreferenceAdapter<ColorStyle>,
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        ColorStyle.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
            )
        }
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.color_style_label),
        modifier = modifier,
    )
}

@Composable
private fun NotificationDotColorContrastWarnings(
    dotColor: ColorOption,
    dotTextColor: ColorOption,
    modifier: Modifier = Modifier,
) {
    val dotColorIsDynamic = when (dotColor) {
        is ColorOption.SystemAccent,
        is ColorOption.WallpaperPrimary,
        is ColorOption.Default,
        -> true

        else -> false
    }

    if (dotColorIsDynamic && dotTextColor !is ColorOption.Default) {
        WarningPreference(
            text = stringResource(id = R.string.notification_dots_color_contrast_warning_sometimes),
            modifier = modifier,
        )
    } else {
        ColorContrastWarning(
            foregroundColor = dotTextColor,
            backgroundColor = dotColor,
            text = stringResource(id = R.string.notification_dots_color_contrast_warning_always),
            modifier = modifier,
        )
    }
}

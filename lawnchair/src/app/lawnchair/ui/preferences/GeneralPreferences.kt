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

package app.lawnchair.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.ExpandAndShrink
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.IconShapePreview
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.NotificationDotsPreference
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.ui.preferences.components.ThemePreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.iconShapeEntries
import app.lawnchair.ui.preferences.components.iconShapeGraph
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import app.lawnchair.ui.preferences.components.notificationServiceEnabled
import com.android.launcher3.R

object GeneralRoutes {
    const val ICON_PACK = "iconPack"
    const val ICON_SHAPE = "iconShape"
}

fun NavGraphBuilder.generalGraph(route: String) {
    preferenceGraph(route, { GeneralPreferences() }) { subRoute ->
        iconPackGraph(route = subRoute(GeneralRoutes.ICON_PACK))
        iconShapeGraph(route = subRoute(GeneralRoutes.ICON_SHAPE))
    }
}

@Composable
fun GeneralPreferences() {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsState()
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
    } else currentIconPackName
    val iconShapeSubtitle = iconShapeEntries(context)
        .firstOrNull { it.value == iconShapeAdapter.state.value }
        ?.label?.invoke()

    PreferenceLayout(label = stringResource(id = R.string.general_label)) {
        PreferenceGroup {
            SwitchPreference(
                adapter = prefs.allowRotation.getAdapter(),
                label = stringResource(id = R.string.home_screen_rotation_label),
                description = stringResource(id = R.string.home_screen_rotaton_description),
            )
            val enableFontSelection = prefs2.enableFontSelection.asState().value
            if (enableFontSelection) {
                FontPreference(
                    fontPref = prefs.fontWorkspace,
                    label = stringResource(id = R.string.font_label),
                )
            }
        }
        
        val wrapAdaptiveIcons = prefs.wrapAdaptiveIcons.getAdapter()
        PreferenceGroup(
            heading = stringResource(id = R.string.icons),
            description = stringResource(id = (R.string.adaptive_icon_background_description)),
            showDescription = wrapAdaptiveIcons.state.value,
        ) {
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_style),
                destination = subRoute(name = GeneralRoutes.ICON_PACK),
                subtitle = iconStyleSubtitle,
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_shape_label),
                destination = subRoute(name = GeneralRoutes.ICON_SHAPE),
                subtitle = iconShapeSubtitle,
                endWidget = {
                    IconShapePreview(iconShape = iconShapeAdapter.state.value)
                }
            )
            SwitchPreference(
                adapter = wrapAdaptiveIcons,
                label = stringResource(id = R.string.auto_adaptive_icons_label),
                description = stringResource(id = R.string.auto_adaptive_icons_description),
            )
            ExpandAndShrink(visible = wrapAdaptiveIcons.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.background_lightness_label),
                    adapter = prefs.coloredBackgroundLightness.getAdapter(),
                    valueRange = 0F..1F,
                    step = 0.1f,
                    showAsPercentage = true,
                )
            }
        }

        PreferenceGroup(heading = stringResource(id = R.string.colors)) {
            ThemePreference()
            ColorPreference(
                preference = prefs2.accentColor,
                label = stringResource(id = R.string.accent_color),
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.notification_dots)) {
            val enabled by remember { notificationDotsEnabled(context) }.collectAsState(initial = false)
            val serviceEnabled = notificationServiceEnabled()
            NotificationDotsPreference(enabled = enabled, serviceEnabled = serviceEnabled)
            if (enabled && serviceEnabled) {
                SwitchPreference(
                    adapter = prefs2.showNotificationCount.getAdapter(),
                    label = stringResource(id = R.string.show_notification_count),
                )
                ColorPreference(
                    preference = prefs2.notificationDotColor,
                    label = stringResource(id = R.string.notification_dots_color),
                )
            }
        }
    }
}

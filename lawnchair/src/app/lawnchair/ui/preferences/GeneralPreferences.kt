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
import app.lawnchair.ui.preferences.components.AccentColorPreference
import app.lawnchair.ui.preferences.components.ExpandAndShrink
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.IconShapePreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.NotificationDotsPreference
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.ui.preferences.components.ThemePreference
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import app.lawnchair.ui.preferences.components.notificationServiceEnabled
import com.android.launcher3.R

object GeneralRoutes {
    const val ICON_PACK = "iconPack"
}

fun NavGraphBuilder.generalGraph(route: String) {
    preferenceGraph(route, { GeneralPreferences() }) { subRoute ->
        iconPackGraph(route = subRoute(GeneralRoutes.ICON_PACK))
    }
}

@Composable
fun GeneralPreferences() {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsState()
    PreferenceLayout(label = stringResource(id = R.string.general_label)) {
        PreferenceGroup {
            SwitchPreference(
                adapter = prefs.allowRotation.getAdapter(),
                label = stringResource(id = R.string.home_screen_rotation_label),
                description = stringResource(id = R.string.home_screen_rotaton_description),
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_pack),
                destination = subRoute(name = GeneralRoutes.ICON_PACK),
                subtitle = iconPacks.find { it.packageName == preferenceManager().iconPackPackage.get() }?.name,
            )
            IconShapePreference()
            val enableFontSelection = prefs2.enableFontSelection.asState().value
            if (enableFontSelection) {
                FontPreference(
                    fontPref = prefs.fontWorkspace,
                    label = stringResource(id = R.string.font_label),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.notification_dots)) {
            val context = LocalContext.current
            val enabled by remember { notificationDotsEnabled(context) }.collectAsState(initial = false)
            val serviceEnabled = notificationServiceEnabled()
            NotificationDotsPreference(enabled = enabled, serviceEnabled = serviceEnabled)
            if (enabled && serviceEnabled) {
                SwitchPreference(
                    adapter = prefs2.showNotificationCount.getAdapter(),
                    label = stringResource(id = R.string.show_notification_count),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.colors)) {
            ThemePreference()
            AccentColorPreference()
        }
        val wrapAdaptiveIcons = prefs.wrapAdaptiveIcons.getAdapter()
        PreferenceGroup(
            heading = stringResource(id = R.string.auto_adaptive_icons_label),
            description = stringResource(id = (R.string.adaptive_icon_background_description)),
            showDescription = wrapAdaptiveIcons.state.value,
        ) {
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
    }
}

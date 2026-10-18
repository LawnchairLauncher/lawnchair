/*
 * Copyright 2024, Lawnchair
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

package app.lawnchair.ui.preferences.destinations.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.not
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun AdvancedIconPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    val wrapAdaptiveIcons = prefs.wrapAdaptiveIcons.getAdapter()
    val alwaysReloadIcons = prefs2.alwaysReloadIcons.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.advanced_icon_settings_title),
        modifier = modifier,
    ) {
        PreferenceGroup {
            SwitchPreference(
                adapter = alwaysReloadIcons,
                label = stringResource(id = R.string.always_reload_icons_label),
                description = stringResource(id = R.string.always_reload_icons_description),
            )
            ExpandAndShrink(visible = alwaysReloadIcons.state.value) {
                WarningPreference(stringResource(R.string.always_reload_icons_warning))
            }
            SwitchPreference(
                adapter = prefs.shadowBGIcons.getAdapter(),
                label = stringResource(id = R.string.shadow_bg_icons_label),
            )
        }

        PreferenceGroup(
            heading = stringResource(R.string.auto_adaptive_icons_label),
            description = stringResource(id = (R.string.adaptive_icon_background_description)),
            showDescription = wrapAdaptiveIcons.state.value,
        ) {
            SwitchPreference(
                adapter = wrapAdaptiveIcons,
                label = stringResource(id = R.string.reshape_unthemed_icons_title),
                description = stringResource(id = R.string.reshape_unthemed_icons_description),
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

/*
 * Copyright 2021, Lawnchair
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

import androidx.compose.animation.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.*
import com.android.launcher3.R

object GeneralRoutes {
    const val ICON_PACK = "iconPack"
}

@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun NavGraphBuilder.generalGraph(route: String) {
    preferenceGraph(route, { GeneralPreferences() }) { subRoute ->
        iconPackGraph(route = subRoute(GeneralRoutes.ICON_PACK))
    }
}

@ExperimentalMaterialApi
@ExperimentalAnimationApi
@Composable
fun GeneralPreferences() {
    val prefs = preferenceManager()
    PreferenceLayout(label = stringResource(id = R.string.general_label)) {
        PreferenceGroup(isFirstChild = true) {
            SwitchPreference(
                adapter = prefs.allowRotation.getAdapter(),
                label = stringResource(id = R.string.home_screen_rotation_label),
                description = stringResource(id = R.string.home_screen_rotaton_description),
            )
            NotificationDotsPreference()
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_pack),
                destination = subRoute(name = GeneralRoutes.ICON_PACK),
                subtitle =
                LocalPreferenceInteractor.current.getIconPacks()
                    .find { it.packageName == preferenceManager().iconPackPackage.get() }?.name
            )
            SwitchPreference(
                adapter = prefs.themedIcons.getAdapter(),
                label = stringResource(id = R.string.themed_icon_title)
            )
            IconShapePreference()
            FontPreference(
                adapter = prefs.workspaceFont.getAdapter(),
                label = stringResource(id = R.string.font_label)
            )
        }
        PreferenceGroup(
            heading = stringResource(id = R.string.colors)
        ) {
            ThemePreference()
            AccentColorPreference()
        }
        val wrapAdaptiveIcons = prefs.wrapAdaptiveIcons.getAdapter()
        PreferenceGroup(
            heading = stringResource(id = R.string.auto_adaptive_icons_label),
            description = stringResource(id = (R.string.adaptive_icon_background_description)),
            showDescription = wrapAdaptiveIcons.state.value
        ) {
            SwitchPreference(
                adapter = wrapAdaptiveIcons,
                label = stringResource(id = R.string.auto_adaptive_icons_label),
                description = stringResource(id = R.string.auto_adaptive_icons_description),
            )
            AnimatedVisibility(
                visible = wrapAdaptiveIcons.state.value,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.background_lightness_label),
                    adapter = prefs.coloredBackgroundLightness.getAdapter(),
                    valueRange = 0F..1F,
                    step = 0.1f,
                    showAsPercentage = true
                )
            }
        }
    }
}

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

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.Meta
import app.lawnchair.util.pageMeta
import app.lawnchair.util.preferences.getAdapter
import app.lawnchair.util.preferences.observeAsState
import app.lawnchair.util.preferences.preferenceManager
import com.android.launcher3.R

object GeneralRoutes {
    const val ICON_PACK = "iconPack"
}

@ExperimentalAnimationApi
fun NavGraphBuilder.generalGraph(route: String) {
    preferenceGraph(route, { GeneralPreferences() }) { subRoute ->
        iconPackGraph(route = subRoute(GeneralRoutes.ICON_PACK))
    }
}

@ExperimentalAnimationApi
@Composable
fun GeneralPreferences() {
    val prefs = preferenceManager()
    pageMeta.provide(Meta(title = stringResource(id = R.string.general_label)))
    PreferenceLayout {
        PreferenceGroup(isFirstChild = true) {
            SwitchPreference(
                adapter = prefs.allowRotation.getAdapter(),
                label = stringResource(id = R.string.home_screen_rotation_label),
                description = stringResource(id = R.string.home_screen_rotaton_description)
            )
            NotificationDotsPreference()
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_pack),
                destination = subRoute(name = GeneralRoutes.ICON_PACK),
                showDivider = false,
                subtitle =
                LocalPreferenceInteractor.current.getIconPacks()
                    .find { it.packageName == preferenceManager().iconPackPackage.get() }?.name
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val wrapAdaptiveIcons = prefs.wrapAdaptiveIcons.observeAsState()
            PreferenceGroup(
                heading = stringResource(id = R.string.auto_adaptive_icons_label),
                description = stringResource(id = (R.string.adaptive_icon_background_description)),
                showDescription = wrapAdaptiveIcons.value
            ) {
                SwitchPreference(
                    adapter = prefs.wrapAdaptiveIcons.getAdapter(),
                    label = stringResource(id = R.string.auto_adaptive_icons_label),
                    description = stringResource(id = R.string.auto_adaptive_icons_description),
                    showDivider = wrapAdaptiveIcons.value
                )
                AnimatedVisibility(
                    visible = wrapAdaptiveIcons.value,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SliderPreference(
                        label = stringResource(id = R.string.background_lightness_label),
                        adapter = prefs.coloredBackgroundLightness.getAdapter(),
                        valueRange = 0F..1F,
                        steps = 9,
                        showAsPercentage = true,
                        showDivider = false
                    )
                }
            }
        }
    }
}

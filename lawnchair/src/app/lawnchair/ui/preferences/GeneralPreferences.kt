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

import androidx.compose.animation.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.Constants.LAWNICONS_PACKAGE_NAME
import app.lawnchair.util.ifNotNull
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.state

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

interface GeneralPreferenceCollectorScope : PreferenceCollectorScope {
    val iconShape: IconShape
    val accentColor: ColorOption
}

@Composable
fun GeneralPreferenceCollector(content: @Composable GeneralPreferenceCollectorScope.() -> Unit) {
    val preferenceManager = preferenceManager2()
    val iconShape by preferenceManager.iconShape.state()
    val accentColor by preferenceManager.accentColor.state()
    ifNotNull(iconShape, accentColor) {
        object : GeneralPreferenceCollectorScope {
            override val iconShape = it[0] as IconShape
            override val accentColor = it[1] as ColorOption
            override val coroutineScope = rememberCoroutineScope()
            override val preferenceManager = preferenceManager
        }.content()
    }
}

@ExperimentalMaterialApi
@ExperimentalAnimationApi
@Composable
fun GeneralPreferences() {
    val prefs = preferenceManager()
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsState()
    val themedIconsAvailable = LocalContext.current.packageManager.isPackageInstalled(LAWNICONS_PACKAGE_NAME)
    GeneralPreferenceCollector {
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
                    subtitle = iconPacks.find { it.packageName == preferenceManager().iconPackPackage.get() }?.name,
                )
                SwitchPreference(
                    adapter = prefs.themedIcons.getAdapter(),
                    label = stringResource(id = R.string.themed_icon_title),
                    enabled = themedIconsAvailable,
                    description = if (!themedIconsAvailable) stringResource(id = R.string.lawnicons_not_installed_description) else null,
                )
                IconShapePreference(
                    value = iconShape,
                    edit = { iconShape.set(value = it) },
                )
                if (prefs.enableFontSelection.get()) {
                    FontPreference(
                        adapter = prefs.workspaceFont.getAdapter(),
                        label = stringResource(id = R.string.font_label),
                    )
                }
            }
            PreferenceGroup(heading = stringResource(id = R.string.colors)) {
                ThemePreference()
                AccentColorPreference(
                    value = accentColor,
                    edit = { accentColor.set(value = it) },
                )
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
                        showAsPercentage = true,
                    )
                }
            }
        }
    }
}

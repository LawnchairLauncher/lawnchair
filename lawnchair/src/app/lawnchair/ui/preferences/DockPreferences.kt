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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.ifNotNull
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.state

@ExperimentalAnimationApi
fun NavGraphBuilder.dockGraph(route: String) {
    preferenceGraph(route, { DockPreferences() })
}

interface DockPreferenceCollectorScope : PreferenceCollectorScope {
    val hotseatQsb: Boolean
    val themedHotseatQsb: Boolean
    val hotseatQsbUseWebsite: Boolean
    val hotseatQsbProvider: QsbSearchProvider
}

@Composable
fun DockPreferenceCollector(content: @Composable DockPreferenceCollectorScope.() -> Unit) {
    val preferenceManager = preferenceManager2()
    val hotseatQsb by preferenceManager.hotseatQsb.state()
    val themedHotseatQsb by preferenceManager.themedHotseatQsb.state()
    val hotseatQsbUseWebsite by preferenceManager.hotseatQsbForceWebsite.state()
    val hotseatQsbProvider by preferenceManager.hotseatQsbProvider.state()

    ifNotNull(
        hotseatQsb,
        themedHotseatQsb,
        hotseatQsbUseWebsite,
        hotseatQsbProvider,
    ) { preferences ->
        object : DockPreferenceCollectorScope {
            override val hotseatQsb = preferences[0] as Boolean
            override val themedHotseatQsb = preferences[1] as Boolean
            override val hotseatQsbUseWebsite = preferences[2] as Boolean
            override val hotseatQsbProvider = preferences[3] as QsbSearchProvider
            override val coroutineScope = rememberCoroutineScope()
            override val preferenceManager = preferenceManager
        }.content()
    }
}

@ExperimentalAnimationApi
@Composable
fun DockPreferences() {
    val prefs = preferenceManager()
    DockPreferenceCollector {
        PreferenceLayout(label = stringResource(id = R.string.dock_label)) {
            PreferenceGroup(
                isFirstChild = true,
                heading = stringResource(id = R.string.search_bar_label),
            ) {
                SwitchPreference2(
                    checked = hotseatQsb,
                    label = stringResource(id = R.string.hotseat_qsb_label),
                    edit = { hotseatQsb.set(value = it) },
                )
                AnimatedVisibility(
                    visible = hotseatQsb,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    DividerColumn {
                        SwitchPreference2(
                            checked = themedHotseatQsb,
                            label = stringResource(id = R.string.apply_accent_color_label),
                            edit = { themedHotseatQsb.set(value = it) },
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.corner_radius_label),
                            adapter = prefs.hotseatQsbCornerRadius.getAdapter(),
                            step = 0.1F,
                            valueRange = 0F..1F,
                            showAsPercentage = true,
                        )
                        QsbProviderPreference(
                            value = hotseatQsbProvider,
                            edit = { hotseatQsbProvider.set(value = it) },
                        )
                        SwitchPreference2(
                            checked = hotseatQsbUseWebsite,
                            label = stringResource(R.string.always_open_website_label),
                            description = stringResource(R.string.always_open_website_description),
                            edit = { hotseatQsbForceWebsite.set(value = it) },
                        )
                    }
                }
            }
            PreferenceGroup(heading = stringResource(id = R.string.grid)) {
                SliderPreference(
                    label = stringResource(id = R.string.dock_icons),
                    adapter = prefs.hotseatColumns.getAdapter(),
                    step = 1,
                    valueRange = 3..10,
                )
            }
        }
    }
}

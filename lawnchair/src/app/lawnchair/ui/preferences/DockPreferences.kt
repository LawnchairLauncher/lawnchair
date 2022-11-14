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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.hotseat.HotseatMode
import app.lawnchair.hotseat.LawnchairHotseat
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.ui.preferences.components.DividerColumn
import app.lawnchair.ui.preferences.components.ExpandAndShrink
import app.lawnchair.ui.preferences.components.ListPreference
import app.lawnchair.ui.preferences.components.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import app.lawnchair.ui.preferences.components.SwitchPreference
import com.android.launcher3.R

object DockRoutes {
    const val SEARCH_PROVIDER = "searchProvider"
}

fun NavGraphBuilder.dockGraph(route: String) {
    preferenceGraph(route, { DockPreferences() }) { subRoute ->
        searchProviderGraph(subRoute(DockRoutes.SEARCH_PROVIDER))
    }
}

@Composable
fun DockPreferences() {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    PreferenceLayout(label = stringResource(id = R.string.dock_label)) {
        PreferenceGroup(heading = stringResource(id = R.string.search_bar_label)) {
            DividerColumn {
                val hotseatModeAdapter = prefs2.hotseatMode.getAdapter()
                HotseatModePreference(
                    adapter = hotseatModeAdapter,
                )
                ExpandAndShrink(visible = hotseatModeAdapter.state.value == LawnchairHotseat) {
                    DividerColumn {
                        SwitchPreference(
                            adapter = prefs2.themedHotseatQsb.getAdapter(),
                            label = stringResource(id = R.string.apply_accent_color_label),
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.corner_radius_label),
                            adapter = prefs.hotseatQsbCornerRadius.getAdapter(),
                            step = 0.05F,
                            valueRange = 0F..1F,
                            showAsPercentage = true,
                        )
                        val hotseatQsbProviderAdapter by preferenceManager2().hotseatQsbProvider.getAdapter()
                        NavigationActionPreference(
                            label = stringResource(R.string.search_provider),
                            destination = subRoute(DockRoutes.SEARCH_PROVIDER),
                            subtitle = stringResource(
                                id = QsbSearchProvider.values()
                                    .first { it == hotseatQsbProviderAdapter }
                                    .name,
                            ),
                        )
                    }
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

@Composable
private fun HotseatModePreference(
    adapter: PreferenceAdapter<HotseatMode>,
) {

    val context = LocalContext.current

    val entries = remember {
        HotseatMode.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
                enabled = mode.isAvailable(context = context),
            )
        }
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.hotseat_mode_label),
    )
}

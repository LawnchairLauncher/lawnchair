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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.hotseat.HotseatMode
import app.lawnchair.hotseat.LawnchairHotseat
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R
import kotlinx.collections.immutable.toPersistentList

object DockRoutes {
    const val SEARCH_PROVIDER = "searchProvider"
}

@Composable
fun DockPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    PreferenceLayout(
        label = stringResource(id = R.string.dock_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val isHotseatEnabled = prefs2.isHotseatEnabled.getAdapter()
        val hotseatModeAdapter = prefs2.hotseatMode.getAdapter()
        MainSwitchPreference(adapter = isHotseatEnabled, label = stringResource(id = R.string.show_hotseat_title)) {
            PreferenceGroup(heading = stringResource(id = R.string.search_bar_label)) {
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
                            destination = DockRoutes.SEARCH_PROVIDER,
                            subtitle = stringResource(
                                id = QsbSearchProvider.values()
                                    .first { it == hotseatQsbProviderAdapter }
                                    .name,
                            ),
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.search_background_transparency),
                            adapter = prefs.searchBackgroundHotseatTransparency.getAdapter(),
                            step = 5,
                            valueRange = 0..100,
                            showUnit = "%",
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.stroke_width),
                            adapter = prefs.searchStrokeWidth.getAdapter(),
                            step = 1f,
                            valueRange = 0f..10f,
                            showUnit = "vw",
                        )
                        ColorPreference(preference = prefs2.strokeColorStyle)
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
                SliderPreference(
                    adapter = prefs2.hotseatBottomFactor.getAdapter(),
                    label = stringResource(id = R.string.hotseat_bottom_space_label),
                    valueRange = 0.0F..1.7F,
                    step = 0.1F,
                    showAsPercentage = true,
                )
            }
        }
    }
}

@Composable
private fun HotseatModePreference(
    adapter: PreferenceAdapter<HotseatMode>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val entries = remember {
        HotseatMode.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
                enabled = mode.isAvailable(context = context),
            )
        }.toPersistentList()
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.hotseat_mode_label),
        modifier = modifier,
    )
}

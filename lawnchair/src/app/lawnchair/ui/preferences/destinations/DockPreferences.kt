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

import android.content.res.Configuration
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.DummyLauncherLayout
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.clipToBottomPercentage
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.createPreviewIdp
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

object DockRoutes {
    const val SEARCH_PROVIDER = "searchProvider"
}

@Composable
fun DockPreferences(modifier: Modifier = Modifier) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    PreferenceLayout(
        label = stringResource(id = R.string.dock_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val hotseatBgAdapter = prefs.hotseatBG.getAdapter()

        MainSwitchPreference(adapter = prefs2.isHotseatEnabled.getAdapter(), label = stringResource(id = R.string.show_hotseat_title)) {
            DockPreferencesPreview()
            PreferenceGroup(heading = stringResource(id = R.string.style)) {
                SwitchPreference(
                    adapter = hotseatBgAdapter,
                    label = stringResource(id = R.string.hotseat_background),
                )
                ExpandAndShrink(visible = hotseatBgAdapter.state.value) {
                    HotseatBackgroundSettings(prefs, prefs2)
                }
            }
            SearchBarPreference(SearchRoute.DOCK_SEARCH)
            PreferenceGroup(heading = stringResource(id = R.string.grid)) {
                GridSettings(prefs, prefs2)
            }
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                SwitchPreference(
                    adapter = prefs2.enableLabelInDock.getAdapter(),
                    label = stringResource(id = R.string.show_labels),
                )
            }
        }
    }
}

@Composable
fun HotseatBackgroundSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    DividerColumn {
        ColorPreference(preference = prefs2.hotseatBackgroundColor)
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_alpha),
            adapter = prefs.hotseatBGAlpha.getAdapter(),
            step = 5,
            valueRange = 5..100,
            showUnit = "%",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_left),
            adapter = prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_right),
            adapter = prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_top),
            adapter = prefs.hotseatBGVerticalInsetTop.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_bottom),
            adapter = prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
    }
}

@Composable
fun GridSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
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
    SliderPreference(
        adapter = prefs2.pageIndicatorHeightFactor.getAdapter(),
        label = stringResource(id = R.string.page_indicator_height),
        valueRange = 0.0F..1.0F,
        step = 0.1F,
        showAsPercentage = true,
    )
}

@Composable
fun ColumnScope.DockPreferencesPreview(modifier: Modifier = Modifier) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()

        val adapters = listOf(
            prefs2.hotseatMode.getAdapter(),
            prefs.hotseatColumns.getAdapter(),
            prefs2.themedHotseatQsb.getAdapter(),
            prefs.hotseatQsbCornerRadius.getAdapter(),
            prefs.hotseatQsbAlpha.getAdapter(),
            prefs.hotseatQsbStrokeWidth.getAdapter(),
            prefs2.hotseatBottomFactor.getAdapter(),
            prefs2.strokeColorStyle.getAdapter(),
            prefs2.enableLabelInDock.getAdapter(),
            prefs.hotseatBG.getAdapter(),
            prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            prefs.hotseatBGVerticalInsetTop.getAdapter(),
            prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            prefs2.pageIndicatorHeightFactor.getAdapter(),
            prefs2.hotseatBackgroundColor.getAdapter(),
            prefs.hotseatBGAlpha.getAdapter(),
        )

        PreferenceGroupHeading(
            heading = stringResource(id = R.string.preview_label),
        )
        DividerColumn(
            modifier = modifier.padding(horizontal = 16.dp),
        ) {
            WithWallpaper { wallpaper ->
                DummyLauncherBox(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterHorizontally)
                        .clip(MaterialTheme.shapes.large)
                        .clipToBottomPercentage(0.3f),
                ) {
                    WallpaperPreview(
                        wallpaper = wallpaper,
                        modifier = Modifier.fillMaxSize(),
                    )
                    key(adapters.map { it.state.value }.toTypedArray()) {
                        DummyLauncherLayout(
                            idp = createPreviewIdp { copy(numHotseatColumns = prefs.hotseatColumns.get()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

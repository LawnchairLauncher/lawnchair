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

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.LawnchairApp
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorMode
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.FeedPreference
import app.lawnchair.ui.preferences.components.GestureHandlerPreference
import app.lawnchair.ui.preferences.components.HomeLayoutSettings
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.OverlayHandlerPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.HomeScreenGrid
import app.lawnchair.util.collectAsStateBlocking
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.celllayout.CellPosMapper
import kotlinx.coroutines.launch

object HomeScreenRoutes {
    const val GRID = "grid"
    const val POPUP_EDITOR = "popup_editor"
}

@Composable
fun HomeScreenPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    PreferenceLayout(
        label = stringResource(id = R.string.home_screen_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val lockHomeScreenAdapter = prefs2.lockHomeScreen.getAdapter()
        val showDeckLayout = prefs2.showDeckLayout.getAdapter().state.value

        if (showDeckLayout) {
            HomeLayoutSettings()
        }

        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            val addIconToHomeAdapter = prefs.addIconToHome.getAdapter()
            val isDeckLayoutAdapter = prefs2.deckLayout.getAdapter()
            Item(
                "add_icon_to_home",
                !isDeckLayoutAdapter.state.value,
            ) {
                SwitchPreference(
                    checked = (!lockHomeScreenAdapter.state.value && addIconToHomeAdapter.state.value) || isDeckLayoutAdapter.state.value,
                    onCheckedChange = addIconToHomeAdapter::onChange,
                    label = stringResource(id = R.string.auto_add_shortcuts_label),
                    description = if (lockHomeScreenAdapter.state.value) stringResource(id = R.string.home_screen_locked) else null,
                    enabled = lockHomeScreenAdapter.state.value.not(),
                )
            }
            Item {
                GestureHandlerPreference(
                    adapter = prefs2.doubleTapGestureHandler.getAdapter(),
                    label = stringResource(id = R.string.gesture_double_tap),
                )
            }
            Item {
                SwitchPreference(
                    prefs.infiniteScrolling.getAdapter(),
                    label = stringResource(id = R.string.infinite_scrolling_label),
                    description = stringResource(id = R.string.infinite_scrolling_description),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.home_screen_actions)) {
            Item {
                ClickablePreference(
                    label = stringResource(id = R.string.remove_all_views_from_home_screen),
                    confirmationText = stringResource(id = R.string.remove_all_views_from_home_screen_desc),
                    onClick = {
                        scope.launch {
                            clearAllViewsFromHomeScreen(context, LauncherSettings.Favorites.CONTAINER_DESKTOP)
                        }
                    },
                )
            }
        }
        val feedAvailable = OverlayCallbackImpl.minusOneAvailable(LocalContext.current)
        val enableFeedAdapter = prefs2.enableFeed.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.minus_one)) {
            Item {
                SwitchPreference(
                    adapter = enableFeedAdapter,
                    label = stringResource(id = R.string.minus_one_enable),
                    description = if (feedAvailable) null else stringResource(id = R.string.minus_one_unavailable),
                    enabled = feedAvailable,
                )
            }
            Item(
                key = "feed_pref",
                visible = feedAvailable && enableFeedAdapter.state.value,
            ) {
                FeedPreference()
            }
        }
        PreferenceGroup(heading = stringResource(R.string.style)) {
            Item { HomeScreenTextColorPreference() }
            Item {
                OverlayHandlerPreference(
                    adapter = prefs2.closingAppOverlay.getAdapter(),
                    label = stringResource(id = R.string.app_closing_animation),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.wallpaper)) {
            Item {
                SwitchPreference(
                    prefs.wallpaperScrolling.getAdapter(),
                    label = stringResource(id = R.string.wallpaper_scrolling_label),
                )
            }
            Item(
                "wallpaper_depth_effect",
                Utilities.ATLEAST_R,
            ) {
                SwitchPreference(
                    prefs2.wallpaperDepthEffect.getAdapter(),
                    label = stringResource(id = R.string.wallpaper_depth_effect_label),
                    description = stringResource(id = R.string.wallpaper_depth_effect_description),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs2.showTopShadow.getAdapter(),
                    label = stringResource(id = R.string.show_sys_ui_scrim),
                )
            }
        }
        val columns by prefs.workspaceColumns.getAdapter()
        val rows by prefs.workspaceRows.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.layout)) {
            Item {
                NavigationActionPreference(
                    label = stringResource(id = R.string.home_screen_grid),
                    destination = HomeScreenGrid,
                    subtitle = stringResource(id = R.string.x_by_y, columns, rows),
                )
            }
            Item {
                SwitchPreference(
                    adapter = lockHomeScreenAdapter,
                    label = stringResource(id = R.string.home_screen_lock),
                    description = stringResource(id = R.string.home_screen_lock_description),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.popup_menu)) {
            Item { LauncherPopupPreferenceItem() }
        }
        val showStatusBarAdapter = prefs2.showStatusBar.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.status_bar_label)) {
            Item {
                SwitchPreference(
                    adapter = showStatusBarAdapter,
                    label = stringResource(id = R.string.show_status_bar),
                )
            }
            Item(
                "dark_status_bar",
                showStatusBarAdapter.state.value,
            ) {
                SwitchPreference(
                    adapter = prefs2.darkStatusBar.getAdapter(),
                    label = stringResource(id = R.string.dark_status_bar_label),
                )
            }
            Item(
                "status_bar_clock",
                showStatusBarAdapter.state.value && LawnchairApp.isRecentsEnabled,
            ) {
                SwitchPreference(
                    adapter = prefs2.statusBarClock.getAdapter(),
                    label = stringResource(id = R.string.status_bar_clock_label),
                    description = stringResource(id = R.string.status_bar_clock_description),
                )
            }
        }
        val homeScreenLabelsAdapter = prefs2.showIconLabelsOnHomeScreen.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            Item {
                SliderPreference(
                    label = stringResource(id = R.string.icon_sizes),
                    adapter = prefs2.homeIconSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
            Item {
                SwitchPreference(
                    adapter = homeScreenLabelsAdapter,
                    label = stringResource(id = R.string.show_labels),
                )
            }
            Item(
                "workspace_label_size",
                homeScreenLabelsAdapter.state.value,
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.homeIconLabelSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
        }
        val overrideRepo = IconOverrideRepository.INSTANCE.get(LocalContext.current)
        val customIconsCount by remember { overrideRepo.observeCount() }.collectAsStateBlocking()
        if (customIconsCount > 0) {
            PreferenceGroup {
                Item {
                    ClickablePreference(
                        label = stringResource(id = R.string.reset_custom_icons),
                        confirmationText = stringResource(id = R.string.reset_custom_icons_confirmation),
                        onClick = { scope.launch { overrideRepo.deleteAll() } },
                    )
                }
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.widget_button_text)) {
            Item {
                SwitchPreference(
                    adapter = prefs2.roundedWidgets.getAdapter(),
                    label = stringResource(id = R.string.force_rounded_widgets),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs2.allowWidgetOverlap.getAdapter(),
                    label = stringResource(id = R.string.allow_widget_overlap),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs2.widgetUnlimitedSize.getAdapter(),
                    label = stringResource(id = R.string.widget_unlimited_size_label),
                    description = stringResource(id = R.string.widget_unlimited_size_description),
                )
            }
            Item {
                SwitchPreference(
                    adapter = prefs2.forceWidgetResize.getAdapter(),
                    label = stringResource(id = R.string.force_widget_resize_label),
                    description = stringResource(id = R.string.force_widget_resize_description),
                )
            }
        }
    }
}

private fun clearAllViewsFromHomeScreen(context: Context, type: Int) {
    val launcherModel = LauncherAppState.getInstance(context).model
    val modelWriter = launcherModel.getWriter(
        verifyChanges = false,
        cellPosMapper = CellPosMapper.DEFAULT,
        owner = null,
    )
    val isViewsRemoved = modelWriter.clearAllHomeScreenViewsByType(type)
    if (isViewsRemoved) {
        launcherModel.forceReload()
        Toast.makeText(
            context,
            R.string.home_screen_all_views_removed_msg,
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@Composable
fun HomeScreenTextColorPreference(
    modifier: Modifier = Modifier,
) {
    ListPreference(
        adapter = preferenceManager2().workspaceTextColor.getAdapter(),
        entries = ColorMode.entries(),
        label = stringResource(id = R.string.home_screen_text_color),
        modifier = modifier,
    )
}

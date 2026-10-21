package app.lawnchair.ui.preferences.destinations

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.fold.FoldAfterFold
import app.lawnchair.fold.FoldDock
import app.lawnchair.fold.FoldDrawerLayout
import app.lawnchair.fold.FoldHomeLayout
import app.lawnchair.fold.FoldLandscape
import app.lawnchair.fold.FoldOption
import app.lawnchair.fold.FoldPageOrder
import app.lawnchair.fold.FoldTransitionStyle
import app.lawnchair.fold.FoldWallpaper
import app.lawnchair.fold.FoldWallpaperOffsets
import app.lawnchair.fold.HingeAngleProvider
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.rememberTransformAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.FoldDualWallpaperPreferences
import app.lawnchair.ui.preferences.components.FoldMirrorNote
import app.lawnchair.ui.preferences.components.FoldNote
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.fold.FoldPickerPreference
import app.lawnchair.ui.preferences.components.fold.FoldPreviewHeader
import app.lawnchair.ui.preferences.components.fold.hasFoldPreview
import app.lawnchair.ui.preferences.components.isFoldHomeIndependent
import app.lawnchair.ui.preferences.components.isFoldHomeMirrored
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.ProvideDescriptionTextStyle
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import kotlin.math.abs

@Composable
fun FoldablePreferences(
    modifier: Modifier = Modifier,
) {
    val prefs2 = preferenceManager2()
    val wallpaper = prefs2.foldWallpaper.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.fold_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            FoldPreviewHeader(
                home = prefs2.foldHomeLayout.getAdapter().state.value,
                animation = prefs2.foldTransitionStyle.getAdapter().state.value,
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.fold_group_large_screen)) {
            val homeLayout = prefs2.foldHomeLayout.getAdapter()
            val drawerLayout = prefs2.foldDrawerLayout.getAdapter()
            FoldListPreference(
                adapter = homeLayout,
                options = FoldHomeLayout.entries,
                label = stringResource(id = R.string.fold_home_screen),
            )
            // "Same as cover" shows one page and "Independent" has its own pages; the "Two pages"
            // drawer follows the order too
            val homePages = homeLayout.state.value.let { it == FoldHomeLayout.PAGES || it == FoldHomeLayout.EXPAND }
            ExpandAndShrink(visible = homePages || drawerLayout.state.value == FoldDrawerLayout.PAGES) {
                FoldListPreference(
                    adapter = prefs2.foldPageOrder.getAdapter(),
                    options = FoldPageOrder.entries,
                    label = stringResource(id = R.string.fold_page_order),
                )
            }
            FoldListPreference(
                adapter = drawerLayout,
                options = FoldDrawerLayout.entries,
                label = stringResource(id = R.string.fold_app_drawer),
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.fold_group_transition)) {
            FoldListPreference(
                adapter = prefs2.foldTransitionStyle.getAdapter(),
                options = FoldTransitionStyle.entries,
                label = stringResource(id = R.string.fold_animation),
            )
            // A sensor that reports only open and closed can't be followed: the timer plays instead
            val context = LocalContext.current
            val postureOnly = remember { HingeAngleProvider.isPostureOnly(context) }
            SwitchPreference(
                adapter = prefs2.foldTrackHinge.getAdapter(),
                label = stringResource(id = R.string.fold_track_hinge),
                description = stringResource(id = R.string.fold_track_hinge_description),
                enabled = !postureOnly,
            )
            if (postureOnly) {
                FoldNote(text = stringResource(id = R.string.fold_track_hinge_posture_only))
            }
        }

        val homeIndependent = isFoldHomeIndependent()
        PreferenceGroup(heading = stringResource(id = R.string.fold_group_after_fold)) {
            FoldListPreference(
                adapter = prefs2.foldAfterFold.getAdapter(),
                options = FoldAfterFold.entries,
                label = stringResource(id = R.string.fold_cover_opens_on),
                enabled = !homeIndependent,
            )
            ExpandAndShrink(visible = homeIndependent) {
                FoldNote(text = stringResource(id = R.string.fold_independent_after_fold_note))
            }
            FoldInfoPreference(text = stringResource(id = R.string.fold_extra_dock_info))
        }

        PreferenceGroup(heading = stringResource(id = R.string.fold_group_advanced)) {
            FoldListPreference(
                adapter = prefs2.foldDock.getAdapter(),
                options = FoldDock.entries,
                label = stringResource(id = R.string.fold_dock),
            )
            ExpandAndShrink(visible = homeIndependent) {
                FoldNote(text = stringResource(id = R.string.fold_independent_dock_note))
            }
            val landscapeAdapter = prefs2.foldLandscape.getAdapter()
            FoldListPreference(
                adapter = landscapeAdapter,
                options = FoldLandscape.entries,
                label = stringResource(id = R.string.fold_landscape),
            )
            val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
            val landscapeOwn = isFoldable && landscapeAdapter.state.value == FoldLandscape.OWN
            // "Same as cover" shows one page, which doesn't use the two-panel landscape profile
            val homeMirrored = isFoldHomeMirrored()
            ExpandAndShrink(visible = landscapeOwn) {
                FoldLandscapeSizePreference(
                    label = stringResource(id = R.string.fold_landscape_icon_size),
                    ownAdapter = prefs2.foldLandscapeIconSizeFactor.getAdapter(),
                    portraitAdapter = prefs2.homeIconSizeFactor.getAdapter(),
                    enabled = !homeMirrored,
                )
            }
            val showLabels = prefs2.showIconLabelsOnHomeScreen.getAdapter().state.value
            ExpandAndShrink(visible = landscapeOwn && showLabels) {
                FoldLandscapeSizePreference(
                    label = stringResource(id = R.string.fold_landscape_label_size),
                    ownAdapter = prefs2.foldLandscapeIconLabelSizeFactor.getAdapter(),
                    portraitAdapter = prefs2.homeIconLabelSizeFactor.getAdapter(),
                    enabled = !homeMirrored,
                )
            }
            ExpandAndShrink(visible = landscapeOwn && homeMirrored) {
                FoldMirrorNote()
            }
            FoldListPreference(
                adapter = wallpaper,
                options = FoldWallpaper.entries,
                label = stringResource(id = R.string.fold_wallpaper),
            )
            if (wallpaper.state.value == FoldWallpaper.EACH && FoldWallpaperOffsets.systemHasWallpaperPerScreen) {
                FoldInfoPreference(text = stringResource(id = R.string.fold_wallpaper_each_system_note))
            }
        }

        // Elsewhere Lawnchair's dual live wallpaper shows one image per screen. It needs API 28,
        // which every foldable has; older devices only open this page with fold_force_show_settings.
        if (wallpaper.state.value == FoldWallpaper.EACH && !FoldWallpaperOffsets.systemHasWallpaperPerScreen &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            FoldDualWallpaperPreferences()
        }
    }
}

/**
 * A list preference for a foldable option. Options with a preview (home screen, page order, app
 * drawer, animation, dock) show it and are picked from cards; the others show the summary of the
 * selected option under the label.
 */
@Composable
private fun <T> FoldListPreference(
    adapter: PreferenceAdapter<T>,
    options: List<T>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) where T : Enum<T>, T : FoldOption {
    if (hasFoldPreview(adapter.state.value)) {
        FoldPickerPreference(adapter, options, label, modifier, enabled)
        return
    }
    val entries = remember(options) {
        options.map { option ->
            ListPreferenceEntry(value = option) {
                stringResource(id = option.labelResourceId)
            }
        }
    }
    val selected = adapter.state.value

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = label,
        modifier = modifier,
        enabled = enabled,
        description = stringResource(
            id = R.string.fold_option_summary,
            stringResource(id = selected.labelResourceId),
            stringResource(id = selected.descriptionResourceId),
        ),
    )
}

/**
 * A size factor of the large screen in landscape ([FoldLandscape.OWN]). Its stored default, a
 * negative value, means "as in portrait": the slider then shows the portrait value, and choosing the
 * portrait value again goes back to following it.
 */
@Composable
private fun FoldLandscapeSizePreference(
    label: String,
    ownAdapter: PreferenceAdapter<Float>,
    portraitAdapter: PreferenceAdapter<Float>,
    enabled: Boolean,
) {
    val adapter = rememberTransformAdapter(
        adapter = ownAdapter,
        transformGet = { if (it < 0f) portraitAdapter.state.value else it },
        transformSet = { if (abs(it - portraitAdapter.state.value) < 0.001f) -1f else it },
    )
    SliderPreference(
        label = label,
        adapter = adapter,
        step = 0.1f,
        valueRange = 0.5F..1.5F,
        showAsPercentage = true,
        enabled = enabled,
    )
}

@Composable
private fun FoldInfoPreference(
    text: String,
    modifier: Modifier = Modifier,
) {
    PreferenceTemplate(
        title = {
            ProvideDescriptionTextStyle {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
        startWidget = {
            Icon(
                imageVector = Icons.Rounded.Info,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        },
    )
}

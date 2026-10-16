package app.lawnchair.ui.preferences.destinations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.fold.FoldAfterFold
import app.lawnchair.fold.FoldDock
import app.lawnchair.fold.FoldDrawerLayout
import app.lawnchair.fold.FoldHomeLayout
import app.lawnchair.fold.FoldLandscape
import app.lawnchair.fold.FoldOption
import app.lawnchair.fold.FoldTransitionStyle
import app.lawnchair.fold.FoldWallpaper
import app.lawnchair.fold.FoldWallpaperOffsets
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.rememberTransformAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.FoldDualWallpaperPreferences
import app.lawnchair.ui.preferences.components.FoldMirrorNote
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
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
        PreferenceGroup(heading = stringResource(id = R.string.fold_group_large_screen)) {
            FoldListPreference(
                adapter = prefs2.foldHomeLayout.getAdapter(),
                options = FoldHomeLayout.entries,
                label = stringResource(id = R.string.fold_home_screen),
            )
            FoldListPreference(
                adapter = prefs2.foldDrawerLayout.getAdapter(),
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
            SwitchPreference(
                adapter = prefs2.foldTrackHinge.getAdapter(),
                label = stringResource(id = R.string.fold_track_hinge),
                description = stringResource(id = R.string.fold_track_hinge_description),
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.fold_group_after_fold)) {
            FoldListPreference(
                adapter = prefs2.foldAfterFold.getAdapter(),
                options = FoldAfterFold.entries,
                label = stringResource(id = R.string.fold_cover_opens_on),
            )
            FoldInfoPreference(text = stringResource(id = R.string.fold_extra_dock_info))
        }

        PreferenceGroup(heading = stringResource(id = R.string.fold_group_advanced)) {
            FoldListPreference(
                adapter = prefs2.foldDock.getAdapter(),
                options = FoldDock.entries,
                label = stringResource(id = R.string.fold_dock),
            )
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

        // Elsewhere Lawnchair's dual live wallpaper shows one image per screen
        if (wallpaper.state.value == FoldWallpaper.EACH && !FoldWallpaperOffsets.systemHasWallpaperPerScreen) {
            FoldDualWallpaperPreferences()
        }
    }
}

/**
 * A list preference for a foldable option. Options that are not implemented yet are disabled and
 * marked as coming soon, and the summary of the selected option is shown under the label.
 */
@Composable
private fun <T> FoldListPreference(
    adapter: PreferenceAdapter<T>,
    options: List<T>,
    label: String,
    modifier: Modifier = Modifier,
) where T : Enum<T>, T : FoldOption {
    val entries = remember(options) {
        options.map { option ->
            ListPreferenceEntry(
                value = option,
                enabled = option.isImplemented,
            ) {
                val name = stringResource(id = option.labelResourceId)
                if (option.isImplemented) name else stringResource(id = R.string.fold_coming_soon, name)
            }
        }
    }
    val selected = adapter.state.value

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = label,
        modifier = modifier,
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

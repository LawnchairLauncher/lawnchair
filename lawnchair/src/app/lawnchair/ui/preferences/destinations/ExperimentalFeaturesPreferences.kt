package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.WallpaperAccessPermissionDialog
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.FileAccessManager
import app.lawnchair.util.FileAccessState
import com.android.launcher3.R
import com.android.launcher3.Utilities.ATLEAST_S
import com.android.systemui.shared.system.BlurUtils

@Composable
fun ExperimentalFeaturesPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    PreferenceLayout(
        label = stringResource(id = R.string.experimental_features_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(
            Modifier,
            stringResource(R.string.workspace_label),
        ) {
            // pE-FeatureTaskForce-TODO(N/A): Make Material 3 Expressive Toggle
            val enableMaterialExpressiveAdapter = prefs.enableMaterialExpressive.getAdapter()
            SwitchPreference(
                adapter = enableMaterialExpressiveAdapter,
                label = stringResource(id = R.string.material_expressive_label),
                description = stringResource(id = R.string.material_expressive_description),
            )
            ExpandAndShrink(visible = enableMaterialExpressiveAdapter.state.value) {
                if (!ATLEAST_S || !BlurUtils.supportsBlursOnWindows()) {
                    WarningPreference(
                        "Expressive Blur will be ignored because blur effect required at " +
                            "least Android 12 or above, and device need performant GPU to render " +
                            "blur and need to enable support rendering cross window blur by the " +
                            "device manufacturer.",
                    )
                }
            }
            SwitchPreference(
                adapter = prefs2.enableFontSelection.getAdapter(),
                label = stringResource(id = R.string.font_picker_label),
                description = stringResource(id = R.string.font_picker_description),
            )
            SwitchPreference(
                adapter = prefs.workspaceIncreaseMaxGridSize.getAdapter(),
                label = stringResource(id = R.string.workspace_increase_max_grid_size_label),
                description = stringResource(id = R.string.workspace_increase_max_grid_size_description),
            )
            SwitchPreference(
                adapter = prefs2.iconSwipeGestures.getAdapter(),
                label = stringResource(R.string.icon_swipe_gestures),
                description = stringResource(R.string.icon_swipe_gestures_description),
            )
            SwitchPreference(
                adapter = prefs2.showDeckLayout.getAdapter(),
                label = stringResource(R.string.show_deck_layout),
                description = stringResource(R.string.show_deck_layout_description),
            )

            val context = LocalContext.current
            val enableWallpaperBlur = prefs.enableWallpaperBlur.getAdapter()
            val fileAccessManager = remember { FileAccessManager.getInstance(context) }
            val allFilesAccessState by fileAccessManager.allFilesAccessState.collectAsStateWithLifecycle()
            val wallpaperAccessState by fileAccessManager.wallpaperAccessState.collectAsStateWithLifecycle()
            val hasPermission = wallpaperAccessState != FileAccessState.Denied
            var showPermissionDialog by remember { mutableStateOf(false) }

            SwitchPreference(
                checked = hasPermission && enableWallpaperBlur.state.value,
                onCheckedChange = {
                    if (!hasPermission) {
                        showPermissionDialog = true
                    } else {
                        enableWallpaperBlur.onChange(it)
                    }
                },
                label = stringResource(id = R.string.wallpaper_blur),
            )
            ExpandAndShrink(visible = hasPermission && enableWallpaperBlur.state.value) {
                DividerColumn {
                    SliderPreference(
                        label = stringResource(id = R.string.wallpaper_background_blur),
                        adapter = prefs.wallpaperBlur.getAdapter(),
                        step = 5,
                        valueRange = 0..100,
                        showUnit = "%",
                    )
                    SliderPreference(
                        label = stringResource(id = R.string.wallpaper_background_blur_factor),
                        adapter = prefs.wallpaperBlurFactorThreshold.getAdapter(),
                        step = 1F,
                        valueRange = 0F..10F,
                    )
                }
            }
            if (showPermissionDialog) {
                WallpaperAccessPermissionDialog(
                    managedFilesChecked = allFilesAccessState != FileAccessState.Denied,
                    onDismiss = {
                        showPermissionDialog = false
                    },
                    onPermissionRequest = { fileAccessManager.refresh() },
                )
            }
            LifecycleResumeEffect(Unit) {
                showPermissionDialog = false
                fileAccessManager.refresh()
                onPauseOrDispose { }
            }
        }

        PreferenceGroup(
            Modifier,
            stringResource(R.string.smartspace_label),
        ) {
            SwitchPreference(
                adapter = prefs2.enableSmartspaceCalendarSelection.getAdapter(),
                label = stringResource(id = R.string.smartspace_calendar_label),
                description = stringResource(id = R.string.smartspace_calendar_description),
            )
        }

        PreferenceGroup(
            Modifier,
            stringResource(R.string.internal_label),
            stringResource(R.string.internal_description),
        ) {
            // Lawnchair-TODO(Merge): Investigate Always Reload Icons
            val alwaysReloadIconsAdapter = prefs2.alwaysReloadIcons.getAdapter()
            SwitchPreference(
                adapter = alwaysReloadIconsAdapter,
                label = stringResource(id = R.string.always_reload_icons_label),
                description = stringResource(id = R.string.always_reload_icons_description),
            )
            ExpandAndShrink(visible = alwaysReloadIconsAdapter.state.value) {
                WarningPreference(stringResource(R.string.always_reload_icons_warning))
            }

            // pE-FeatureTaskForce-TODO(N/A): Make GestureNavContract API Toggle
            val enableGncAdapter = prefs.enableGnc.getAdapter()
            SwitchPreference(
                adapter = enableGncAdapter,
                label = stringResource(id = R.string.gesturenavcontract_label),
                description = stringResource(id = R.string.gesturenavcontract_description),
                enabled = ATLEAST_S,
            )
            ExpandAndShrink(visible = enableGncAdapter.state.value) {
                WarningPreference(stringResource(R.string.gesturenavcontract_warning_incompatibility))
            }
        }
    }
}

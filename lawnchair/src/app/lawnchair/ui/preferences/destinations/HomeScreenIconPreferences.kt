/*
 * Copyright 2026, Renns Project
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GeneralIconPack
import app.lawnchair.ui.preferences.navigation.GeneralIconShape
import app.lawnchair.util.collectAsStateBlocking
import com.android.launcher3.R
import kotlinx.coroutines.launch

/**
 * Every setting that decides how an icon looks, in one place.
 *
 * They used to be split three ways: shape and adaptive handling under General,
 * size and labels under Home screen, and a second shape of its own under
 * Folders, with nothing to indicate any of them were related. A user could set
 * an icon shape on one screen and still be looking at differently shaped
 * folders, because the setting governing those was two screens away.
 */
@Composable
fun HomeScreenIconPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val scope = rememberCoroutineScope()

    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val themedIconsAdapter = prefs.themedIcons.getAdapter()
    val drawerThemedIconsAdapter = prefs.drawerThemedIcons.getAdapter()
    val iconShapeAdapter = prefs2.iconShape.getAdapter()
    val wrapAdaptiveIcons = prefs.wrapAdaptiveIcons.getAdapter()
    val transparentIconBackground = prefs.transparentIconBackground.getAdapter()
    val maskOnlyIcons = prefs.maskOnlyIcons.getAdapter()
    val labelsAdapter = prefs2.showIconLabelsOnHomeScreen.getAdapter()

    val currentIconPackName = iconPacks
        .find { it.packageName == prefs.iconPackPackage.get() }
        ?.name
    val themedIconsEnabled = ThemedIconsState.getForSettings(
        themedIcons = themedIconsAdapter.state.value,
        drawerThemedIcons = drawerThemedIconsAdapter.state.value,
    ) != ThemedIconsState.Off
    val iconStyleSubtitle = if (currentIconPackName != null && themedIconsEnabled) {
        stringResource(
            id = R.string.x_and_y,
            currentIconPackName,
            stringResource(id = R.string.themed_icon_title),
        )
    } else {
        currentIconPackName
    }
    val iconShapeSubtitle = iconShapeEntries(context)
        .firstOrNull { it.value == iconShapeAdapter.state.value }
        ?.label?.invoke()
        ?: stringResource(id = R.string.custom)

    PreferenceLayout(
        label = stringResource(id = R.string.icons),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.style)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_style_label),
                destination = GeneralIconPack,
                subtitle = iconStyleSubtitle,
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_shape_label),
                destination = GeneralIconShape(),
                subtitle = iconShapeSubtitle,
                endWidget = {
                    IconShapePreview(iconShape = iconShapeAdapter.state.value)
                },
            )
            ExpandAndShrink(visible = themedIconsEnabled) {
                SwitchPreference(
                    adapter = transparentIconBackground,
                    label = stringResource(id = R.string.transparent_background_icons_label),
                    description = stringResource(id = R.string.transparent_background_icons_description),
                )
            }
        }

        PreferenceGroup(
            heading = stringResource(id = R.string.auto_adaptive_icons_label),
            description = stringResource(id = R.string.adaptive_icon_background_description),
            showDescription = wrapAdaptiveIcons.state.value,
        ) {
            SwitchPreference(
                adapter = wrapAdaptiveIcons,
                label = stringResource(id = R.string.auto_adaptive_icons_label),
                description = stringResource(id = R.string.auto_adaptive_icons_description),
            )
            ExpandAndShrink(visible = wrapAdaptiveIcons.state.value) {
                SwitchPreference(
                    adapter = maskOnlyIcons,
                    label = stringResource(id = R.string.mask_only_icons_label),
                    description = stringResource(id = R.string.mask_only_icons_description),
                )
            }
            SwitchPreference(
                adapter = prefs.shadowBGIcons.getAdapter(),
                label = stringResource(id = R.string.shadow_bg_icons_label),
            )
            ExpandAndShrink(
                visible = wrapAdaptiveIcons.state.value && !transparentIconBackground.state.value,
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

        PreferenceGroup(heading = stringResource(id = R.string.layout)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_sizes),
                adapter = prefs2.homeIconSizeFactor.getAdapter(),
                step = 0.1f,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
            )
            SwitchPreference(
                adapter = labelsAdapter,
                label = stringResource(id = R.string.show_labels),
            )
            ExpandAndShrink(visible = labelsAdapter.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.homeIconLabelSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
        }

        val overrideRepo = IconOverrideRepository.INSTANCE.get(context)
        val customIconsCount by remember { overrideRepo.observeCount() }.collectAsStateBlocking()
        if (customIconsCount > 0) {
            PreferenceGroup {
                ClickablePreference(
                    label = stringResource(id = R.string.reset_custom_icons),
                    confirmationText = stringResource(id = R.string.reset_custom_icons_confirmation),
                    onClick = { scope.launch { overrideRepo.deleteAll() } },
                )
            }
        }
    }
}

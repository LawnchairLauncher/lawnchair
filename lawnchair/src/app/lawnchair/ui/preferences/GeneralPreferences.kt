/*
 * Copyright 2021, Lawnchair
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

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.android.launcher3.R

@ExperimentalAnimationApi
@Composable
fun GeneralPreferences(navController: NavController, interactor: PreferenceInteractor) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroup(isFirstChild = true) {
            SwitchPreference(
                checked = interactor.allowRotation.value,
                onCheckedChange = { interactor.setAllowRotation(it) },
                label = stringResource(id = R.string.home_screen_rotation_label),
                description = stringResource(id = R.string.home_screen_rotaton_description)
            )
            NotificationDotsPreference(interactor)
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_pack),
                navController = navController,
                destination = Routes.ICON_PACK,
                showDivider = false
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PreferenceGroup(
                heading = stringResource(id = R.string.adaptive_icons),
                description = stringResource(id = (R.string.adaptive_icon_background_description)),
                showDescription = interactor.wrapAdaptiveIcons.value
            ) {
                SwitchPreference(
                    checked = interactor.wrapAdaptiveIcons.value,
                    onCheckedChange = { interactor.setWrapAdaptiveIcons(it) },
                    label = stringResource(id = R.string.make_icon_packs_adaptive_label),
                    description = stringResource(id = R.string.make_icon_packs_adaptive_description),
                    showDivider = interactor.wrapAdaptiveIcons.value
                )
                AnimatedVisibility(
                    visible = interactor.wrapAdaptiveIcons.value,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SliderPreference(
                        label = stringResource(id = R.string.background_lightness_label),
                        value = interactor.coloredBackgroundLightness.value,
                        onValueChange = { interactor.setColoredBackgroundLightness(it) },
                        valueRange = 0F..1F,
                        steps = 9,
                        showAsPercentage = true,
                        showDivider = false
                    )
                }
            }
        }
    }
}
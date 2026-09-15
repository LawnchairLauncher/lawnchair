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

package app.mica.ui.preferences.components.colorpreference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import app.mica.preferences.PreferenceAdapter
import app.mica.preferences.getAdapter
import app.mica.theme.color.ColorOption
import app.mica.ui.preferences.LocalNavController
import app.mica.ui.preferences.components.layout.PreferenceTemplate
import app.mica.ui.preferences.navigation.ColorSelection as ColorSelectionRoute
import app.mica.ui.theme.MicaTheme
import app.mica.ui.util.preview.PreferenceGroupPreviewContainer
import app.mica.ui.util.preview.PreviewMica
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.patrykmichalik.opto.domain.Preference

/**
 * A custom implementation of [PreferenceTemplate] for [ColorOption] preferences.
 *
 * @see ColorSelection
 */
@Composable
fun ColorPreference(
    preference: Preference<ColorOption, String, Preferences.Key<String>>,
    modifier: Modifier = Modifier,
) {
    val modelList = ColorPreferenceModelList.INSTANCE.get(LocalContext.current)
    val model = modelList[preference.key.name]
    val adapter: PreferenceAdapter<ColorOption> = model.prefObject.getAdapter()
    val navController = LocalNavController.current
    ColorPreference(
        label = stringResource(id = model.labelRes),
        selectedColor = adapter.state.value,
        onClick = { navController.navigate(route = ColorSelectionRoute(model.prefObject.key.name)) },
        modifier = modifier,
    )
}

@Composable
fun ColorPreference(
    label: String,
    selectedColor: ColorOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = modifier,
        description = {
            Text(text = selectedColor.colorPreferenceEntry.label())
        },
        endWidget = {
            ColorDot(selectedColor.colorPreferenceEntry)
        },
        onClick = {
            mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
            onClick()
        },
    )
}

@PreviewMica
@Composable
private fun ColorPreferencePreview() {
    MicaTheme {
        PreferenceGroupPreviewContainer {
            ColorPreference(
                label = "Accent Color",
                selectedColor = ColorOption.MicaBlue,
                onClick = {},
            )
        }
    }
}

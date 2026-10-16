/*
 * Copyright 2024, Lawnchair
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

package app.lawnchair.ui.preferences.destinations.icons

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.util.addIf
import com.android.launcher3.R
import com.android.launcher3.util.painterResource

enum class ThemedIconsState(
    @StringRes val labelResourceId: Int,
    val themedIcons: Boolean = true,
    val drawerThemedIcons: Boolean = false,
) {
    Off(labelResourceId = R.string.themed_icons_off_label, themedIcons = false),
    Home(labelResourceId = R.string.themed_icons_home_label),
    HomeAndDrawer(
        labelResourceId = R.string.themed_icons_home_and_drawer_label,
        drawerThemedIcons = true,
    ),
    ;

    companion object {
        fun getForSettings(
            themedIcons: Boolean,
            drawerThemedIcons: Boolean,
        ) = entries.find {
            it.themedIcons == themedIcons && it.drawerThemedIcons == drawerThemedIcons
        } ?: Off
    }
}

@Composable
fun ThemedIconsPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val themedIconsAdapter = prefs.themedIcons.getAdapter()
    val drawerThemedIconsAdapter = prefs.drawerThemedIcons.getAdapter()
    val themedIconPackAdapter = prefs.themedIconPackPackage.getAdapter()
    val forceMonochromeAdapter = prefs.forceIconMonochrome.getAdapter()

    val currentState = ThemedIconsState.getForSettings(
        themedIcons = themedIconsAdapter.state.value,
        drawerThemedIcons = drawerThemedIconsAdapter.state.value,
    )

    val preferenceInteractor = LocalPreferenceInteractor.current
    val themedIconPacks by preferenceInteractor.themedIconPacks.collectAsStateWithLifecycle()

    val themedIconPackEntries = themedIconPacks.map { item ->
        ListPreferenceEntry(
            value = item.packageName,
            label = { item.name },
        )
    }

    PreferenceLayout(
        label = stringResource(id = R.string.themed_icon_title),
        modifier = modifier,
    ) {
        PreferenceGroup {
            ThemedIconsState.entries.forEach { state ->
                PreferenceTemplate(
                    title = { Text(text = stringResource(id = state.labelResourceId)) },
                    startWidget = {
                        RadioButton(
                            selected = currentState == state,
                            onClick = null,
                        )
                    },
                    onClick = {
                        themedIconsAdapter.onChange(newValue = state.themedIcons)
                        drawerThemedIconsAdapter.onChange(newValue = state.drawerThemedIcons)
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(id = R.string.themed_icons_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ExpandAndShrink(currentState != ThemedIconsState.Off) {
            PreferenceGroup(heading = stringResource(id = R.string.advanced)) {
                val fallbackEnabled = !forceMonochromeAdapter.state.value
                ListPreference(
                    adapter = themedIconPackAdapter,
                    entries = themedIconPackEntries,
                    label = stringResource(id = R.string.fallback_icon_source_title),
                    enabled = fallbackEnabled,
                    endWidget = {
                        themedIconPacks.firstOrNull { it.packageName == themedIconPackAdapter.state.value }
                            .let {
                                if (it?.icon !== null) {
                                    Image(
                                        painter = painterResource(it.icon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .addIf(!fallbackEnabled) {
                                                alpha(0.38f)
                                            },
                                    )
                                }
                            }
                    },
                )
                SwitchPreference(
                    adapter = forceMonochromeAdapter,
                    label = stringResource(id = R.string.enforce_themed_icons_title),
                    description = stringResource(id = R.string.enforce_themed_icons_description),
                )
            }
        }
    }
}

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

package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.PreferenceRoute
import app.lawnchair.ui.util.addIf

@Composable
fun NavigationActionPreference(
    label: String,
    modifier: Modifier = Modifier,
    destination: PreferenceRoute? = null,
    subtitle: String? = null,
    endWidget: (@Composable () -> Unit)? = null,
) {
    val navController = LocalNavController.current

    PreferenceTemplate(
        modifier = modifier.addIf(destination != null) {
            clickable {
                // LC-Note: We probably shouldn't do this, but IDE/Kotlin won't stop complaining even if there's addIf condition
                destination?.let {
                    navController.navigate(
                        route = it,
                    )
                }
            }
        },
        title = { Text(text = label) },
        description = { subtitle?.let { Text(text = it) } },
        endWidget = endWidget,
    )
}

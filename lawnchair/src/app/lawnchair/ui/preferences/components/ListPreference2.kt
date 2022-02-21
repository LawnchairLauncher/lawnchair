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

package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.util.bottomSheetHandler

@ExperimentalMaterialApi
@Composable
fun <T> ListPreference2(
    value: T,
    entries: List<ListPreferenceEntry2<T>>,
    label: String,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onValueChange: (T) -> Unit,
) {
    val bottomSheetHandler = bottomSheetHandler
    val currentLabel = entries
        .firstOrNull { it.value == value }
        ?.label?.invoke()

    PreferenceTemplate(
        title = { Text(text = label) },
        description = { currentLabel?.let { Text(text = it) } },
        enabled = enabled,
        showDivider = showDivider,
        modifier = Modifier.clickable(enabled) {
            bottomSheetHandler.show {
                AlertBottomSheetContent(
                    title = { Text(label) },
                    buttons = {
                        OutlinedButton(onClick = { bottomSheetHandler.hide() }) {
                            Text(text = stringResource(id = android.R.string.cancel))
                        }
                    }
                ) {
                    LazyColumn {
                        itemsIndexed(entries) { index, item ->
                            PreferenceTemplate(
                                title = { Text(item.label()) },
                                showDivider = index > 0,
                                dividerIndent = 40.dp,
                                modifier = Modifier.clickable {
                                    onValueChange(item.value)
                                    bottomSheetHandler.hide()
                                },
                                startWidget = {
                                    RadioButton(
                                        selected = item.value == value,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

class ListPreferenceEntry2<T>(
    val value: T,
    val label: @Composable () -> String,
)

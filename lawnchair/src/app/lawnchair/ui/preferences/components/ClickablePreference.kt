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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.util.bottomSheetHandler

@Composable
fun ClickablePreference(
    label: String,
    subtitle: String? = null,
    confirmationText: String? = null,
    onClick: () -> Unit,
) {
    val bottomSheetHandler = bottomSheetHandler
    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = Modifier
            .clickable {
               if (confirmationText != null) {
                   bottomSheetHandler.show {
                       PreferenceClickConfirmation(
                           title = label,
                           text = confirmationText,
                           onDismissRequest = { bottomSheetHandler.hide() },
                           onConfirm = onClick
                       )
                   }
               } else {
                   onClick()
               }
            },
        description = { subtitle?.let { Text(text = it) } },
    )
}


@Composable
fun PreferenceClickConfirmation(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertBottomSheetContent(
        title = { Text(text = title) },
        text = { Text(text = text) },
        buttons = {
            OutlinedButton(
                onClick = onDismissRequest
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Button(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                }
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        }
    )
}

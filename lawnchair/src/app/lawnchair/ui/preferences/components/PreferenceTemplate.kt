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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.addIf

@Composable
fun PreferenceTemplate(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    applyPaddings: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 16.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    val contentAlphaDisabled = ContentAlpha.disabled
    Column {
        Row(
            verticalAlignment = verticalAlignment,
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth()
                .addIf(applyPaddings) {
                    padding(horizontal = horizontalPadding, vertical = verticalPadding)
                },
        ) {
            startWidget?.let {
                startWidget()
                if (applyPaddings) {
                    Spacer(modifier = Modifier.requiredWidth(16.dp))
                }
            }
            Row(
                modifier = contentModifier
                    .weight(1f)
                    .addIf(!enabled) {
                        alpha(contentAlphaDisabled)
                    },
                verticalAlignment = verticalAlignment,
            ) {
                Column(Modifier.weight(1f)) {
                    CompositionLocalProvider(
                        LocalContentColor provides Material3Theme.colorScheme.onBackground,
                        LocalTextStyle provides Material3Theme.typography.bodyLarge,
                    ) {
                        title()
                    }
                    CompositionLocalProvider(
                        LocalContentColor provides Material3Theme.colorScheme.onBackground.copy(alpha = ContentAlpha.medium),
                        LocalTextStyle provides Material3Theme.typography.bodyMedium,
                    ) {
                        description()
                    }
                }
            }
            endWidget?.let {
                if (applyPaddings) {
                    Spacer(modifier = Modifier.requiredWidth(16.dp))
                }
                endWidget()
            }
        }
    }
}

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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.subRoute

@Composable
fun PreferenceCategory(
    label: String,
    description: String? = null,
    @DrawableRes iconResource: Int,
    route: String
) {
    val navController = LocalNavController.current
    val resolvedRoute = subRoute(name = route)

    PreferenceTemplate(
        verticalPadding = 14.dp,
        modifier = Modifier.clickable(onClick = { navController.navigate(resolvedRoute) }),
        title = {
            Text(text = label)
        },
        description = {
            if (description != null) {
                Text(text = description)
            }
        },
        startWidget = {
            Image(
                painter = painterResource(id = iconResource),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        },
    )
}

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

@Composable
fun PreferenceCategory(
    label: String,
    description: String? = null,
    @DrawableRes iconResource: Int,
    route: String
) {
    val navController = LocalNavController.current
    Row(
        modifier = Modifier
            .clickable(onClick = { navController.navigate(route) })
            .height(72.dp)
            .padding(start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconResource),
            contentDescription = null,
            modifier = Modifier
                .width(32.dp)
                .height(32.dp)
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
            if (description != null) {
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
        }
    }
}

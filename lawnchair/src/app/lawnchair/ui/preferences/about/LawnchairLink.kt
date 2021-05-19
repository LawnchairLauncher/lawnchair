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

package app.lawnchair.ui.preferences.about

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun LawnchairLink(@DrawableRes iconResId: Int, label: String, modifier: Modifier = Modifier, url: String) {
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                val webpage = Uri.parse(url)
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(id = iconResId),
            contentDescription = null,
            modifier = Modifier
                .height(24.dp)
                .width(24.dp),
            tint = MaterialTheme.colors.onBackground
        )
        Spacer(modifier = Modifier.requiredHeight(4.dp))
        Text(text = label, style = MaterialTheme.typography.body1, color = MaterialTheme.colors.onBackground)
    }
}

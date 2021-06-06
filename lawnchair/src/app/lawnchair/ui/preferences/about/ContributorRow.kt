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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import com.google.accompanist.glide.rememberGlidePainter

@Composable
fun ContributorRow(name: String, description: String, photoUrl: String, url: String, showDivider: Boolean = true) {
    val context = LocalContext.current

    PreferenceTemplate(height = 72.dp, showDivider = showDivider) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    val webpage = Uri.parse(url)
                    val intent = Intent(Intent.ACTION_VIEW, webpage)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                }
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberGlidePainter(request = photoUrl, fadeIn = true),
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .width(32.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colors.onBackground.copy(alpha = 0.12F)),
            )
            Spacer(modifier = Modifier.requiredWidth(16.dp))
            Column {
                Text(text = name, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(text = description, style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}
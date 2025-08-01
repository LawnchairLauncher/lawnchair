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

package app.lawnchair.ui.preferences.about.acknowledgements

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import com.android.launcher3.R

@Composable
fun Acknowledgements(
    modifier: Modifier = Modifier,
    viewModel: AcknowledgementsViewModel = viewModel(),
) {
    val ossLibraries by viewModel.ossLibraries.collectAsStateWithLifecycle()
    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.acknowledgements),
        modifier = modifier,
    ) {
        preferenceGroupItems(ossLibraries, isFirstChild = true) { _, library ->
            OssLibraryItem(
                name = library.name,
                license = library.license,
            )
        }
    }
}

@Composable
fun OssLibraryItem(
    name: String,
    license: OssLibrary.License?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    ClickablePreference(
        label = name,
        modifier = modifier,
        subtitle = license?.name,
        onClick = {
            license?.url?.let { urlString ->
                val webpage = urlString.toUri()
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            }
        },
    )
}

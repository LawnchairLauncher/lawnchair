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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.util.bottomSheetHandler
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
                ossLibrary = library,
            )
        }
    }
}

@Composable
fun OssLibraryItem(
    ossLibrary: OssLibrary,
    modifier: Modifier = Modifier,
) {
    val bottomSheetHandler = bottomSheetHandler

    PreferenceTemplate(
        title = {
            Text(
                text = ossLibrary.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier
            .clickable {
                bottomSheetHandler.show {
                    NoticePage(ossLibrary = ossLibrary)
                }
            },
    )
}

@Composable
fun NoticePage(
    ossLibrary: OssLibrary,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val license = ossLibrary.spdxLicenses?.get(0) ?: ossLibrary.unknownLicenses?.get(0) ?: return

    ModalBottomSheetContent(
        title = {
            Text(text = ossLibrary.name)
        },
        buttons = {},
        modifier = modifier,
    ) {
        Column {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                    .clickable {
                        val webpage = license.url.toUri()
                        val intent = Intent(Intent.ACTION_VIEW, webpage)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                    },
                text = license.url,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
        }
    }
}

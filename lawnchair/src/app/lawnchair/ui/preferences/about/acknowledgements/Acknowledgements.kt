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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.layout.LoadingScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R

@Composable
fun Acknowledgements(
    modifier: Modifier = Modifier,
) {
    val ossLibraries by LocalPreferenceInteractor.current.ossLibraries.collectAsStateWithLifecycle()
    LoadingScreen(
        obj = ossLibraries,
        modifier = modifier,
    ) { libraries ->
        PreferenceLayoutLazyColumn(
            label = stringResource(id = R.string.acknowledgements),
        ) {
            preferenceGroupItems(libraries, isFirstChild = true) { index, library ->
                OssLibraryItem(
                    ossLibrary = library,
                    index = index,
                )
            }
        }
    }
}

@Composable
fun OssLibraryItem(
    ossLibrary: OssLibrary,
    index: Int,
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
    ossLibrary: OssLibrary?,
    modifier: Modifier = Modifier,
) {
    val dataState = ossLibrary?.let { loadNotice(ossLibrary = it) }
    val data = dataState?.value

    ModalBottomSheetContent(
        title = {
            Text(text = ossLibrary?.name ?: stringResource(id = R.string.loading))
        },
        buttons = {},
        modifier = modifier,
    ) {
        Column {
            Crossfade(targetState = data, label = "") { it ->
                it ?: return@Crossfade
                val uriHandler = LocalUriHandler.current
                val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
                val pressIndicator = Modifier.pointerInput(Unit) {
                    detectTapGestures { pos ->
                        layoutResult.value?.let { layoutResult ->
                            val position = layoutResult.getOffsetForPosition(pos)
                            val annotation =
                                it.notice.getStringAnnotations(position, position).firstOrNull()
                            if (annotation?.tag == "URL") {
                                uriHandler.openUri(annotation.item)
                            }
                        }
                    }
                }

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                        .then(pressIndicator),
                    text = it.notice,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    onTextLayout = {
                        layoutResult.value = it
                    },
                )
            }
        }
    }
}

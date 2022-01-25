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

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import app.lawnchair.ossnotices.OssLibrary
import com.android.launcher3.R

@Composable
fun loadNotice(ossLibrary: OssLibrary): State<OssLibraryWithNotice?> {
    val context = LocalContext.current
    val noticeStringState = remember { mutableStateOf<OssLibraryWithNotice?>(null) }
    val accentColor = MaterialTheme.colorScheme.primary
    DisposableEffect(Unit) {
        val string = ossLibrary.getNotice(context = context, thirdPartyLicensesId = R.raw.third_party_licenses)
        val spannable = SpannableString(string)
        Linkify.addLinks(spannable, Linkify.WEB_URLS)
        val spans = spannable.getSpans(0, string.length, URLSpan::class.java)
        val annotatedString = buildAnnotatedString {
            append(string)
            spans.forEach { urlSpan ->
                val start = spannable.getSpanStart(urlSpan)
                val end = spannable.getSpanEnd(urlSpan)
                addStyle(
                    style = SpanStyle(
                        color = accentColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = start,
                    end = end
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = urlSpan.url,
                    start = start,
                    end = end
                )
            }
        }
        noticeStringState.value = OssLibraryWithNotice(ossLibrary, annotatedString)
        onDispose { }
    }
    return noticeStringState
}

data class OssLibraryWithNotice(
    val ossLibrary: OssLibrary,
    val notice: AnnotatedString,
)

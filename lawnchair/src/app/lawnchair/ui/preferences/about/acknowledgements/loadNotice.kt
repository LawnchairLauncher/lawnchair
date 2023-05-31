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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.serialization.Serializable

@Composable
fun loadNotice(ossLibrary: OssLibrary): State<OssLibraryWithNotice?> {
    val noticeStringState = remember { mutableStateOf<OssLibraryWithNotice?>(null) }
    val accentColor = MaterialTheme.colorScheme.primary
    DisposableEffect(Unit) {
        val string = (ossLibrary.spdxLicenses ?: ossLibrary.unknownLicenses)
            ?.firstOrNull()?.url.orEmpty()
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

@Serializable
data class OssLibrary(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val name: String,
    val scm: Scm? = null,
    val spdxLicenses: List<License>? = null,
    val unknownLicenses: List<License>? = null,
) {
    @Serializable
    data class License(
        val identifier: String? = null,
        val name: String,
        val url: String,
    )

    @Serializable
    data class Scm(val url: String)
}

data class OssLibraryWithNotice(
    val ossLibrary: OssLibrary,
    val notice: AnnotatedString,
)

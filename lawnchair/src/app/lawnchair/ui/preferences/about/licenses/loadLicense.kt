package app.lawnchair.ui.preferences.about.licenses

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
import com.android.launcher3.R

@Composable
fun loadLicense(license: License): State<LicenseData?> {
    val res = LocalContext.current.resources
    val licenseStringState = remember { mutableStateOf<LicenseData?>(null) }
    val accentColor = MaterialTheme.colorScheme.primary
    DisposableEffect(Unit) {
        val reader = res.openRawResource(R.raw.third_party_licenses)
        reader.skip(license.start)
        val bytes = ByteArray(license.length)
        reader.read(bytes, 0, license.length)

        val string = String(bytes)
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
        licenseStringState.value = LicenseData(license, annotatedString)
        onDispose { }
    }
    return licenseStringState
}

data class LicenseData(val license: License, val data: AnnotatedString)

package app.lawnchair.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding

@Composable
fun AlertBottomSheetContent(
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    val contentPadding = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)

    Column(
        modifier = modifier
            .navigationBarsOrDisplayCutoutPadding()
            .fillMaxWidth()
    ) {
        if (title != null) {
            Box(modifier = contentPadding) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                    val textStyle = MaterialTheme.typography.titleLarge
                    ProvideTextStyle(textStyle, title)
                }
            }
        }
        if (text != null) {
            Box(modifier = contentPadding) {
                val textStyle = MaterialTheme.typography.bodyMedium
                ProvideTextStyle(textStyle, text)
            }
        }
        if (content != null) {
            Box(modifier = Modifier.padding(top = if (title != null || text != null) 16.dp else 0.dp)) {
                content()
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            buttons()
        }
    }
}

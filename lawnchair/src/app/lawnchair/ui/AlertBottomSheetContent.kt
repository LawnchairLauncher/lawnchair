package app.lawnchair.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.navigationBarsPadding

@Composable
fun AlertBottomSheetContent(
    buttons: @Composable RowScope.() -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    val contentPadding = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(
                top = 16.dp,
                bottom = 16.dp,
            )
            .fillMaxWidth()
    ) {
        if (title != null) {
            Box(modifier = Modifier.then(contentPadding)) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                    val textStyle = MaterialTheme.typography.h6
                    ProvideTextStyle(textStyle, title)
                }
            }
        }
        if (text != null) {
            Box(modifier = Modifier.then(contentPadding)) {
                val textStyle = MaterialTheme.typography.body2
                ProvideTextStyle(textStyle, text)
            }
        }
        if (content != null) {
            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                content()
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .padding(top = 4.dp)
                .then(contentPadding)
                .fillMaxWidth()
        ) {
            buttons()
        }
    }
}

package app.lawnchair.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.BottomSpacer

@Composable
fun ModalBottomSheetContent(
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val contentPadding = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding(),
    ) {
        title?.let {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                val textStyle = MaterialTheme.typography.titleLarge
                ProvideTextStyle(textStyle, title)
            }
        }
        text?.let {
            Box(modifier = contentPadding) {
                val textStyle = MaterialTheme.typography.bodyMedium
                ProvideTextStyle(textStyle, text)
            }
        }
        content?.let {
            Box(
                modifier = Modifier.padding(
                    top = if (title != null || text != null) 16.dp else 0.dp,
                ),
            ) {
                content()
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            buttons()
            BottomSpacer()
        }
    }
}

package app.lawnchair.ui.preferences.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.lawnchair.util.App

@Composable
fun AppItem(
    app: App,
    onClick: (app: App) -> Unit,
    showDivider: Boolean = true,
    content: (@Composable RowScope.() -> Unit)?,
) {
    AppItem(
        label = app.info.label.toString(),
        icon = app.icon,
        onClick = { onClick(app) },
        showDivider,
        content
    )
}

@Composable
fun AppItem(
    label: String,
    icon: Bitmap,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    content: (@Composable RowScope.() -> Unit)?,
) {
    PreferenceTemplate(
        height = 52.dp,
        showDivider = showDivider
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() }
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Image(
                icon.asImageBitmap(),
                null,
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = label,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            content?.invoke(this)
        }
    }
}

@ExperimentalAnimationApi
@Composable
fun AnimatedCheck(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Image(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colors.primary)
        )
    }
}

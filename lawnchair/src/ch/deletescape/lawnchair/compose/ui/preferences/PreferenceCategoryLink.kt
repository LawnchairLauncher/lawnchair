package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun PreferenceCategoryLink(titleResId: Int, onClick: () -> Unit, subtitleResId: Int?, iconResId: Int?) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(64.dp)
            .padding(start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        iconResId?.let {
            Image(
                painter = painterResource(id = it),
                contentDescription = null,
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
            )
        }
        Column(Modifier.padding(start = 24.dp)) {
            Text(
                text = stringResource(id = titleResId),
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
            subtitleResId?.let {
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(
                        text = stringResource(id = it),
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
        }
    }
}
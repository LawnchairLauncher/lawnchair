package app.lawnchair.ui.preferences

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceCategoryLink(titleResId: Int, onClick: () -> Unit, subtitle: String?, iconResId: Int?) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(72.dp)
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
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(id = titleResId),
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
            subtitle?.let {
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
        }
    }
}
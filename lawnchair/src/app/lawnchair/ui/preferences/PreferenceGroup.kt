package app.lawnchair.ui.preferences

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.lawnchair.util.smartBorder

@ExperimentalAnimationApi
@Composable
fun PreferenceGroup(
    heading: String? = null,
    isFirstChild: Boolean = false,
    description: String? = null,
    showDescription: Boolean = true,
    content: @Composable () -> Unit
) {
    Spacer(
        modifier = Modifier.requiredHeight(
            if (isFirstChild) {
                if (heading != null) 0.dp else 8.dp
            } else {
                if (heading != null) 8.dp else 16.dp
            }
        )
    )
    heading?.let {
        Column(
            modifier = Modifier
                .height(48.dp)
                .padding(start = 32.dp, end = 32.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.medium,
                LocalContentColor provides MaterialTheme.colors.onBackground
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
    Column {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .smartBorder(
                    1.dp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.12F),
                    shape = MaterialTheme.shapes.large
                )
                .clip(shape = MaterialTheme.shapes.large)
        ) {
            content()
        }
    }
    description?.let {
        AnimatedVisibility(
            visible = showDescription,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp)) {
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
    }
}
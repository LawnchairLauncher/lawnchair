package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.util.addIf
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun <T> ListPreference(
    adapter: PreferenceAdapter<T>,
    entries: List<ListPreferenceEntry<T>>,
    label: String,
    enabled: Boolean = true,
    showDivider: Boolean = true
) {
    val sheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()

    val currentValue = adapter.state.value
    val currentLabel = entries
        .firstOrNull { it.value == currentValue }
        ?.label?.invoke()

    PreferenceTemplate(height = if (currentLabel != null) 72.dp else 52.dp, showDivider = showDivider) {
        Row(
            modifier = Modifier
                .clickable(enabled) { scope.launch { sheetState.show() } }
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .addIf(!enabled) { alpha(ContentAlpha.disabled) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onBackground,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                currentLabel?.let {
                    CompositionLocalProvider(
                        LocalContentAlpha provides ContentAlpha.medium,
                        LocalContentColor provides MaterialTheme.colors.onBackground
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.body2,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    BottomSheet(sheetState = sheetState) {
        AlertBottomSheetContent(
            title = { Text(label) },
            buttons = {
                OutlinedButton(
                    shape = MaterialTheme.shapes.small,
                    onClick = { scope.launch { sheetState.hide() } }
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            }
        ) {
            LazyColumn {
                items(entries) { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(52.dp)
                            .fillMaxSize()
                            .clickable {
                                adapter.onChange(item.value)
                                scope.launch { sheetState.hide() }
                            }
                            .padding(horizontal = 16.dp)
                    ) {
                        RadioButton(
                            modifier = Modifier.padding(end = 16.dp),
                            selected = item.value == currentValue,
                            onClick = null
                        )
                        Text(text = item.label())
                    }
                }
            }
        }
    }
}

class ListPreferenceEntry<T>(
    val value: T,
    val label: @Composable () -> String,
)

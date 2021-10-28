package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.AlertBottomSheetContent
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun <T> ListPreference(
    adapter: PreferenceAdapter<T>,
    entries: List<ListPreferenceEntry<T>>,
    label: String,
    enabled: Boolean = true,
    showDivider: Boolean = false
) {
    val sheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()

    val currentValue = adapter.state.value
    val currentLabel = entries
        .firstOrNull { it.value == currentValue }
        ?.label?.invoke()

    PreferenceTemplate(
        title = { Text(text = label) },
        description = { currentLabel?.let { Text(text = it) } },
        enabled = enabled,
        modifier = Modifier
            .clickable(enabled) { scope.launch { sheetState.show() } },
        showDivider = showDivider
    )

    BottomSheet(sheetState = sheetState) {
        AlertBottomSheetContent(
            title = { Text(label) },
            buttons = {
                OutlinedButton(
                    onClick = { scope.launch { sheetState.hide() } }
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            }
        ) {
            LazyColumn {
                itemsIndexed(entries) { index, item ->
                    PreferenceTemplate(
                        title = { Text(item.label()) },
                        modifier = Modifier.clickable {
                            adapter.onChange(item.value)
                            scope.launch { sheetState.hide() }
                        },
                        startWidget = {
                            RadioButton(
                                selected = item.value == currentValue,
                                onClick = null
                            )
                        },
                        showDivider = index > 0,
                        dividerIndent = 40.dp
                    )
                }
            }
        }
    }
}

class ListPreferenceEntry<T>(
    val value: T,
    val label: @Composable () -> String,
)

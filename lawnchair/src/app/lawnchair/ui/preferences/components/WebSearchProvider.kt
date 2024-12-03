package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.search.algorithms.data.WebSearchProvider
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.layout.Chip
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R

@Composable
fun WebSearchProvider(
    adapter: PreferenceAdapter<WebSearchProvider>,
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        WebSearchProvider.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.label) },
            )
        }
    }

    ListPreferenceChips(
        adapter = adapter,
        entries = entries,
        label = stringResource(R.string.allapps_web_suggestion_provider_label),
        modifier = modifier,
    )
}

@Composable
fun <T> ListPreferenceChips(
    adapter: PreferenceAdapter<T>,
    entries: List<ListPreferenceEntry<T>>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListPreferenceChips(
        entries = entries,
        value = adapter.state.value,
        onValueChange = adapter::onChange,
        label = label,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun <T> ListPreferenceChips(
    entries: List<ListPreferenceEntry<T>>,
    value: T,
    onValueChange: (T) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PreferenceTemplate(
        modifier = modifier,
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(text = label) },
        description = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                entries.forEach { item ->
                    Chip(
                        label = item.label(),
                        selected = item.value == value,
                        onClick = { onValueChange(item.value) },
                    )
                }
            }
        },
        enabled = enabled,
        applyPaddings = false,
    )
}

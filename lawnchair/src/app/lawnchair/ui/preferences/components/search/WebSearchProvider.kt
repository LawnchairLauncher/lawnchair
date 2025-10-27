package app.lawnchair.ui.preferences.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.search.algorithms.engine.provider.web.WebSearchProvider
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R

@Composable
fun WebSearchProvider(
    adapter: PreferenceAdapter<WebSearchProvider>,
    nameAdapter: PreferenceAdapter<String>,
    urlAdapter: PreferenceAdapter<String>,
    suggestionsUrlAdapter: PreferenceAdapter<String>,
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

    Column(modifier) {
        ListPreference(
            adapter = adapter,
            entries = entries,
            label = stringResource(R.string.allapps_web_suggestion_provider_label),
        )
        if (adapter.state.value == WebSearchProvider.fromString("custom")) {
            SearchPopupPreference(
                title = stringResource(R.string.custom_search_label),
                initialValue = nameAdapter.state.value,
                placeholder = stringResource(R.string.custom),
                onConfirm = nameAdapter::onChange,
                isErrorCheck = { it.isEmpty() },
            )
            SearchPopupPreference(
                title = stringResource(R.string.custom_search_url),
                initialValue = urlAdapter.state.value,
                placeholder = stringResource(R.string.custom_search_input_placeholder),
                hint = stringResource(R.string.custom_search_input_hint),
                onConfirm = urlAdapter::onChange,
                modifier = Modifier,
            )
            SearchPopupPreference(
                title = stringResource(R.string.custom_search_suggestions_url),
                initialValue = suggestionsUrlAdapter.state.value,
                placeholder = stringResource(R.string.custom_search_input_placeholder),
                hint = stringResource(R.string.custom_search_suggestions_hint),
                onConfirm = suggestionsUrlAdapter::onChange,
                modifier = Modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPopupPreference(
    title: String,
    initialValue: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    isErrorCheck: (String) -> Boolean = { it.isEmpty() || !it.contains("%s") },
) {
    var showPopup by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue(initialValue)) }

    if (showPopup) {
        AlertDialog(
            onDismissRequest = { showPopup = false },
            confirmButton = {
                Button(
                    onClick = {
                        showPopup = false
                        onConfirm(value.text)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPopup = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
            title = {
                Text(title)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isErrorCheck(value.text),
                        placeholder = {
                            Text(placeholder)
                        },
                    )
                    if (hint != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(hint)
                    }
                }
            },
        )
    }

    PreferenceTemplate(
        modifier = modifier.clickable {
            showPopup = true
        },
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(text = title) },
        description = { Text(initialValue) },
        applyPaddings = false,
    )
}

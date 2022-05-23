package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.util.bottomSheetHandler

@Composable
fun TextPreference(
    adapter: PreferenceAdapter<String>,
    label: String,
    description: (String) -> String? = { it },
    enabled: Boolean = true,
) {
    val value = adapter.state.value
    TextPreference(
        value = value,
        onChange = adapter::onChange,
        label = label,
        description = description,
        enabled = enabled,
    )
}

@Composable
fun TextPreference(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    description: (String) -> String? = { it },
    enabled: Boolean = true,
) {
    val bottomSheetHandler = bottomSheetHandler
    PreferenceTemplate(
        title = { Text(text = label) },
        description = { description(value)?.let { Text(text = it) } },
        modifier = Modifier
            .clickable(enabled) {
                bottomSheetHandler.show {
                    TextPreferenceDialog(
                        title = label,
                        initialValue = value,
                        onDismissRequest = { bottomSheetHandler.hide() },
                        onConfirm = onChange,
                    )
                }
            },
        enabled = enabled,
    )
}

@Composable
fun TextPreferenceDialog(
    title: String,
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertBottomSheetContent(
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        buttons = {
            OutlinedButton(
                onClick = onDismissRequest
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Button(
                onClick = {
                    onDismissRequest()
                    onConfirm(value)
                }
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        }
    )
}

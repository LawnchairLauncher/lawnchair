package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.bottomSheetHandler
import app.lawnchair.ui.util.preview.PreferenceGroupPreviewContainer
import app.lawnchair.ui.util.preview.PreviewLawnchair

@Composable
fun TextPreference(
    adapter: PreferenceAdapter<String>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: (String) -> String? = { it },
) {
    val value = adapter.state.value
    TextPreference(
        value = value,
        onChange = adapter::onChange,
        label = label,
        description = description,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
fun TextPreference(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: (String) -> String? = { it },
) {
    val bottomSheetHandler = bottomSheetHandler
    PreferenceTemplate(
        title = { Text(text = label) },
        description = { description(value)?.let { Text(text = it) } },
        modifier = modifier
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextPreferenceDialog(
    title: String,
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(initialValue) }
    ModalBottomSheetContent(
        modifier = modifier,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        buttons = {
            OutlinedButton(
                onClick = onDismissRequest,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Button(
                onClick = {
                    onDismissRequest()
                    onConfirm(value)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
    )
}

@PreviewLawnchair
@Composable
private fun TextPreferencePreview() {
    LawnchairTheme {
        PreferenceGroupPreviewContainer {
            Item {
                TextPreference(
                    value = "Value",
                    onChange = {},
                    label = "Label",
                )
            }
        }
    }
}

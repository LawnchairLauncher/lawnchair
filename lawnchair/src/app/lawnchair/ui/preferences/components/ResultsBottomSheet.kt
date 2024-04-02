package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.PreviewLawnchair
import app.lawnchair.ui.util.bottomSheetHandler

@Composable
fun ResultsBottomSheet(
    adapter: PreferenceAdapter<Boolean>,
    maxCountAdapter: PreferenceAdapter<Int>,
    maxCountRange: ClosedRange<Int>,
    label: String,
    maxCountLabel: String,
    preventSwitchChange: Boolean = false,
    description: String? = null,
    enabled: Boolean = true,
    requestEnabled: (() -> Unit)? = null,
    requestEnabledDescription: String = "Requested permission not granted.",
    content: @Composable (() -> Unit)? = null
) {
    val bottomSheetHandler = bottomSheetHandler

    CustomSwitchPreference(
        label = label,
        description = description,
        checked = adapter.state.value,
        enabled = enabled,
        onClick = {
            bottomSheetHandler.show {
                ResultsBottomSheetContent(
                    onHide = { bottomSheetHandler.hide() },
                    enabled = enabled,
                    adapterValue = adapter.state.value,
                    adapterOnChange = adapter::onChange,
                    label = label,
                    description = description,
                    maxCountLabel = maxCountLabel,
                    maxCountAdapter = maxCountAdapter,
                    maxCountRange = maxCountRange,
                    content = content,
                    requestEnabled = requestEnabled,
                    requestEnabledDescription = requestEnabledDescription,
                    preventSwitchChange = preventSwitchChange
                )
            }
        },
    )
}

@Composable
private fun ResultsBottomSheetContent(
    onHide: () -> Unit,
    enabled: Boolean,
    adapterValue: Boolean,
    adapterOnChange: (Boolean) -> Unit,
    label: String,
    description: String?,
    maxCountLabel: String,
    maxCountAdapter: PreferenceAdapter<Int>,
    maxCountRange: ClosedRange<Int>,
    content: @Composable (() -> Unit)?,
    requestEnabled: (() -> Unit)?,
    requestEnabledDescription: String,
    preventSwitchChange: Boolean = false,
) {
    ModalBottomSheetContent(
        buttons = {
            OutlinedButton(onClick = { onHide() }) {
                Text(text = "Apply")
            }
        },
    ) {
        Column {
            MainSwitchPreference(
                checked = adapterValue,
                onCheckedChange = { if (!preventSwitchChange) adapterOnChange(it) },
                label = label,
                description = description,
                enabled = enabled,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (!enabled) {
                        if (requestEnabled != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = requestEnabledDescription,
                                )
                                Button(
                                    onClick = { requestEnabled() }
                                ) {
                                    Text(text = "Grant permissions")
                                }
                            }
                        }
                    } else {
                        SliderPreference(
                            label = maxCountLabel,
                            adapter = maxCountAdapter,
                            valueRange = maxCountRange,
                            step = 1,
                        )
                        content?.invoke()
                    }
                }
            }
        }
    }
}


@Composable
private fun CustomSwitchPreference(
    label: String,
    description: String? = null,
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    PreferenceTemplate(
        modifier = Modifier.clickable {
            onClick()
        },
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(text = label) },
        description = { description?.let { Text(text = it) } },
        endWidget = {
            Switch(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .height(24.dp),
                checked = checked,
                onCheckedChange = { onClick() },
                enabled = enabled,
            )
        },
        applyPaddings = false,
    )
}

@PreviewLawnchair
@Composable
fun CustomSwitchPreferencePreview() {
    LawnchairTheme {
        CustomSwitchPreference(
            label = "example",
            checked = true,
            onClick = { /*TODO*/ },
            enabled = true,
        )
    }
}

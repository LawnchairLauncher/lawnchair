package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.theme.dividerColor
import app.lawnchair.ui.util.PreviewLawnchair
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SearchSuggestionPreference(
    adapter: PreferenceAdapter<Boolean>,
    maxCountAdapter: PreferenceAdapter<Int>,
    maxCountRange: ClosedRange<Int>,
    label: String,
    maxCountLabel: String,
    description: String? = null,
    permissionState: PermissionState? = null,
    permissionRationale: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val isGranted = permissionState?.status?.isGranted ?: true

    LaunchedEffect("") {
        if (!isGranted) {
            adapter.onChange(false)
        }
    }

    SearchSuggestionPreference(
        adapter = adapter,
        maxCountAdapter = maxCountAdapter,
        maxCountRange = maxCountRange,
        label = label,
        maxCountLabel = maxCountLabel,
        description = description,
        isGranted = isGranted,
        onRequestPermission = {
            permissionState?.launchPermissionRequest()
        },
        permissionRationale = permissionRationale,
        content = content,
    )
}

@Composable
fun SearchSuggestionPreference(
    adapter: PreferenceAdapter<Boolean>,
    maxCountAdapter: PreferenceAdapter<Int>,
    maxCountRange: ClosedRange<Int>,
    label: String,
    maxCountLabel: String,
    onRequestPermission: (() -> Unit)?,
    isGranted: Boolean = true,
    description: String? = null,
    permissionRationale: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val bottomSheetHandler = bottomSheetHandler
    val preventSwitchChange = false

    SearchSuggestionsSwitchPreference(
        label = label,
        description = description,
        checked = adapter.state.value,
        enabled = isGranted,
        preventSwitchChange = preventSwitchChange,
        onClick = {
            bottomSheetHandler.show {
                BottomSheetContent(
                    onHide = { bottomSheetHandler.hide() },
                    isPermissionGranted = isGranted,
                    adapterValue = adapter.state.value,
                    adapterOnChange = adapter::onChange,
                    label = label,
                    description = description,
                    maxCountLabel = maxCountLabel,
                    maxCountAdapter = maxCountAdapter,
                    maxCountRange = maxCountRange,
                    content = content,
                    onRequestPermission = onRequestPermission,
                    permissionRationale = permissionRationale,
                    preventSwitchChange = preventSwitchChange,
                )
            }
        },
    )
}

@Composable
private fun BottomSheetContent(
    adapterValue: Boolean,
    adapterOnChange: (Boolean) -> Unit,
    label: String,
    description: String?,
    maxCountLabel: String,
    maxCountAdapter: PreferenceAdapter<Int>,
    maxCountRange: ClosedRange<Int>,
    isPermissionGranted: Boolean,
    onHide: () -> Unit,
    onRequestPermission: (() -> Unit)?,
    // TODO optimize permission requesting code
    permissionRationale: String?,
    preventSwitchChange: Boolean = false,
    content: @Composable (() -> Unit)?,
) {
    ModalBottomSheetContent(
        buttons = {
            OutlinedButton(onClick = { onHide() }) {
                Text(text = stringResource(id = R.string.action_apply))
            }
        },
    ) {
        Column {
            MainSwitchPreference(
                checked = adapterValue,
                onCheckedChange = {
                    if (!preventSwitchChange) {
                        adapterOnChange(it)
                    }
                },
                label = label,
                description = description,
                enabled = if (preventSwitchChange) false else isPermissionGranted,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SliderPreference(
                        label = maxCountLabel,
                        adapter = maxCountAdapter,
                        valueRange = maxCountRange,
                        step = 1,
                    )
                    content?.invoke()
                }
            }
            if (!isPermissionGranted) {
                if (onRequestPermission != null && permissionRationale != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp),
                        ) {
                            Text(
                                text = permissionRationale,
                            )
                            Button(
                                onClick = {
                                    onHide()
                                    onRequestPermission()
                                },
                            ) {
                                Text(text = stringResource(id = R.string.grant_requested_permissions))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionsSwitchPreference(
    label: String,
    checked: Boolean,
    preventSwitchChange: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    description: String? = null,
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
            Spacer(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(dividerColor()),
            )
            Switch(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .height(24.dp),
                checked = checked,
                onCheckedChange = { onClick() },
                enabled = if (preventSwitchChange) false else enabled,
            )
        },
        applyPaddings = false,
    )
}

@PreviewLawnchair
@Composable
private fun SearchSuggestionsSwitchPreferencePreview() {
    LawnchairTheme {
        SearchSuggestionsSwitchPreference(
            label = "example",
            checked = true,
            onClick = { /*TODO*/ },
            preventSwitchChange = false,
            enabled = true,
        )
    }
}

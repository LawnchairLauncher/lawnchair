package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.ui.theme.lightenColor
import com.android.launcher3.R
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterialApi::class)
fun ColorPreference(
    adapter: PreferenceAdapter<ColorOption>,
    label: String,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>>,
    staticEntries: List<ColorPreferenceEntry<ColorOption>>
) {
    var selectedColor by adapter
    val selectedEntry = dynamicEntries.firstOrNull { it.value == selectedColor }
        ?: staticEntries.firstOrNull { it.value == selectedColor }
    val defaultTabIndex = if (dynamicEntries.any { it.value == selectedColor }) 0 else 1
    val bottomSheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val description = selectedEntry?.label?.invoke()

    PreferenceTemplate(
        title = { Text(text = label) },
        endWidget = { ColorDot(color = MaterialTheme.colors.primary) },
        modifier = Modifier.clickable {
            coroutineScope.launch {
                bottomSheetState.show()
            }
        },
        description = {
            if (description != null) {
                Text(text = description)
            }
        }
    )

    BottomSheet(sheetState = bottomSheetState) {
        var selectedTabIndex by remember { mutableStateOf(value = defaultTabIndex) }
        AlertBottomSheetContent(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
            title = { Text(text = label) },
            buttons = {
                Button(
                    shape = MaterialTheme.shapes.small,
                    onClick = { coroutineScope.launch { bottomSheetState.hide() } }
                ) {
                    Text(text = stringResource(id = R.string.done))
                }
            }
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Chip(
                        label = stringResource(id = R.string.dynamic),
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 }
                    )
                    Chip(
                        label = stringResource(id = R.string.presets),
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 }
                    )
                }
                Box(
                    modifier = Modifier
                        .pagerHeight(
                            dynamicCount = dynamicEntries.size,
                            staticCount = staticEntries.size
                        )
                ) {
                    when (selectedTabIndex) {
                        0 -> {
                            PresetsList(dynamicEntries, adapter)
                        }
                        1 -> {
                            SwatchGrid(
                                entries = staticEntries,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 20.dp,
                                    end = 16.dp,
                                    bottom = 16.dp
                                ),
                                onSwatchClick = { selectedColor = it },
                                isSwatchSelected = { it == selectedColor }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetsList(
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>>,
    adapter: PreferenceAdapter<ColorOption>,
) {
    DividerColumn(modifier = Modifier.padding(top = 16.dp)) {
        dynamicEntries.map { entry ->
            key(entry) {
                PreferenceTemplate(
                    title = { Text(text = entry.label()) },
                    verticalPadding = 12.dp,
                    modifier = Modifier.clickable { adapter.onChange(entry.value) },
                    startWidget = {
                        RadioButton(
                            selected = entry.value == adapter.state.value,
                            onClick = null
                        )
                        ColorDot(
                            entry = entry,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                )
            }
        }
    }
}

open class ColorPreferenceEntry<T>(
    val value: T,
    val label: @Composable () -> String,
    val lightColor: @Composable () -> Int,
    val darkColor: @Composable () -> Int = { lightenColor(lightColor()) },
)
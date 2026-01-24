package app.lawnchair.ui.preferences.components.colorpreference.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.ColorDot
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R

@Composable
fun PresetsList(
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>>,
    onPresetClick: (ColorOption) -> Unit,
    isPresetSelected: (ColorOption) -> Boolean,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        heading = stringResource(id = R.string.dynamic),
        modifier = modifier.padding(top = 12.dp),
    ) {
        dynamicEntries.forEach { entry ->
            Item(key = entry) {
                PreferenceTemplate(
                    title = { Text(text = entry.label()) },
                    verticalPadding = 12.dp,
                    modifier = Modifier.clickable { onPresetClick(entry.value) },
                    startWidget = {
                        RadioButton(
                            selected = isPresetSelected(entry.value),
                            onClick = null,
                        )
                        ColorDot(
                            entry = entry,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
        }
    }
}

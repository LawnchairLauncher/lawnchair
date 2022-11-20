package app.lawnchair.ui.preferences.components.colorpreference.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.PreferenceDivider
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import app.lawnchair.ui.preferences.components.colorpreference.ColorDot
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import com.android.launcher3.R

@Composable
fun PresetsList(
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>>,
    onPresetClick: (ColorOption) -> Unit,
    isPresetSelected: (ColorOption) -> Boolean,
) {

    PreferenceGroup(
        heading = stringResource(id = R.string.dynamic),
        modifier = Modifier.padding(top = 12.dp),
        showDividers = false,
    ) {

        dynamicEntries.mapIndexed { index, entry ->
            key(entry) {
                if (index > 0) {
                    PreferenceDivider(startIndent = 40.dp)
                }
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

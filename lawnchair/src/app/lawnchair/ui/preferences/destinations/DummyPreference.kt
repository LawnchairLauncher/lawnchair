package app.lawnchair.ui.preferences.destinations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R

@Composable
fun DummyPreference(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = "",
        backArrowVisible = false,
        modifier = modifier,
    ) {
        PreferenceTemplate(title = {
            Text(stringResource(R.string.settings_pick_a_page))
        })
    }
}

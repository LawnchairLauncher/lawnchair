package ch.deletescape.lawnchair.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

@Composable
fun GeneralSettings() {
    Column {
        SwitchSetting(
            checked = true,
            onCheckedChange = { /*TODO*/ },
            title = stringResource(id = R.string.home_screen_rotation_label),
            subtitle = stringResource(id = R.string.home_screen_rotaton_description)
        )
    }
}
package ch.deletescape.lawnchair.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

@Composable
fun HomeScreenSettings(interactor: SettingsInteractor) {
    Column {
        SwitchSetting(
            checked = interactor.addIconToHome.value,
            onCheckedChange = {
                interactor.setAddIconToHome(it)
            },
            title = stringResource(id = R.string.auto_add_shortcuts_label)
        )
    }
}

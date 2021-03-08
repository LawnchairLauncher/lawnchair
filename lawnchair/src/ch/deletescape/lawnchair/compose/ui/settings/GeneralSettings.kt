package ch.deletescape.lawnchair.compose.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.android.launcher3.R

@Composable
fun GeneralSettings(navController: NavController, interactor: SettingsInteractor) {
    Column {
        SwitchSetting(
            checked = interactor.allowRotation.value,
            onCheckedChange = {
                interactor.setAllowRotation(it)
            },
            title = stringResource(id = R.string.home_screen_rotation_label),
            subtitle = stringResource(id = R.string.home_screen_rotaton_description)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SwitchSetting(
                checked = interactor.wrapAdaptiveIcons.value,
                onCheckedChange = {
                    interactor.setWrapAdaptiveIcons(it)
                },
                title = stringResource(id = R.string.make_icon_packs_adaptive_label),
                subtitle = stringResource(id = R.string.make_icon_packs_adaptive_description)
            )
        }
        NavActionSetting(
            title = stringResource(id = R.string.icon_pack),
            navController = navController,
            destination = Screen.IconPackSettings.route
        )
    }
}
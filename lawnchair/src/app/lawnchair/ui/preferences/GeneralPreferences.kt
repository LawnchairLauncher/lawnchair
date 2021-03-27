package app.lawnchair.ui.preferences

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.android.launcher3.R

@ExperimentalAnimationApi
@Composable
fun GeneralPreferences(navController: NavController, interactor: PreferenceInteractor) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroup(isFirstChild = true) {
            SwitchPreference(
                checked = interactor.allowRotation.value,
                onCheckedChange = { interactor.setAllowRotation(it) },
                label = stringResource(id = R.string.home_screen_rotation_label),
                description = stringResource(id = R.string.home_screen_rotaton_description),
                showDivider = false
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            NotificationDotsPreference(interactor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SwitchPreference(
                    checked = interactor.wrapAdaptiveIcons.value,
                    onCheckedChange = { interactor.setWrapAdaptiveIcons(it) },
                    label = stringResource(id = R.string.make_icon_packs_adaptive_label),
                    description = stringResource(id = R.string.make_icon_packs_adaptive_description)
                )
                AnimatedVisibility(
                    visible = interactor.wrapAdaptiveIcons.value,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SwitchPreference(
                        checked = interactor.makeColoredBackgrounds.value,
                        onCheckedChange = { interactor.setMakeColoredBackgrounds(it) },
                        label = stringResource(id = R.string.colored_generated_icon_backgrounds_label),
                        description = stringResource(id = R.string.colored_generated_icon_backgrounds_description)
                    )
                }
            }
            NavActionPreference(
                label = stringResource(id = R.string.icon_pack),
                navController = navController,
                destination = ICON_PACK_PREFERENCES_ROUTE,
                showDivider = false
            )
        }
    }
}
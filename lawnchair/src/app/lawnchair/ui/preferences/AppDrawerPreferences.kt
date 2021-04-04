package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

@ExperimentalAnimationApi
@Composable
fun AppDrawerPreferences(interactor: PreferenceInteractor) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.general_label), isFirstChild = true) {
            SliderPreference(
                label = stringResource(id = R.string.background_opacity),
                value = interactor.drawerOpacity.value,
                onValueChange = { interactor.setDrawerOpacity(it) },
                steps = 2,
                valueRange = 0.7F..1F,
                showDivider = false,
                showAsPercentage = true
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.app_drawer_columns),
                value = interactor.allAppsColumns.value,
                onValueChange = { interactor.setAllAppsColumns(it) },
                steps = 3,
                valueRange = 3.0F..7.0F,
                showDivider = false
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_size),
                value = interactor.allAppsIconSizeFactor.value,
                onValueChange = { interactor.setAllAppsIconSizeFactor(it) },
                steps = 9,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true
            )
            SliderPreference(
                label = stringResource(id = R.string.label_size),
                value = interactor.allAppsTextSizeFactor.value,
                onValueChange = { interactor.setAllAppsTextSizeFactor(it) },
                steps = 9,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
                showDivider = false
            )
        }
    }
}
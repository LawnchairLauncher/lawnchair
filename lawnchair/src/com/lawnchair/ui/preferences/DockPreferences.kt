package com.lawnchair.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

@Composable
fun DockPreferences(interactor: PreferenceInteractor) {
    Column {
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            SliderPreference(
                label = stringResource(id = R.string.dock_icons),
                value = interactor.hotseatColumns.value,
                onValueChange = { interactor.setHotseatColumns(it) },
                steps = 3,
                valueRange = 3.0F..7.0F,
                showDivider = false
            )
        }
    }
}
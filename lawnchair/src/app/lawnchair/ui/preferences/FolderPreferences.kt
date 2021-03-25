package app.lawnchair.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

@Composable
fun FolderPreferences(interactor: PreferenceInteractor) {
    PreferenceGroup(heading = stringResource(id = R.string.grid), isFirstChild = true) {
        SliderPreference(
            label = stringResource(id = R.string.max_folder_columns),
            value = interactor.folderColumns.value,
            onValueChange = { interactor.setFolderColumns(it) },
            steps = 2,
            valueRange = 2.0F..5.0F
        )
        SliderPreference(
            label = stringResource(id = R.string.max_folder_rows),
            value = interactor.folderRows.value,
            onValueChange = { interactor.setFolderRows(it) },
            steps = 2,
            valueRange = 2.0F..5.0F,
            showDivider = false
        )
    }
}
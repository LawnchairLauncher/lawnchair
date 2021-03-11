package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

@Composable
fun FolderPreferences(interactor: PreferenceInteractor) {
    PreferenceGroup {
        SliderPreference(
            label = stringResource(id = R.string.max_folder_columns),
            value = interactor.folderColumns.value,
            onValueChange = { interactor.setFolderColumns(it) },
            steps = 3,
            valueRange = 3.0F..7.0F
        )
        SliderPreference(
            label = stringResource(id = R.string.max_folder_rows),
            value = interactor.folderRows.value,
            onValueChange = { interactor.setFolderRows(it) },
            steps = 3,
            valueRange = 3.0F..7.0F
        )
    }
}
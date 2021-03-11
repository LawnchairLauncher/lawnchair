package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.runtime.Composable

@Composable
fun FolderPreferences(interactor: PreferenceInteractor) {
    PreferenceGroup {
        SliderPreference(
            label = "Max. Folder Columns",
            value = interactor.folderColumns.value,
            onValueChange = { interactor.setFolderColumns(it) },
            steps = 3,
            valueRange = 3.0F..7.0F
        )
        SliderPreference(
            label = "Max. Folder Rows",
            value = interactor.folderRows.value,
            onValueChange = { interactor.setFolderRows(it) },
            steps = 3,
            valueRange = 3.0F..7.0F
        )
    }
}
package app.lawnchair.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

@Composable
fun FolderPreferences(interactor: PreferenceInteractor) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
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
}
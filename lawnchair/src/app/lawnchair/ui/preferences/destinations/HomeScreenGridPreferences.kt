package app.lawnchair.ui.preferences.destinations

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.asPreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.GridOverridesPreview
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreenGridPreferences(
    modifier: Modifier = Modifier,
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val scrollState = rememberScrollState()
    PreferenceLayout(
        label = stringResource(id = R.string.home_screen_grid),
        modifier = modifier,
        isExpandedScreen = true,
        scrollState = if (isPortrait) null else scrollState,
    ) {
        val prefs = preferenceManager()
        val columnsAdapter = prefs.workspaceColumns.getAdapter()
        val rowsAdapter = prefs.workspaceRows.getAdapter()
        val increaseMaxGridSize = prefs.workspaceIncreaseMaxGridSize.getAdapter()

        val originalColumns = remember { columnsAdapter.state.value }
        val originalRows = remember { rowsAdapter.state.value }
        val columns = rememberSaveable { mutableIntStateOf(originalColumns) }
        val rows = rememberSaveable { mutableIntStateOf(originalRows) }

        if (isPortrait) {
            GridOverridesPreview {
                copy(numColumns = columns.intValue, numRows = rows.intValue)
            }
        }

        val maxGridSize = if (increaseMaxGridSize.state.value) 20 else 10

        PreferenceGroup {
            SliderPreference(
                label = stringResource(id = R.string.columns),
                adapter = columns.asPreferenceAdapter(),
                step = 1,
                valueRange = 3..maxGridSize,
            )
            SliderPreference(
                label = stringResource(id = R.string.rows),
                adapter = rows.asPreferenceAdapter(),
                step = 1,
                valueRange = 3..maxGridSize,
            )
        }

        val navController = LocalNavController.current
        val context = LocalContext.current
        val applyOverrides = {
            prefs.batchEdit {
                columnsAdapter.onChange(columns.intValue)
                rowsAdapter.onChange(rows.intValue)
            }
            LauncherAppState.getIDP(context).onPreferencesChanged(context)
            navController.popBackStack()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp),
        ) {
            Button(
                onClick = { applyOverrides() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(),
                enabled = columns.intValue != originalColumns || rows.intValue != originalRows,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = R.string.action_apply))
            }
        }
    }
}

package app.lawnchair.ui.preferences

import android.content.res.Configuration
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.asPreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.GridOverridesPreview
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SliderPreference
import com.android.launcher3.R

@ExperimentalAnimationApi
fun NavGraphBuilder.homeScreenGridGraph(route: String) {
    preferenceGraph(route, { HomeScreenGridPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun HomeScreenGridPreferences() {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val scrollState = rememberScrollState()
    PreferenceLayout(
        label = stringResource(id = R.string.home_screen_grid),
        scrollState = if (isPortrait) null else scrollState
    ) {
        val prefs = preferenceManager()
        val columnsAdapter = prefs.workspaceColumns.getAdapter()
        val rowsAdapter = prefs.workspaceRows.getAdapter()

        val originalColumns = remember { columnsAdapter.state.value }
        val originalRows = remember { rowsAdapter.state.value }
        val columns = rememberSaveable { mutableStateOf(originalColumns) }
        val rows = rememberSaveable { mutableStateOf(originalRows) }

        if (isPortrait) {
            GridOverridesPreview(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .weight(1f)
                    .align(Alignment.CenterHorizontally)
                    .clip(MaterialTheme.shapes.large)
            ) {
                numColumns = columns.value
                numRows = rows.value
            }
        }

        PreferenceGroup {
            SliderPreference(
                label = stringResource(id = R.string.columns),
                adapter = columns.asPreferenceAdapter(),
                step = 1,
                valueRange = 3..10,
            )
            SliderPreference(
                label = stringResource(id = R.string.rows),
                adapter = rows.asPreferenceAdapter(),
                step = 1,
                valueRange = 3..10,
            )
        }

        val navController = LocalNavController.current
        val applyOverrides = {
            prefs.batchEdit {
                columnsAdapter.onChange(columns.value)
                rowsAdapter.onChange(rows.value)
            }
            navController.popBackStack()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp)
        ) {
            Button(
                onClick = { applyOverrides() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(),
                enabled = columns.value != originalColumns || rows.value != originalRows
            ) {
                Text(text = stringResource(id = R.string.apply_grid))
            }
        }
    }
}

package app.lawnchair.ui.preferences.destinations

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.preferences.asPreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.GridOverridesPreview
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreenGridPreferences(
    modifier: Modifier = Modifier,
) {
    val isExpandedScreen = LocalIsExpandedScreen.current
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    PreferenceLayout(
        label = stringResource(id = R.string.home_screen_grid),
        modifier = modifier,
        isExpandedScreen = true,
        scrollState = null,
    ) {
        val controlsScrollState = rememberScrollState()
        val prefs = preferenceManager()
        val columnsAdapter = prefs.workspaceColumns.getAdapter()
        val rowsAdapter = prefs.workspaceRows.getAdapter()
        val hotseatColumnsAdapter = prefs.hotseatColumns.getAdapter()
        val hotseatColumnsUnfoldedAdapter = prefs.hotseatColumnsUnfolded.getAdapter()
        val increaseMaxGridSize = prefs.workspaceIncreaseMaxGridSize.getAdapter()
        val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY

        val originalColumns = remember { columnsAdapter.state.value }
        val originalRows = remember { rowsAdapter.state.value }
        val originalHotseatColumns = remember { hotseatColumnsAdapter.state.value }
        val originalHotseatColumnsUnfolded = remember { hotseatColumnsUnfoldedAdapter.state.value }

        val columns = rememberSaveable { mutableIntStateOf(originalColumns) }
        val rows = rememberSaveable { mutableIntStateOf(originalRows) }
        val hotseatColumns = rememberSaveable { mutableIntStateOf(originalHotseatColumns) }
        val hotseatColumnsUnfolded = rememberSaveable {
            mutableIntStateOf(originalHotseatColumnsUnfolded.coerceAtLeast(originalHotseatColumns))
        }

        LaunchedEffect(hotseatColumns.intValue) {
            if (hotseatColumnsUnfolded.intValue < hotseatColumns.intValue) {
                hotseatColumnsUnfolded.intValue = hotseatColumns.intValue
            }
        }

        val maxGridSize = if (increaseMaxGridSize.state.value) 20 else 10

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Don't stress over the coerce value,
            // we eyeballing it till there's a much better solutions(tm) or permanent workarounds.
            val settingsMinHeight = when {
                // This should allow user to see the unfolded label,
                // which will be their indicator that they can scroll the settings entries.
                isFoldable -> (maxHeight * 0.58f).coerceAtLeast(360.dp)

                // This should be enough for 3 preferences, they can't scroll beyond this.
                // Should you introduce another prefs, raise the value by a little so that they
                // have an indication that you can scroll the entries.
                isPortrait -> (maxHeight * 0.40f).coerceAtLeast(315.dp)

                // Landscape mode
                else -> (maxHeight * 0.52f).coerceAtLeast(280.dp)
            }
            val previewMaxHeight = (maxHeight - settingsMinHeight)
                .coerceAtLeast(if (isPortrait) 180.dp else 140.dp)

            Column(
                modifier = Modifier.fillMaxHeight(),
            ) {
                GridOverridesPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = previewMaxHeight)
                        .padding(horizontal = 16.dp, vertical = if (isPortrait) 16.dp else 12.dp),
                    expandToAvailableSpace = false,
                    updateGridOptions = {
                        copy(
                            numColumns = columns.intValue,
                            numRows = rows.intValue,
                            numHotseatColumns = hotseatColumns.intValue,
                        )
                    },
                    previewOverrides = if (isFoldable) {
                        DeviceProfileOverrides.PreviewOverrides(
                            foldableDatabaseHotseatIcons = hotseatColumnsUnfolded.intValue,
                        )
                    } else {
                        DeviceProfileOverrides.PreviewOverrides()
                    },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = settingsMinHeight),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(controlsScrollState),
                    ) {
                        if (isFoldable) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = if (isExpandedScreen) Int.MAX_VALUE else 1,
                            ) {
                                PreferenceGroup(heading = stringResource(id = R.string.when_folded_label)) {
                                    Item {
                                        SliderPreference(
                                            label = stringResource(id = R.string.columns),
                                            adapter = columns.asPreferenceAdapter(),
                                            step = 1,
                                            valueRange = 3..maxGridSize,
                                        )
                                    }
                                    Item {
                                        SliderPreference(
                                            label = stringResource(id = R.string.rows),
                                            adapter = rows.asPreferenceAdapter(),
                                            step = 1,
                                            valueRange = 3..maxGridSize,
                                        )
                                    }
                                    Item {
                                        SliderPreference(
                                            label = stringResource(id = R.string.dock_icons),
                                            adapter = hotseatColumns.asPreferenceAdapter(),
                                            step = 1,
                                            valueRange = 3..maxGridSize,
                                        )
                                    }
                                }

                                PreferenceGroup(
                                    heading = stringResource(id = R.string.when_unfolded_label),
                                ) {
                                    Item {
                                        SliderPreference(
                                            label = stringResource(id = R.string.dock_icons),
                                            adapter = hotseatColumnsUnfolded.asPreferenceAdapter(),
                                            step = 1,
                                            valueRange = hotseatColumns.intValue..maxGridSize,
                                        )
                                    }
                                    Item {
                                        FakeExpandedGridPreference(
                                            columns = columns.intValue * 2,
                                            rows = rows.intValue,
                                            description = stringResource(id = R.string.unfolded_grid_description),
                                        )
                                    }
                                }
                            }
                        } else {
                            PreferenceGroup {
                                Item {
                                    SliderPreference(
                                        label = stringResource(id = R.string.columns),
                                        adapter = columns.asPreferenceAdapter(),
                                        step = 1,
                                        valueRange = 3..maxGridSize,
                                    )
                                }
                                Item {
                                    SliderPreference(
                                        label = stringResource(id = R.string.rows),
                                        adapter = rows.asPreferenceAdapter(),
                                        step = 1,
                                        valueRange = 3..maxGridSize,
                                    )
                                }
                                Item {
                                    SliderPreference(
                                        label = stringResource(id = R.string.dock_icons),
                                        adapter = hotseatColumns.asPreferenceAdapter(),
                                        step = 1,
                                        valueRange = 3..maxGridSize,
                                    )
                                }
                            }
                        }
                    }

                    val navController = LocalNavController.current
                    val context = LocalContext.current
                    val applyOverrides = {
                        prefs.batchEdit {
                            columnsAdapter.onChange(columns.intValue)
                            rowsAdapter.onChange(rows.intValue)
                            hotseatColumnsAdapter.onChange(hotseatColumns.intValue)
                            hotseatColumnsUnfoldedAdapter.onChange(hotseatColumnsUnfolded.intValue)
                        }
                        InvariantDeviceProfile.INSTANCE.get(context).onPreferencesChanged(context)
                        navController.popBackStack()
                    }

                    val isChanged = columns.intValue != originalColumns ||
                        rows.intValue != originalRows ||
                        hotseatColumns.intValue != originalHotseatColumns ||
                        hotseatColumnsUnfolded.intValue != originalHotseatColumnsUnfolded

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
                            enabled = isChanged,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(text = stringResource(id = R.string.action_apply))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FakeExpandedGridPreference(
    columns: Int,
    rows: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    PreferenceTemplate(
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.grid),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                ) {
                    Text(
                        text = stringResource(id = R.string.x_by_y, columns, rows),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        description = {
            Text(description)
        },
        modifier = modifier,
        applyPaddings = false,
        enabled = false,
    )
}

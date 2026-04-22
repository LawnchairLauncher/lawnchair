/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.destinations

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.OverflowMenuGrouped
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appComparator
import app.lawnchair.util.appsState
import com.android.launcher3.R
import java.util.Comparator.comparing

@Composable
fun HiddenAppsPreferences(
    modifier: Modifier = Modifier,
) {
    val adapter = preferenceManager2().hiddenApps.getAdapter()
    val hiddenApps by adapter.state
    val pageTitle =
        if (hiddenApps.isEmpty()) {
            stringResource(id = R.string.hidden_apps_label)
        } else {
            stringResource(id = R.string.hidden_apps_label_with_count, hiddenApps.size)
        }
    val apps by appsState(comparator = hiddenAppsComparator(hiddenApps))
    val state = rememberLazyListState()
    PreferenceScaffold(
        label = pageTitle,
        actions = {
            if (apps.isNotEmpty()) {
                ListSortingOptions(
                    originalList = apps,
                    filteredList = hiddenApps,
                    onUpdateList = {
                        adapter.onChange(it)
                    },
                )
            }
        },
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = apps.isNotEmpty(), label = "") { present ->
            if (present) {
                PreferenceLazyColumn(it, state = state) {
                    val toggleHiddenApp = { app: App ->
                        val key = app.key.toString()
                        val newSet = apps.asSequence()
                            .filter { hiddenApps.contains(it.key.toString()) }
                            .map { it.key.toString() }
                            .toMutableSet()
                        val isHidden = !hiddenApps.contains(key)
                        if (isHidden) newSet.add(key) else newSet.remove(key)
                        adapter.onChange(newSet)
                    }
                    preferenceGroupItems(
                        items = apps,
                        isFirstChild = true,
                    ) { _, app ->
                        AppItem(
                            app = app,
                            onClick = toggleHiddenApp,
                        ) {
                            Checkbox(
                                checked = hiddenApps.contains(app.key.toString()),
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, enabled = false) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                    ) {
                        AppItemPlaceholder {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListSortingOptions(
    originalList: List<App>,
    filteredList: Set<String>,
    onUpdateList: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    OverflowMenuGrouped(modifier) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, 2),
        ) {
            DropdownMenuItem(
                onClick = {
                    val inverseSelection = originalList
                        .map { it.key.toString() }
                        .filter { !filteredList.contains(it) }
                        .toSet()
                    onUpdateList(inverseSelection)
                    hideMenu()
                },
                text = {
                    Text(stringResource(R.string.inverse_selection))
                },
            )
            val originalKeys = originalList
                .map { it.key.toString() }
                .toSet()
            val selectedAll = originalKeys == filteredList
            DropdownMenuItem(
                onClick = {
                    onUpdateList(
                        if (selectedAll) {
                            emptySet()
                        } else {
                            originalList
                                .map { it.key.toString() }
                                .toSet()
                        },
                    )
                    hideMenu()
                },
                text = {
                    Text(
                        stringResource(if (selectedAll) R.string.deselect_all else R.string.select_all),
                    )
                },
            )
        }

        Spacer(Modifier.height(MenuDefaults.GroupSpacing))

        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(1, 2),
        ) {
            DropdownMenuItem(
                onClick = {
                    onUpdateList(
                        emptySet(),
                    )
                },
                text = {
                    Text(stringResource(R.string.action_reset))
                },
            )
        }
    }
}

@Composable
fun hiddenAppsComparator(hiddenApps: Set<String>): Comparator<App> = remember {
    comparing<App, Int> {
        if (hiddenApps.contains(it.key.toString())) 0 else 1
    }.then(appComparator)
}

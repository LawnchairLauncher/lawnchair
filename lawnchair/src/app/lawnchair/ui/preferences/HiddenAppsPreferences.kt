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

package app.lawnchair.ui.preferences

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.App
import app.lawnchair.util.appComparator
import app.lawnchair.util.appsList
import com.android.launcher3.R
import java.util.Comparator.comparing

fun NavGraphBuilder.hiddenAppsGraph(route: String) {
    preferenceGraph(route, { HiddenAppsPreferences() })
}

@Composable
fun HiddenAppsPreferences() {
    val adapter = preferenceManager2().hiddenApps.getAdapter()
    val hiddenApps by adapter.state
    val pageTitle =
        if (hiddenApps.isEmpty()) stringResource(id = R.string.hidden_apps_label)
        else stringResource(id = R.string.hidden_apps_label_with_count, hiddenApps.size)
    val optionalApps by appsList(comparator = hiddenAppsComparator(hiddenApps))
    val state = rememberLazyListState()
    PreferenceScaffold(
        label = pageTitle,
    ) {
        Crossfade(targetState = optionalApps.isPresent) { present ->
            if (present) {
                PreferenceLazyColumn(state = state) {
                    val apps = optionalApps.get()
                    val toggleHiddenApp = { app: App ->
                        val key = app.key.toString()
                        val newSet = apps
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
                        dividerStartIndent = 40.dp,
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
                PreferenceLazyColumn(enabled = false) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                        dividerStartIndent = 40.dp,
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

@Composable
fun hiddenAppsComparator(hiddenApps: Set<String>): Comparator<App> = remember {
    comparing<App, Int> {
        if (hiddenApps.contains(it.key.toString())) 0 else 1
    }.then(appComparator)
}

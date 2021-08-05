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
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.DefaultAppFilter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.getState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.*
import com.android.launcher3.R
import java.util.Comparator.comparing

@ExperimentalAnimationApi
fun NavGraphBuilder.hiddenAppsGraph(route: String) {
    preferenceGraph(route, { HiddenAppsPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun HiddenAppsPreferences() {
    val pageTitle =
        with(hiddenAppsCount()) {
            if (this == 0) {
                stringResource(id = R.string.hidden_apps_label)
            } else {
                stringResource(id = R.string.hidden_apps_label_with_count, this)
            }
        }
    val context = LocalContext.current
    var hiddenApps by preferenceManager().hiddenAppSet.getAdapter()
    val optionalApps by appsList(
        filter = remember { DefaultAppFilter(context) },
        comparator = hiddenAppsComparator(hiddenApps)
    )
    val state = rememberLazyListState()
    PreferenceScaffold(
        label = pageTitle,
        floating = rememberFloatingState(state)
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
                        if (isHidden) {
                            newSet.add(key)
                        } else {
                            newSet.remove(key)
                        }
                        hiddenApps = newSet
                    }
                    preferenceGroupItems(
                        items = apps,
                        isFirstChild = true
                    ) { index, app ->
                        AppItem(
                            app = app,
                            onClick = toggleHiddenApp,
                            showDivider = index != apps.lastIndex
                        ) {
                            Checkbox(
                                checked = hiddenApps.contains(app.key.toString()),
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    uncheckedColor = MaterialTheme.colors.onBackground.copy(alpha = 0.48F)
                                )
                            )
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(
                    enabled = false,
                ) {
                    preferenceGroupItems(count = 20, isFirstChild = true) { index ->
                        AppItemPlaceholder(showDivider = index != 19)
                    }
                }
            }
        }
    }
}

@Composable
fun hiddenAppsCount(): Int = preferenceManager().hiddenAppSet.getState().value.size

@Composable
fun hiddenAppsComparator(hiddenApps: Set<String>): Comparator<App> =
    remember {
        comparing<App, Int> { if (hiddenApps.contains(it.key.toString())) 0 else 1 }
            .then(appComparator)
    }
